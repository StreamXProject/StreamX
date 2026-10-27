package com.music.streamx.data.playlistsync

import android.content.Context
import com.music.streamx.data.extensions.ApkCatalogue
import com.music.streamx.data.extensions.ApkExtensions
import com.music.streamx.data.sources.SourceConfig
import com.music.streamx.data.YtMusicRepository
import com.music.streamx.data.model.*
import com.music.streamx.data.sources.SourceKind
import com.music.streamx.data.sources.SourceRegistry
import com.music.streamx.data.sources.TrackMatcher
import com.music.streamx.data.spotify.LocalPlaylistStore
import com.music.streamx.data.streamx.StreamXClient
import com.music.streamx.data.streamx.StreamXSession
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

@Serializable
data class SyncSource(val browseId: String, val title: String, val owner: String = "", val provider: String = "")
@Serializable
data class PlaylistSyncLink(
    val id: String = UUID.randomUUID().toString(),
    val source: SyncSource,
    val youtubeIdentity: String,
    val destinationId: String,
    val destinationTitle: String,
    val keepSynchronized: Boolean = false,
    val matches: Map<String, String> = emptyMap(),
    val skipped: Set<String> = emptySet(),
    val lastSynced: Long = 0,
    val needsReview: Int = 0,
    val error: String? = null,
)
data class SyncMatch(val source: Song, val candidates: List<Song>, val selectedId: String?, val uncertain: Boolean, val excluded: Boolean = false)
enum class SyncStage { CONFIGURE, REVIEW, COMPLETE }
data class PlaylistSyncUi(
    val sources: List<SyncSource> = emptyList(),
    val destinations: List<UserPlaylist> = emptyList(),
    val links: List<PlaylistSyncLink> = emptyList(),
    val source: SyncSource? = null,
    val destinationId: String? = null,
    val destinationTitle: String = "",
    val keepSynchronized: Boolean = false,
    val matches: List<SyncMatch> = emptyList(),
    val stage: SyncStage = SyncStage.CONFIGURE,
    val busy: Boolean = false,
    val progress: String = "",
    val error: String? = null,
    val connected: Boolean = false,
    val accountName: String = "",
    val spotifyInstalled: Boolean = false,
    val spotifyAccount: String = "",
    val spotifyError: String? = null,
)

