package com.music.streamx.playback.smart

import android.content.Context
import android.content.SharedPreferences
import android.media.MediaCrypto
import android.media.MediaDataSource
import android.media.MediaDrm
import android.media.NotProvisionedException
import android.net.Uri
import android.util.Log
import androidx.media3.common.C
import com.music.streamx.data.Http
import com.music.streamx.data.sources.SourceRegistry
import com.music.streamx.data.sources.StreamRequest
import com.music.streamx.playback.StreamDrm
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Lets Automix measure tracks whose audio is encrypted (Amazon Music), without
 * that audio ever existing decrypted outside a decoder.
 *
 * The analyzer normally reads a track's cached file. An encrypted DASH track
 * has no such file — its cache holds a manifest and encrypted segments — so a
 * plain decode fails, and failed on every tick. Instead the extension names a
 * small rendition for analysis (Amazon's lowest Opus tier, a single fragmented
 * MP4 of a megabyte or two) along with its Widevine PSSH. That file is fetched
 * into memory as it is, still encrypted, and the analyzer decodes it through
 * [AudioDecoder] with a [MediaCrypto] from this device's CDM — the same keys,
 * from the same licence path ([StreamDrm.licence]), that playback uses.
 * Decryption happens inside the codec; the only thing that leaves the decode is
 * the PCM the analyzer reduces to beat and energy features, held in memory for
 * the length of one analysis pass. Nothing decrypted is written anywhere.
 *
 * Works the same for the track playing and the one queued after it, which is
 * what gives the incoming side of a blend its measurements before the blend.
 */
object ProtectedAnalysis {

    enum class State { NOT_PROTECTED, PENDING, READY, UNAVAILABLE }

    private const val TAG = "StreamXProtected"
    private const val MAX_BYTES = 24L shl 20
    private const val KEEP = 3
    private val WIDEVINE: UUID = C.WIDEVINE_UUID

    private class Offer(val configId: String, val url: String, val pssh: ByteArray)

