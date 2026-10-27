package com.music.streamx

import com.music.streamx.data.sources.SourceConfig
import com.music.streamx.data.sources.SourceKind
import com.music.streamx.data.sources.SourceRegistry
import org.junit.Assert.assertEquals
import org.junit.Test

/** Playback priority follows the saved order for every source kind. */
class SourceOrderTest {
    private val streamx = SourceConfig(id = "streamx", kind = SourceKind.STREAMX, baseUrl = "https://sx.example", enabled = true)
    private val spotify = SourceConfig(
        id = "spotify", kind = SourceKind.APK_PLUGIN, enabled = true,
        extensionPackage = "com.music.streamx.extensions.spotify",
        extensionAuthority = "spotify.source", extensionCertificate = "fixture",
    )
    private val apple = spotify.copy(id = "apple", extensionPackage = "com.music.streamx.extensions.applemusic")
    private val addon = SourceConfig(id = "addon", kind = SourceKind.ADDON, baseUrl = "https://flac.example", enabled = true)
    private val jio = SourceConfig(id = "jio", kind = SourceKind.JIOSAAVN, enabled = true)
    private val youtube = SourceConfig(id = "youtube", kind = SourceKind.YOUTUBE, enabled = true)

    /** Stored order on disk is deliberately scrambled so nothing passes by accident. */
    private val stored = listOf(youtube, addon, apple, jio, spotify, streamx)

    @Test fun `without a saved order the old fixed walk is reproduced`() {
        val walk = SourceRegistry.ordered(stored, order = emptyList()).map { it.id }
        // Rank -1 first, then the user-added kinds in stored order, then JioSaavn, then YouTube.
        assertEquals(listOf("streamx", "addon", "apple", "spotify", "jio", "youtube"), walk)
    }

    @Test fun `a saved order wins over kind, for every kind`() {
        val order = listOf("spotify", "apple", "youtube", "addon", "jio", "streamx")
        assertEquals(order, SourceRegistry.ordered(stored, order).map { it.id })
    }

    @Test fun `a partial saved order is kept`() {
        val order = listOf("addon", "youtube", "spotify")
        val walk = SourceRegistry.ordered(stored, order).map { it.id }
        assertEquals(listOf("addon", "youtube", "spotify", "streamx", "apple", "jio"), walk)
    }

    @Test fun `sources the saved order does not name follow it by rank`() {
        val order = listOf("spotify", "apple")
        val walk = SourceRegistry.ordered(stored, order).map { it.id }
        assertEquals(listOf("spotify", "apple", "streamx", "addon", "jio", "youtube"), walk)
    }

    @Test fun `ids in the saved order that name nothing are ignored`() {
        val order = listOf("gone", "youtube", "also-gone", "spotify")
        val walk = SourceRegistry.ordered(listOf(spotify, youtube), order).map { it.id }
        assertEquals(listOf("youtube", "spotify"), walk)
    }

    @Test fun `ordering a filtered list keeps the relative order of what is left`() {
        val order = listOf("spotify", "apple", "youtube", "addon")
        val enabledOnly = SourceRegistry.enabledConfigs(stored.map { if (it.id == "apple") it.copy(enabled = false) else it })
        val walk = SourceRegistry.ordered(enabledOnly, order).map { it.id }
        assertEquals(listOf("spotify", "youtube", "addon", "streamx", "jio"), walk)
    }

}
