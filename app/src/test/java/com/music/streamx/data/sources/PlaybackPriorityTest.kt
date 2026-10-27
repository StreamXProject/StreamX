package com.music.streamx.data.sources

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class PlaybackPriorityTest {
    @Test fun `Amazon queue item enters remote routing when Spotify ranks first`() {
        assertTrue(hasEarlierRemoteSource("amazon", listOf("spotify", "amazon"), setOf("spotify")))
        assertFalse(hasEarlierRemoteSource("amazon", listOf("amazon", "spotify"), setOf("spotify")))
        assertFalse(hasEarlierRemoteSource("amazon", listOf("amazon"), setOf("spotify")))
    }

    @Test fun `Spotify match is playable through its renderer without a stream URL`() = runBlocking {
        val match = com.music.streamx.data.model.Song(videoId = "spotify:fixture", title = "Blinding Lights", artist = "The Weeknd", durationText = "3:20", thumbnailUrl = null, fromAutoplay = false)
        val source = object : MusicSource {
            override val configId = "spotify"
            override val kind = SourceKind.APK_PLUGIN
            override val displayName = "Spotify"
            override suspend fun health() = SourceHealth.Ok()
            override suspend fun search(query: String, limit: Int, waitForAll: Boolean, request: StreamRequest?) = listOf(match)
            override suspend fun stream(trackId: String, request: StreamRequest): SourceStream? = error("Browser route must not require a stream URL")
        }
        assertEquals(match, matchRemotePlaybackTrack(source, TrackMatcher.Target("Blinding Lights", "The Weeknd", 200), StreamRequest.Best))
        assertNull(matchRemotePlaybackTrack(source, TrackMatcher.Target("Unrelated Song", "Unrelated Artist", 120), StreamRequest.Best))
    }

    @Test fun `slow preferred source beats a faster originating plugin`() = runBlocking {
        val visited = mutableListOf<String>()
        val result = firstAvailableInOrder(listOf("preferred", "plugin")) {
            visited += it
            if (it == "preferred") delay(20)
            it
        }
        assertEquals("preferred", result)
        assertEquals(listOf("preferred"), visited)
    }

    @Test fun `unavailable sources fall through in saved order`() = runBlocking {
        val visited = mutableListOf<String>()
        val result = firstAvailableInOrder(listOf("first", "second", "plugin")) {
            visited += it
            if (it == "first") null else it
        }
        assertEquals("second", result)
        assertEquals(listOf("first", "second"), visited)
        assertNull(firstAvailableInOrder(listOf("missing")) { null as String? })
    }

    @Test fun `cancellation never starts a lower ranked plugin`() = runBlocking {
        val visited = mutableListOf<String>()
        try {
            firstAvailableInOrder(listOf("preferred", "plugin")) {
                visited += it
                throw CancellationException("Track changed")
                @Suppress("UNREACHABLE_CODE") it
            }
            fail("Cancellation must propagate")
        } catch (_: CancellationException) {
            assertEquals(listOf("preferred"), visited)
        }
    }
}
