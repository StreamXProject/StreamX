package com.music.streamx.playback.smart

import android.content.Context
import android.net.Uri
import android.util.Log
import com.music.streamx.data.Http
import com.music.streamx.data.sources.SourceResolver
import com.music.streamx.playback.PlayedHlsCapture
import kotlinx.coroutines.ensureActive
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.io.InputStream
import kotlin.coroutines.coroutineContext

/**
 * Reads the *next* natively streamed plugin track ahead of playback, so
 * Automix can measure how it begins before the transition into it — and, for
 * Spotify, so it then plays from a seekable local copy (see
 * [com.music.streamx.data.sources.SourceResolver.resolve]).
 *
 * [PlayedHlsCapture] only sees what playback reads, and playback starts on the
 * incoming track seconds before the blend — too late to analyse it. This reads
 * the same stream through the same plugin, one fragment at a time, into the
 * file [PlayedHlsCapture] would have written. It is not a second download: the
 * Apple Music extension keeps fragments per song (not per playback session), so
 * when the track then plays, its fragments come from the extension's cache.
 */
internal object HlsReadAhead {

    private const val TAG = "StreamXHlsReadAhead"

    suspend fun read(context: Context, trackId: String, playbackUri: Uri): File {
        val target = PlayedHlsCapture.target(trackId) ?: throw IOException("Capture is not initialised")
        if (target.isFile) return target
        val stream = SourceResolver.resolve(playbackUri) ?: throw IOException("No stream")

        fun open(url: String): InputStream =
            if (url.startsWith("content:")) {
                context.contentResolver.openInputStream(Uri.parse(url)) ?: throw IOException("Provider returned nothing")
            } else {
                val response = Http.client.newCall(
                    Request.Builder().url(url).apply { stream.headers.forEach { (name, value) -> header(name, value) } }.build(),
                ).execute()
                if (!response.isSuccessful) { response.close(); throw IOException("HLS HTTP ${response.code}") }
                response.body.byteStream()
            }
        fun resolve(base: String, reference: String): String = when {
            Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:").containsMatchIn(reference) -> reference
            base.startsWith("content:") -> base.substringBefore('?').substringBeforeLast('/') + "/" + reference
            else -> base.toHttpUrlOrNull()?.resolve(reference)?.toString() ?: throw IOException("Bad URL $reference")
        }

        var url = stream.url
        // A progressive stream (Spotify's Ogg Vorbis) is the track itself: kept whole.
        if (stream.format.codec == "vorbis") return keep(trackId, target) { output -> open(url).use { it.copyTo(output) } }
        var playlist = open(url).use { it.readBytes().decodeToString() }
        if (!playlist.startsWith("#EXTM3U")) throw IOException("Not an HLS playlist")
        if (playlist.contains("#EXT-X-STREAM-INF")) {
            val variant = Regex("""#EXT-X-STREAM-INF:([^\n]*)\n\s*([^#\s][^\n]*)""").findAll(playlist)
                .minByOrNull { Regex("""BANDWIDTH=(\d+)""").find(it.groupValues[1])?.groupValues?.get(1)?.toLong() ?: Long.MAX_VALUE }
                ?.groupValues?.get(2)?.trim() ?: throw IOException("HLS master has no variants")
            url = resolve(url, variant)
            playlist = open(url).use { it.readBytes().decodeToString() }
        }
        if (Regex("""#EXT-X-KEY:(?![^\n]*METHOD=NONE)""").containsMatchIn(playlist)) throw IOException("Encrypted HLS")
        val parts = buildList {
            Regex("""#EXT-X-MAP:[^\n]*URI="([^"]+)"""").find(playlist)?.let { add(it.groupValues[1]) }
            addAll(playlist.lineSequence().map(String::trim).filter { it.isNotEmpty() && !it.startsWith('#') })
        }
        if (parts.isEmpty()) throw IOException("HLS playlist has no fragments")

        return keep(trackId, target) { output ->
            for (reference in parts) {
                coroutineContext.ensureActive()
                open(resolve(url, reference)).use { it.copyTo(output) }
            }
        }
    }

    private suspend fun keep(trackId: String, target: File, write: suspend (java.io.OutputStream) -> Unit): File {
        val temporary = File(target.path + ".ahead")
        try {
            temporary.outputStream().buffered().use { write(it) }
            // Playback may have finished its own copy meanwhile; either is the same audio.
            if (!target.isFile && !temporary.renameTo(target)) throw IOException("Could not keep the copy")
        } finally {
            temporary.delete()
        }
        PlayedHlsCapture.prune()
        Log.d(TAG, "Read $trackId ahead of playback: ${target.length()} bytes")
        return target
    }
}
