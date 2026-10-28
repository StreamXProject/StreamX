package com.music.streamx.playback

import androidx.media3.common.*
import java.util.concurrent.CopyOnWriteArraySet

/** The existing local player retains the timeline; a browser supplies transport and clock. */
class WebSessionPlayer(
    player: Player,
    private val browser: WebExtensionPlayback,
    private val onError: (PlaybackException) -> Unit = {},
) : ForwardingPlayer(player) {
    private val listeners = CopyOnWriteArraySet<Player.Listener>()
    private var selected: String? = null
    private var wanted = false
    private var reportedPosition = 0L
    private var reportedItem: String? = null
    private var reportedError: PlaybackException? = null
    private var reportedMetadata: MediaMetadata? = null
    private var reportedDuration = C.TIME_UNSET
    private var reportedBroadcast: String? = null
    private var reportedState: WebPlayerReportedState? = null
    private val wrappedListener = object : Player.Listener {
        override fun onMediaItemTransition(item: MediaItem?, reason: Int) {
            sync()
            listeners.forEach { it.onMediaItemTransition(item, reason) }
        }
        override fun onTimelineChanged(timeline: Timeline, reason: Int) {
            sync()
            listeners.forEach { it.onTimelineChanged(timeline, reason) }
        }
        // Repeat and shuffle belong to the local timeline, never to the browser, so they are
        // forwarded whether or not the browser is driving transport. Without these the
        // session never heard a repeat change: the player looped, but the button stayed off.
        override fun onRepeatModeChanged(repeatMode: Int) {
            listeners.forEach { it.onRepeatModeChanged(repeatMode) }
        }
        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            listeners.forEach { it.onShuffleModeEnabledChanged(shuffleModeEnabled) }
        }
        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (!browser.active) listeners.forEach { it.onPlayWhenReadyChanged(playWhenReady, reason) }
        }
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (!browser.active) listeners.forEach { it.onIsPlayingChanged(isPlaying) }
        }
        override fun onPlaybackStateChanged(playbackState: Int) {
            if (!browser.active) listeners.forEach { it.onPlaybackStateChanged(playbackState) }
        }
        override fun onIsLoadingChanged(isLoading: Boolean) {
            if (!browser.active) listeners.forEach { it.onIsLoadingChanged(isLoading) }
        }
        override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
            if (!browser.active) listeners.forEach { it.onPositionDiscontinuity(oldPosition, newPosition, reason) }
        }
        override fun onAvailableCommandsChanged(availableCommands: Player.Commands) {
            if (!browser.active) listeners.forEach { it.onAvailableCommandsChanged(availableCommands) }
        }
        override fun onPlayerErrorChanged(error: PlaybackException?) {
            if (!browser.active) listeners.forEach { it.onPlayerErrorChanged(error) }
        }
        override fun onPlayerError(error: PlaybackException) {
            if (!browser.active) listeners.forEach { it.onPlayerError(error) }
        }
        override fun onEvents(player: Player, events: Player.Events) {
            if (!browser.active) {
                listeners.forEach { it.onEvents(this@WebSessionPlayer, events) }
            } else {
                val filtered = FlagSet.Builder()
                for (i in 0 until events.size()) {
                    val event = events.get(i)
                    if (event != Player.EVENT_PLAY_WHEN_READY_CHANGED &&
                        event != Player.EVENT_IS_PLAYING_CHANGED &&
                        event != Player.EVENT_PLAYBACK_STATE_CHANGED &&
                        event != Player.EVENT_IS_LOADING_CHANGED &&
                        event != Player.EVENT_POSITION_DISCONTINUITY &&
                        event != Player.EVENT_AVAILABLE_COMMANDS_CHANGED &&
                        event != Player.EVENT_PLAYER_ERROR) {
                        filtered.add(event)
                    }
                }
                val filteredEvents = Player.Events(filtered.build())
                if (filteredEvents.size() > 0) {
                    listeners.forEach { it.onEvents(this@WebSessionPlayer, filteredEvents) }
                }
            }
        }
    }
    init {
        browser.controllerOwner = this
        browser.changed = ::report
        browser.orderedPlugin = { item, shouldPlay, song ->
            if (browser.controllerOwner !== this || selected != item.mediaId ||
                wrappedPlayer.currentMediaItem?.mediaId != item.mediaId) false
            else {
                val index = wrappedPlayer.currentMediaItemIndex
                val position = wrappedPlayer.currentPosition.coerceAtLeast(0L)
                val routed = song.toMediaItem().buildUpon().setMediaMetadata(item.mediaMetadata).build()
                browser.stop()
                selected = null; reportedError = null; reportedState = null
                // A play pressed while the extension was starting can land on the local
                // player instead of [shouldPlay]; dropping it left the track paused until resume.
                wanted = shouldPlay || wanted || wrappedPlayer.playWhenReady
                wrappedPlayer.replaceMediaItem(index, routed)
                wrappedPlayer.seekTo(index, position)
                wrappedPlayer.prepare()
                // Browser playback owns play/pause once the queue transition
                // selects the matched plugin item.
                if (browser.target(routed) != null) browser.load(routed, wanted)
                else wrappedPlayer.playWhenReady = wanted
                true
            }
        }
        browser.orderedStream = { item, shouldPlay, stream ->
            val uri = item.localConfiguration?.uri
            if (uri == null || browser.controllerOwner !== this || selected != item.mediaId ||
                wrappedPlayer.currentMediaItem?.mediaId != item.mediaId) false
            else {
                val index = wrappedPlayer.currentMediaItemIndex
                val position = wrappedPlayer.currentPosition.coerceAtLeast(0L)
                StreamChoice.remember(item.mediaId, stream, substituted = true)
                val routed = item.buildUpon()
                    .setUri(uri.buildUpon().appendQueryParameter("ordered_stream", "1").build())
                    .setMimeType(StreamContainer.manifestMimeOf(stream.url))
                    .setCustomCacheKey(null).build()
                browser.stop()
                selected = null; reportedError = null; reportedState = null
                // A play pressed while the extension was starting can land on the local
                // player instead of [shouldPlay]; dropping it left the track paused until resume.
                wanted = shouldPlay || wanted || wrappedPlayer.playWhenReady
                wrappedPlayer.replaceMediaItem(index, routed)
                wrappedPlayer.seekTo(index, position)
                wrappedPlayer.prepare()
                wrappedPlayer.playWhenReady = wanted
                true
            }
        }
        browser.nativeStream = { item, shouldPlay, mime ->
            if (browser.controllerOwner !== this || selected != item.mediaId || wrappedPlayer.currentMediaItem?.mediaId != item.mediaId) false
            else {
                val uri = item.localConfiguration?.uri
                if (uri == null) false else {
                    val index = wrappedPlayer.currentMediaItemIndex
                    val position = wrappedPlayer.currentPosition.coerceAtLeast(0L)
                    browser.stop(); selected = null; reportedError = null; reportedState = null
                    // A play pressed while the extension was starting can land on the local
                    // player instead of [shouldPlay]; dropping it left the track paused until resume.
                    wanted = shouldPlay || wanted || wrappedPlayer.playWhenReady
                    val sourceId = com.music.streamx.data.sources.SourceRegistry.parseTrackKey(item.mediaId)?.first
                    if (sourceId != null) NativeExtensionRouting.update(sourceId, true)
                    // Mark the whole remaining queue, not just its first track. Read-ahead
                    // otherwise starts those items as generic progressive/fallback sources.
                    val remaining = (index until wrappedPlayer.mediaItemCount).map { at ->
                        NativeExtensionRouting.decorate(wrappedPlayer.getMediaItemAt(at))
                    }
                    wrappedPlayer.replaceMediaItems(index, wrappedPlayer.mediaItemCount, remaining)
                    wrappedPlayer.seekTo(index,position); wrappedPlayer.prepare(); wrappedPlayer.playWhenReady = wanted
                    true
                }
            }
        }
        browser.youtubeFallback = { item, shouldPlay ->
            if (browser.controllerOwner !== this || selected != item.mediaId || wrappedPlayer.currentMediaItem?.mediaId != item.mediaId) {
                false
            } else {
                val fallback = item.toYouTubeFallbackMediaItem()
                if (fallback == null) false else {
                    val index = wrappedPlayer.currentMediaItemIndex
                    // Version swaps and restored queues already carry their seek
                    // position on the local timeline. Reopening through YouTube
                    // must retain it rather than restart the recording.
                    val position = wrappedPlayer.currentPosition.coerceAtLeast(0L)
                    browser.stop()
                    selected = null; reportedError = null; reportedState = null
                    // A play pressed while the extension was starting can land on the local
                    // player instead of [shouldPlay]; dropping it left the track paused until resume.
                    wanted = shouldPlay || wanted || wrappedPlayer.playWhenReady
                    wrappedPlayer.replaceMediaItem(index, fallback)
                    wrappedPlayer.seekTo(index, position)
                    com.music.streamx.data.TrackLog.d("WebPlayback", "YouTube handoff at ${position}ms", about = item.mediaId)
                    wrappedPlayer.prepare()
                    wrappedPlayer.playWhenReady = wanted
                    true
                }
            }
        }
        browser.ended = {
            // Only the wrapper owning this renderer may advance its timeline.
            if (browser.controllerOwner !== this || selected != wrappedPlayer.currentMediaItem?.mediaId) {
                com.music.streamx.data.TrackLog.w("WebPlayback", "Ignoring stale extension completion")
                false
            } else {
                val next = wrappedPlayer.nextMediaItemIndex
                com.music.streamx.data.TrackLog.d("WebPlayback",
                    "Extension completion: index=${wrappedPlayer.currentMediaItemIndex} next=$next " +
                        "track=${if (next in 0 until wrappedPlayer.mediaItemCount) wrappedPlayer.getMediaItemAt(next).mediaId else "none"} " +
                        "repeat=${wrappedPlayer.repeatMode} shuffle=${wrappedPlayer.shuffleModeEnabled}")
                when {
                    wrappedPlayer.repeatMode == Player.REPEAT_MODE_ONE -> { browser.seekTo(0); browser.play(); true }
                    wrappedPlayer.hasNextMediaItem() -> { wrappedPlayer.seekToNextMediaItem(); true }
                    else -> { browser.pause(); false }
                }
            }
        }
        wrappedPlayer.addListener(wrappedListener)
    }
    private fun sync() {
        if (browser.controllerOwner !== this) return
        val item = wrappedPlayer.currentMediaItem
        if (browser.target(item) != null) {
            if (selected == item?.mediaId) return
            wanted = if (browser.active) browser.playWhenReady else (wanted || wrappedPlayer.playWhenReady)
            selected = item?.mediaId
            // Stopped, not paused: a paused player stays prepared and goes on
            // loading this track, which only the extension can play — so it
            // failed to resolve it, and took audio focus from the extension
            // while it retried. [queueRemote] stops it the same way.
            wrappedPlayer.stop()
            if (item != null) browser.load(item, wanted)
        } else if (browser.active) {
            selected = null; reportedError = null; browser.stop()
            if (wanted) { wrappedPlayer.prepare(); wrappedPlayer.play() }
        }
    }
    private fun queueRemote(items: List<MediaItem>, index: Int) {
        if (browser.target(items.getOrNull(index)) != null) wrappedPlayer.stop()
    }
    override fun setMediaItems(items: List<MediaItem>, index: Int, positionMs: Long) {
        queueRemote(items, index); selected = null; super.setMediaItems(items, index, positionMs); sync()
    }
    override fun setMediaItems(items: List<MediaItem>, resetPosition: Boolean) {
        queueRemote(items, if (resetPosition) 0 else currentMediaItemIndex); selected = null
        super.setMediaItems(items, resetPosition); sync()
    }
    override fun setMediaItems(items: List<MediaItem>) { setMediaItems(items, true) }
    override fun prepare() { sync(); if (!browser.active) super.prepare() }
    override fun play() { wanted = true; sync(); if (browser.active) browser.play() else super.play() }
    // The local player is paused too while the extension plays, so its own flag
    // stays a true record of the listener's intent for a later handoff.
    override fun pause() { wanted = false; if (browser.active) { browser.pause(); wrappedPlayer.playWhenReady = false } else super.pause() }
    override fun setPlayWhenReady(play: Boolean) { if (play) play() else pause() }
    override fun stop() {
        wanted = false
        if (browser.active) {
            browser.stop()
            selected = null
            reportedError = null
            reportedState = null
        }
        super.stop()
    }
    override fun release() {
        wrappedPlayer.removeListener(wrappedListener)
        super.release()
    }
    override fun seekTo(positionMs: Long) { if (browser.active) browser.seekTo(positionMs) else super.seekTo(positionMs) }
    override fun seekTo(index: Int, positionMs: Long) {
        if (browser.active && index == currentMediaItemIndex) browser.seekTo(positionMs)
        else { super.seekTo(index, positionMs); sync() }
    }
    override fun seekBack() { if (browser.active) browser.seekTo(currentPosition - seekBackIncrement) else super.seekBack() }
    override fun seekForward() { if (browser.active) browser.seekTo(currentPosition + seekForwardIncrement) else super.seekForward() }
    override fun seekToPrevious() {
        if (!browser.active) { super.seekToPrevious(); return }
        if (currentPosition > maxSeekToPreviousPosition || !hasPreviousMediaItem()) browser.seekTo(0)
        else { super.seekToPreviousMediaItem(); sync() }
    }
    override fun seekToPreviousMediaItem() { seekToPrevious() }
    override fun seekToNext() { super.seekToNext(); sync() }
    override fun seekToNextMediaItem() { super.seekToNextMediaItem(); sync() }
    override fun seekToDefaultPosition() { if (browser.active) browser.seekTo(0) else super.seekToDefaultPosition() }
    override fun seekToDefaultPosition(index: Int) { if (browser.active && index == currentMediaItemIndex) browser.seekTo(0) else { super.seekToDefaultPosition(index); sync() } }
    private fun decorate(item: MediaItem?): MediaItem? =
        if (browser.active && item != null) browser.nowPlaying?.applyTo(item, browser.durationMs) ?: item else item
    override fun getCurrentMediaItem(): MediaItem? = decorate(super.getCurrentMediaItem())
    override fun getMediaItemAt(index: Int): MediaItem =
        if (index == currentMediaItemIndex) decorate(super.getMediaItemAt(index))!! else super.getMediaItemAt(index)
    override fun getMediaMetadata(): MediaMetadata = if (browser.active) currentMediaItem?.mediaMetadata ?: MediaMetadata.EMPTY else super.getMediaMetadata()
    /**
     * The catalogue's length for a local stream that cannot tell its own.
     *
     * A plugin's native transfer (Spotify's librespot Ogg) is a one-way pipe:
     * ExoPlayer never learns its length and treats the window as live, so the
     * player showed 0:00 / -0:00 for the whole track. The listed runtime is
     * what the scrubber, notification and lock screen go by until a seekable
     * copy plays instead.
     */
    private fun lengthlessFallbackMs(): Long {
        if (browser.active || super.getDuration() != C.TIME_UNSET) return C.TIME_UNSET
        val seconds = com.music.streamx.data.sources.TrackMatcher.secondsOf(
            super.getCurrentMediaItem()?.mediaMetadata?.extras?.getString("streamx.durationText"),
        ) ?: return C.TIME_UNSET
        return if (seconds > 0) seconds * 1000L else C.TIME_UNSET
    }
    override fun isCurrentMediaItemLive() = if (lengthlessFallbackMs() > 0) false else super.isCurrentMediaItemLive()
    override fun isCurrentMediaItemDynamic() = if (lengthlessFallbackMs() > 0) false else super.isCurrentMediaItemDynamic()
    override fun getCurrentTimeline(): Timeline {
        val timeline = super.getCurrentTimeline()
        if (!browser.active && !timeline.isEmpty) {
            val fallback = lengthlessFallbackMs()
            if (fallback <= 0) return timeline
            val currentIndex = currentMediaItemIndex
            return object : androidx.media3.exoplayer.source.ForwardingTimeline(timeline) {
                override fun getWindow(index: Int, window: Timeline.Window, defaultPositionProjectionUs: Long): Timeline.Window {
                    super.getWindow(index, window, defaultPositionProjectionUs)
                    if (index == currentIndex) {
                        window.durationUs = fallback * 1000
                        window.isDynamic = false
                        window.liveConfiguration = null
                    }
                    return window
                }
                override fun getPeriod(index: Int, period: Timeline.Period, setIds: Boolean): Timeline.Period {
                    super.getPeriod(index, period, setIds)
                    if (period.windowIndex == currentIndex) period.durationUs = fallback * 1000
                    return period
                }
            }
        }
        if (!browser.active || timeline.isEmpty) return timeline
        val currentIndex = currentMediaItemIndex
        val item = currentMediaItem
        val duration = browser.durationMs
        return object : androidx.media3.exoplayer.source.ForwardingTimeline(timeline) {
            override fun getWindow(index: Int, window: Timeline.Window, defaultPositionProjectionUs: Long): Timeline.Window {
                super.getWindow(index, window, defaultPositionProjectionUs)
                if (index == currentIndex) {
                    if (item != null) window.mediaItem = item
                    window.durationUs = if (duration > 0) duration * 1000 else C.TIME_UNSET
                    window.isSeekable = duration > 0
                }
                return window
            }
            override fun getPeriod(index: Int, period: Timeline.Period, setIds: Boolean): Timeline.Period {
                super.getPeriod(index, period, setIds)
                if (period.windowIndex == currentIndex) period.durationUs = if (duration > 0) duration * 1000 else C.TIME_UNSET
                return period
            }
        }
    }
    override fun getPlayWhenReady() = if (browser.active) browser.playWhenReady else super.getPlayWhenReady()
    override fun isPlaying() = if (browser.active) browser.isPlaying else super.isPlaying()
    override fun getPlaybackState() = if (browser.active) browser.playbackState else super.getPlaybackState()
    override fun getPlayerError() = if (browser.active) browser.error else super.getPlayerError()
    override fun getCurrentPosition() = if (browser.active) browser.positionMs else super.getCurrentPosition()
    override fun getContentPosition() = currentPosition
    override fun getBufferedPosition() = if (browser.active) browser.bufferedPositionMs else super.getBufferedPosition()
    override fun getContentBufferedPosition() = bufferedPosition
    override fun getTotalBufferedDuration() = if(browser.active) (bufferedPosition-currentPosition).coerceAtLeast(0) else super.getTotalBufferedDuration()
    override fun isLoading() = if(browser.active) playbackState == Player.STATE_BUFFERING else super.isLoading()
    override fun getPlaybackParameters() = if(browser.active) PlaybackParameters.DEFAULT else super.getPlaybackParameters()
    override fun getDuration() = if (browser.active) browser.durationMs else lengthlessFallbackMs().takeIf { it > 0 } ?: super.getDuration()
    override fun getContentDuration() = duration
    override fun getAvailableCommands(): Player.Commands = if (!browser.active) super.getAvailableCommands() else super.getAvailableCommands().buildUpon()
        .addAll(Player.COMMAND_PLAY_PAUSE, Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM, Player.COMMAND_SEEK_TO_DEFAULT_POSITION,
            Player.COMMAND_SEEK_TO_NEXT, Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM, Player.COMMAND_SEEK_TO_PREVIOUS,
            Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM, Player.COMMAND_SEEK_BACK, Player.COMMAND_SEEK_FORWARD).build()
    override fun isCommandAvailable(command: Int) = if (browser.active) availableCommands.contains(command) else super.isCommandAvailable(command)
    override fun addListener(listener: Player.Listener) { listeners += listener }
    override fun removeListener(listener: Player.Listener) { listeners -= listener }
    private fun report() {
        if (!browser.active) { reportedState=null; return }
        val state = WebPlayerReportedState(playWhenReady,isPlaying,playbackState,isLoading,
            availableCommands.let { commands -> (0 until commands.size()).map(commands::get).toSet() })
        val changes = state.changesFrom(reportedState)
        reportedState=state
        val flags = FlagSet.Builder()
        changes.forEach(flags::add)
        val metadata = mediaMetadata
        val metadataChanged = metadata != reportedMetadata
        val timelineChanged = metadataChanged || duration != reportedDuration
        val broadcast = browser.nowPlaying?.id
        val broadcastChanged = broadcast != null && broadcast != reportedBroadcast
        reportedMetadata = metadata; reportedDuration = duration; reportedBroadcast = broadcast
        if (metadataChanged) flags.add(Player.EVENT_MEDIA_METADATA_CHANGED)
        if (timelineChanged) flags.add(Player.EVENT_TIMELINE_CHANGED)
        if (broadcastChanged) flags.add(Player.EVENT_POSITION_DISCONTINUITY)
        val jumped = reportedItem == currentMediaItem?.mediaId &&
            (currentPosition < reportedPosition - 750 || currentPosition > reportedPosition + 5000)
        if (jumped) flags.add(Player.EVENT_POSITION_DISCONTINUITY)
        val oldPosition = Player.PositionInfo(null, currentMediaItemIndex, currentMediaItem, null, currentPeriodIndex,
            reportedPosition, reportedPosition, C.INDEX_UNSET, C.INDEX_UNSET)
        reportedPosition = currentPosition; reportedItem = currentMediaItem?.mediaId
        val position = Player.PositionInfo(null, currentMediaItemIndex, currentMediaItem, null, currentPeriodIndex,
            currentPosition, contentPosition, C.INDEX_UNSET, C.INDEX_UNSET)
        listeners.forEach {
            if (metadataChanged) it.onMediaMetadataChanged(metadata)
            if (timelineChanged) it.onTimelineChanged(currentTimeline, Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE)
            if (broadcastChanged && !jumped) it.onPositionDiscontinuity(oldPosition, position, Player.DISCONTINUITY_REASON_AUTO_TRANSITION)
            if(Player.EVENT_PLAY_WHEN_READY_CHANGED in changes) it.onPlayWhenReadyChanged(playWhenReady, Player.PLAY_WHEN_READY_CHANGE_REASON_REMOTE)
            if(Player.EVENT_IS_PLAYING_CHANGED in changes) it.onIsPlayingChanged(isPlaying)
            if(Player.EVENT_PLAYBACK_STATE_CHANGED in changes) it.onPlaybackStateChanged(playbackState)
            if(Player.EVENT_IS_LOADING_CHANGED in changes) it.onIsLoadingChanged(isLoading)
            if (jumped) it.onPositionDiscontinuity(oldPosition, position, Player.DISCONTINUITY_REASON_INTERNAL)
            if(Player.EVENT_AVAILABLE_COMMANDS_CHANGED in changes) it.onAvailableCommandsChanged(availableCommands)
        }
        val failure = browser.error
        if (failure != reportedError) {
            reportedError = failure
            flags.add(Player.EVENT_PLAYER_ERROR)
            listeners.forEach {
                it.onPlayerErrorChanged(failure)
                if (failure != null) it.onPlayerError(failure)
            }
            if (failure != null) onError(failure)
        }
        val events = Player.Events(flags.build())
        if(events.size()>0) listeners.forEach { it.onEvents(this, events) }
    }
}
