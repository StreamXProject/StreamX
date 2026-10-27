package com.music.streamx.data.playlistsync

import com.music.streamx.data.model.Song
import com.music.streamx.data.model.UserPlaylist
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class PlaylistTransferTest {
    private fun song(id: String)=Song(videoId=id,title="Night Drive",artist="Example Artist",thumbnailUrl=null,durationText="3:20")
    private class Backend(override val id: String,override val writable: Boolean=true): PlaylistTransferBackend {
        override val name=id
        var owner="account-one"
        var unreadableCreated=false
        var creates=0
        var writes=0
        var failAfterWrite=false
        var changeOwnerAfterWrite=false
        var found=emptyList<Song>()
        val searches=mutableListOf<String>()
        val contents=linkedMapOf<String,MutableList<Song>>("source" to mutableListOf(),"target" to mutableListOf())
        val batches=mutableListOf<List<String>>()
        override suspend fun identity()=owner
        override suspend fun playlists(editable: Boolean)=contents.keys.map{UserPlaylist(it,it,"",null)}
        override suspend fun tracks(playlist: String): List<Song> {
            if(unreadableCreated && playlist=="created") error("HTTP 404")
            return contents.getValue(playlist).toList()
        }
        override suspend fun search(query: String): List<Song> {searches+=query;return found}
        override fun ownsTrack(id: String)=id.startsWith("${this.id}:")
        override suspend fun create(title: String): TransferDestination {creates++;contents["created"]=mutableListOf();return TransferDestination("created","Provider warning")}
        override suspend fun add(playlist: String,tracks: List<String>) {
            writes++;batches+=tracks;contents.getValue(playlist).addAll(tracks.map{Song(it,it,"artist",null)})
            if(changeOwnerAfterWrite)owner="account-two"
            if(failAfterWrite){failAfterWrite=false;error("Disconnected after the server accepted a batch")}
        }
    }
    private fun plan(source: Backend,destination: Backend,ids: List<String>,target: String?="target")=PlaylistTransferPlan(
        "source",source.owner,destination.owner,target,"Transferred playlist",ids.map{val s=song(it);SyncMatch(s,listOf(s),s.videoId,false)})
    private suspend fun expectFailure(block: suspend ()->Unit) {try {block();fail("Expected transfer to stop")} catch(expected: IllegalStateException) {}}

    @Test fun pluginToPluginMatchesOnlyInTheReceivingCatalogue()=runBlocking {
        val source=Backend("spotify");source.contents.getValue("source")+=song("spotify:one")
        val destination=Backend("apple");destination.found=listOf(song("apple:one"))
        val prepared=preparePlaylistTransfer(source,destination,"source",null,"Name"){_,_->}
        assertEquals("apple:one",prepared.rows.single().selectedId)
        assertEquals(1,destination.searches.size)
        assertEquals(0,destination.writes)
        assertEquals(0,destination.creates)
    }
    @Test fun aPluginCannotReturnAnotherProvidersTracksAsMatches()=runBlocking {
        val source=Backend("spotify");source.contents.getValue("source")+=song("spotify:one")
        val destination=Backend("apple");destination.found=listOf(song("foreign:one"))
        val prepared=preparePlaylistTransfer(source,destination,"source",null,"Name"){_,_->}
        assertNull(prepared.rows.single().selectedId)
        assertTrue(prepared.rows.single().candidates.isEmpty())
    }
    @Test fun selectedTracksAreAddedInSourceOrderAndExistingTracksAreKept()=runBlocking {
        val source=Backend("spotify");val destination=Backend("apple")
        destination.contents.getValue("target")+=song("apple:existing")
        val result=applyPlaylistTransfer(source,destination,plan(source,destination,listOf("apple:b","apple:existing","apple:b","apple:a")),{}, {_,_->})
        assertEquals(listOf("apple:b","apple:a"),destination.batches.single())
        assertEquals(2,result.added);assertEquals(1,result.alreadyPresent)
        assertEquals(listOf("apple:existing","apple:b","apple:a"),destination.contents.getValue("target").map{it.videoId})
    }
    @Test fun changingEitherAccountPreventsAnyWrite()=runBlocking {
        for(changeSource in listOf(true,false)) {
            val source=Backend("spotify");val destination=Backend("apple");val prepared=plan(source,destination,listOf("apple:one"),null)
            if(changeSource)source.owner="other" else destination.owner="other"
            expectFailure{applyPlaylistTransfer(source,destination,prepared,{}, {_,_->})}
            assertEquals(0,destination.creates);assertEquals(0,destination.writes)
        }
    }
    @Test fun accountChangesBetweenBatchesStopFurtherWrites()=runBlocking {
        val source=Backend("spotify");val destination=Backend("apple");destination.changeOwnerAfterWrite=true
        expectFailure{applyPlaylistTransfer(source,destination,plan(source,destination,(1..60).map{"apple:$it"}),{}, {_,_->})}
        assertEquals(1,destination.writes);assertEquals(25,destination.contents.getValue("target").size)
    }
    @Test fun partialFailureCanResumeWithoutCreatingOrAddingDuplicates()=runBlocking {
        val source=Backend("spotify");val destination=Backend("apple");destination.failAfterWrite=true
        val prepared=plan(source,destination,(1..40).map{"apple:$it"},null)
        var created: String?=null
        expectFailure{applyPlaylistTransfer(source,destination,prepared,{created=it}, {_,_->})}
        assertEquals("created",created)
        val result=applyPlaylistTransfer(source,destination,prepared.copy(destinationPlaylist=created),{}, {_,_->})
        assertEquals(1,destination.creates);assertEquals(40,destination.contents.getValue("created").size)
        assertEquals(15,result.added);assertEquals(25,result.alreadyPresent)
    }
    @Test fun anUneditableDestinationIsRejectedBeforeMutation()=runBlocking {
        val source=Backend("spotify");val destination=Backend("apple")
        expectFailure{applyPlaylistTransfer(source,destination,plan(source,destination,listOf("apple:one"),"missing"),{}, {_,_->})}
        assertEquals(0,destination.writes)
    }
    @Test fun rejectedForeignSelectionsAndSkippedRowsNeverReachTheProvider()=runBlocking {
        val source=Backend("spotify");val destination=Backend("apple")
        expectFailure{applyPlaylistTransfer(source,destination,plan(source,destination,listOf("foreign:one")),{}, {_,_->})}
        val prepared=plan(source,destination,listOf("apple:one","apple:two"))
        val result=applyPlaylistTransfer(source,destination,prepared.copy(rows=prepared.rows.mapIndexed{i,row->if(i==0)row.copy(excluded=true) else row}),{}, {_,_->})
        assertEquals(1,result.added);assertEquals(listOf("apple:two"),destination.batches.single())
    }
    @Test fun anUnreviewedSelectionIsRejected()=runBlocking {
        val source=Backend("spotify");val destination=Backend("apple")
        val prepared=plan(source,destination,listOf("apple:one"))
        expectFailure{applyPlaylistTransfer(source,destination,prepared.copy(rows=prepared.rows.map{it.copy(selectedId="apple:unreviewed")}),{}, {_,_->})}
        assertEquals(0,destination.writes)
    }
    @Test fun selfTransferAndReadOnlyDestinationsAreRejected()=runBlocking {
        val backend=Backend("apple")
        expectFailure{applyPlaylistTransfer(backend,backend,plan(backend,backend,listOf("apple:one"),"source"),{}, {_,_->})}
        val readOnly=Backend("readonly",false)
        expectFailure{applyPlaylistTransfer(backend,readOnly,plan(backend,readOnly,listOf("readonly:one"),null),{}, {_,_->})}
        assertEquals(0,readOnly.creates)
    }
    @Test fun zeroSelectedTracksDoNotCreateAnEmptyRemotePlaylist()=runBlocking {
        val source=Backend("spotify");val destination=Backend("apple")
        expectFailure{applyPlaylistTransfer(source,destination,plan(source,destination,emptyList(),null),{}, {_,_->})}
        assertEquals(0,destination.creates)
    }

    @Test fun automaticSyncDoesNotSearchPreviouslyHandledTracks()=runBlocking {
        val source=Backend("spotify");val destination=Backend("apple")
        source.contents.getValue("source")+=listOf(song("spotify:old"),song("spotify:new"))
        destination.found=listOf(song("apple:new"))
        val prepared=preparePlaylistTransfer(source,destination,"source","target","Name",handled=setOf("spotify:old")){_,_->}
        assertEquals(listOf("spotify:new"),prepared.rows.map{it.source.videoId})
        assertEquals(1,destination.searches.size)
        val pending=pendingSyncPlan(prepared,setOf("spotify:old"))
        applyPlaylistTransfer(source,destination,pending,{}, {_,_->})
        assertEquals(listOf("apple:new"),destination.batches.single())
    }
    @Test fun automaticSyncLeavesUncertainAndSkippedTracksForReview() {
        val source=Backend("spotify");val destination=Backend("apple")
        val prepared=plan(source,destination,listOf("apple:handled","apple:uncertain","apple:skip","apple:new"))
        val rows=prepared.rows.mapIndexed { i,row ->when(i) {1->row.copy(uncertain=true);2->row.copy(excluded=true);else->row} }
        assertEquals(listOf("apple:new"),pendingSyncPlan(prepared.copy(rows=rows),setOf("apple:handled")).rows.map{it.source.videoId})
    }
    @Test fun emptyOrUnchangedLinkedPlaylistsProduceNoWrites()=runBlocking {
        val source=Backend("spotify");val destination=Backend("apple")
        val empty=preparePlaylistTransfer(source,destination,"source","target","Name",allowEmpty=true){_,_->}
        assertTrue(pendingSyncPlan(empty,emptySet()).rows.isEmpty())
        source.contents.getValue("source")+=song("spotify:old")
        val unchanged=preparePlaylistTransfer(source,destination,"source","target","Name",handled=setOf("spotify:old")){_,_->}
        assertTrue(unchanged.rows.isEmpty());assertTrue(destination.searches.isEmpty());assertEquals(0,destination.writes)
    }
    @Test fun newDestinationCanReceiveTracksBeforeItsTracksEndpointIsReadable()=runBlocking {
        val source=Backend("youtube");val destination=Backend("apple")
        destination.unreadableCreated=true
        val result=applyPlaylistTransfer(source,destination,plan(source,destination,listOf("apple:one"),null),{}, {_,_->})
        assertEquals(1,result.added)
        assertEquals(1,destination.creates)
        assertEquals(1,destination.writes)
    }

}
