package com.music.streamx.data.playlistsync

import com.music.streamx.data.model.Song
import org.json.JSONArray
import org.json.JSONObject

internal data class TransferRecord(
    val id: String, val title: String, val route: String, val createdAt: Long,
    val rows: List<SyncMatch>,
)

private fun songJson(song: Song) = JSONObject().put("id",song.videoId).put("title",song.title)
    .put("artist",song.artist).put("art",song.thumbnailUrl).put("duration",song.durationText)
private fun readSong(json: JSONObject) = Song(videoId=json.getString("id"),title=json.getString("title"),
    artist=json.getString("artist"),thumbnailUrl=json.optString("art").takeIf{it.isNotBlank()},
    durationText=json.optString("duration").takeIf{it.isNotBlank()})

internal fun encodeTransferRecords(records: List<TransferRecord>): String = JSONArray().apply {
    records.forEach { record -> put(JSONObject().put("id",record.id).put("title",record.title)
        .put("route",record.route).put("createdAt",record.createdAt).put("rows",JSONArray().apply {
            record.rows.forEach { row -> put(JSONObject().put("source",songJson(row.source))
                .put("candidates",JSONArray().apply {row.candidates.forEach{put(songJson(it))}})
                .put("selected",row.selectedId).put("uncertain",row.uncertain).put("excluded",row.excluded)) }
        })) }
}.toString()

internal fun decodeTransferRecords(value: String): List<TransferRecord> = runCatching {
    val array=JSONArray(value)
    (0 until array.length()).map { index ->
        val json=array.getJSONObject(index)
        val rows=json.getJSONArray("rows")
        TransferRecord(json.getString("id"),json.getString("title"),json.getString("route"),json.getLong("createdAt"),
            (0 until rows.length()).map { rowIndex ->
                val row=rows.getJSONObject(rowIndex);val candidates=row.getJSONArray("candidates")
                SyncMatch(readSong(row.getJSONObject("source")),(0 until candidates.length()).map{readSong(candidates.getJSONObject(it))},
                    row.optString("selected").takeIf{it.isNotBlank()},row.optBoolean("uncertain"),row.optBoolean("excluded"))
            })
    }
}.getOrDefault(emptyList())
