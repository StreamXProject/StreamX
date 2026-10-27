package com.music.streamx.playback

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class WebNowPlayingTest {
    @Test fun stationRecordingChangesIndependentlyOfStationSelection() {
        fun status(id: String, title: String) = JSONObject().put("track_id", "stations:ra.discovery")
            .put("now_playing", JSONObject().put("id", id).put("title", title).put("artist", "Artist")
                .put("thumbnail_url", "https://example.com/art.jpg"))
        val first = WebNowPlaying.from(status("123", "First"))!!
        val second = WebNowPlaying.from(status("456", "Second"))!!
        assertNotEquals(first, second)
        assertEquals("456", second.id)
        assertEquals("Second", second.title)
    }
    @Test fun missingBroadcastDoesNotBecomeFakeSongOrNullText() {
        assertNull(WebNowPlaying.from(JSONObject()))
        assertNull(WebNowPlaying.from(JSONObject().put("now_playing", JSONObject.NULL)))
        assertNull(WebNowPlaying.from(JSONObject().put("now_playing", JSONObject().put("id", "123").put("title", ""))))
    }
    @Test fun untrustedArtworkAndOversizedMetadataAreBounded() {
        val row = JSONObject().put("id", "123").put("title", "A".repeat(1000)).put("thumbnail_url", "file:///private/art")
        val item = WebNowPlaying.from(JSONObject().put("now_playing", row))!!
        assertEquals(500, item.title.length)
        assertNull(item.artwork)
    }
}
