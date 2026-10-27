package com.music.streamx.playback

import androidx.media3.common.Player
import org.junit.Assert.*
import org.junit.Test

class WebPlayerReportedStateTest {
    private fun state() = WebPlayerReportedState(true,true,Player.STATE_READY,false,
        setOf(Player.COMMAND_PLAY_PAUSE))

    @Test fun repeatedClockSamplesDoNotResetTheMediaSessionPositionTimer() {
        var previous: WebPlayerReportedState? = null
        repeat(100) { sample ->
            val current=state()
            val events=current.changesFrom(previous)
            if(sample==0) assertFalse(events.isEmpty()) else assertTrue(events.isEmpty())
            previous=current
        }
    }
    @Test fun pausePublishesOnlyActualTransportChanges() {
        val playing=state()
        assertEquals(setOf(Player.EVENT_PLAY_WHEN_READY_CHANGED,Player.EVENT_IS_PLAYING_CHANGED),
            playing.copy(playWhenReady=false,playing=false).changesFrom(playing))
    }
    @Test fun bufferingCompletionPublishesReadyAndLoadingChangesOnce() {
        val buffering=state().copy(playing=false,loading=true,playbackState=Player.STATE_BUFFERING)
        assertEquals(setOf(Player.EVENT_IS_PLAYING_CHANGED,Player.EVENT_PLAYBACK_STATE_CHANGED,Player.EVENT_IS_LOADING_CHANGED),
            state().changesFrom(buffering))
    }
    @Test fun changingCommandsStillPublishesTheCommandEvent() {
        val previous=state()
        val next=previous.copy(commands=setOf(Player.COMMAND_PLAY_PAUSE,Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM))
        assertEquals(setOf(Player.EVENT_AVAILABLE_COMMANDS_CHANGED),next.changesFrom(previous))
    }
}
