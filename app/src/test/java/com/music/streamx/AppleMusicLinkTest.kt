package com.music.streamx

import com.music.streamx.playback.AppleMusicLink
import org.junit.Assert.*
import org.junit.Test

class AppleMusicLinkTest {
    @Test fun albumSongParameterOpensTheNamedSong() {
        assertEquals(AppleMusicLink("track", "songs:1624945512"), AppleMusicLink.parse("https://music.apple.com/us/album/never-gonna-give-you-up/1624945511?i=1624945512&ls=1"))
    }
    @Test fun songLinksWithAndWithoutSlug() {
        for (url in listOf("https://music.apple.com/in/song/you-move-me/1624945520", "https://music.apple.com/song/1624945520"))
            assertEquals(AppleMusicLink("track", "songs:1624945520"), AppleMusicLink.parse(url))
    }
    @Test fun collectionLinksKeepTheirCatalogueType() {
        assertEquals(AppleMusicLink("album", "albums:1624945511"), AppleMusicLink.parse("https://music.apple.com/us/album/title/1624945511"))
        assertEquals(AppleMusicLink("playlist", "playlists:pl.3950454ced8c45a3b0cc693c2a7db97b"), AppleMusicLink.parse("https://music.apple.com/us/playlist/title/pl.3950454ced8c45a3b0cc693c2a7db97b"))
        assertEquals(AppleMusicLink("artist", "artists:159260351"), AppleMusicLink.parse("https://music.apple.com/us/artist/taylor-swift/159260351"))
    }
    @Test fun unrelatedAndMalformedLinksAreRejected() {
        for (url in listOf("https://music.apple.com.evil.test/us/song/123", "https://evil@music.apple.com/us/song/123", "https://music.apple.com/us/song/../secret", "https://music.apple.com/us/album/title/123?i=nope", "file://music.apple.com/us/song/123", "https://music.apple.com/us/account"))
            assertNull(url, AppleMusicLink.parse(url))
    }
}