    private val offers = ConcurrentHashMap<String, Offer>()
    private val fetching = ConcurrentHashMap.newKeySet<String>()
    private val failed = ConcurrentHashMap.newKeySet<String>()
    /** Fetched copies, newest last; bounded to the few tracks around the playhead. */
    private val copies = LinkedHashMap<String, Copy>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        if (prefs == null) prefs = context.applicationContext.getSharedPreferences("protected_analysis", Context.MODE_PRIVATE)
    }

    /**
     * Records that [configId] serves encrypted streams. Persisted, so a queued
     * track from that source is routed here before anything has resolved it
     * this session — otherwise its first analysis would read the manifest.
     */
    fun rememberSource(configId: String) {
        val store = prefs ?: return
        val known = store.getStringSet("sources", emptySet()).orEmpty()
        if (configId !in known) store.edit().putStringSet("sources", known + configId).apply()
    }

    fun isProtectedSource(configId: String) =
        prefs?.getStringSet("sources", emptySet()).orEmpty().contains(configId)

    /** What the extension said to analyse [trackId] from. */
    fun offer(configId: String, trackId: String, url: String, pssh: ByteArray) {
        rememberSource(configId)
        offers[SourceRegistry.trackKey(configId, trackId)] = Offer(configId, url, pssh)
    }

    private fun keyOf(uri: Uri): Pair<String, Pair<String, String>>? {
        if (uri.scheme != "streamx" || uri.authority != "source") return null
        val configId = uri.getQueryParameter("s") ?: return null
        val trackId = uri.getQueryParameter("t") ?: return null
        return SourceRegistry.trackKey(configId, trackId) to (configId to trackId)
    }

    fun isReady(uri: Uri): Boolean = keyOf(uri)?.first?.let { synchronized(copies) { copies.containsKey(it) } } == true

    /**
     * Where [uri] stands, starting the fetch of its analysis copy if it is an
     * encrypted track with none yet. Called every tick, so it never blocks.
     */
    fun state(uri: Uri): State {
        val (key, ids) = keyOf(uri) ?: return State.NOT_PROTECTED
        if (isReady(uri)) return State.READY
        if (key in failed) return State.UNAVAILABLE
        if (offers[key] == null && !isProtectedSource(ids.first)) return State.NOT_PROTECTED
        if (fetching.add(key)) scope.launch {
            try {
                fetch(key, ids.first, ids.second)
            } catch (error: Throwable) {
                Log.w(TAG, "No analysis copy for $key: ${error.message}")
                failed.add(key)
            } finally {
                fetching.remove(key)
            }
        }
        return State.PENDING
    }

    private suspend fun fetch(key: String, configId: String, trackId: String) {
        if (offers[key] == null) {
            // A queued track nobody has resolved yet: ask its own extension,
            // never the cross-source race, which may hand back another
            // provider's recording with a different timeline.
            val source = SourceRegistry.activeForPlayback().firstOrNull { it.configId == configId }
                ?: throw IOException("Source is disabled")
            source.stream(trackId, StreamRequest.Best)
        }
        val offer = offers[key] ?: throw IOException("Extension named no analysis rendition")
        val bytes = Http.client.newCall(Request.Builder().url(offer.url).build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val body = response.body ?: throw IOException("Empty response")
            if (body.contentLength() > MAX_BYTES) throw IOException("Analysis rendition is too large")
            body.bytes().also { if (it.size > MAX_BYTES) throw IOException("Analysis rendition is too large") }
        }
        val copy = Copy(bytes, offer.configId, offer.pssh)
        synchronized(copies) {
            copies.remove(key)?.release()
            copies[key] = copy
            while (copies.size > KEEP) copies.remove(copies.keys.first())?.release()
        }
        Log.d(TAG, "Analysis copy of $key ready (${bytes.size} bytes, encrypted)")
    }

    /** A readable view of [uri]'s analysis copy, or null if there is none. */
    fun open(uri: Uri): MediaDataSource? {
        val key = keyOf(uri)?.first ?: return null
        val copy = synchronized(copies) { copies[key] } ?: return null
        return Source(copy)
    }

    /**
     * The encrypted bytes and, once a decode asks for it, a CDM session holding
     * their keys. One session per copy, shared by every pass over it — the
     * structural decode, the head and the tail — so a track costs one licence.
     */
    internal class Copy(val bytes: ByteArray, val configId: String, val pssh: ByteArray) {
        private var drm: MediaDrm? = null
        private var session: ByteArray? = null
        private var crypto: MediaCrypto? = null

        @Synchronized
        fun crypto(): MediaCrypto {
            crypto?.let { return it }
            val drm = MediaDrm(WIDEVINE).also { this.drm = it }
            val session = try { drm.openSession() } catch (fresh: NotProvisionedException) { provision(drm); drm.openSession() }
            this.session = session
            val request = try {
                drm.getKeyRequest(session, pssh, "audio/mp4", MediaDrm.KEY_TYPE_STREAMING, null)
            } catch (fresh: NotProvisionedException) {
                provision(drm)
                drm.getKeyRequest(session, pssh, "audio/mp4", MediaDrm.KEY_TYPE_STREAMING, null)
            }
            drm.provideKeyResponse(session, StreamDrm.licence(configId, request.data))
            return MediaCrypto(WIDEVINE, session).also { crypto = it }
        }

        @Synchronized
        fun release() {
            runCatching { crypto?.release() }
            session?.let { open -> runCatching { drm?.closeSession(open) } }
            runCatching { drm?.close() }
            crypto = null
            session = null
            drm = null
        }

        /** The one-time device certificate, fetched from Google as Media3 does for playback. */
        private fun provision(drm: MediaDrm) {
            val request = drm.provisionRequest
            val url = request.defaultUrl + "&signedRequest=" + String(request.data, Charsets.UTF_8)
            val body = Http.client.newCall(
                Request.Builder().url(url).post(ByteArray(0).toRequestBody()).build(),
            ).execute().use { response ->
                if (!response.isSuccessful) throw IOException("Provisioning failed: HTTP ${response.code}")
                response.body?.bytes() ?: throw IOException("Empty provisioning response")
            }
            drm.provideProvisionResponse(body)
        }
    }

    /**
     * What [AudioDecoder] reads. Closing it (the extractor does, after every
     * pass) leaves the copy and its keys in place for the next pass.
     */
    class Source internal constructor(private val copy: Copy) : MediaDataSource() {
        private val bytes get() = copy.bytes
        fun crypto(): MediaCrypto = copy.crypto()
        override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
            if (position >= bytes.size) return -1
            val count = minOf(size.toLong(), bytes.size - position).toInt()
            System.arraycopy(bytes, position.toInt(), buffer, offset, count)
            return count
        }
        override fun getSize(): Long = bytes.size.toLong()
        override fun close() {}
    }
}
