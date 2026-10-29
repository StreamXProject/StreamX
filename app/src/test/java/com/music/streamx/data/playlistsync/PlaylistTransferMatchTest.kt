package com.music.streamx.data.playlistsync

import com.music.streamx.data.model.Song
import com.music.streamx.data.model.UserPlaylist
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class PlaylistTransferMatchTest {
    /** A destination whose search answers [results] by query. */
    private class Destination(val results: Map<String,List<Song>>) : PlaylistTransferBackend {
        val asked=mutableListOf<String>()
        override val id="dest"; override val name="Dest"; override val writable=true
        override suspend fun identity()="me"
        override suspend fun playlists(editable: Boolean)=emptyList<UserPlaylist>()
        override suspend fun tracks(playlist: String)=emptyList<Song>()
        override suspend fun search(query: String): List<Song> { asked+=query; return results[query].orEmpty() }
        override fun ownsTrack(id: String)=id.startsWith("yt")
        override suspend fun create(title: String)=TransferDestination("new")
        override suspend fun add(playlist: String,tracks: List<String>) {}
    }
    private val source=Song("amz1","Meant to Be (feat. Florida Georgia Line)","Bebe Rexha",null,"2:43",isExplicit=true)
    private val song=Song("yt1","Meant to Be (feat. Florida Georgia Line)","Bebe Rexha",null,"2:44",isExplicit=false)

    @Test fun aFlagOneCatalogueSetsIsACloseMatchNotMissing() = runBlocking {
        val destination=Destination(mapOf("meant to be bebe rexha" to listOf(song),"meant to be" to listOf(song)))
        val row=matchOnDestination(destination,source)
        assertNull(row.selectedId)
        assertEquals(listOf("yt1"),row.candidates.map{it.videoId})
        assertEquals(RecordOutcome.NOT_ADDED,outcomeOf(row))
    }
    @Test fun theNextQueryIsTriedWhenTheFirstFindsNothing() = runBlocking {
        val clean=source.copy(isExplicit=null)
        val destination=Destination(mapOf("meant to be" to listOf(song)))
        val row=matchOnDestination(destination,clean)
        assertEquals("yt1",row.selectedId)
        assertEquals(2,destination.asked.size)
    }
}
