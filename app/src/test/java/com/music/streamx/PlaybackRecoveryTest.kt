package com.music.streamx

import com.music.streamx.playback.PlaybackRecovery
import org.junit.Assert.*
import org.junit.Test

class PlaybackRecoveryTest {
    @Test fun `retry preserves matching metadata and isolates corrupted bytes`() {
        val original = "streamx://watch?v=SxGLPVvNjvY&n=So%20Am%20I&a=Ava%20Max&q=original#track"
        val retry = PlaybackRecovery.retryUri(original, true, "first")!!
        assertTrue(retry.contains("v=SxGLPVvNjvY&n=So%20Am%20I&a=Ava%20Max&q=original"))
        assertTrue(retry.contains("recovery_audio=aac"))
        assertTrue(retry.endsWith("#track"))
        assertNotEquals("SxGLPVvNjvY", PlaybackRecovery.cacheKey("SxGLPVvNjvY", "first"))
        assertNotEquals("SxGLPVvNjvY#alt", PlaybackRecovery.cacheKey("SxGLPVvNjvY", "first"))
    }

    @Test fun `second retry retains AAC and replaces cache identity instead of accumulating markers`() {
        val first = PlaybackRecovery.retryUri("streamx://watch?v=id", true, "first")!!
        val second = PlaybackRecovery.retryUri(first, false, "second")!!
        assertEquals("streamx://watch?v=id&recovery_cache=second&recovery_audio=aac", second)
        assertNotEquals(PlaybackRecovery.cacheKey("id", "first"), PlaybackRecovery.cacheKey("id", "second"))
    }

    @Test fun `network recovery does not force a different codec`() {
        val retry = PlaybackRecovery.retryUri("streamx://watch?v=id", false, "retry")!!
        assertEquals("streamx://watch?v=id&recovery_cache=retry", retry)
    }

    @Test fun `local files and source matching URIs keep their existing recovery paths`() {
        assertNull(PlaybackRecovery.retryUri("file:///music/song.opus", true))
        assertNull(PlaybackRecovery.retryUri("https://host/song?v=id", true))
        assertNull(PlaybackRecovery.retryUri("streamx://source?s=spotify&t=id&n=Song", true))
    }
}
