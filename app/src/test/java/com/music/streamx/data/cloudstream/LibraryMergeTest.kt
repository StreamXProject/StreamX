package com.music.streamx.data.cloudstream

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class LibraryMergeTest {
    private fun item(time: Long, deleted: Boolean = false) = JSONObject().put("key","favourite:track").put("updated",time).put("deleted",deleted)
    @Test fun `older remote data cannot resurrect an offline deletion`() {
        val local = JSONObject().put("favourite:track",item(20,true))
        val merged = LibraryMerge.apply(local,JSONArray().put(item(10)))
        assertTrue(merged.getJSONObject("favourite:track").getBoolean("deleted"))
    }
    @Test fun `newer cloud deletion replaces local favourite`() {
        val merged = LibraryMerge.apply(JSONObject().put("favourite:track",item(10)),JSONArray().put(item(20,true)))
        assertTrue(merged.getJSONObject("favourite:track").getBoolean("deleted"))
    }
    @Test fun `edits made during a sync remain pending`() {
        val pending = JSONObject().put("favourite:track",item(30))
        assertTrue(LibraryMerge.acknowledge(pending,listOf(item(20))).has("favourite:track"))
        assertFalse(LibraryMerge.acknowledge(pending,listOf(item(30))).has("favourite:track"))
    }
    @Test fun `same millisecond and backwards clocks still distinguish successive edits`() {
        assertEquals(31,LibraryMerge.nextTimestamp(30,30))
        assertEquals(31,LibraryMerge.nextTimestamp(10,30))
    }
}
