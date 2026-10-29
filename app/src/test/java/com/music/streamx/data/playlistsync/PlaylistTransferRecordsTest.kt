package com.music.streamx.data.playlistsync

import com.music.streamx.data.model.Song
import org.junit.Assert.*
import org.junit.Test

class PlaylistTransferRecordsTest {
    private fun song(id: String)=Song(videoId=id,title="Track $id",artist="Artist",thumbnailUrl=null,durationText="3:20")
    private val unresolved=SyncMatch(song("source"),listOf(song("first"),song("second")),null,true)

    @Test fun brutePicksFirstAndKeepsReviewFlag() {
        val row=brutePlaylistMatches(listOf(unresolved),true).single()
        assertEquals("first",row.selectedId)
        assertTrue(row.uncertain)
        assertEquals(unresolved,brutePlaylistMatches(listOf(row),false).single())
    }
    @Test fun brutePreservesManualChoicesSkipsAndMissingCandidates() {
        val manual=unresolved.copy(selectedId="second",uncertain=false)
        val skipped=unresolved.copy(excluded=true)
        val missing=unresolved.copy(candidates=emptyList())
        assertEquals(listOf(manual,skipped,missing),brutePlaylistMatches(listOf(manual,skipped,missing),true))
    }
    @Test fun recordsRetainAlternativesAndUncertaintyAcrossRestart() {
        val record=TransferRecord("record","Mix","YouTube Music → Apple Music",1234L,
            brutePlaylistMatches(listOf(unresolved),true))
        assertEquals(listOf(record),decodeTransferRecords(encodeTransferRecords(listOf(record))))
    }
    @Test fun recordsRetainSkippedAndUnmatchedRows() {
        val record=TransferRecord("record","Mix","Route",1234L,
            listOf(unresolved.copy(excluded=true),unresolved.copy(candidates=emptyList())))
        assertEquals(listOf(record),decodeTransferRecords(encodeTransferRecords(listOf(record))))
    }
    @Test fun recordsKeepSongsAlreadyThere() {
        val record=TransferRecord("record","Mix","Route",1234L,listOf(unresolved),listOf(song("had") to "Favourites"))
        assertEquals(listOf(record),decodeTransferRecords(encodeTransferRecords(listOf(record))))
    }
    @Test fun everyRowHasAnOutcome() {
        assertEquals(RecordOutcome.NOT_ADDED,outcomeOf(unresolved))
        assertEquals(RecordOutcome.PICKED,outcomeOf(unresolved.copy(selectedId="first")))
        assertEquals(RecordOutcome.ADDED,outcomeOf(unresolved.copy(selectedId="first",uncertain=false)))
        assertEquals(RecordOutcome.NOT_FOUND,outcomeOf(unresolved.copy(candidates=emptyList())))
        assertEquals(RecordOutcome.SKIPPED,outcomeOf(unresolved.copy(excluded=true)))
    }
    @Test fun invalidRecordStorageDoesNotInventSelections() {
        assertTrue(decodeTransferRecords("broken").isEmpty())
    }
}
