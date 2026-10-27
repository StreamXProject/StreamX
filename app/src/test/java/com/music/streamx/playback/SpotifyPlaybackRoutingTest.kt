package com.music.streamx.playback

import org.json.JSONObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpotifyPlaybackRoutingTest {

    @Test
    fun `free tier always uses YouTube`() {
        val freeRoute = JSONObject().put("tier", "free").put("backend", "youtube")
        assertTrue(SpotifyPlaybackRouting.usesYouTube(freeRoute))
    }

    @Test
    fun `unknown tier uses YouTube`() {
        val unknownRoute = JSONObject().put("tier", "unknown").put("backend", "youtube")
        assertTrue(SpotifyPlaybackRouting.usesYouTube(unknownRoute))
    }

    @Test
    fun `missing backend or tier uses YouTube`() {
        assertTrue(SpotifyPlaybackRouting.usesYouTube(JSONObject()))
    }

    @Test
    fun `premium tier uses Spotify playback`() {
        val premiumRoute = JSONObject().put("tier", "premium").put("backend", "spotify")
        assertFalse(SpotifyPlaybackRouting.usesYouTube(premiumRoute))
    }

    @Test
    fun `platinum tier uses Spotify playback`() {
        val platinumRoute = JSONObject().put("tier", "platinum").put("backend", "spotify")
        assertFalse(SpotifyPlaybackRouting.usesYouTube(platinumRoute))
    }

}
