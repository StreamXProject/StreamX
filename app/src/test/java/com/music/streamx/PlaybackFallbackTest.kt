package com.music.streamx

import androidx.media3.common.MediaItem
import com.music.streamx.playback.PlaybackFallback
import com.music.streamx.playback.toYouTubeFallbackMediaItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackFallbackTest {
    @Test fun explicitNativeExtensionDoesNotSilentlyFallBackToYouTube() {
        assertFalse(PlaybackFallback.isAlternative("streamx://source?s=apple&t=songs:123&extension_native=1",true))
    }


    @Test
    fun `failed upgraded YouTube item is an alternative`() {
        assertTrue(
            PlaybackFallback.isAlternative(
                "streamx://watch?v=5IX-nUxDtJI&n=Bin%20Tere&q=hifi",
                substitutedYouTube = false,
            ),
        )
    }

    @Test
    fun `failed substituted base item is an alternative`() {
        assertTrue(
            PlaybackFallback.isAlternative(
                "streamx://watch?v=5IX-nUxDtJI&n=Bin%20Tere",
                substitutedYouTube = true,
            ),
        )
    }

    @Test
    fun `direct YouTube fallback never falls back recursively`() {
        assertFalse(
            PlaybackFallback.isAlternative(
                "streamx://watch?v=5IX-nUxDtJI&direct_youtube=1&q=original",
                substitutedYouTube = true,
            ),
        )
    }

    @Test
    fun `ordinary YouTube audio is not treated as a failed alternative`() {
        assertFalse(
            PlaybackFallback.isAlternative(
                "streamx://watch?v=5IX-nUxDtJI&n=Bin%20Tere",
                substitutedYouTube = false,
            ),
        )
    }

    @Test
    fun `source already forced to YouTube never falls back recursively`() {
        assertFalse(
            PlaybackFallback.isAlternative(
                "streamx://source?s=addon-1&t=3919244&n=Bin%20Tere&direct_youtube=1&q=original",
                substitutedYouTube = false,
            ),
        )
    }

    @Test
    fun `source fallback removes manifest and upgrade markers and gets its own rendition`() {
        val failed =
            "streamx://source?s=addon-1&t=3919244&n=Bin%20Tere&a=Vishal%20%26%20Shekhar" +
                "&manifest_reopen=dash&q=hifi#part"

        assertEquals(
            "streamx://source?s=addon-1&t=3919244&n=Bin%20Tere&a=Vishal%20%26%20Shekhar" +
                "&direct_youtube=1&q=original#part",
            PlaybackFallback.directYouTubeSourceUri(failed),
        )
    }

    @Test
    fun `spotify source item without direct_youtube is an alternative`() {
        assertTrue(
            PlaybackFallback.isAlternative(
                "streamx://source?s=spotify-1&t=3mrQ2rs6gJH55skAHSaTAk&n=Ala%20Bolelo",
                substitutedYouTube = false,
            ),
        )
    }

    @Test
    fun `spotify source item converted to direct youtube is not an alternative`() {
        val uri = "streamx://source?s=spotify-1&t=3mrQ2rs6gJH55skAHSaTAk&n=Ala%20Bolelo&a=Anirudh"
        val fallbackUri = PlaybackFallback.directYouTubeSourceUri(uri)
        assertTrue(fallbackUri.contains("direct_youtube=1"))
        assertFalse(PlaybackFallback.isAlternative(fallbackUri, substitutedYouTube = false))
    }
}
