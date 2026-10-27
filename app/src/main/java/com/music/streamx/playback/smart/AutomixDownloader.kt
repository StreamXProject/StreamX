package com.music.streamx.playback.smart

import com.music.streamx.BuildConfig
import kotlinx.coroutines.ensureActive
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream
import kotlin.coroutines.coroutineContext

/**
 * Fetches and installs an advanced Automix package; see [AutomixEngine].
 *
 * Nothing becomes visible to the engine until it has been checked: the zip
 * downloads to a `.part` file (kept across failures and resumed with a Range
 * request), is hashed against the manifest, unpacked into a staging directory,
 * each file checked against the package's own description — and the native
 * pair against the pins compiled into the app — and only then renamed into its
 * version directory in one step.
 */
internal object AutomixDownloader {

    /** One ABI's entry in automix-manifest.json. */
    data class Entry(val version: Int, val file: String, val size: Long, val sha256: String)

    /** A package's own package.json. */
    data class Description(val version: Int, val abi: String, val onnxRuntime: String, val files: List<FileEntry>) {
        data class FileEntry(val name: String, val size: Long, val sha256: String)

        companion object {
            fun parse(text: String): Description {
                val json = JSONObject(text)
                val files = json.getJSONArray("files")
                return Description(
                    version = json.getInt("version"),
                    abi = json.getString("abi"),
                    onnxRuntime = json.getString("onnxRuntime"),
                    files = List(files.length()) { index ->
                        val file = files.getJSONObject(index)
                        FileEntry(file.getString("name"), file.getLong("size"), file.getString("sha256"))
                    },
                )
            }
        }
    }

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    private fun url(name: String) = "${BuildConfig.AUTOMIX_BASE_URL}/$name"

    /** The repository access token, when one has been saved; see [AutomixEngine.attach]. */
    @Volatile var token: () -> String = { "" }

    private val githubRelease = Regex("https://github\\.com/(StreamXProject/[A-Za-z0-9._-]+)/releases/download/([A-Za-z0-9._-]+)")

    /**
     * How to fetch [name]. A plain GET of the base URL, unless that URL is a
     * release of one of StreamX's own repositories and an access token is
     * saved: those repositories are private, so the public download link
     * answers 404, and the file is fetched through GitHub's API as the
     * plugin catalogue does. Only api.github.com sees the token — OkHttp drops
     * it on the redirect to the download host — and no other host ever does.
     */
    private fun request(name: String): Request.Builder {
        val release = githubRelease.matchEntire(BuildConfig.AUTOMIX_BASE_URL)
        val key = if (release != null) runCatching { token() }.getOrDefault("") else ""
        if (release == null || key.isBlank()) return Request.Builder().url(url(name))
        val (repository, tag) = release.destructured
        val listing = Request.Builder().url("https://api.github.com/repos/$repository/releases/tags/$tag")
            .header("Authorization", "Bearer $key").header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28").build()
        val assets = client.newCall(listing).execute().use {
            if (!it.isSuccessful) throw IOException("Release: HTTP ${it.code}")
            JSONObject(it.body.string()).getJSONArray("assets")
        }
        val id = (0 until assets.length()).map { assets.getJSONObject(it) }
            .firstOrNull { it.getString("name") == name }?.getLong("id") ?: throw IOException("$name is not published")
        return Request.Builder().url("https://api.github.com/repos/$repository/releases/assets/$id")
            .header("Authorization", "Bearer $key").header("Accept", "application/octet-stream")
            .header("X-GitHub-Api-Version", "2022-11-28")
    }

    /** The manifest's package for [abi], refused unless it is for the ONNX Runtime the app was built with. */
    fun manifest(abi: String): Entry {
        val text = client.newCall(request("automix-manifest.json").build()).execute().use {
            if (!it.isSuccessful) throw IOException("Manifest: HTTP ${it.code}")
            it.body.string()
        }
        val json = JSONObject(text)
        val runtime = json.getString("onnxRuntime")
        if (runtime != BuildConfig.AUTOMIX_ONNX_RUNTIME) {
            throw IOException("Package is for ONNX Runtime $runtime, this app needs ${BuildConfig.AUTOMIX_ONNX_RUNTIME}")
        }
        val version = json.getInt("version")
        if (version < BuildConfig.AUTOMIX_PACKAGE_VERSION) throw IOException("Package version $version is too old")
        val packages = json.getJSONObject("packages")
        if (!packages.has(abi)) throw IOException("No package for $abi")
        val entry = packages.getJSONObject(abi)
        val file = entry.getString("file")
        require(file.matches(Regex("[A-Za-z0-9._-]+"))) { "Bad package name" }
        return Entry(version, file, entry.getLong("size"), entry.getString("sha256").lowercase())
    }

