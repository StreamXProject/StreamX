package com.music.streamx.data.lyrics

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class PluginLyricsTest {
    private val response=JSONObject().put("enabled",true).put("ttml","<tt xmlns=\"http://www.w3.org/ns/ttml\"><body><div><p begin=\"1.250s\" end=\"3s\">Test line</p></div></body></tt>")
    @Test fun keepsAppleTimingWithoutMatchingAnotherRecording() {
        val lines=requireNotNull(PluginLyrics.parseResponse(response))
        assertEquals(1250L,lines.single().timeMs)
        assertEquals("Test line",lines.single().text)
    }
    @Test fun disabledOrFailedPluginCanFallBackToOtherProviders() {
        assertNull(PluginLyrics.parseResponse(response.put("enabled",false)))
        assertNull(PluginLyrics.parseResponse(response.put("enabled",true).put("error","Unavailable")))
    }
}
