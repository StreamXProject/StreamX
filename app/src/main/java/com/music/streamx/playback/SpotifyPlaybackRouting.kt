package com.music.streamx.playback

import org.json.JSONObject

/** Spotify catalogue metadata works for every plan; Spotify audio requires verified Premium. */
internal object SpotifyPlaybackRouting {
    const val PACKAGE = "com.music.streamx.extensions.spotify"

    fun usesYouTube(route: JSONObject): Boolean =
        route.optString("backend") != "spotify" ||
            route.optString("tier") !in setOf("premium", "platinum")
}
