package com.music.streamx.playback

import android.content.ContentProviderClient
import android.content.Context
import android.os.Binder
import android.os.Bundle
import com.music.streamx.data.TrackLog
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import com.music.streamx.data.extensions.ApkExtensions
import com.music.streamx.data.sources.SourceConfig
import com.music.streamx.data.sources.SourceRegistry
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.selects.onTimeout
import org.json.JSONObject

/** Browser audio stays in the extension. The host owns the queue and media controls; the renderer owns audio focus. */
class WebExtensionPlayback(private val context: Context, private val scope: CoroutineScope) {
    var changed: (() -> Unit)? = null
    var ended: (() -> Boolean)? = null
    var controllerOwner: Any? = null
    var orderedPlugin: ((MediaItem, Boolean, com.music.streamx.data.model.Song) -> Boolean)? = null
    var orderedStream: ((MediaItem, Boolean, com.music.streamx.data.sources.SourceStream) -> Boolean)? = null
    var nativeStream: ((MediaItem, Boolean, String) -> Boolean)? = null
    var youtubeFallback: ((MediaItem, Boolean) -> Boolean)? = null
    var active = false; private set
    var playWhenReady = false; private set
    var isPlaying = false; private set
    var playbackState = Player.STATE_IDLE; private set
    var positionMs = 0L; private set
    var durationMs = C.TIME_UNSET; private set
    var bufferedPositionMs = 0L; private set
    var error: PlaybackException? = null; private set
    internal var nowPlaying: WebNowPlaying? = null; private set
    private var mediaId: String? = null
    private var sourceId: String? = null
    private var lastItem: MediaItem? = null
    private var heardAudio = false
    private var catalogueDurationMs = C.TIME_UNSET
    private var job: Job? = null
    private val owner = Binder()
    private var commands = Channel<Bundle>(Channel.UNLIMITED)

