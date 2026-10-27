package com.music.streamx.data.sources

import android.net.Uri
import android.os.Bundle
import com.music.streamx.data.extensions.ApkExtensions
import com.music.streamx.data.model.Song
import kotlinx.coroutines.CancellationException
import org.json.JSONObject
import java.util.Locale

/** Native source IPC; host credentials and session cookies are never sent to an extension. */
class ApkExtensionSource(override val config: SourceConfig) : MusicSource, SourceRegistry.ConfigBacked {
    companion object {
        /** What each extension last advertised in its health check, by config id. */
        private val capabilities = java.util.concurrent.ConcurrentHashMap<String, Set<String>>()

        /** Whether [configId]'s extension has said it supports [operation]. False until its health has been read. */
        fun supports(configId: String, operation: String): Boolean = capabilities[configId]?.contains(operation) == true

        /** Reads [config]'s capabilities once per session, for menus that open before Sources has been visited. */
        suspend fun learnCapabilities(config: SourceConfig) {
            if (capabilities.containsKey(config.id)) return
            runCatching { ApkExtensionSource(config).health() }
        }
    }

    override val configId get() = config.id
    override val kind = SourceKind.APK_PLUGIN
    override val displayName get() = config.displayName
    private suspend fun response(method: String, extras: Bundle = Bundle()): JSONObject {
        val value = JSONObject(ApkExtensions.call(config, method, extras))
        check(!value.has("error")) { value.optString("error", "Extension request failed") }
        return value
    }
    override suspend fun health(): SourceHealth = try {
        val value = response("health")
        value.optJSONArray("capabilities")?.let { list ->
            capabilities[config.id] = (0 until list.length()).mapNotNull { list.optString(it).takeIf(String::isNotBlank) }.toSet()
        }
        if (value.optBoolean("ok")) SourceHealth.Ok(value.optString("message").ifBlank { null }) else SourceHealth.Rejected(value.optString("message", "Open the extension to sign in"))
    } catch (e: CancellationException) { throw e }
    catch (e: Exception) { SourceHealth.Unreachable(e.message ?: "Extension unavailable") }

    override suspend fun search(query: String, limit: Int, waitForAll: Boolean, request: StreamRequest?): List<Song> {
        val extras = Bundle().apply { putString("query", query); putInt("limit", limit.coerceIn(1, 100)) }
        val values = response("search", extras).optJSONArray("items") ?: return emptyList()
        return (0 until minOf(values.length(), limit.coerceIn(1, 100))).mapNotNull { index -> values.optJSONObject(index)?.let(::parseSong) }
    }
    private val songs = android.util.LruCache<String, Song>(500)
    fun cachedSong(videoId: String): Song? = songs.get(videoId)
    internal fun parseSong(value: JSONObject): Song? {
        if (value.has("type") && value.optString("type") != "track") return null
        val id = value.optString("id"); val title = value.optString("title")
        if (id.isBlank() || id.length > 256 || title.isBlank()) return null
        val seconds = value.optInt("duration_sec", 0)
        val album = value.optString("album_id").takeIf { it.isNotBlank() }
        val artist = value.optString("artist_id").takeIf { it.isNotBlank() }
        val song = Song(videoId = SourceRegistry.trackKey(config.id, id), title = title, artist = value.optString("artist"),
            albumName = value.optString("album").ifBlank { null },
            albumId = album?.let { com.music.streamx.data.extensions.ApkCatalogue.browseId(config.id, "album", it) },
            artistId = artist?.let { com.music.streamx.data.extensions.ApkCatalogue.browseId(config.id, "artist", it) },
            thumbnailUrl = value.optString("thumbnail_url").takeIf { it.startsWith("https://") },
            durationText = if (seconds > 0) "${seconds / 60}:${"%02d".format(Locale.ROOT, seconds % 60)}" else null,
            isExplicit = value.optBoolean("explicit"))
        songs.put(song.videoId, song)
        return song
    }
    override suspend fun stream(trackId: String, request: StreamRequest): SourceStream? {
        val extras = Bundle().apply {
            putString("track_id", trackId)
            putString("quality", when (request) { StreamRequest.Lossless -> "lossless"; StreamRequest.Best -> "best"; is StreamRequest.Capped -> "capped" })
            if (request is StreamRequest.Capped) putInt("max_kbps", request.maxKbps)
        }
        val value = response("stream", extras)
        val url = value.optString("url").ifBlank { return null }
        val uri = Uri.parse(url)
        require((uri.scheme == "https" && !uri.host.isNullOrBlank()) || (uri.scheme == "content" && uri.authority == config.extensionAuthority)) { "Extension returned an unsupported stream URI" }
        val headers = value.optJSONObject("headers")?.let { map -> map.keys().asSequence().associateWith { map.getString(it) } }.orEmpty()
        require(headers.size <= 32 && headers.all { (key, data) -> key.length <= 128 && data.length <= 8192 && '\n' !in key && '\r' !in key && '\n' !in data && '\r' !in data }) { "Invalid stream headers" }
        fun positive(key: String) = value.optInt(key, 0).takeIf { it > 0 }
        val format = StreamFormat(codec = value.optString("codec").ifBlank { null }, kbps = positive("kbps"),
            sampleRateHz = positive("sample_rate_hz"), bitDepth = positive("bit_depth"))
        if (request == StreamRequest.Lossless && format.isLossless != true) return null
        if (request is StreamRequest.Capped && (format.isLossless == true || (format.kbps ?: Int.MAX_VALUE) > request.maxKbps)) return null
        // A manifest says so: a content:// URI has no extension to judge by.
        value.optString("transport").takeIf { it.isNotBlank() }?.let { com.music.streamx.playback.StreamContainer.declare(url, it) }
        // Encrypted streams are licensed back through this extension, never decrypted here.
        value.optJSONObject("drm")?.let { drm ->
            require(com.music.streamx.playback.StreamDrm.declare(url, drm.optString("scheme"), config.id)) { "Extension stream uses an unsupported DRM scheme" }
            com.music.streamx.playback.smart.ProtectedAnalysis.rememberSource(config.id)
        }
        // What Automix may measure an encrypted track from: a small rendition
        // and its Widevine PSSH, decoded through the CDM — see ProtectedAnalysis.
        value.optJSONObject("analysis")?.let { analysis ->
            val analysisUrl = analysis.optString("url")
            val pssh = runCatching { android.util.Base64.decode(analysis.optString("pssh"), android.util.Base64.DEFAULT) }.getOrNull()
            if (analysisUrl.startsWith("https://") && pssh != null && pssh.size in 32..4096)
                com.music.streamx.playback.smart.ProtectedAnalysis.offer(config.id, trackId, analysisUrl, pssh)
        }
        return SourceStream(url, format, headers, durationSec = positive("duration_sec"))
    }
}
