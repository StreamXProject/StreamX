package com.music.streamx.data.extensions

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.net.URI
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

internal data class RepositoryExtension(
    val name: String,val packageName: String,val versionCode: Long,val versionName: String,
    val type: String,val abis: List<String>,val minSdk: Int,val apkUrl: String,
    val sha256: String,val certificateSha256: String,val size: Long,
    val repositoryUrl: String? = null, val repositoryName: String? = null,
    val iconUrl: String? = null,
)

internal data class RepositoryCatalogue(val entries: List<RepositoryExtension>, val errors: List<String>)

/** Official signer pins are fixed; user-added repositories retain the signers accepted when added. */
internal object ExtensionRepository {
    const val SIGNER="f0d96c1f8f886ba0c4049fdfeaddf3f945e06ac5c623f734f7a88d28edff10f2"
    private val client=OkHttpClient.Builder().followSslRedirects(false).connectTimeout(20,TimeUnit.SECONDS)
        .readTimeout(60,TimeUnit.SECONDS).callTimeout(10,TimeUnit.MINUTES).build()
    private const val MAX_APK=200L*1024*1024
    internal fun safeIconUrl(value: String): String? = runCatching {
        val uri = URI(value.trim())
        value.trim().takeIf {
            uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null && uri.fragment == null
        }
    }.getOrNull()

