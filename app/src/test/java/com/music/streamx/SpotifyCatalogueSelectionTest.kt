package com.music.streamx

import com.music.streamx.data.sources.SourceConfig
import com.music.streamx.data.sources.SourceKind
import com.music.streamx.data.sources.SourceRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SpotifyCatalogueSelectionTest {
    private val spotify = SourceConfig(
        id = "spotify", kind = SourceKind.APK_PLUGIN, enabled = true,
        extensionPackage = "com.music.streamx.extensions.spotify",
        extensionAuthority = "spotify.source", extensionCertificate = "fixture",
    )
    private val youtube = SourceConfig(id = "youtube", kind = SourceKind.YOUTUBE, enabled = true)

    @Test fun `enabled Spotify restores its feed even with enabled YouTube and no saved selection`() {
        assertEquals("spotify", SourceRegistry.catalogueExtension(listOf(youtube, spotify), null))
    }

    @Test fun `stale saved extension cannot redirect the enabled Spotify feed`() {
        assertEquals("spotify", SourceRegistry.catalogueExtension(listOf(youtube, spotify), "removed-extension"))
    }

    @Test fun `disabled or incomplete Spotify does not claim the feed`() {
        assertNull(SourceRegistry.catalogueExtension(listOf(youtube, spotify.copy(enabled = false)), null))
        assertNull(SourceRegistry.catalogueExtension(listOf(spotify.copy(extensionCertificate = "")), null))
    }

    @Test fun `explicitly chosen feed may be left out of playback`() {
        assertEquals("spotify", SourceRegistry.catalogueExtension(listOf(youtube, spotify.copy(enabled = false)), "spotify"))
    }

    @Test fun `explicit selection of another enabled extension is retained`() {
        val other = spotify.copy(id = "other", extensionPackage = "example.other")
        assertEquals("other", SourceRegistry.catalogueExtension(listOf(spotify, other), "other"))
    }
    @Test fun `hidden Spotify cannot claim catalogue or supply playback`() {
        val hidden = spotify.copy(shownInSources = false)
        assertNull(SourceRegistry.catalogueExtension(listOf(youtube, hidden), "spotify"))
        assertEquals(listOf(youtube), SourceRegistry.enabledConfigs(listOf(youtube, hidden)))
    }

    @Test fun `legacy source configuration stays visible`() {
        val config = kotlinx.serialization.json.Json.decodeFromString<SourceConfig>(
            """{"id":"old","kind":"APK_PLUGIN","enabled":false}""")
        assertEquals(true, config.shownInSources)
    }
}
