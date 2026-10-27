package com.music.streamx.data.cloudstream

import org.json.JSONArray
import org.json.JSONObject

/** Entity timestamps include tombstones, so stale devices cannot resurrect deleted library items. */
internal object LibraryMerge {
    fun nextTimestamp(clock: Long, previous: Long) = maxOf(clock, previous + 1)
    fun apply(local: JSONObject, remote: JSONArray): JSONObject {
        val result = JSONObject(local.toString())
        for (i in 0 until remote.length()) {
            val item = remote.getJSONObject(i)
            val key = item.getString("key")
            if (item.getLong("updated") >= (result.optJSONObject(key)?.optLong("updated") ?: 0L)) result.put(key,item)
        }
        return result
    }
    fun acknowledge(pending: JSONObject, sent: List<JSONObject>): JSONObject {
        val result = JSONObject(pending.toString())
        for (item in sent) if (result.optJSONObject(item.getString("key"))?.optLong("updated") == item.getLong("updated")) result.remove(item.getString("key"))
        return result
    }
}
