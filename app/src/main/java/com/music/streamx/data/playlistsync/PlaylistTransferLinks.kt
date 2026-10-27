package com.music.streamx.data.playlistsync

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.json.JSONArray
import org.json.JSONObject

/** Only metadata changes in this process are observable immediately. */
internal object PlaylistTransferEvents {
    private val changes=MutableSharedFlow<String>(extraBufferCapacity=64)
    val flow=changes.asSharedFlow()
    fun changed(provider: String) {changes.tryEmit(provider)}
}
internal data class TransferLink(
    val id: String,val source: String,val destination: String,
    val sourceAccount: String,val destinationAccount: String,
    val sourcePlaylist: String,val destinationPlaylist: String,val title: String,
    val handled: Set<String>,val enabled: Boolean=true,val status: String="Waiting for changes",
)
internal fun encodeTransferLinks(links: List<TransferLink>): String=JSONArray().apply {
    links.forEach { l -> put(JSONObject().put("id",l.id).put("source",l.source).put("destination",l.destination)
        .put("sourceAccount",l.sourceAccount).put("destinationAccount",l.destinationAccount)
        .put("sourcePlaylist",l.sourcePlaylist).put("destinationPlaylist",l.destinationPlaylist)
        .put("title",l.title).put("handled",JSONArray(l.handled.toList())).put("enabled",l.enabled).put("status",l.status)) }
}.toString()
internal fun decodeTransferLinks(value: String): List<TransferLink> = runCatching {
    val array=JSONArray(value)
    (0 until array.length()).map { i -> val l=array.getJSONObject(i);val handled=l.getJSONArray("handled")
        TransferLink(l.getString("id"),l.getString("source"),l.getString("destination"),l.getString("sourceAccount"),l.getString("destinationAccount"),
            l.getString("sourcePlaylist"),l.getString("destinationPlaylist"),l.getString("title"),
            (0 until handled.length()).map{handled.getString(it)}.toSet(),l.optBoolean("enabled",true),l.optString("status","Waiting for changes"))
    }
}.getOrDefault(emptyList())

/** Uncertain matches remain pending for explicit review; previously handled tracks are not re-added. */
internal fun pendingSyncPlan(plan: PlaylistTransferPlan,handled: Set<String>): PlaylistTransferPlan = plan.copy(rows=plan.rows.filter {
    it.source.videoId !in handled && !it.uncertain && !it.excluded && it.selectedId!=null
})
