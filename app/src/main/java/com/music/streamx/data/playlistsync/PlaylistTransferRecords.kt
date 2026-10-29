package com.music.streamx.data.playlistsync

import com.music.streamx.data.model.Song
import org.json.JSONArray
import org.json.JSONObject

/**
 * One transfer, every song in it: [rows] are the songs that were matched,
 * picked, skipped or not found, and [known] the ones left out because they
 * were already in one of the listener's playlists, with that playlist's name.
 */
internal data class TransferRecord(
    val id: String, val title: String, val route: String, val createdAt: Long,
    val rows: List<SyncMatch>,
    val known: List<Pair<Song,String>> = emptyList(),
)

/** How each song of a record ended up, for its sections and counts. */
internal enum class RecordOutcome { ADDED, PICKED, ALREADY_THERE, NOT_ADDED, NOT_FOUND, SKIPPED }

internal fun outcomeOf(row: SyncMatch): RecordOutcome = when {
    row.excluded && row.candidates.isEmpty() -> RecordOutcome.NOT_FOUND
    row.excluded -> RecordOutcome.SKIPPED
    row.selectedId!=null && row.uncertain -> RecordOutcome.PICKED
    row.selectedId!=null -> RecordOutcome.ADDED
    row.candidates.isEmpty() -> RecordOutcome.NOT_FOUND
    else -> RecordOutcome.NOT_ADDED
}

/** A row as a record keeps it: alternatives only where there was a choice to make. */
internal fun recordRow(row: SyncMatch): SyncMatch =
    if(row.selectedId!=null && !row.uncertain) row.copy(candidates=row.candidates.filter{it.videoId==row.selectedId}) else row

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
        }).put("known",JSONArray().apply {
            record.known.forEach { (song,playlist) -> put(JSONObject().put("song",songJson(song)).put("playlist",playlist)) }
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
            },
            json.optJSONArray("known")?.let { known -> (0 until known.length()).map { known.getJSONObject(it).let { k ->
                readSong(k.getJSONObject("song")) to k.optString("playlist") } } }.orEmpty())
    }
}.getOrDefault(emptyList())