    fun target(item: MediaItem?): Pair<SourceConfig, String>? {
        val uri = item?.localConfiguration?.uri
        if (uri?.getQueryParameter("ordered_stream") == "1" || uri?.getQueryParameter(DIRECT_YOUTUBE_PARAMETER) == "1") return null
        val key = item?.mediaId?.let(SourceRegistry::parseTrackKey) ?: return null
        val config = SourceRegistry.config(key.first) ?: return null
        if (uri?.getQueryParameter("extension_native") == "1" &&
            SourceRegistry.activeForPlayback().firstOrNull()?.configId == key.first) return null
        val active = SourceRegistry.activeForPlayback()
        val remoteIds = active.filter { source -> SourceRegistry.config(source.configId)?.let(ApkExtensions::usesRemoteWebPlayer) == true }
            .mapTo(mutableSetOf()) { it.configId }
        val preferredRemote = com.music.streamx.data.sources.hasEarlierRemoteSource(key.first, active.map { it.configId }, remoteIds)
        return if (ApkExtensions.usesRemoteWebPlayer(config) || preferredRemote) config to key.second else null
    }
    fun load(item: MediaItem, shouldPlay: Boolean) {
        val target = target(item) ?: return
        if (active && mediaId == item.mediaId) { if (shouldPlay) play() else pause(); return }
        val previous = job; previous?.cancel()
        commands.close(); commands = Channel(Channel.UNLIMITED)
        val channel = commands
        catalogueDurationMs = com.music.streamx.data.sources.TrackMatcher.secondsOf(item.mediaMetadata.extras?.getString("streamx.durationText"))
            ?.toLong()?.times(1000)?.takeIf { it > 0 } ?: C.TIME_UNSET
        nowPlaying = null
        lastItem = item; mediaId = item.mediaId; sourceId = target.first.id; active = true; playWhenReady = shouldPlay
        isPlaying = false; heardAudio = false; positionMs = 0; bufferedPositionMs = 0; durationMs = catalogueDurationMs; error = null; playbackState = Player.STATE_BUFFERING
        changed?.invoke()
        job = scope.launch {
            previous?.join()
            ensureActive()
            var client: ContentProviderClient? = null
            try {
                val uri = item.localConfiguration?.uri
                if (uri != null) {
                    val preferred = com.music.streamx.data.sources.SourceResolver.preferredPlayback(
                        target.first.id, com.music.streamx.data.sources.SourceResolver.targetIn(uri),
                    )
                    ensureActive()
                    when (preferred) {
                        is com.music.streamx.data.sources.SourceResolver.PreferredPlayback.Stream ->
                            if (orderedStream?.invoke(item, playWhenReady, preferred.value) == true) return@launch
                        is com.music.streamx.data.sources.SourceResolver.PreferredPlayback.Plugin ->
                            if (orderedPlugin?.invoke(item, playWhenReady, preferred.song) == true) return@launch
                        null -> Unit
                    }
                    if (!ApkExtensions.usesRemoteWebPlayer(target.first)) {
                        val stream = com.music.streamx.data.sources.SourceResolver.resolve(uri)
                            ?: error("No enabled playback source matched this track")
                        ensureActive()
                        check(orderedStream?.invoke(item, playWhenReady, stream) == true) { "Could not start ordered playback" }
                        return@launch
                    }
                }
                check(com.music.streamx.data.sources.SourceRegistry.activeForPlayback().any { it.configId == target.first.id }) {
                    "The selected plugin is disabled for playback and no enabled source matched this track"
                }
                if (target.first.extensionPackage == SpotifyPlaybackRouting.PACKAGE) {
                    val route = try { JSONObject(ApkExtensions.call(target.first, "playback_route")) }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { JSONObject() }
                    ensureActive()
                    if (SpotifyPlaybackRouting.usesYouTube(route)) {
                        useYouTube(item)
                        return@launch
                    }
                    // Premium with the plugin's native engine on: StreamX's own player
                    // plays the plugin's native transfer instead of the plugin playing
                    // it privately, so crossfade and Automix can reach the audio.
                    val preferences = try { JSONObject(ApkExtensions.call(target.first, "preferences")) }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { JSONObject() }
                    ensureActive()
                    if (preferences.optBoolean("native_enabled") && preferences.optBoolean("native_available") &&
                        nativeStream?.invoke(item, playWhenReady, "audio/ogg") == true
                    ) return@launch
                }
                if (target.first.extensionPackage == "com.music.streamx.extensions.applemusic") {
                    val route = JSONObject(ApkExtensions.call(target.first,"playback_route",Bundle().apply {putString("track_id",target.second)}))
                    check(!route.has("error")){route.optString("error")}
                    if (route.optString("backend") == "stream") {
                        ensureActive()
                        check(route.optString("mime_type") == "application/x-mpegURL"){"Unsupported native stream type"}
                        // A warm MusicKit renderer may still own output from the preceding song.
                        ApkExtensions.call(target.first,"playback",Bundle().apply {putString("command","stop");putBinder("owner",owner)})
                        ensureActive()
                        check(nativeStream?.invoke(item,playWhenReady,"application/x-mpegURL") == true){"Could not start native extension streaming"}
                        return@launch
                    }
                }
                val connected = ApkExtensions.playbackClient(target.first)
                TrackLog.d("WebPlayback", "Provider connected; loading browser renderer")
                client = connected
                val loaded = command(connected, Bundle().apply { putString("command", "load"); putString("track_id", target.second); putString("track_title", item.mediaMetadata.title?.toString()); putString("track_metadata", JSONObject().apply {
                    put("id",target.second); put("type","track"); put("title",item.mediaMetadata.title?.toString().orEmpty())
                    put("artist",item.mediaMetadata.artist?.toString().orEmpty()); put("album",item.mediaMetadata.albumTitle?.toString().orEmpty())
                    put("thumbnail_url",item.mediaMetadata.artworkUri?.toString().orEmpty()); put("duration_sec",catalogueDurationMs.coerceAtLeast(0)/1000)
                }.toString()); putBinder("owner", owner); putBoolean("play", shouldPlay) })
                if (loaded.optString("backend") == "youtube") {
                    ensureActive()
                    useYouTube(item)
                    return@launch
                }
                var completionReported = false
                var reportedControlSequence = -1L
                var reportedPhase = ""
                var reportedNativeError = ""
                var startupReported = false
                while (isActive) {
                    check(SourceRegistry.config(target.first.id)?.enabled == true) { "Playback source was disabled" }
                    var next = channel.tryReceive().getOrNull()
                    while (next != null) { command(connected, next); next = channel.tryReceive().getOrNull() }
                    val status = command(connected, Bundle().apply { putString("command", "status") })
                    ensureActive() // A cancelled track must not publish over its successor.
                    if (status.optString("track_id", target.second) != target.second) { delay(500); continue }
                    WebPlaybackResponse.error(status)?.let { throw IllegalStateException(it) }
                    positionMs = status.optLong("position_ms").coerceAtLeast(0)
                    durationMs = WebPlaybackResponse.duration(catalogueDurationMs, status)
                    nowPlaying = WebNowPlaying.from(status)
                    bufferedPositionMs = status.optLong("buffered_ms", positionMs).coerceAtLeast(positionMs)
                    val nativeError = status.optString("native_error")
                    if (nativeError.isNotBlank() && nativeError != reportedNativeError) {
                        reportedNativeError = nativeError
                        TrackLog.w("WebPlayback", "Native playback fallback: $nativeError", about = item.mediaId)
                    }
                    val phase = status.optString("play_control")
                    if (phase.isNotBlank() && phase != reportedPhase) {
                        reportedPhase = phase
                        TrackLog.d("WebPlayback", "Browser startup: $phase; ready=${status.optInt("ready_state", -1)} network=${status.optInt("network_state", -1)}", about = item.mediaId)
                    }
                    val controlSequence = status.optLong("control_sequence", -1)
                    if (controlSequence != reportedControlSequence && status.optLong("control_latency_ms", -1) >= 0) {
                        reportedControlSequence = controlSequence
                        TrackLog.d("WebPlayback", "Browser ${status.optString("control_name")} applied in ${status.optLong("control_latency_ms")} ms", about = item.mediaId)
                    }
                    val state = status.optString("state")
                    // Remote telemetry must never undo a newer local pause intent.
                    isPlaying = playWhenReady && state == "playing"
                    if (isPlaying) {
                        heardAudio = true
                        if (!startupReported) {
                            startupReported = true
                            TrackLog.d("WebPlayback", "Browser audio started in ${status.optLong("startup_ms")} ms; recovery=${status.optBoolean("recovered")}; reason=${status.optString("recovery_reason").ifBlank { "none" }}", about = item.mediaId)
                        }
                    }
                    publishQuality(status, target.first.displayName)
                    // Advance the host timeline before announcing terminal queue state.
                    // Publishing ENDED first lets queue-exhaustion listeners run for a
                    // playlist which still has a next item. A handoff cancels this job.
                    if (state == "ended" && !completionReported) {
                        completionReported = true
                        val advanced = ended?.invoke() == true
                        ensureActive()
                        if (mediaId != item.mediaId) return@launch
                        if (advanced) continue // Repeat-one/next must not announce queue exhaustion.
                    } else if (state != "ended") completionReported = false
                    playbackState = when (state) { "playing", "paused" -> Player.STATE_READY; "ended" -> Player.STATE_ENDED; else -> Player.STATE_BUFFERING }
                    changed?.invoke()
                    // A transport command wakes this wait immediately; telemetry alone stays at 2 Hz.
                    select<Unit> {
                        channel.onReceiveCatching { received ->
                            received.getOrNull()?.let { command(connected, it) }
                        }
                        onTimeout(500) { }
                    }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { fail(failure.message ?: "Extension browser playback failed") }
            finally {
                withContext(NonCancellable + Dispatchers.IO) {
                    val handoff = active && sourceId == target.first.id && mediaId != item.mediaId && error == null
                    if (!handoff) runCatching { client?.call("streamx.playback.v1", null, Bundle().apply { putString("command", "stop"); putBinder("owner", owner) }) }
                    else TrackLog.d("WebPlayback", "Keeping browser warm for the next track", about = item.mediaId)
                    client?.close()
                }
            }
        }
    }
    private fun useYouTube(item: MediaItem) {
        TrackLog.d("WebPlayback", "Spotify audio requires verified Premium; using YouTube audio", about = item.mediaId)
        check(youtubeFallback?.invoke(item, playWhenReady) == true) { "Could not start YouTube audio for this Spotify track" }
    }
    private fun publishQuality(status: JSONObject, source: String) {
        val type = status.opt("mime_type") as? String
        val mime = when {
            type?.contains("mp4a", true) == true -> "audio/mp4a-latm"
            type?.contains("opus", true) == true -> "audio/opus"
            type?.startsWith("audio/") == true -> type.substringBefore(';')
            else -> null
        }
        com.music.streamx.data.NerdStats.onLosslessRaceEnd(mediaId ?: return)
        com.music.streamx.data.NerdStats.recordSource(mediaId, source)
        val native = status.optString("backend") == "native"
        fun reported(key: String) = status.optInt(key,0).takeIf { native && it>0 }
        com.music.streamx.data.NerdStats.current.value = com.music.streamx.data.NerdStats.Snapshot(
            mimeType = mime, bitrateKbps = reported("bitrate_kbps"), sampleRateHz = reported("sample_rate_hz"), channels = reported("channels"),
            bitDepth = reported("bit_depth"),
            bitrateProvenance = if(reported("bitrate_kbps") != null) com.music.streamx.data.TelemetryProvenance.AUTHORITATIVE else com.music.streamx.data.TelemetryProvenance.UNKNOWN,
            sourceName = source, container = if(native) "Native player" else "Web player")
    }
    private suspend fun command(client: ContentProviderClient, args: Bundle): JSONObject = withContext(Dispatchers.IO) {
        args.putBinder("owner", owner)
        val value = JSONObject(client.call("streamx.playback.v1", null, args)?.getString("json") ?: throw IllegalStateException("Missing browser playback response"))
        check(!value.has("error") || value.isNull("error")) { value.optString("error") }
        value
    }
    private fun fail(message: String) {
        TrackLog.w("WebPlayback", "Browser playback failed: $message")
        error = PlaybackException(message, null, PlaybackException.ERROR_CODE_REMOTE_ERROR)
        playWhenReady = false; isPlaying = false; playbackState = Player.STATE_IDLE
        changed?.invoke()
    }
    fun play() {
        if (!active) return
        if (error != null) { mediaId = null; lastItem?.let { load(it, true) }; return }
        playWhenReady = true
        commands.trySend(Bundle().apply { putString("command", "play"); putLong("queued_at", android.os.SystemClock.elapsedRealtime()) }); changed?.invoke()
    }
    fun pause() {
        playWhenReady = false; isPlaying = false; if(active)playbackState = Player.STATE_READY; commands.trySend(Bundle().apply { putString("command", "pause"); putLong("queued_at", android.os.SystemClock.elapsedRealtime()) }); changed?.invoke()
    }
    fun seekTo(position: Long) { commands.trySend(Bundle().apply { putString("command", "seek"); putLong("position_ms", position.coerceAtLeast(0)) }) }
    /**
     * Starts [item] over in the extension, keeping whether it should play.
     * How a failed extension track is retried: the local player has no stream
     * for it to fall back on, so preparing that instead only failed again.
     */
    fun reload(item: MediaItem) {
        val play = playWhenReady
        stop()
        load(item, play)
    }
    fun stop() {
        job?.cancel(); commands.close(); mediaId = null; active = false; playWhenReady = false; isPlaying = false
        nowPlaying = null
        playbackState = Player.STATE_IDLE; error = null; changed?.invoke()
    }
}