    suspend fun install(
        root: File,
        abi: String,
        entry: Entry,
        pins: Pair<String, String>,
        progress: (AutomixEngine.State) -> Unit,
    ) {
        val downloads = File(root, "download").apply { mkdirs() }
        // Partial downloads of any other package are stale now.
        downloads.listFiles().orEmpty().filter { it.name != "${entry.file}.part" }.forEach { it.deleteRecursively() }
        val part = File(downloads, "${entry.file}.part")
        fetch(entry, part, progress)

        progress(AutomixEngine.State.Verifying)
        if (part.length() != entry.size || AutomixEngine.sha256(part) != entry.sha256) {
            part.delete()
            throw IOException("Download was corrupted; retry to fetch it again")
        }

        val staging = File(root, "staging").apply { deleteRecursively(); mkdirs() }
        try {
            unpack(part, staging)
            val description = Description.parse(File(staging, "package.json").readText())
            check(description.version == entry.version) { "Package version does not match the manifest" }
            check(description.abi == abi) { "Package is for ${description.abi}, not $abi" }
            check(description.onnxRuntime == BuildConfig.AUTOMIX_ONNX_RUNTIME) { "Package ONNX Runtime mismatch" }
            check(description.files.map { it.name }.toSet() == AutomixEngine.REQUIRED) { "Package is incomplete" }
            for (file in description.files) {
                val local = File(staging, file.name)
                check(local.length() == file.size && AutomixEngine.sha256(local) == file.sha256.lowercase()) {
                    "${file.name} failed verification"
                }
            }
            // Matched to the Java classes compiled into this app, whatever the manifest says.
            check(AutomixEngine.sha256(File(staging, "libonnxruntime.so")) == pins.first) { "Engine is not the pinned build" }
            check(AutomixEngine.sha256(File(staging, "libonnxruntime4j_jni.so")) == pins.second) { "Bridge is not the pinned build" }

            val target = File(root, "v${entry.version}-$abi")
            target.deleteRecursively()
            if (!staging.renameTo(target)) throw IOException("Could not install the package")
            part.delete()
        } finally {
            staging.deleteRecursively()
        }
    }

    private suspend fun fetch(entry: Entry, part: File, progress: (AutomixEngine.State) -> Unit) {
        if (part.length() > entry.size) part.delete()
        val have = part.length()
        if (have == entry.size) return
        val request = request(entry.file)
            .apply { if (have > 0) header("Range", "bytes=$have-") }
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Download: HTTP ${response.code}")
            // A server that ignores Range answers 200 with the whole file: start over.
            val append = have > 0 && response.code == 206
            var done = if (append) have else 0L
            progress(AutomixEngine.State.Downloading(done, entry.size))
            java.io.FileOutputStream(part, append).use { output ->
                response.body.byteStream().use { input ->
                    val buffer = ByteArray(1 shl 16)
                    var reported = 0L
                    while (true) {
                        coroutineContext.ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        done += read
                        if (done > entry.size) throw IOException("Download is larger than the manifest says")
                        if (done - reported >= 256 * 1024 || done == entry.size) {
                            reported = done
                            progress(AutomixEngine.State.Downloading(done, entry.size))
                        }
                    }
                }
            }
        }
    }

    /** Extracts only the expected flat file names, so no entry can write outside [into]. */
    private fun unpack(zip: File, into: File) {
        val allowed = AutomixEngine.REQUIRED + "package.json"
        ZipInputStream(zip.inputStream().buffered()).use { stream ->
            while (true) {
                val zipEntry = stream.nextEntry ?: break
                check(!zipEntry.isDirectory && zipEntry.name in allowed) { "Unexpected file ${zipEntry.name}" }
                File(into, zipEntry.name).outputStream().use { stream.copyTo(it) }
            }
        }
    }
}
