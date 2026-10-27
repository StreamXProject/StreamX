package com.music.streamx.playback

import android.net.Uri
import android.util.Log
import androidx.media3.datasource.DataSpec
import java.io.File
import java.io.OutputStream
import java.util.concurrent.ConcurrentHashMap

/**
 * Keeps the audio of natively streamed plugin tracks as playback reads it,
 * for Automix to analyse without fetching the song a second time — an HLS
 * stream's fragments (Apple Music), or a progressive stream whole (Spotify).
 *
 * The native Apple Music extension plays through HLS served from a content
 * provider, which [AudioCache.playbackFactory] reads past the cache. Each read
 * that factory makes is offered here: the track's playlist (opened under its
 * `streamx://source` URI) tells which fragments belong to it and in what
 * order, and every fragment read whole from its start is kept. Once every
 * fragment of the playlist has been read — the track has been played, or
 * buffered, through — they are joined into one file, which is a fragmented
 * MP4 the analyzer decodes like a local file (see
 * [com.music.streamx.playback.smart.TrackAnalyzer]). A fragment skipped by a
 * seek leaves the track uncaptured this time, and it is analysed on a later
 * play instead.
 *
 * Best-effort throughout: nothing here may fail a playback read.
 */
internal object PlayedHlsCapture {

    private const val TAG = "StreamXHlsCapture"
    private const val KEEP = 4

    @Volatile private var root: File? = null

    /** Which track a provider URI belongs to, learned from the playlists already read. */
    private val owners = ConcurrentHashMap<String, String>()

    /** Each track's fragments, init section first, in playlist order. */
    private val parts = ConcurrentHashMap<String, List<String>>()

    fun init(cacheDir: File) {
        root = File(cacheDir, "automix-hls").apply { mkdirs() }
    }

    /** The joined copy of [trackId], once there is one. */
    fun fileFor(trackId: String): File? = target(trackId)?.takeIf { it.isFile }

    /** Where [trackId]'s joined copy goes, whoever writes it. */
    fun target(trackId: String): File? = root?.let { File(it, "${slug(trackId)}.played.media") }

    /** Whether playback has opened [trackId]'s playlist and is reading its fragments. */
    fun isLoading(trackId: String): Boolean = parts.containsKey(trackId) || owners.containsValue(trackId)

    /** Drops all but the newest few joined copies. */
    fun prune() {
        root?.listFiles { candidate -> candidate.name.endsWith(".media") }.orEmpty()
            .sortedByDescending { it.lastModified() }.drop(KEEP).forEach { it.delete() }
    }

    /**
     * A sink for one playback read, or null when it is not worth keeping.
     * [resolved] is where the read actually went, which for the playlist is the
     * provider URI its fragment references are relative to.
     */
    fun open(spec: DataSpec, resolved: Uri?): Sink? = runCatching {
        val dir = root ?: return null
        if (spec.position != 0L) return null
        val direct = spec.uri.getQueryParameter("extension_native") == "1"
        val trackId = when {
            direct -> mediaIdIn(spec.uri)
            spec.uri.scheme == "content" -> owners[spec.uri.toString()]
            else -> null
        } ?: return null
        if (fileFor(trackId) != null) return null
        val uri = (resolved ?: spec.uri).toString()
        val work = File(dir, slug(trackId)).apply { mkdirs() }
        val kept = File(work, slug(uri))
        if (kept.isFile) return null
        Sink(trackId, uri, kept, direct)
    }.getOrNull()

    class Sink internal constructor(
        private val trackId: String,
        private val uri: String,
        private val target: File,
        /** The read of the track's own URI: its playlist, or for a progressive plugin the whole track. */
        private val direct: Boolean,
    ) {
        private val temporary = File(target.path + ".tmp")
        private val output: OutputStream = temporary.outputStream().buffered()
        private var failed = false

        fun write(buffer: ByteArray, offset: Int, length: Int) {
            if (failed || length <= 0) return
            runCatching { output.write(buffer, offset, length) }.onFailure { failed = true }
        }

        /** The read reached its end: keep what was read. */
        fun finish() {
            runCatching {
                output.close()
                if (failed || !temporary.renameTo(target)) error("not kept")
                completed(trackId, uri, target, direct)
            }.onFailure { temporary.delete() }
        }

        /** The read stopped short: drop it. */
        fun abort() {
            runCatching { output.close() }
            temporary.delete()
        }
    }

    private fun completed(trackId: String, uri: String, file: File, direct: Boolean) {
        val head = file.inputStream().use { input -> ByteArray(7).also { input.read(it) } }
        if (head.decodeToString() == "#EXTM3U") {
            playlist(trackId, uri, file.readText())
            file.delete()
            return
        }
        if (direct) {
            // Not a playlist: a progressive stream (Spotify's Ogg Vorbis), read whole.
            val joined = target(trackId) ?: return
            if (file.renameTo(joined)) {
                file.parentFile?.deleteRecursively()
                Log.d(TAG, "Captured $trackId from playback: one stream, ${joined.length()} bytes")
                prune()
            }
            return
        }
        synchronized(this) {
            val order = parts[trackId] ?: return
            val work = file.parentFile ?: return
            val pieces = order.map { File(work, slug(it)) }
            if (!pieces.all { it.isFile }) return
            val joined = File(root, "${slug(trackId)}.played.media")
            val temporary = File(joined.path + ".tmp")
            temporary.outputStream().buffered().use { output -> pieces.forEach { piece -> piece.inputStream().use { it.copyTo(output) } } }
            temporary.renameTo(joined)
            work.deleteRecursively()
            parts.remove(trackId)
            owners.values.removeAll { it == trackId }
            Log.d(TAG, "Captured $trackId from playback: ${pieces.size} parts, ${joined.length()} bytes")
            prune()
        }
    }

    private fun playlist(trackId: String, uri: String, text: String) {
        val references = buildList {
            Regex("""#EXT-X-MAP:[^\n]*URI="([^"]+)"""").find(text)?.let { add(it.groupValues[1]) }
            addAll(text.lineSequence().map(String::trim).filter { it.isNotEmpty() && !it.startsWith('#') })
        }.map { resolve(uri, it) }
        references.forEach { owners[it] = trackId }
        // A master playlist only names variants, which are read as playlists in turn.
        // An encrypted one is kept out too: its fragments would not decode.
        val encrypted = Regex("""#EXT-X-KEY:(?![^\n]*METHOD=NONE)""").containsMatchIn(text)
        if (!text.contains("#EXT-X-STREAM-INF") && !encrypted) {
            parts[trackId] = references
        }
    }

    private fun resolve(base: String, reference: String): String = when {
        Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:").containsMatchIn(reference) -> reference
        else -> Uri.parse(base).let { parsed ->
            parsed.buildUpon().encodedQuery(null)
                .encodedPath((parsed.encodedPath ?: "").substringBeforeLast('/') + "/" + reference.substringBefore('?'))
                .encodedQuery(reference.substringAfter('?', "").ifEmpty { null })
                .build().toString()
        }
    }

    private fun slug(value: String): String = value.hashCode().toUInt().toString(16)
}
