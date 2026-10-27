package com.music.streamx.download

import android.content.Context
import android.net.Uri
import android.os.Bundle
import com.music.streamx.data.extensions.ApkExtensions
import com.music.streamx.data.model.Song
import com.music.streamx.data.sources.*
import org.json.JSONObject
import java.io.OutputStream
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

/** Uses the selected plugin's account and native mode, independent of home-source visibility. */
internal object PluginDownloads {
    suspend fun nativeStream(track: Song): SourceStream? {
        val (source, id) = SourceRegistry.parseTrackKey(track.videoId) ?: return null
        val config = SourceRegistry.configs.value.firstOrNull { it.id == source && it.kind == SourceKind.APK_PLUGIN } ?: return null
        suspend fun call(method: String, args: Bundle = Bundle()): JSONObject {
            val result = JSONObject(ApkExtensions.call(config, method, args))
            check(!result.has("error")) { result.optString("error") }
            return result
        }
        val preferences = call("preferences")
        return when (config.extensionPackage) {
            "com.music.streamx.extensions.applemusic" -> {
                if (!preferences.optBoolean("alac_enabled")) null
                else requireNotNull(ApkExtensionSource(config).stream(id, StreamRequest.Best)) { "Apple Music did not provide its native download" }
            }
            "com.music.streamx.extensions.spotify" -> {
                if (!preferences.optBoolean("native_enabled") || !preferences.optBoolean("native_available")) null
                else {
                    val result = call("download", Bundle().apply { putString("track_id", id) })
                    val uri = Uri.parse(result.getString("url"))
                    require(uri.scheme == "content" && uri.authority == config.extensionAuthority) { "Invalid plugin download URI" }
                    SourceStream(uri.toString(), StreamFormat(codec = result.getString("codec"), kbps = result.optInt("kbps").takeIf { it > 0 }))
                }
            }
            else -> null
        }
    }

    fun readSegment(context: Context, origin: String, target: String): ByteArray {
        val uri = Uri.parse(target)
        require(uri.scheme == "content" && uri.authority == Uri.parse(origin).authority) { "Invalid plugin segment" }
        return requireNotNull(context.contentResolver.openInputStream(uri)).use { input ->
            val out = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(32768)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                check(out.size().toLong() + count <= 64L * 1024 * 1024) { "Plugin segment is too large" }
                out.write(buffer, 0, count)
            }
            out.toByteArray()
        }
    }

    suspend fun copy(context: Context, url: String, sink: OutputStream, progress: (Long, Long) -> Unit) {
        requireNotNull(context.contentResolver.openInputStream(Uri.parse(url))).use { input ->
            var bytes = 0L
            var lastProgressAt = 0L
            val buffer = ByteArray(65536)
            while (true) {
                coroutineContext.ensureActive()
                val count = input.read(buffer)
                if (count < 0) break
                bytes += count
                check(bytes <= 512L * 1024 * 1024) { "Plugin download is too large" }
                sink.write(buffer, 0, count)
                val now = android.os.SystemClock.elapsedRealtime()
                if (lastProgressAt == 0L || now - lastProgressAt >= 250L) {
                    progress(bytes, -1)
                    lastProgressAt = now
                }
            }
            progress(bytes, -1)
        }
    }
}
