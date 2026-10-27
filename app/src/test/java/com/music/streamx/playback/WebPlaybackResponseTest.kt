package com.music.streamx.playback

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class WebPlaybackResponseTest {
    @org.junit.Test fun catalogueDurationSurvivesBrowserStartupAndDifferentMediaRuntime() {
        for (duration in listOf(0L, 30000L, 230999L))
            org.junit.Assert.assertEquals(230000L, WebPlaybackResponse.duration(230000L, JSONObject().put("duration_ms", duration)))
        org.junit.Assert.assertEquals(200000L, WebPlaybackResponse.duration(200000L, JSONObject().put("duration_ms", 230000L)))
    }
    @org.junit.Test fun tracksWithoutCatalogueRuntimeUseTheBrowserDuration() {
        org.junit.Assert.assertEquals(230000L, WebPlaybackResponse.duration(0, JSONObject().put("duration_ms", 230000L)))
        org.junit.Assert.assertEquals(androidx.media3.common.C.TIME_UNSET, WebPlaybackResponse.duration(0, JSONObject()))
    }

    @Test fun jsonNullDoesNotStopBufferingOrPlayback() {
        // Reproduce Android's optString coercion even on a JVM org.json implementation.
        val status = object : JSONObject() {
            override fun optString(name: String, fallback: String?): String =
                if (name == "error" && isNull(name)) "null" else super.optString(name, fallback)
        }.put("state", "buffering").put("error", JSONObject.NULL)
        assertEquals("null", status.optString("error", ""))
        assertNull(WebPlaybackResponse.error(status))
        status.put("state", "playing").put("duration_ms", 200000).put("position_ms", 1000)
        assertNull(WebPlaybackResponse.error(status))
    }
    @Test fun missingAndBlankErrorsAreNotFailures() {
        assertNull(WebPlaybackResponse.error(JSONObject()))
        assertNull(WebPlaybackResponse.error(JSONObject().put("error", "")))
        assertNull(WebPlaybackResponse.error(JSONObject().put("error", "  ")))
    }
    @Test fun genuineErrorsKeepTheirMessage() {
        assertEquals("Spotify blocked browser playback", WebPlaybackResponse.error(
            JSONObject().put("error", "Spotify blocked browser playback")))
    }
}
