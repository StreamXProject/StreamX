package com.music.streamx.playback

import androidx.media3.common.Player

/** Clock samples are not state transitions: repeating these events starves Media3's clock timer. */
internal data class WebPlayerReportedState(
    val playWhenReady: Boolean,
    val playing: Boolean,
    val playbackState: Int,
    val loading: Boolean,
    val commands: Set<Int>,
) {
    fun changesFrom(previous: WebPlayerReportedState?): Set<Int> = buildSet {
        if(previous?.playWhenReady != playWhenReady) add(Player.EVENT_PLAY_WHEN_READY_CHANGED)
        if(previous?.playing != playing) add(Player.EVENT_IS_PLAYING_CHANGED)
        if(previous?.playbackState != playbackState) add(Player.EVENT_PLAYBACK_STATE_CHANGED)
        if(previous?.loading != loading) add(Player.EVENT_IS_LOADING_CHANGED)
        if(previous?.commands != commands) add(Player.EVENT_AVAILABLE_COMMANDS_CHANGED)
    }
}
