package com.music.streamx.data.playlistsync

import org.junit.Assert.*
import org.junit.Test

class PlaylistTransferLinksTest {
    @Test fun persistedLinksRetainAccountBindingsAndHandledTracks() {
        val link=TransferLink("link","plugin:spotify","plugin:apple","spotify-account","apple-account","original","copy","My mix",setOf("one","two"),false,"2 tracks need review")
        assertEquals(listOf(link),decodeTransferLinks(encodeTransferLinks(listOf(link))))
    }
    @Test fun corruptStorageDoesNotCreateAnUnboundLink() {
        assertTrue(decodeTransferLinks("not json").isEmpty())
        assertTrue(decodeTransferLinks("[{\"id\":\"missing-account-binding\"}]").isEmpty())
    }
}