/** Metadata-only, add-only sync. No source file or playlist is modified. */
class PlaylistSyncCoordinator(context: Context, private val scope: CoroutineScope, private val identity: () -> String?, private val accountName: () -> String, private val onChanged: () -> Unit) {
    private val prefs = context.getSharedPreferences("streamx_playlist_sync", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private var links = runCatching {
        json.decodeFromString(ListSerializer(PlaylistSyncLink.serializer()), prefs.getString("links", null) ?: "[]")
    }.getOrDefault(emptyList())
    private val _state = MutableStateFlow(PlaylistSyncUi())
    val state = _state.asStateFlow()
    val hasLinks: Boolean get() = links.isNotEmpty()
    private var work: Job? = null
    private var automatic: Job? = null
    private var preparedIdentity: String? = null
    private var preparedSourceScope: String? = null
    private val mutations = Mutex()
    private var lastAutomaticCheck = 0L
    private var managerOpen = false
    private var transferOpen = false
    fun blockAutomaticSync(blocked: Boolean) { transferOpen=blocked;if(blocked) automatic?.cancel() }
    private var automaticIdentity: String? = null
    private fun sameSource(a: SyncSource, b: SyncSource) = a.browseId == b.browseId && a.owner == b.owner

    private fun spotifyConfig(configId: String? = null): SourceConfig {
        val extension = ApkExtensions.installed.value.firstOrNull { it.packageName == "com.music.streamx.extensions.spotify" }
            ?: error("Install the Spotify extension in Skins & plugins")
        val existing = SourceRegistry.configs.value.firstOrNull { it.kind == SourceKind.APK_PLUGIN && it.extensionPackage == extension.packageName }
        val config = existing ?: SourceConfig(id = extension.skinId, kind = SourceKind.APK_PLUGIN, enabled = false,
            shownInSources = false, label = extension.name, extensionPackage = extension.packageName,
            extensionAuthority = requireNotNull(extension.sourceAuthority) { "Spotify has no catalogue provider" }, extensionCertificate = extension.certificate)
        check(config.extensionCertificate == extension.certificate && config.extensionAuthority == extension.sourceAuthority) {
            "Review the updated Spotify extension in Skins & plugins"
        }
        check(configId == null || config.id == configId) { "The original Spotify connection was removed" }
        return config
    }
    private suspend fun spotifyOwner(config: SourceConfig): String =
        "${config.extensionPackage}|${config.extensionCertificate}|${ApkCatalogue(config, metadataOnly = true).accountIdentity()}"

    private fun sourceOwner(id: String): String = if (id.removePrefix("VL").startsWith(StreamXClient.BROWSE_PREFIX))
        if (SourceRegistry.catalogue.value == SourceKind.CLOUDSTREAM) com.music.streamx.data.cloudstream.CloudStream.read(com.music.streamx.data.cloudstream.CloudStream.PROFILE).profileId else StreamXSession.state.value.userId?.toString() ?: "guest" else ""
    private fun guard(expected: String) { check(identity() == expected) { "YouTube account changed. Review this sync again." } }
    private suspend fun sourceScope(source: SyncSource): String? {
        val extension = ApkCatalogue.parseBrowseId(source.browseId.removePrefix("VL"))
        if (extension != null) {
            val owner = spotifyOwner(spotifyConfig(extension.configId))
            check(owner == source.owner) { "Sign in to the original Spotify account" }
            return owner
        }
        return if (source.browseId.removePrefix("VL").startsWith(StreamXClient.BROWSE_PREFIX))
            if (SourceRegistry.catalogue.value == SourceKind.CLOUDSTREAM) sourceOwner(source.browseId) else StreamXSession.state.value.scope else null
    }
    private fun save(link: PlaylistSyncLink) {
        links = links.filterNot { it.id == link.id || (it.youtubeIdentity == link.youtubeIdentity && sameSource(it.source, link.source)) } + link
        persist()
    }
    private fun persist() {
        prefs.edit().putString("links", json.encodeToString(ListSerializer(PlaylistSyncLink.serializer()), links)).apply()
        _state.value = _state.value.copy(links = links.filter { it.youtubeIdentity == identity() })
    }
    fun cancelWork() { work?.cancel(); automatic?.cancel(); _state.value = _state.value.copy(busy = false) }
    fun close() { managerOpen = false; work?.cancel(); _state.value = _state.value.copy(busy = false) }

    fun open(requested: SyncSource? = _state.value.source) {
        work?.cancel()
        automatic?.cancel()
        managerOpen = true
        val selected = requested?.copy(owner = requested.owner.ifBlank { sourceOwner(requested.browseId) })
        _state.value = PlaylistSyncUi(source = selected, destinationTitle = selected?.title.orEmpty(), busy = true, connected = identity() != null, accountName = accountName(),
            links = links.filter { it.youtubeIdentity == identity() },
            spotifyInstalled = ApkExtensions.installed.value.any { it.packageName == "com.music.streamx.extensions.spotify" })
        work = scope.launch {
            try {
                val yt = identity()
                val sources = LocalPlaylistStore.playlists.value.map { SyncSource(it.browseId, it.title) }.toMutableList()
                if (SourceRegistry.catalogue.value == SourceKind.CLOUDSTREAM || SourceRegistry.catalogue.value == SourceKind.STREAMX && StreamXSession.state.value.personalAccount) {
                    sources += StreamXClient.current().playlists().map { SyncSource(it.browseId, it.title, sourceOwner(it.browseId)) }
                }
                if (_state.value.spotifyInstalled) {
                    try {
                        val config = spotifyConfig()
                        val catalogue = ApkCatalogue(config, metadataOnly = true)
                        val owner = spotifyOwner(config)
                        val spotify = catalogue.libraryPlaylists()
                        check(owner == spotifyOwner(config)) { "Spotify account changed. Refresh playlists." }
                        sources += spotify.map { SyncSource(it.browseId, it.title, owner, "Spotify") }
                        _state.value = _state.value.copy(spotifyAccount = catalogue.account().name)
                    } catch (e: CancellationException) { throw e }
                    catch (e: Exception) { _state.value = _state.value.copy(spotifyError = e.message ?: "Could not load Spotify playlists") }
                }
                val destinations = if (yt != null) {
                    try { YtMusicRepository.userPlaylists().getOrThrow().also { guard(yt) } }
                    catch (e: CancellationException) { throw e }
                    catch (e: Exception) {
                        _state.value = _state.value.copy(error = e.message ?: "Could not load YouTube Music playlists")
                        emptyList()
                    }
                } else emptyList()
                if (SourceRegistry.catalogue.value == SourceKind.YOUTUBE) sources += destinations.map { SyncSource(it.browseId, it.title) }
                if (selected != null && sources.none { it.browseId == selected.browseId }) sources += selected
                _state.value = _state.value.copy(sources = sources.distinctBy { it.browseId }, destinations = destinations, busy = false)
                selected?.let { requestedSource ->
                    selectSource(sources.firstOrNull { it.browseId == requestedSource.browseId && (requestedSource.owner.isBlank() || it.owner == requestedSource.owner) } ?: requestedSource)
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { _state.value = _state.value.copy(busy = false, error = e.message ?: "Could not load playlists") }
        }
    }
    fun selectSource(source: SyncSource) {
        val link = links.firstOrNull { sameSource(it.source, source) && it.youtubeIdentity == identity() }
        _state.value = _state.value.copy(source = source, destinationTitle = link?.destinationTitle ?: source.title,
            destinationId = link?.destinationId, keepSynchronized = link?.keepSynchronized ?: false,
            stage = SyncStage.CONFIGURE, matches = emptyList(), error = null)
    }
    fun destination(id: String?, title: String) { _state.value = _state.value.copy(destinationId = id, destinationTitle = title) }
    fun title(value: String) { _state.value = _state.value.copy(destinationTitle = value) }
    fun keep(value: Boolean) { _state.value = _state.value.copy(keepSynchronized = value) }
    fun choose(index: Int, id: String?) {
        _state.value = _state.value.copy(matches = _state.value.matches.mapIndexed { i, row -> if (i == index) row.copy(selectedId = id, excluded = id == null, uncertain = if (id == null) row.uncertain else false) else row })
    }
    fun configure() { _state.value = _state.value.copy(stage = SyncStage.CONFIGURE, error = null) }
    fun pause(link: PlaylistSyncLink, enabled: Boolean) { automatic?.cancel(); save(link.copy(keepSynchronized = enabled)) }
    fun forget(link: PlaylistSyncLink) { automatic?.cancel(); links = links.filterNot { it.id == link.id }; persist() }

    private suspend fun songs(source: SyncSource): List<Song> {
        val id = source.browseId
        return when {
            id.startsWith("local:playlist:") -> LocalPlaylistStore.getPlaylist(id.removePrefix("local:playlist:"))?.songs ?: error("Source playlist was removed")
            id.removePrefix("VL").startsWith(StreamXClient.BROWSE_PREFIX) -> {
                check(source.owner == sourceOwner(id)) { "Sign in to the original StreamX account" }
                val detail = StreamXClient.current().detail(id, source.title, "", null)
                (detail.songs as? UiState.Success)?.data ?: error("Could not load the source playlist")
            }
            ApkCatalogue.parseBrowseId(id.removePrefix("VL")) != null -> {
                sourceScope(source)
                val target = ApkCatalogue.parseBrowseId(id.removePrefix("VL"))!!
                ApkCatalogue(spotifyConfig(target.configId), metadataOnly = true).playlistTracks(id)
            }
            else -> YtMusicRepository.playlistEntries(id).getOrThrow()
        }
    }
    private suspend fun match(source: SyncSource, expected: String, reuseKnown: Boolean = false, progress: (Int, Int) -> Unit): List<SyncMatch> = coroutineScope {
        val snapshot = sourceScope(source)
        val rows = songs(source)
        check(snapshot == sourceScope(source)) { "Source account changed" }
        val previous = links.firstOrNull { sameSource(it.source, source) && it.youtubeIdentity == expected }
        val known = previous?.matches.orEmpty()
        val skipped = previous?.skipped.orEmpty()
        val permits = Semaphore(3)
        val completed = AtomicInteger()
        rows.map { song -> async {
            permits.withPermit {
                guard(expected)
                check(snapshot == sourceScope(source)) { "Source account changed" }
                val selected = known[song.videoId]
                val row = if (reuseKnown && song.videoId in skipped) {
                    SyncMatch(song, emptyList(), null, false, excluded = true)
                } else if (reuseKnown && selected != null) {
                    SyncMatch(song, emptyList(), selected, false)
                } else if (Regex("[A-Za-z0-9_-]{11}").matches(song.videoId)) {
                    SyncMatch(song, listOf(song), song.videoId, false)
                } else {
                    val target = TrackMatcher.targetOf(song)
                    val query = TrackMatcher.queries(target).firstOrNull()
                    val candidates = if (query == null) emptyList() else
                        YtMusicRepository.search(query, SearchFilter.SONGS).getOrThrow()
                            .filterIsInstance<SearchResult.Track>().map { it.song }
                    withContext(Dispatchers.Default) { reviewPlaylistMatch(song, candidates, selected) }
                }
                guard(expected)
                check(snapshot == sourceScope(source)) { "Source account changed" }
                progress(completed.incrementAndGet(), rows.size)
                if (song.videoId in skipped) row.copy(selectedId = null, excluded = true, uncertain = false) else row
            }
        } }.awaitAll()
    }
    fun prepare() {
        val ui = _state.value
        val source = ui.source ?: return
        val expected = identity() ?: return
        if (ui.busy || ui.destinationTitle.isBlank()) return
        if (ui.destinationId != null && source.browseId == "VL${ui.destinationId}") {
            _state.value = ui.copy(error = "Choose a different destination playlist")
            return
        }
        preparedIdentity = expected
        _state.value = ui.copy(busy = true, error = null, progress = "Matching tracks…")
        work = scope.launch {
            try {
                preparedSourceScope = sourceScope(source)
                ui.destinationId?.let { target ->
                    guard(expected)
                    check(YtMusicRepository.playlistOwned("VL$target").getOrThrow() == true) { "Choose a playlist owned by your YouTube account" }
                }
                val rows = match(source, expected) { done, total -> _state.value = _state.value.copy(progress = "$done / $total tracks matched") }
                check(sourceScope(source) == preparedSourceScope) { "Source account changed. Review this sync again." }
                _state.value = _state.value.copy(matches = rows, stage = SyncStage.REVIEW, busy = false)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { _state.value = _state.value.copy(busy = false, error = e.message) }
        }
    }
    private suspend fun transfer(source: SyncSource, expected: String, title: String, destination: String?, rows: List<SyncMatch>, keep: Boolean, created: (String) -> Unit = {}): PlaylistSyncLink = mutations.withLock {
        val snapshot = sourceScope(source)
        guard(expected)
        val choices = rows.mapNotNull { row -> row.selectedId?.let { row.source.videoId to it } }.toMap()
        check(choices.isNotEmpty()) { "Choose at least one track to sync" }
        val previous = links.firstOrNull { sameSource(it.source, source) && it.youtubeIdentity == expected }
        destination?.let {
            check(YtMusicRepository.playlistOwned("VL$it").getOrThrow() == true) { "The destination is no longer editable by this YouTube account" }
            guard(expected)
        }
        check(snapshot == sourceScope(source)) { "Source account changed" }
        val target = destination ?: YtMusicRepository.createPlaylist(title.trim(), PlaylistPrivacy.PRIVATE).getOrThrow().also(created)
        var link = PlaylistSyncLink(id = previous?.id ?: UUID.randomUUID().toString(), source = source, youtubeIdentity = expected,
            destinationId = target, destinationTitle = title.trim(), keepSynchronized = keep, matches = choices,
            skipped = rows.filter { it.excluded }.mapTo(HashSet()) { it.source.videoId },
            needsReview = rows.count { it.selectedId == null && !it.excluded })
        // Save the destination before batching: a failed transfer can resume without creating another playlist.
        save(link)
        if (destination == null) onChanged()
        guard(expected)
        val existing = YtMusicRepository.playlistEntries("VL$target").getOrThrow().mapTo(HashSet()) { it.videoId }
        choices.values.distinct().filterNot { it in existing }.chunked(50).forEach { batch ->
            guard(expected)
            check(snapshot == sourceScope(source)) { "Source account changed" }
            YtMusicRepository.addToPlaylist(target, batch).getOrThrow()
        }
        guard(expected)
        check(snapshot == sourceScope(source)) { "Source account changed" }
        link = link.copy(lastSynced = System.currentTimeMillis())
        save(link)
        onChanged()
        link
    }
    fun apply() {
        val ui = _state.value
        val source = ui.source ?: return
        val expected = preparedIdentity ?: return
        if (ui.busy || ui.stage != SyncStage.REVIEW) return
        _state.value = ui.copy(busy = true, error = null, progress = "Syncing to YouTube Music…")
        work = scope.launch {
            try {
                check(sourceScope(source) == preparedSourceScope) { "Source account changed. Review this sync again." }
                transfer(source, expected, ui.destinationTitle, ui.destinationId, ui.matches, ui.keepSynchronized) { id ->
                    _state.value = _state.value.copy(destinationId = id)
                }
                _state.value = _state.value.copy(busy = false, stage = SyncStage.COMPLETE)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                links.firstOrNull { sameSource(it.source, source) && it.youtubeIdentity == expected && it.destinationId == _state.value.destinationId }
                    ?.let { save(it.copy(error = e.message)) }
                _state.value = _state.value.copy(busy = false, error = e.message)
            }
        }
    }
    /** Opt-in additions, at most hourly while the app is resumed; uncertain matches wait for review. */
    fun syncLinked() {
        val expected = identity() ?: return
        if (automaticIdentity != expected) { automaticIdentity = expected; lastAutomaticCheck = 0L }
        if (transferOpen || managerOpen || _state.value.busy || automatic?.isActive == true || System.currentTimeMillis() - lastAutomaticCheck < 3_600_000) return
        val pending = links.filter { it.youtubeIdentity == expected && it.keepSynchronized }
        if (pending.isEmpty()) return
        lastAutomaticCheck = System.currentTimeMillis()
        automatic = scope.launch {
            pending.forEach { link ->
                try {
                    val sourceSnapshot = sourceScope(link.source)
                    val rows = match(link.source, expected, reuseKnown = true) { _, _ -> }
                    check(sourceSnapshot == sourceScope(link.source)) { "Source account changed" }
                    if (rows.any { it.selectedId != null }) transfer(link.source, expected, link.destinationTitle, link.destinationId, rows, true)
                    else save(link.copy(needsReview = rows.count { it.selectedId == null && !it.excluded }))
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) { save(link.copy(error = e.message)) }
            }
        }
    }
}
