package com.music.streamx.data.playlistsync

import com.music.streamx.data.model.UserPlaylist
import org.junit.Assert.*
import org.junit.Test

class PlaylistTransferVisibilityTest {
    private val rows=listOf(UserPlaylist("one","Morning Drive","You",null),UserPlaylist("two","Weekend","Artist",null),UserPlaylist("three","Late Night","Friend",null))
    @Test fun hidingDoesNotRemoveTheUnderlyingPlaylists() {
        assertEquals(listOf("one","three"),visibleTransferPlaylists(rows,setOf("two"),false,"").map{it.playlistId})
        assertEquals(3,rows.size)
    }
    @Test fun hiddenChoicesCanAlwaysBeShownAgainInOriginalOrder() {
        assertEquals(rows,visibleTransferPlaylists(rows,setOf("one","two","three"),true,""))
    }
    @Test fun hiddenPreferencesCannotCrossAccountsOrProviders() {
        val key=transferVisibilityKey("spotify","account-one")
        assertEquals(key,transferVisibilityKey("spotify","account-one"))
        assertNotEquals(key,transferVisibilityKey("spotify","account-two"))
        assertNotEquals(key,transferVisibilityKey("apple","account-one"))
        assertNotEquals(transferVisibilityKey("a","bc"),transferVisibilityKey("ab","c"))
    }
    @Test fun searchesAreTrimmedAndIgnoreCaseWithoutRevealingHiddenRows() {
        assertEquals(listOf("one"),visibleTransferPlaylists(rows,setOf("two"),false," MORNING ").map{it.playlistId})
        assertTrue(visibleTransferPlaylists(rows,setOf("two"),false,"artist").isEmpty())
        assertEquals(listOf("two"),visibleTransferPlaylists(rows,setOf("two"),true,"artist").map{it.playlistId})
    }
}
