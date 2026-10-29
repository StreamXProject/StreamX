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
    /** Source songs left out because the listener already has them, with the playlist each is in. */
    val known: List<Pair<com.music.streamx.data.model.Song,String>> = emptyList(),
)
internal data class PlaylistTransferResult(val playlistId: String,val added: Int,val alreadyPresent: Int,val warning: String)

internal suspend fun guardTransferAccounts(source: PlaylistTransferBackend,destination: PlaylistTransferBackend,sourceIdentity: String,destinationIdentity: String) {
    check(source.identity()==sourceIdentity) { "Source account changed. Review the transfer again." }
    check(destination.identity()==destinationIdentity) { "Destination account changed. Review the transfer again." }
}

internal suspend fun preparePlaylistTransfer(source: PlaylistTransferBackend,destination: PlaylistTransferBackend,
    sourcePlaylist: String,destinationPlaylist: String?,title: String,handled: Set<String> = emptySet(),allowEmpty: Boolean = false,existing: ExistingTracks? = null,progress: (Int,Int)->Unit): PlaylistTransferPlan = coroutineScope {
    check(destination.writable) { "This provider cannot receive playlists" }
    check(source.id!=destination.id || sourcePlaylist!=destinationPlaylist) { "Choose a different destination playlist" }
    val sourceOwner=source.identity();val destinationOwner=destination.identity()
    check(sourceOwner.isNotBlank() && destinationOwner.isNotBlank()) { "Connect both accounts first" }
    destinationPlaylist?.let { id -> check(destination.playlists(true).any{it.playlistId==id}) { "Choose an editable destination playlist" } }
    val allSongs=source.tracks(sourcePlaylist)
    check(allSongs.isNotEmpty() || allowEmpty) { "The source playlist is empty" }
    val pending=allSongs.filterNot{it.videoId in handled}
    // Songs the listener already has are not searched for at all.
    val known=pending.mapNotNull{song->existing?.find(song)?.let{song to it}}
    val knownIds=known.mapTo(HashSet()){it.first.videoId}
    val songs=pending.filterNot{it.videoId in knownIds}
    guardTransferAccounts(source,destination,sourceOwner,destinationOwner)
    val permits=Semaphore(3);val done=AtomicInteger()
    val matches=songs.map { song -> async {
        permits.withPermit {
            val row=if(destination.ownsTrack(song.videoId)) SyncMatch(song,listOf(song),song.videoId,false)
                else matchOnDestination(destination,song)
            progress(done.incrementAndGet(),songs.size)
            row
        }
    }}.awaitAll()
    guardTransferAccounts(source,destination,sourceOwner,destinationOwner)
    // A match can also turn out to be a track they have under another name.
    val matchedKnown=matches.mapNotNull{row->row.selectedId?.let{existing?.findId(it)}?.let{row.source to it}}
    val skip=matchedKnown.mapTo(HashSet()){it.first.videoId}
    PlaylistTransferPlan(sourcePlaylist,sourceOwner,destinationOwner,destinationPlaylist,title,
        matches.filterNot{it.source.videoId in skip},known+matchedKnown)
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

/**
 * Searches [destination] for [song], one query after another until one finds
 * it: "title artist" first, then the title alone for catalogues that credit
 * someone else. When nothing passes the strict match, the rows that miss only
 * on an explicit flag, a runtime or a release one catalogue states and the
 * other doesn't are offered as close matches to review, not reported as
 * missing.
 */
internal suspend fun matchOnDestination(destination: PlaylistTransferBackend,song: com.music.streamx.data.model.Song): SyncMatch {
    val seen=LinkedHashMap<String,com.music.streamx.data.model.Song>()
    for(query in TrackMatcher.queries(TrackMatcher.targetOf(song))) {
        destination.search(query).filter{destination.ownsTrack(it.videoId)}.forEach{seen.putIfAbsent(it.videoId,it)}
        val row=reviewPlaylistMatch(song,seen.values.toList())
        if(row.candidates.isNotEmpty()) return row
    }
    val loose=song.copy(isExplicit=null,durationText=null,albumName=null)
    val close=TrackMatcher.ranked(seen.values.toList(),TrackMatcher.targetOf(loose)).take(5)
    return SyncMatch(song,close,null,close.isNotEmpty())
}
