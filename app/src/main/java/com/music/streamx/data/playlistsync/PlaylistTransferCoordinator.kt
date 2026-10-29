package com.music.streamx.data.playlistsync

import android.content.Context
import com.music.streamx.data.extensions.ApkCatalogue
import com.music.streamx.data.extensions.ApkExtensions
import com.music.streamx.data.model.UserPlaylist
import com.music.streamx.data.sources.SourceKind
import com.music.streamx.data.sources.SourceRegistry
import com.music.streamx.data.streamx.StreamXSession
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit

internal data class TransferProvider(val id: String,val name: String,val writable: Boolean,val userPlaylists: Boolean = true,val pluginPackage: String? = null)
internal data class PlaylistTransferUi(
    val providers: List<TransferProvider> = emptyList(),
    val sourceProvider: String? = null,val destinationProvider: String? = null,
    val sourcePlaylists: List<UserPlaylist> = emptyList(),val destinationPlaylists: List<UserPlaylist> = emptyList(),
    val sourcePlaylist: String? = null,val destinationPlaylist: String? = null,val title: String = "",
    val sourceUserOnly: Boolean = true,val hiddenSourcePlaylists: Set<String> = emptySet(),val hiddenDestinationPlaylists: Set<String> = emptySet(),
    val matches: List<SyncMatch> = emptyList(),val stage: SyncStage = SyncStage.CONFIGURE,
    val busy: Boolean = false,val progress: String = "",val error: String? = null,
    val reviewingLink: Boolean = false,val keepSynchronized: Boolean = false,val links: List<TransferLink> = emptyList(),
    val notice: String? = null,val result: PlaylistTransferResult? = null,
    val brute: Boolean = false,val records: List<TransferRecord> = emptyList(),
    val selectedSources: Set<String> = emptySet(),val batch: List<BatchTransfer> = emptyList(),
    val trackCounts: Map<String,Int> = emptyMap(),
    /** Several source playlists go into one new playlist named [title] rather than one each. */
    val combine: Boolean = false,
    /** Leave out songs already in the listener's destination playlists. */
    val skipExisting: Boolean = false,
    /** Destination playlists checked for [skipExisting]; empty means all of them. */
    val checkPlaylists: Set<String> = emptySet(),
)
internal enum class BatchStatus { WAITING, MATCHING, ADDING, DONE, FAILED }
/** One playlist of a multi-playlist transfer, as the progress screen lists it. */
internal data class BatchTransfer(
    val playlist: UserPlaylist,val status: BatchStatus = BatchStatus.WAITING,val done: Int = 0,val total: Int = 0,
    val added: Int = 0,val alreadyPresent: Int = 0,val notFound: List<com.music.streamx.data.model.Song> = emptyList(),
    val close: List<SyncMatch> = emptyList(),val error: String? = null,val warning: String = "",
    /** Songs left out because they are already in one of the checked playlists, with that playlist's name. */
    val known: List<Pair<com.music.streamx.data.model.Song,String>> = emptyList(),
)
internal class PlaylistTransferCoordinator(context: Context,private val scope: CoroutineScope,private val youtubeIdentity: ()->String?,private val onChanged: ()->Unit) {
    private val preferences=context.getSharedPreferences("streamx_playlist_transfer",Context.MODE_PRIVATE)
    private val _state=MutableStateFlow(PlaylistTransferUi(sourceUserOnly=preferences.getBoolean("user_playlists",true)))
    private val mutationLock=Mutex()
    private var managerOpen=false
    private var foreground=false
    private var polling: Job?=null
    private var automatic: Job?=null
    private var syncAgain=false
    private var links=decodeTransferLinks(preferences.getString("links","[]") ?: "[]")
    private var records=decodeTransferRecords(preferences.getString("records","[]") ?: "[]")
    init {
        scope.launch { PlaylistTransferEvents.flow.collect { provider -> syncLinked(provider) } }
        scope.launch { com.music.streamx.data.spotify.LocalPlaylistStore.playlists.collect { syncLinked("local") } }
    }
    private fun saveLinks() {
        preferences.edit().putString("links",encodeTransferLinks(links)).apply()
        _state.value=_state.value.copy(links=links)
    }
    fun keepSynchronized(value: Boolean) {if(!_state.value.busy)_state.value=_state.value.copy(keepSynchronized=value)}
    fun brute(value: Boolean) {
        val ui=_state.value
        if(ui.busy)return
        preferences.edit().putBoolean("brute",value).apply()
        _state.value=ui.copy(brute=value,matches=brutePlaylistMatches(ui.matches,value))
    }
    fun markRecordReviewed(id: String) {
        records=records.map{if(it.id==id)it.copy(rows=it.rows.map{row->row.copy(uncertain=false)})else it}
        saveRecords()
    }
    private fun saveRecords() {
        preferences.edit().putString("records",encodeTransferRecords(records)).apply()
        _state.value=_state.value.copy(records=records)
    }
    fun enableLink(id: String,enabled: Boolean) {links=links.map{if(it.id==id)it.copy(enabled=enabled)else it};saveLinks();syncLinked()}
    fun removeLink(id: String) {links=links.filterNot{it.id==id};saveLinks()}
    fun foreground(active: Boolean) {
        foreground=active
        polling?.cancel()
        if(active) polling=scope.launch {while(isActive) {syncLinked();delay(60_000)}}
        else automatic?.cancel()
    }
    private fun installedBackends(): Map<String,PlaylistTransferBackend> {
        val available=mutableListOf<PlaylistTransferBackend>(LocalTransferBackend(),YouTubeTransferBackend(youtubeIdentity))
        available+=ApkExtensions.installed.value.filter{it.sourceAuthority!=null}.map(::transferPlugin)
        val kind=SourceRegistry.catalogue.value
        if(kind==SourceKind.CLOUDSTREAM || kind==SourceKind.STREAMX && StreamXSession.state.value.personalAccount)available+=StreamXTransferBackend(kind)
        return available.associateBy{it.id}
    }
    fun syncLinked(provider: String? = null) {
        if (!foreground || managerOpen || links.none { it.enabled && (provider == null || it.source == provider) }) return
        if (automatic?.isActive == true) {
            syncAgain = true
            return
        }
        automatic = scope.launch {
            try {
                mutationLock.withLock {
                    ApkExtensions.refresh()
                    val available = installedBackends()
                    for (snapshot in links.filter { it.enabled && (provider == null || it.source == provider) }) {
                        if (managerOpen || !foreground) break
                        try {
                            val source = available[snapshot.source]
                                ?: error("Source plugin unavailable")
                            val destination = available[snapshot.destination]
                                ?: error("Destination plugin unavailable")
                            guardTransferAccounts(source, destination, snapshot.sourceAccount, snapshot.destinationAccount)
                            val prepared = preparePlaylistTransfer(
                                source, destination, snapshot.sourcePlaylist, snapshot.destinationPlaylist,
                                snapshot.title, handled = snapshot.handled, allowEmpty = true,
                            ) { _, _ -> }
                            guardTransferAccounts(source, destination, snapshot.sourceAccount, snapshot.destinationAccount)
                            val pending = pendingSyncPlan(prepared, snapshot.handled).copy(
                                sourceIdentity = snapshot.sourceAccount,
                                destinationIdentity = snapshot.destinationAccount,
                            )
                            if (links.none { it.id == snapshot.id && it.enabled }) continue
                            if (pending.rows.isNotEmpty()) {
                                applyPlaylistTransfer(source, destination, pending, {}, { _, _ -> })
                                onChanged()
                            }
                            val handled = snapshot.handled + pending.rows.map { it.source.videoId }
                            val review = prepared.rows.count { it.source.videoId !in handled }
                            links = links.map {
                                if (it.id == snapshot.id) it.copy(
                                    handled = handled,
                                    status = if (review > 0) "$review tracks need review" else "Up to date",
                                ) else it
                            }
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (failure: Exception) {
                            links = links.map {
                                if (it.id == snapshot.id) it.copy(status = failure.message ?: "Sync failed") else it
                            }
                        }
                        saveLinks()
                    }
                }
            } finally {
                automatic = null
                if (syncAgain) {
                    syncAgain = false
                    scope.launch { syncLinked() }
                }
            }
        }
    }
    private var sourceVisibilityKey: String?=null
    private var destinationVisibilityKey: String?=null
    val state=_state.asStateFlow()
    private var backends=emptyMap<String,PlaylistTransferBackend>()
    private var work: Job?=null
    private var plan: PlaylistTransferPlan?=null
    private var reviewingLink: TransferLink?=null
    private fun work(block: suspend ()->Unit) {
        work?.cancel()
        _state.value=_state.value.copy(busy=true,error=null)
        work=scope.launch {
            try { block();publishProviders();_state.value=_state.value.copy(busy=false) }
            catch(cancelled: CancellationException) { throw cancelled }
            catch(failure: Exception) { publishProviders();_state.value=_state.value.copy(busy=false,error=failure.message ?: "Could not transfer playlist") }
        }
    }
    fun close() { work?.cancel();managerOpen=false;_state.value=_state.value.copy(busy=false);syncLinked() }
    fun open(requested: SyncSource? = null) {
        managerOpen=true
        automatic?.cancel()
        plan=null
        reviewingLink=null
        _state.value=PlaylistTransferUi(links=links,records=records,brute=preferences.getBoolean("brute",false),skipExisting=preferences.getBoolean("skip_existing",false),sourceUserOnly=preferences.getBoolean("user_playlists",true),busy=true,progress="Finding playlist providers…")
        work {
            ApkExtensions.refresh()
            backends=installedBackends()
            val kind=SourceRegistry.catalogue.value
            publishProviders()
            requested?.let { source ->
                val plugin=ApkCatalogue.parseBrowseId(source.browseId.removePrefix("VL"))
                val provider=when {
                    plugin!=null -> "plugin:${plugin.configId}"
                    source.browseId.startsWith("local:playlist:")->"local"
                    source.browseId.removePrefix("VL").startsWith(com.music.streamx.data.streamx.StreamXClient.BROWSE_PREFIX)->"streamx:${kind.name}"
                    else->"youtube"
                }
                if(provider in backends) {
                    loadSource(provider)
                    val playlist=_state.value.sourcePlaylists.firstOrNull{it.browseId==source.browseId || it.playlistId==source.browseId}
                        ?: UserPlaylist(if(provider=="local") source.browseId else source.browseId.removePrefix("VL"),source.title,"Selected playlist",null).also {
                            _state.value=_state.value.copy(sourcePlaylists=_state.value.sourcePlaylists+it)
                        }
                    selectPlaylist(playlist)
                }
            }
        }
    }
    private fun publishProviders() {
        _state.value=_state.value.copy(providers=backends.values.map{TransferProvider(it.id,it.name,it.writable,it.supportsUserPlaylists,it.pluginPackage)})
    }
    private suspend fun loadSource(id: String) {
        val backend=backends.getValue(id)
        _state.value=_state.value.copy(sourceProvider=id,sourcePlaylist=null,selectedSources=emptySet(),trackCounts=emptyMap(),sourcePlaylists=emptyList(),matches=emptyList(),stage=SyncStage.CONFIGURE,result=null,progress="Loading ${backend.name} playlists…")
        val owner=backend.identity()
        publishProviders()
        val userOnly=preferences.getBoolean("user_playlists",true) && backend.supportsUserPlaylists
        val rows=backend.playlists(userOnly)
        check(backend.identity()==owner) { "Source account changed. Refresh playlists." }
        sourceVisibilityKey=transferVisibilityKey(backend.id,owner)
        val hidden=preferences.getStringSet(sourceVisibilityKey,emptySet()).orEmpty().toSet()
        _state.value=_state.value.copy(sourcePlaylists=rows,sourceUserOnly=userOnly,hiddenSourcePlaylists=hidden)
    }
    fun reviewLink(id: String) {
        val link=links.firstOrNull{it.id==id} ?: return
        if(_state.value.busy)return
        work {
            val source=backends[link.source] ?: error("Source plugin unavailable")
            val destination=backends[link.destination] ?: error("Destination plugin unavailable")
            guardTransferAccounts(source,destination,link.sourceAccount,link.destinationAccount)
            reviewingLink=link
            _state.value=_state.value.copy(sourceProvider=link.source,destinationProvider=link.destination,sourcePlaylist=link.sourcePlaylist,destinationPlaylist=link.destinationPlaylist,title=link.title,reviewingLink=true,keepSynchronized=true,progress="Reviewing new tracks…")
            plan=preparePlaylistTransfer(source,destination,link.sourcePlaylist,link.destinationPlaylist,link.title,handled=link.handled){_,_->}
            guardTransferAccounts(source,destination,link.sourceAccount,link.destinationAccount)
            _state.value=_state.value.copy(matches=brutePlaylistMatches(plan!!.rows,_state.value.brute),stage=SyncStage.REVIEW)
        }
    }
    fun source(id: String) { if(!_state.value.busy) {plan=null;reviewingLink=null;work{loadSource(id)}} }
    fun sourceFilter(userOnly: Boolean) {
        val ui=_state.value
        if(ui.busy || userOnly && backends[ui.sourceProvider]?.supportsUserPlaylists!=true) return
        preferences.edit().putBoolean("user_playlists",userOnly).apply()
        ui.sourceProvider?.let(::source)
    }
    fun hidePlaylist(playlist: UserPlaylist,fromSource: Boolean,hide: Boolean) {
        val ui=_state.value
        if(ui.busy) return
        val key=(if(fromSource) sourceVisibilityKey else destinationVisibilityKey) ?: return
        val hidden=preferences.getStringSet(key,emptySet()).orEmpty().toMutableSet().apply {if(hide)add(playlist.playlistId) else remove(playlist.playlistId)}
        preferences.edit().putStringSet(key,hidden.toSet()).apply()
        plan=null
        _state.value=ui.copy(
            hiddenSourcePlaylists=if(key==sourceVisibilityKey)hidden else ui.hiddenSourcePlaylists,
            hiddenDestinationPlaylists=if(key==destinationVisibilityKey)hidden else ui.hiddenDestinationPlaylists,
            sourcePlaylist=if(hide && fromSource && ui.sourcePlaylist==playlist.playlistId)null else ui.sourcePlaylist,
            destinationPlaylist=if(hide && !fromSource && ui.destinationPlaylist==playlist.playlistId)null else ui.destinationPlaylist,
            matches=emptyList(),stage=SyncStage.CONFIGURE,error=null)
    }
    fun selectPlaylist(playlist: UserPlaylist) {
        reviewingLink=null
        if(playlist !in _state.value.sourcePlaylists) return
        plan=null
        _state.value=_state.value.copy(sourcePlaylist=playlist.playlistId,selectedSources=setOf(playlist.playlistId),title=if(_state.value.destinationPlaylist==null)playlist.title else _state.value.title,matches=emptyList(),stage=SyncStage.CONFIGURE,error=null,result=null)
    }
    fun destinationProvider(id: String) {
        if(_state.value.busy) return
        plan=null
        work {
            val backend=backends.getValue(id)
            check(backend.writable) { "This provider is read-only" }
            _state.value=_state.value.copy(destinationProvider=id,destinationPlaylist=null,
                title=if(_state.value.destinationPlaylist!=null) newTitle(_state.value.copy(destinationPlaylist=null),_state.value.selectedSources) else _state.value.title,destinationPlaylists=emptyList(),matches=emptyList(),stage=SyncStage.CONFIGURE,result=null,progress="Loading ${backend.name} playlists…")
            val owner=backend.identity()
            publishProviders()
            check(backend.writable) {"${backend.name} cannot receive playlists"}
            val rows=backend.playlists(true)
            check(backend.identity()==owner) { "Destination account changed. Refresh playlists." }
            destinationVisibilityKey=transferVisibilityKey(backend.id,owner)
            val hidden=preferences.getStringSet(destinationVisibilityKey,emptySet()).orEmpty().toSet()
            val checked=preferences.getStringSet(checkKey(id),emptySet()).orEmpty().filterTo(HashSet()){c->rows.any{it.playlistId==c}}
            _state.value=_state.value.copy(destinationPlaylists=rows,hiddenDestinationPlaylists=hidden,checkPlaylists=checked)
        }
    }
    private fun checkKey(provider: String)="check_playlists:$provider"
    fun skipExisting(value: Boolean) {
        if(_state.value.busy) return
        preferences.edit().putBoolean("skip_existing",value).apply()
        _state.value=_state.value.copy(skipExisting=value)
    }
    /** Which destination playlists to check for songs already there; empty is all. */
    fun checkPlaylists(ids: Set<String>) {
        val ui=_state.value
        if(ui.busy) return
        val all=ui.destinationPlaylists.map{it.playlistId}.toSet()
        val chosen=if(ids.containsAll(all)) emptySet() else ids
        ui.destinationProvider?.let{preferences.edit().putStringSet(checkKey(it),chosen).apply()}
        _state.value=ui.copy(checkPlaylists=chosen)
    }
    /** Reads every checked destination playlist, a few at a time. */
    private suspend fun existingTracks(destination: PlaylistTransferBackend,ui: PlaylistTransferUi): ExistingTracks = coroutineScope {
        val playlists=ui.destinationPlaylists.filter{ui.checkPlaylists.isEmpty() || it.playlistId in ui.checkPlaylists}
        val permits=kotlinx.coroutines.sync.Semaphore(3)
        val read=java.util.concurrent.atomic.AtomicInteger()
        _state.value=_state.value.copy(progress="Checking ${playlists.size} ${destination.name} playlists…")
        ExistingTracks.of(playlists.map{playlist->async{
            permits.withPermit{
                val songs=destination.tracks(playlist.playlistId)
                val n=read.incrementAndGet()
                _state.value=_state.value.copy(progress="Checking your playlists · $n of ${playlists.size}")
                playlist.title to songs
            }
        }}.awaitAll())
    }
    fun destination(playlist: UserPlaylist?) {
        if(_state.value.busy || playlist!=null && playlist !in _state.value.destinationPlaylists) return
        plan=null
        _state.value=_state.value.copy(destinationPlaylist=playlist?.playlistId,combine=_state.value.combine && playlist==null,title=playlist?.title ?: _state.value.sourcePlaylists.firstOrNull{it.playlistId==_state.value.sourcePlaylist}?.title.orEmpty(),matches=emptyList(),stage=SyncStage.CONFIGURE,error=null,result=null)
    }
    fun title(value: String) { if(!_state.value.busy) _state.value=_state.value.copy(title=value) }
    fun configure() { if(!_state.value.busy) {reviewingLink=null;_state.value=_state.value.copy(stage=SyncStage.CONFIGURE,error=null,reviewingLink=false)} }
    fun choose(index: Int,id: String?) {
        if(_state.value.busy) return
        _state.value=_state.value.copy(matches=_state.value.matches.mapIndexed { i,row ->
            if(i!=index || id!=null && row.candidates.none{it.videoId==id}) row else row.copy(selectedId=id,excluded=id==null,uncertain=false)
        })
    }
    fun prepare() {
        val ui=_state.value
        val source=backends[ui.sourceProvider] ?: return
        val destination=backends[ui.destinationProvider] ?: return
        val playlist=ui.sourcePlaylist ?: return
        if(ui.busy || ui.title.isBlank()) return
        work {
            _state.value=_state.value.copy(progress="Matching tracks in ${destination.name}…")
            plan=preparePlaylistTransfer(source,destination,playlist,ui.destinationPlaylist,ui.title) {done,total->_state.value=_state.value.copy(progress="$done / $total tracks matched")}
            _state.value=_state.value.copy(matches=brutePlaylistMatches(plan!!.rows,_state.value.brute),stage=SyncStage.REVIEW)
        }
    }
    fun apply() {
        val ui=_state.value
        val prepared=plan ?: return
        val source=backends[ui.sourceProvider] ?: return
        val destination=backends[ui.destinationProvider] ?: return
        if(ui.busy || ui.stage!=SyncStage.REVIEW) return
        work {
            _state.value=_state.value.copy(progress="Transferring to ${destination.name}…")
            val result=mutationLock.withLock {
                if(reviewingLink!=null && ui.matches.none{it.selectedId!=null && !it.excluded}) {
                    guardTransferAccounts(source,destination,prepared.sourceIdentity,prepared.destinationIdentity)
                    PlaylistTransferResult(requireNotNull(prepared.destinationPlaylist),0,0,"")
                } else applyPlaylistTransfer(source,destination,prepared.copy(rows=ui.matches),onCreated={ id ->
                // Retain the created destination before any add request, including failed/cancelled attempts.
                plan=prepared.copy(destinationPlaylist=id)
                _state.value=_state.value.copy(destinationPlaylist=id)
            },progress={done,total->_state.value=_state.value.copy(progress="$done / $total tracks added")}) }
            if(ui.keepSynchronized) {
                val link=TransferLink(reviewingLink?.id ?: java.util.UUID.randomUUID().toString(),source.id,destination.id,prepared.sourceIdentity,prepared.destinationIdentity,prepared.sourcePlaylist,result.playlistId,prepared.title,reviewingLink?.handled.orEmpty()+ui.matches.filter{it.excluded || it.selectedId!=null}.map{it.source.videoId}.toSet(),enabled=reviewingLink?.enabled ?: true)
                links=links.filterNot{it.source==link.source && it.destination==link.destination && it.sourcePlaylist==link.sourcePlaylist && it.destinationPlaylist==link.destinationPlaylist}+link
                saveLinks()
            }
            // Keep the chosen release and its alternatives even for a one-off transfer.
            if(ui.brute || ui.matches.any{it.uncertain && it.selectedId!=null}) {
                val record=TransferRecord(java.util.UUID.randomUUID().toString(),prepared.title,
                    "${source.name} → ${destination.name}",System.currentTimeMillis(),ui.matches)
                records=(listOf(record)+records).take(20)
                saveRecords()
            }
            _state.value=_state.value.copy(stage=SyncStage.COMPLETE,result=result)
            onChanged()
        }
    }
    fun toggleSource(id: String) {
        val ui=_state.value
        if(ui.busy || ui.sourcePlaylists.none{it.playlistId==id}) return
        val selected=if(id in ui.selectedSources) ui.selectedSources-id else ui.selectedSources+id
        // An existing destination only makes sense for a single playlist.
        _state.value=ui.copy(selectedSources=selected,sourcePlaylist=selected.singleOrNull(),error=null,title=newTitle(ui,selected))
    }
    fun selectAllSources(select: Boolean) {
        val ui=_state.value
        if(ui.busy) return
        val selected=if(select) ui.sourcePlaylists.map{it.playlistId}.toSet() else emptySet()
        _state.value=ui.copy(selectedSources=selected,sourcePlaylist=selected.singleOrNull(),error=null,title=newTitle(ui,selected))
    }
    /** The suggested name of a new playlist: the source's own name for one playlist, or the user's combined name. */
    private fun newTitle(ui: PlaylistTransferUi,selected: Set<String>)=when {
        ui.destinationPlaylist!=null || ui.combine -> ui.title
        else -> ui.sourcePlaylists.firstOrNull{it.playlistId==selected.singleOrNull()}?.title.orEmpty()
    }
    /** Chooses between one new playlist per source ([value] false) and a single combined new playlist. */
    fun combine(value: Boolean) {
        val ui=_state.value
        if(ui.busy) return
        val reset=ui.copy(combine=value,destinationPlaylist=null)
        _state.value=reset.copy(title=if(value) "" else newTitle(reset,ui.selectedSources))
    }
    private val countPermits=kotlinx.coroutines.sync.Semaphore(3)
    private val countsRequested=mutableSetOf<String>()
    /** Loads a source playlist's size for the picker when the provider's subtitle doesn't give one. */
    fun requestTrackCount(id: String) {
        val provider=_state.value.sourceProvider ?: return
        val backend=backends[provider] ?: return
        if(!countsRequested.add("$provider|$id")) return
        scope.launch {
            val count=countPermits.withPermit { runCatching{backend.tracks(id).size}.getOrNull() } ?: return@launch
            if(_state.value.sourceProvider==provider) _state.value=_state.value.copy(trackCounts=_state.value.trackCounts+(id to count))
        }
    }
    private val batchPlans=mutableMapOf<String,PlaylistTransferPlan>()
    private fun updateBatch(id: String,change: (BatchTransfer)->BatchTransfer) {
        _state.value=_state.value.copy(batch=_state.value.batch.map{if(it.playlist.playlistId==id)change(it) else it})
    }
    /** Transfers each selected playlist in turn, adding confident matches only; the rest is summarised per playlist. */
    fun transferSelected() {
        val ui=_state.value
        val source=backends[ui.sourceProvider] ?: return
        val destination=backends[ui.destinationProvider] ?: return
        val playlists=ui.sourcePlaylists.filter{it.playlistId in ui.selectedSources}
        if(ui.busy || playlists.isEmpty() || !destination.writable) return
        // Into one playlist: an existing one, or a new one created by the first playlist with a match.
        var shared=ui.destinationPlaylist
        val combined=shared!=null || ui.combine
        val sharedTitle=when {
            shared!=null -> ui.destinationPlaylists.firstOrNull{it.playlistId==shared}?.title ?: ui.title
            ui.combine || playlists.size==1 -> ui.title.trim()
            else -> null
        }
        if(sharedTitle!=null && sharedTitle.isBlank()) return
        val seen=HashSet<String>()
        batchPlans.clear()
        _state.value=ui.copy(batch=playlists.map{BatchTransfer(it)},stage=SyncStage.COMPLETE,progress="Transferring to ${destination.name}…")
        work {
            val existing=if(ui.skipExisting) existingTracks(destination,ui) else null
            _state.value=_state.value.copy(progress="Transferring to ${destination.name}…")
            for(playlist in playlists) {
                val id=playlist.playlistId
                try {
                    updateBatch(id){it.copy(status=BatchStatus.MATCHING)}
                    val title=sharedTitle ?: playlist.title
                    // Songs already taken from an earlier source playlist are skipped, so the combined order is the sources' order.
                    val prepared=preparePlaylistTransfer(source,destination,id,ui.destinationPlaylist,title,handled=if(combined)seen.toSet() else emptySet(),allowEmpty=combined,existing=existing) {done,total->updateBatch(id){it.copy(done=done,total=total)}}
                        .copy(destinationPlaylist=shared)
                    if(combined) seen+=prepared.rows.map{it.source.videoId}+prepared.known.map{it.first.videoId}
                    val rows=prepared.rows
                    val confident=rows.filter{it.selectedId!=null && !it.excluded}
                    updateBatch(id){it.copy(status=BatchStatus.ADDING,total=rows.size+prepared.known.size,done=0,known=prepared.known,
                        notFound=rows.filter{row->!row.excluded && row.selectedId==null && row.candidates.isEmpty()}.map{row->row.source},
                        close=rows.filter{row->!row.excluded && row.selectedId==null && row.candidates.isNotEmpty()})}
                    var plan=prepared
                    val result=if(confident.isEmpty()) null else mutationLock.withLock {
                        applyPlaylistTransfer(source,destination,prepared,onCreated={created->plan=prepared.copy(destinationPlaylist=created)},
                            progress={done,_->updateBatch(id){it.copy(done=done)}})
                    }
                    plan=plan.copy(destinationPlaylist=result?.playlistId ?: plan.destinationPlaylist)
                    batchPlans[id]=plan
                    if(combined) shared=plan.destinationPlaylist
                    if(ui.keepSynchronized && plan.destinationPlaylist!=null) {
                        val link=TransferLink(java.util.UUID.randomUUID().toString(),source.id,destination.id,plan.sourceIdentity,plan.destinationIdentity,
                            plan.sourcePlaylist,plan.destinationPlaylist!!,plan.title,confident.map{it.source.videoId}.toSet())
                        links=links.filterNot{it.source==link.source && it.destination==link.destination && it.sourcePlaylist==link.sourcePlaylist && it.destinationPlaylist==link.destinationPlaylist}+link
                        saveLinks()
                    }
                    updateBatch(id){it.copy(status=BatchStatus.DONE,done=it.total,added=result?.added ?: 0,alreadyPresent=result?.alreadyPresent ?: 0,warning=result?.warning.orEmpty())}
                    if(result!=null) onChanged()
                } catch(cancelled: CancellationException) { throw cancelled }
                catch(failure: Exception) { updateBatch(id){it.copy(status=BatchStatus.FAILED,error=failure.message ?: "Could not transfer playlist")} }
            }
        }
    }
    /**
     * Adds the close matches of one finished playlist: [picks] maps a source
     * song to the candidate chosen for it; songs left out are skipped. The
     * choices are kept as a transfer record for later review.
     */
    fun addCloseMatches(id: String,picks: Map<String,String>) {
        val ui=_state.value
        val item=ui.batch.firstOrNull{it.playlist.playlistId==id} ?: return
        val plan=batchPlans[id] ?: return
        val source=backends[ui.sourceProvider] ?: return
        val destination=backends[ui.destinationProvider] ?: return
        if(ui.busy || item.close.isEmpty() || picks.isEmpty()) return
        val rows=item.close.map{row->picks[row.source.videoId]?.takeIf{p->row.candidates.any{it.videoId==p}}
            ?.let{row.copy(selectedId=it,uncertain=true)} ?: row.copy(excluded=true)}
        work {
            _state.value=_state.value.copy(progress="Adding close matches…")
            val result=mutationLock.withLock {
                applyPlaylistTransfer(source,destination,plan.copy(rows=rows),onCreated={created->batchPlans[id]=plan.copy(destinationPlaylist=created)},progress={_,_->})
            }
            batchPlans[id]=plan.copy(destinationPlaylist=result.playlistId)
            records=(listOf(TransferRecord(java.util.UUID.randomUUID().toString(),plan.title,"${source.name} → ${destination.name}",System.currentTimeMillis(),rows))+records).take(20)
            saveRecords()
            updateBatch(id){it.copy(added=it.added+result.added,alreadyPresent=it.alreadyPresent+result.alreadyPresent,close=emptyList())}
            onChanged()
        }
    }
}