    internal fun indexUrl(type: String): String {
        check(type == "skin" || type == "plugin") { "Unknown extension type" }
        return "https://streamxproject.github.io/StreamX-Releases/extensions/index.json"
    }
    private fun trustedRepositories(type: String) = when (type) {
        "skin" -> setOf("StreamX-Releases", "Plugins", "Skins") // Keep existing public skin releases installable.
        "plugin" -> setOf("StreamX-Releases", "Plugins")
        else -> error("Unknown extension type")
    }
    internal fun parseIndex(json: String,type: String,supportedAbis: List<String>,sdk: Int, source: ExtensionRepositoryLink? = null): List<RepositoryExtension> {
        val index=JSONObject(json)
        check(index.getInt("schemaVersion")==1){"Update StreamX to read this extension repository"}
        val array=index.getJSONArray("extensions")
        check(array.length()<=500){"Extension index is too large"}
        val all=(0 until array.length()).map {i->val item=array.getJSONObject(i);val abis=item.getJSONArray("abis")
            RepositoryExtension(item.getString("name"),item.getString("packageName"),item.getLong("versionCode"),item.getString("versionName"),
                item.getString("type"),(0 until abis.length()).map{abis.getString(it)},item.getInt("minSdk"),item.getString("apkUrl"),
                item.getString("sha256"),item.getString("certificateSha256"),item.getLong("size"),source?.url,source?.name,
                safeIconUrl(item.optString("iconUrl")))
        }.filter{it.type==type && it.minSdk<=sdk && (it.abis.isEmpty() || it.abis.any{abi->abi in supportedAbis})}
        all.forEach {item->
            check(item.packageName.matches(Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+"))){"Invalid extension package"}
            if (source == null) check(item.packageName.startsWith(if(type=="skin")"com.music.streamx.skins." else "com.music.streamx.")){"Unexpected extension package"}
            check(item.versionCode>0 && item.size in 1..MAX_APK && item.sha256.matches(Regex("[a-f0-9]{64}"))){"Invalid extension metadata"}
            check(item.certificateSha256.matches(Regex("[a-f0-9]{64}"))){"Invalid signing certificate"}
            if (source == null) check(item.certificateSha256==SIGNER){"Repository signing certificate is not trusted"}
            else check(source.certificates.isEmpty() || item.certificateSha256 in source.certificates){"Repository signing key changed. Remove and add the repository again to accept its new key."}
            val uri=URI(item.apkUrl)
            check(uri.scheme=="https" && !uri.host.isNullOrBlank() && uri.rawQuery==null && uri.userInfo==null && uri.fragment==null && uri.path.endsWith(".apk")){"Invalid HTTPS APK download URL"}
            if (source == null) check(uri.host=="github.com" &&
                trustedRepositories(type).any { repo -> uri.path.startsWith("/StreamXProject/$repo/releases/download/") }){"Untrusted APK download URL"}
        }
        return all.groupBy{it.packageName}.map {(_,variants)->
            val newest=variants.maxOf{it.versionCode}
            variants.filter{it.versionCode==newest}.minBy{entry->
                if(entry.abis.size==1)supportedAbis.indexOf(entry.abis.first()).takeIf{it>=0} ?: Int.MAX_VALUE else 100
            }
        }.sortedBy{it.name.lowercase()}
    }
    private fun readIndex(response: okhttp3.Response, official: Boolean = true): String {
        check(response.isSuccessful){when(response.code){
            401,403->if (official) "The extension repository is unavailable or access was denied."
                else "Repository access was rejected. Use a public repository link."
            404->if (official) "The extension repository is unavailable or has no published index."
                else "Repository index was not found. Use the contributor’s published index.json link."
            else->"Could not load extensions (HTTP ${response.code})"
        }}
        val body=requireNotNull(response.body)
        check(body.contentLength()<=2*1024*1024){"Extension index is too large"}
        val bytes=body.byteStream().use {input->
            val output=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192)
            while(output.size()<=2*1024*1024){val read=input.read(buffer,0,minOf(buffer.size,2*1024*1024+1-output.size()));if(read<0)break;output.write(buffer,0,read)}
            output.toByteArray()
        }
        check(bytes.size<=2*1024*1024){"Extension index is too large"}
        return bytes.toString(Charsets.UTF_8)
    }
    private fun assetRequest(repository: String,tag: String,filename: String,token: String): Request {
        if(token.isBlank())return Request.Builder().url("https://github.com/StreamXProject/${repository}/releases/download/$tag/$filename").header("Cache-Control","no-cache").build()
        val encoded=java.net.URLEncoder.encode(tag,"UTF-8").replace("+","%20")
        val url="https://api.github.com/repos/StreamXProject/${repository}/releases/tags/$encoded"
        val request=Request.Builder().url(url).header("Authorization","Bearer $token")
            .header("Accept","application/vnd.github+json").header("X-GitHub-Api-Version","2022-11-28").build()
        val release=client.newCall(request).execute().use{JSONObject(readIndex(it))}
        val assets=release.getJSONArray("assets")
        val asset=(0 until assets.length()).map{assets.getJSONObject(it)}.firstOrNull{it.getString("name")==filename}
            ?: error("Release asset is not published yet")
        val id=asset.getLong("id");check(id>0){"Invalid release asset"}
        // Only GitHub's API receives the credential. OkHttp strips it on cross-host redirects.
        return Request.Builder().url("https://api.github.com/repos/StreamXProject/${repository}/releases/assets/$id")
            .header("Authorization","Bearer $token").header("Accept","application/octet-stream")
            .header("X-GitHub-Api-Version","2022-11-28").build()
    }
    suspend fun load(context: Context,type: String): List<RepositoryExtension> = withContext(Dispatchers.IO) {
        // The Pages catalogue and its public APKs never receive a saved GitHub credential.
        val request = Request.Builder().url(indexUrl(type)).header("Cache-Control", "no-cache").build()
        client.newCall(request).execute().use {response->
            parseIndex(readIndex(response),type,Build.SUPPORTED_ABIS.toList(),Build.VERSION.SDK_INT)
        }
    }
    private suspend fun loadLink(source: ExtensionRepositoryLink): String = withContext(Dispatchers.IO) {
        // User-added hosts never receive the official repositories' GitHub credential.
        client.newCall(Request.Builder().url(source.url).header("Cache-Control", "no-cache").build())
            .execute().use { readIndex(it, official = false) }
    }
    suspend fun inspectLink(value: String): ExtensionRepositoryLink {
        val url = ExtensionRepositoryLinks.normalizeUrl(value)
        val draft = ExtensionRepositoryLink(url, URI(url).host, emptySet())
        val json = loadLink(draft)
        val index = JSONObject(json)
        val entries = listOf("plugin", "skin").flatMap {
            // Include releases for other devices so adding a repository is not tied to this ABI.
            parseIndex(json, it, listOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64"), Int.MAX_VALUE, draft)
        }
        check(entries.isNotEmpty()) { "No StreamX plugins or skins found in this repository" }
        val github = URI(url).takeIf { it.host == "github.com" }?.path?.trim('/')?.split('/')
        val name = index.optString("name").trim().take(80).ifEmpty {
            if (github != null && github.size >= 2) "${github[0]}/${github[1]}" else URI(url).host
        }
        return draft.copy(name = name, certificates = entries.map { it.certificateSha256 }.toSet())
    }
    suspend fun loadCatalogue(context: Context, type: String, links: List<ExtensionRepositoryLink>): RepositoryCatalogue = coroutineScope {
        suspend fun attempt(name: String, block: suspend () -> List<RepositoryExtension>): Pair<List<RepositoryExtension>, String?> =
            try { block() to null }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { emptyList<RepositoryExtension>() to "$name: ${failure.message ?: "Could not load repository"}" }
        val official = async { attempt("StreamX") { load(context, type) } }
        val contributors = links.map { link -> async { attempt(link.name) {
            parseIndex(loadLink(link), type, Build.SUPPORTED_ABIS.toList(), Build.VERSION.SDK_INT, link)
        } } }
        val results = listOf(official.await()) + contributors.map { it.await() }
        RepositoryCatalogue(results.flatMap { it.first }.distinctBy { it.packageName }, results.mapNotNull { it.second })
    }
    @Suppress("DEPRECATION")
    suspend fun download(context: Context,item: RepositoryExtension,onProgress: (Int)->Unit): File = withContext(Dispatchers.IO) {
        val directory=File(context.cacheDir,"updates/extensions").apply{mkdirs()}
        val target=File(directory,"${item.packageName}-${item.versionCode}.apk")
        val partial=File(directory,target.name+".part")
        try {
            val request = if (item.repositoryUrl == null) URI(item.apkUrl).path.split('/').let { parts ->
                check(parts.size == 7) { "Invalid release asset URL" }
                if (parts[2] == "Plugins") {
                    // Compatibility for an older private catalogue cached by an existing install.
                    assetRequest(parts[2], parts[5], parts[6], RepositoryAccess.token(context))
                } else Request.Builder().url(item.apkUrl).build()
            } else {
                val link = ExtensionRepositoryLinks.observe(context).value.firstOrNull { it.url == item.repositoryUrl }
                    ?: error("This repository was removed. Refresh the extension list.")
                check(item.certificateSha256 in link.certificates) { "Repository signing certificate changed" }
                Request.Builder().url(item.apkUrl).build()
            }
            client.newCall(request).execute().use {response->
                check(response.isSuccessful){"Download failed (HTTP ${response.code})"}
                val body=requireNotNull(response.body)
                check(body.contentLength()<=MAX_APK){"APK is too large"}
                var count=0L;val digest=MessageDigest.getInstance("SHA-256");var previous=-1
                body.byteStream().use {input->partial.outputStream().use {output->
                    val buffer=ByteArray(64*1024)
                    while(true){coroutineContext.ensureActive();val read=input.read(buffer);if(read<0)break
                        count+=read;check(count<=item.size && count<=MAX_APK){"Download size does not match repository"}
                        output.write(buffer,0,read);digest.update(buffer,0,read)
                        val percent=(count*100/item.size).toInt()
                        if(percent!=previous){onProgress(percent);previous=percent}
                    }
                }}
                check(count==item.size && digest.digest().joinToString(""){"%02x".format(it)}==item.sha256){"APK checksum did not match; retry the download"}
            }
            val info=context.packageManager.getPackageArchiveInfo(partial.absolutePath,
                if(Build.VERSION.SDK_INT>=28)PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES)
                ?: error("Downloaded file is not a valid APK")
            check(info.packageName==item.packageName && (if(Build.VERSION.SDK_INT>=28)info.longVersionCode else info.versionCode.toLong())==item.versionCode){"Downloaded APK identity does not match repository"}
            val signers=if(Build.VERSION.SDK_INT>=28)info.signingInfo?.apkContentsSigners.orEmpty() else info.signatures.orEmpty()
            check(signers.size==1 && ApkExtension.digest(signers.single().toByteArray())==item.certificateSha256){"APK signing certificate is not trusted"}
            val installed=runCatching{context.packageManager.getPackageInfo(item.packageName,0)}.getOrNull()
            if(installed!=null){
                check(ApkExtensions.certificate(item.packageName)==item.certificateSha256){"Installed extension uses a different signing key"}
                val version=if(Build.VERSION.SDK_INT>=28)installed.longVersionCode else installed.versionCode.toLong()
                check(item.versionCode>version){"This extension is already up to date"}
            }
            check(partial.renameTo(target)){"Could not save downloaded APK"}
            target
        } catch(cancelled: CancellationException){partial.delete();throw cancelled}
        catch(failure: Exception){partial.delete();throw failure}
    }
}
