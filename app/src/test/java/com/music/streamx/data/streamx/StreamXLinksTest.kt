package com.music.streamx.data.streamx

import com.music.streamx.data.sources.SourceConfig
import com.music.streamx.data.sources.SourceKind
import com.music.streamx.data.sources.SourceRegistry
import org.junit.Assert.*
import org.junit.Test

class StreamXLinksTest {
    @Test fun appleMusicSharesCatalogueLinksAndHidesPrivateLibraryIds() {
        val previous = SourceRegistry.configs.value
        try {
            SourceRegistry.configs.value = listOf(SourceConfig(id="apple", kind=SourceKind.APK_PLUGIN, extensionPackage="com.music.streamx.extensions.applemusic"))
            assertEquals("https://music.apple.com/song/1624945512", StreamXLinks.track("src:apple::songs:1624945512"))
            assertEquals("https://music.apple.com/album/1624945511", StreamXLinks.browse("apk:apple:album:albums:1624945511"))
            assertEquals("https://music.apple.com/playlist/pl.example", StreamXLinks.browse("apk:apple:playlist:playlists:pl.example"))
            assertNull(StreamXLinks.track("src:apple::library-songs:i.private"))
            assertNull(StreamXLinks.browse("apk:apple:album:library-albums:l.private"))
        } finally { SourceRegistry.configs.value = previous }
    }
    @Test fun spotifySharesPublicLinksWithoutInternalSourceKeys() {
        val previous = SourceRegistry.configs.value
        try {
            SourceRegistry.configs.value = listOf(SourceConfig(id = "spotify", kind = SourceKind.APK_PLUGIN,
                extensionPackage = "com.music.streamx.extensions.spotify"))
            val id = "0VjIjW4GlUZAMYd2vXMi3b"
            assertEquals("https://open.spotify.com/track/$id", StreamXLinks.track("src:spotify::$id"))
            assertEquals("https://open.spotify.com/playlist/$id", StreamXLinks.browse("apk:spotify:playlist:$id"))
            assertNull(StreamXLinks.browse("apk:spotify:collection:liked"))
            assertNull(StreamXLinks.track("src:spotify::invalid/id"))
        } finally { SourceRegistry.configs.value = previous }
    }

    @Test fun serverLinksUsePublicRoutesAndEncodeTrackIds() {
        val previous = SourceRegistry.configs.value
        try {
            SourceRegistry.configs.value = listOf(SourceConfig(id = "server", kind = SourceKind.STREAMX, baseUrl = "https://example.test/api"))
            assertEquals("https://example.test/api/share/tracks/id%2Fone", StreamXLinks.track("src:server::id/one"))
            assertEquals("https://example.test/api/share/playlists/list", StreamXLinks.browse("VLstreamx:server:playlist:list"))
            assertEquals("https://example.test/api/albums/album", StreamXLinks.browse("streamx:server:album:album"))
            assertNull(StreamXLinks.browse("streamx:server:favourites:all"))
            assertNull(StreamXLinks.track("src:old-server::id"))
        } finally { SourceRegistry.configs.value = previous }
    }

    @Test fun youtubeTracksKeepTheirOriginalLinks() {
        assertEquals("https://music.youtube.com/watch?v=video", StreamXLinks.track("video"))
    }
}
