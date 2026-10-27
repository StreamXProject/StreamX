package com.music.streamx.data.playlistsync

import com.music.streamx.data.model.Song
import org.junit.Assert.*
import org.junit.Test

class PlaylistSyncMatcherTest {
    private fun track(id: String, title: String = "Night Drive", artist: String = "Example Artist", duration: String? = "3:20") =
        Song(videoId = id, title = title, artist = artist, thumbnailUrl = null, durationText = duration)

    @Test fun aUniqueMatchingRecordingCanBeSelected() {
        assertEquals("youtube-one", reviewPlaylistMatch(track("source"), listOf(track("youtube-one"))).selectedId)
    }

    @Test fun duplicateCatalogueReleasesDoNotRequireReview() {
        val match=reviewPlaylistMatch(track("source"),listOf(track("one"),track("two"),track("three")))
        assertEquals("one",match.selectedId)
        assertEquals(1,match.candidates.size)
        assertFalse(match.uncertain)
    }
    @Test fun genuinelyDifferentCloseRecordingsStillRequireReview() {
        val match=reviewPlaylistMatch(track("source"),listOf(track("one",duration="3:19"),track("two",duration="3:21")))
        assertNull(match.selectedId)
        assertTrue(match.uncertain)
    }
    @Test fun brandedMusicCreditCanMatchItsSourceArtist() {
        val match=reviewPlaylistMatch(track("source",artist="VALORANT"),listOf(track("one",artist="VALORANT Music, Grabbitz")))
        assertEquals("one",match.selectedId)
    }
    @Test fun explicitAndCleanReleasesRemainSeparateChoices() {
        val match=reviewPlaylistMatch(track("source"),listOf(track("clean"),track("explicit").copy(isExplicit=true)))
        assertEquals(2,match.candidates.size)
    }
    @Test fun rememberedDuplicateReleaseRemainsVisible() {
        val match=reviewPlaylistMatch(track("source"),listOf(track("one"),track("two")),"two")
        assertEquals("two",match.selectedId)
        assertTrue(match.candidates.any {it.videoId=="two"})
    }

    @Test fun unknownDurationRequiresReview() {
        assertNull(reviewPlaylistMatch(track("source", duration = null), listOf(track("one"))).selectedId)
    }

    @Test fun aRemixCannotSilentlyReplaceTheOriginal() {
        assertNull(reviewPlaylistMatch(track("source"), listOf(track("remix", title = "Night Drive (Remix)"))).selectedId)
    }

    @Test fun explicitChoicesSurviveAChangedSearchResult() {
        assertEquals("chosen", reviewPlaylistMatch(track("source"), emptyList(), "chosen").selectedId)
    }
    @Test fun albumVersionMarkersAreNotCollapsed() {
        val match=reviewPlaylistMatch(track("source"),listOf(track("album").copy(albumName="Album"),track("remastered").copy(albumName="Album Remastered")))
        assertEquals(2,match.candidates.size)
    }

}
