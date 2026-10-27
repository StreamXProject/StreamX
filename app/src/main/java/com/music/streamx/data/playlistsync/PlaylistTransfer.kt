package com.music.streamx.data.playlistsync

import com.music.streamx.data.model.Song
import com.music.streamx.data.model.UserPlaylist
import com.music.streamx.data.sources.TrackMatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.atomic.AtomicInteger

/** Account-bound metadata operations; no audio or login credentials cross providers. */
internal interface PlaylistTransferBackend {
    val id: String
    val name: String
    val writable: Boolean
    val supportsUserPlaylists: Boolean get() = true
    val pluginPackage: String? get() = null
    suspend fun identity(): String
    suspend fun playlists(editable: Boolean): List<UserPlaylist>
    suspend fun tracks(playlist: String): List<Song>
    suspend fun search(query: String): List<Song>
    fun ownsTrack(id: String): Boolean
    suspend fun create(title: String): TransferDestination
    suspend fun add(playlist: String, tracks: List<String>)
}
internal data class TransferDestination(val id: String,val warning: String = "")
internal data class PlaylistTransferPlan(
    val sourcePlaylist: String,
    val sourceIdentity: String,
    val destinationIdentity: String,
    val destinationPlaylist: String?,
    val title: String,
    val rows: List<SyncMatch>,
)
internal data class PlaylistTransferResult(val playlistId: String,val added: Int,val alreadyPresent: Int,val warning: String)

internal suspend fun guardTransferAccounts(source: PlaylistTransferBackend,destination: PlaylistTransferBackend,sourceIdentity: String,destinationIdentity: String) {
    check(source.identity()==sourceIdentity) { "Source account changed. Review the transfer again." }
    check(destination.identity()==destinationIdentity) { "Destination account changed. Review the transfer again." }
}

internal suspend fun preparePlaylistTransfer(source: PlaylistTransferBackend,destination: PlaylistTransferBackend,
    sourcePlaylist: String,destinationPlaylist: String?,title: String,handled: Set<String> = emptySet(),allowEmpty: Boolean = false,progress: (Int,Int)->Unit): PlaylistTransferPlan = coroutineScope {
    check(destination.writable) { "This provider cannot receive playlists" }
    check(source.id!=destination.id || sourcePlaylist!=destinationPlaylist) { "Choose a different destination playlist" }
    val sourceOwner=source.identity();val destinationOwner=destination.identity()
    check(sourceOwner.isNotBlank() && destinationOwner.isNotBlank()) { "Connect both accounts first" }
    destinationPlaylist?.let { id -> check(destination.playlists(true).any{it.playlistId==id}) { "Choose an editable destination playlist" } }
    val allSongs=source.tracks(sourcePlaylist)
    check(allSongs.isNotEmpty() || allowEmpty) { "The source playlist is empty" }
    val songs=allSongs.filterNot{it.videoId in handled}
    guardTransferAccounts(source,destination,sourceOwner,destinationOwner)
    val permits=Semaphore(3);val done=AtomicInteger()
    val matches=songs.map { song -> async {
        permits.withPermit {
            val candidates=if(destination.ownsTrack(song.videoId)) listOf(song) else {
                val query=TrackMatcher.queries(TrackMatcher.targetOf(song)).firstOrNull()
                if(query==null) emptyList() else destination.search(query).filter { destination.ownsTrack(it.videoId) }
            }
            val row=if(destination.ownsTrack(song.videoId)) SyncMatch(song,listOf(song),song.videoId,false)
                else reviewPlaylistMatch(song,candidates)
            progress(done.incrementAndGet(),songs.size)
            row
        }
    }}.awaitAll()
    guardTransferAccounts(source,destination,sourceOwner,destinationOwner)
    PlaylistTransferPlan(sourcePlaylist,sourceOwner,destinationOwner,destinationPlaylist,title,matches)
}

/** Add-only, deduplicated transfer. Never retry an ambiguous provider mutation automatically. */
internal suspend fun applyPlaylistTransfer(source: PlaylistTransferBackend,destination: PlaylistTransferBackend,
    plan: PlaylistTransferPlan,onCreated: (String)->Unit,progress: (Int,Int)->Unit): PlaylistTransferResult {
    check(destination.writable) { "This provider cannot receive playlists" }
    check(plan.title.isNotBlank()) { "Enter a playlist name" }
    val ids=plan.rows.mapNotNull { row ->
        if(row.excluded) null else row.selectedId?.also { id ->
            check(destination.ownsTrack(id) && row.candidates.any { it.videoId==id }) { "Review the selected track matches again" }
        }
    }.distinct()
    check(ids.isNotEmpty()) { "Choose at least one track" }
    guardTransferAccounts(source,destination,plan.sourceIdentity,plan.destinationIdentity)
    check(source.id!=destination.id || plan.sourcePlaylist!=plan.destinationPlaylist) { "Choose a different destination playlist" }
    plan.destinationPlaylist?.let { id ->
        check(destination.playlists(true).any{it.playlistId==id}) { "The destination is no longer editable by this account" }
    }
    guardTransferAccounts(source,destination,plan.sourceIdentity,plan.destinationIdentity)
    val target=plan.destinationPlaylist?.let { TransferDestination(it) } ?: destination.create(plan.title.trim()).also { onCreated(it.id) }
    guardTransferAccounts(source,destination,plan.sourceIdentity,plan.destinationIdentity)
    // A destination created by this operation is known to be empty. Apple may
    // not expose its tracks relationship until the first add has completed.
    // Existing/retried destinations still require a read for deduplication.
    val existing=if(plan.destinationPlaylist==null) emptySet() else destination.tracks(target.id).mapTo(HashSet()){it.videoId}
    val additions=ids.filterNot { it in existing }
    var added=0
    additions.chunked(25).forEach { batch ->
        guardTransferAccounts(source,destination,plan.sourceIdentity,plan.destinationIdentity)
        destination.add(target.id,batch)
        added+=batch.size
        progress(added,additions.size)
    }
    guardTransferAccounts(source,destination,plan.sourceIdentity,plan.destinationIdentity)
    return PlaylistTransferResult(target.id,added,ids.size-additions.size,target.warning)
}
