package com.music.streamx.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.music.streamx.R
import com.music.streamx.auth.AuthStore
import com.music.streamx.auth.SignedIn
import com.music.streamx.auth.GoogleAccountSession
import com.music.streamx.auth.YouTubeProfile
import com.music.streamx.auth.profileId
import com.music.streamx.auth.sessionId
import com.music.streamx.auth.adjacentProfile
import com.music.streamx.data.streamx.StreamXClient
import com.music.streamx.data.streamx.StreamXSession
import com.music.streamx.data.AppUpdateChecker
import com.music.streamx.data.LocalMediaRepository
import com.music.streamx.data.LikeState
import com.music.streamx.data.YtMusicRepository
import com.music.streamx.data.model.SPOTIFY_MISSING_PREFIX
import com.music.streamx.data.model.SPOTIFY_PENDING_PREFIX
import com.music.streamx.data.spotify.SPOTIFY_PAGE_PREFIX
import com.music.streamx.data.spotify.SpotifyImporter
import com.music.streamx.data.spotify.SpotifyLibrary
import com.music.streamx.data.spotify.SpotifyTrack
import com.music.streamx.data.lyrics.EmbeddedLyrics
import com.music.streamx.data.lyrics.LyricLine
import com.music.streamx.data.lyrics.LyricsRepository
import com.music.streamx.data.lyrics.LyricsSource
import com.music.streamx.data.settings.AppSettings
import com.music.streamx.data.innertube.Innertube
import com.music.streamx.data.innertube.PlaybackTracker
import com.music.streamx.data.innertube.StreamResolver
import com.music.streamx.auth.CapturedSession
import com.music.streamx.auth.WebSessionMode
import com.music.streamx.data.model.Account
import com.music.streamx.data.model.AccountChannel
import com.music.streamx.data.model.BrowseType
import com.music.streamx.data.model.DetailPage
import com.music.streamx.data.model.HomeShelf
import com.music.streamx.data.model.withoutRepeatsOf
import com.music.streamx.data.model.LibraryPage
import com.music.streamx.data.model.LibraryState
import com.music.streamx.data.model.LikeStatus
import com.music.streamx.data.model.MoodGenre
import com.music.streamx.data.model.MoodGenreSection
import com.music.streamx.data.model.PlaylistPrivacy
import com.music.streamx.data.model.SearchFilter
import com.music.streamx.data.model.SearchResult
import com.music.streamx.data.model.ShelfItem
import com.music.streamx.data.model.Song
import com.music.streamx.data.model.SongMenu
import com.music.streamx.data.model.SubscriptionState
import com.music.streamx.data.model.UiState
import com.music.streamx.data.model.UserPlaylist
import com.music.streamx.data.model.SearchHistoryEntity
import com.music.streamx.data.model.EntityType
import com.music.streamx.data.settings.SearchHistory
import com.music.streamx.download.Downloads
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import com.music.streamx.data.sources.AddonSource
import com.music.streamx.data.sources.SourceKind
import com.music.streamx.data.sources.SourceRegistry
import com.music.streamx.data.sources.SourceResolver
import com.music.streamx.data.sources.TrackMatcher
import com.music.streamx.playback.AudioCache
import com.music.streamx.playback.BoundedContextRequests
import com.music.streamx.playback.contextEntryKey
import com.music.streamx.playback.StreamChoice
import com.music.streamx.ui.screens.CACHE_FOLDER_BROWSE_ID
import com.music.streamx.data.LocalGroup
import com.music.streamx.data.LocalGroups
import com.music.streamx.data.matchesSearch
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/** The selected server catalogue, YouTube Music, local music, or enabled APK sources. */
enum class SearchSource { STREAMX, YOUTUBE, LIBRARY, EXTENSIONS, ADDONS }

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val authStore = AuthStore(app)

    private val extensionRevision = AtomicLong()
    private val usingExtension get() = SourceRegistry.selectedExtensionId.value != null
    private val usingCloud get() = SourceRegistry.catalogue.value == SourceKind.CLOUDSTREAM
    private val usingStreamX get() = SourceRegistry.catalogue.value in listOf(SourceKind.STREAMX, SourceKind.CLOUDSTREAM)
    private val _signedIn = MutableStateFlow(if (usingExtension) false else if (usingStreamX) (usingCloud || StreamXSession.state.value.personalAccount) else authStore.isSignedIn)
    val signedIn: StateFlow<Boolean> = _signedIn.asStateFlow()

    private val _extensionBrowse = MutableStateFlow<UiState<List<HomeShelf>>>(UiState.Loading)
    val extensionBrowse: StateFlow<UiState<List<HomeShelf>>> = _extensionBrowse.asStateFlow()
    private val _extensionSearchLanding = MutableStateFlow<List<ShelfItem>>(emptyList())
    val extensionSearchLanding = _extensionSearchLanding.asStateFlow()
    private val _extensionFullSearch = MutableStateFlow(false)
    val extensionFullSearch = _extensionFullSearch.asStateFlow()
    private val _extensionBrowseTitle = MutableStateFlow("Browse")
    val extensionBrowseTitle = _extensionBrowseTitle.asStateFlow()
    /** A source's Radio page, or null where it has none — which is what keeps the tab out of the bar. */
    private val _extensionRadio = MutableStateFlow<UiState<List<HomeShelf>>?>(null)
    val extensionRadio = _extensionRadio.asStateFlow()
    private val _extensionBrowseGrid = MutableStateFlow(true)
    val extensionBrowseGrid = _extensionBrowseGrid.asStateFlow()
    private val _home = MutableStateFlow<UiState<List<HomeShelf>>>(UiState.Loading)
    val home: StateFlow<UiState<List<HomeShelf>>> = _home.asStateFlow()

    /**
     * Token for the next page of Home shelves; null once there's nothing
     * more. Declared here rather than by [loadMoreHome] because [init] calls
     * [loadHome] synchronously up to its first suspension point — a property
     * declared after [init] would still be null when that runs.
     */
    private var homeContinuation: String? = null

    /** Titles already on screen, so a later page can't repeat a shelf. */
    private val homeSeenTitles = mutableSetOf<String>()

    private val _homeLoadingMore = MutableStateFlow(false)

    /**
     * Requests still filling out the first Play page. The core feed and each
     * supplement browse run in parallel and publish as they land, so the list
     * goes Success while several shelves are still on the wire — counted here
     * so the feed can say more is coming instead of just ending mid-load.
     */
    private val _homePendingShelves = MutableStateFlow(0)

    /** True whenever shelves are still due at the end of the feed, from either source. */
    val homeLoadingMore: StateFlow<Boolean> =
        combine(_homeLoadingMore, _homePendingShelves) { paging, pending -> paging || pending > 0 }
            .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _homeRecentlyPlayedLoading = MutableStateFlow(false)
    val homeRecentlyPlayedLoading: StateFlow<Boolean> = _homeRecentlyPlayedLoading.asStateFlow()
    private val homeLoadGeneration = AtomicLong(0L)

    private val _explore = MutableStateFlow<UiState<List<MoodGenreSection>>>(UiState.Loading)
    val explore: StateFlow<UiState<List<MoodGenreSection>>> = _explore.asStateFlow()

    private val _selectedMoodGenre = MutableStateFlow<MoodGenre?>(null)
    val selectedMoodGenre: StateFlow<MoodGenre?> = _selectedMoodGenre.asStateFlow()

    private val _moodGenreShelves = MutableStateFlow<UiState<List<HomeShelf>>>(UiState.Loading)
    val moodGenreShelves: StateFlow<UiState<List<HomeShelf>>> = _moodGenreShelves.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _results = MutableStateFlow<UiState<List<SearchResult>>?>(null)
    val results: StateFlow<UiState<List<SearchResult>>?> = _results.asStateFlow()

    /** The mixed YouTube Music result page is the fast, useful default. */
    private val _filter = MutableStateFlow(SearchFilter.ALL)
    val filter: StateFlow<SearchFilter> = _filter.asStateFlow()

    private val _searchLoadingMore = MutableStateFlow(false)
    val searchLoadingMore: StateFlow<Boolean> = _searchLoadingMore.asStateFlow()

    /** Increments once per first-page request so the UI can reset its list. */
    private val _searchScrollReset = MutableStateFlow(0)
    val searchScrollReset: StateFlow<Int> = _searchScrollReset.asStateFlow()

    /** True while a committed search is in flight — gates suggestion/media callbacks. */
    private var searchSubmitted = false

    /**
     * What the search page offers while a query is being typed, led by the
     * query itself.
     *
     * Non-empty *is* the signal that the field is mid-edit, so the screen
     * needs no second flag: these rows are shown in place of the results
     * whenever there are any, and cleared the moment a search is actually run
     * — see [submitSearch], [searchFor].
     *
     * Element 0 is always the raw text as typed. It's put there by the
     * keystroke itself rather than taken from the response, so the row the
     * thumb is already heading for is correct before the network answers, and
     * stays correct if it never does — YouTube's list never contains the
     * half-typed text, only completions of it.
     */
    private val _suggestions = MutableStateFlow<List<String>>(emptyList())
    val suggestions: StateFlow<List<String>> = _suggestions.asStateFlow()

    /**
     * Live media results shown alongside typeahead suggestions. Populated by a
     * lightweight search that runs in parallel with text completions; the UI
     * renders these as playable track cards and browse items below the text
     * suggestion rows. Cleared when the user commits to a search or empties
     * the field.
     */
    private val _typeaheadResults = MutableStateFlow<List<SearchResult>>(emptyList())
    val typeaheadResults: StateFlow<List<SearchResult>> = _typeaheadResults.asStateFlow()

    /** Where the Search tab looks — see [setSearchSource]. */
    private val _searchSource = MutableStateFlow(if (usingExtension) SearchSource.EXTENSIONS else if (usingStreamX) SearchSource.STREAMX else SearchSource.YOUTUBE)
    val searchSource: StateFlow<SearchSource> = _searchSource.asStateFlow()

    /**
     * The Addons source's answer for the submitted query: every enabled addon
     * searched at once and their rows interleaved. Null until something has
     * been submitted. These songs carry source track keys, so a tap plays the
     * addon's own stream with no YouTube lookup in between.
     */
    private val _addonResults = MutableStateFlow<UiState<List<Song>>?>(null)
    val addonResults: StateFlow<UiState<List<Song>>?> = _addonResults.asStateFlow()
    private var addonSearchJob: Job? = null

    /** The words typed into the Addons search before, newest first. */
    val addonSearchHistory: StateFlow<List<String>> = SearchHistory.addonRecent

    fun removeAddonSearch(query: String) = SearchHistory.removeAddonQuery(query)

    fun clearAddonSearchHistory() = SearchHistory.clearAddonQueries()

    /** Every track in the Local Music folder, read when the Library source is picked. */
    private val _librarySongs = MutableStateFlow<UiState<List<Song>>>(UiState.Loading)

    /**
     * The Library source's answer for the current query: the Local Music
     * folder narrowed by the same match its own filter box uses, so the two
     * places never disagree about what is on the device. Null while the field
     * is empty — there is nothing asked yet.
     */
    val libraryResults: StateFlow<UiState<List<Song>>?> = combine(_librarySongs, _query) { state, query ->
        val term = query.trim()
        when {
            term.isEmpty() -> null
            state is UiState.Success -> UiState.Success(state.data.filter { it.matchesSearch(term) })
            else -> state
        }
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    // The search pipeline's own state. Declared here, above [init], because
    // that is where the collector is started from and a property declared
    // below it would still be null when it runs. See [startSearchPipeline].

    /**
     * Buffered so an emission is never lost to a collector that happens to be
     * mid-search, and [BufferOverflow.DROP_OLDEST] because when two arrive
     * together the later one is the one meant.
     */
    private val searchRequests = MutableSharedFlow<SearchRequest>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /**
     * The same arrangement as [searchRequests], for the typeahead — where the
     * drop policy earns its keep rather than just being safe: this one really
     * does take a keystroke each, and a fast typist's backlog should collapse
     * to the prefix they ended on instead of being worked through a letter at
     * a time.
     */
    private val suggestRequests = MutableSharedFlow<String>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    private val newestRequestId = AtomicLong(0L)

    /**
     * Results of recent searches, so a query searched before is answered
     * without asking again. That covers the two ways a query is repeated most:
     * a filter tab, which re-runs the same text against a different tab and
     * then usually goes back, and a term tapped out of the recent searches.
     *
     * Its other half is [prefixMatch], which is what the typeahead makes worth
     * keeping: picking "coldplay yellow" off a list is normally preceded by
     * having searched "coldplay", and those results are close enough to leave
     * up for the moment the narrower one takes rather than blanking the page
     * to a spinner.
     */
    private data class SearchCacheEntry(
        val rows: List<SearchResult>,
        val continuation: String?,
    )

    private data class SearchSession(
        val key: String,
        val requestId: Long,
        val filter: SearchFilter,
        val continuation: String?,
    )

    private val searchCache = LruCache<String, SearchCacheEntry>(SEARCH_CACHE_ENTRIES)
    private var searchSession: SearchSession? = null

    /** Synced lyrics for whatever is playing; null while unknown or absent. */
    private val _lyrics = MutableStateFlow<List<LyricLine>?>(null)
    val lyrics: StateFlow<List<LyricLine>?> = _lyrics.asStateFlow()

    /** Which of the four databases [lyrics] came from, for the panel's credit. */
    private val _lyricsSource = MutableStateFlow<LyricsSource?>(null)
    val lyricsSource: StateFlow<LyricsSource?> = _lyricsSource.asStateFlow()

    /**
     * Whether the lookup for the current track has finished. [lyrics] alone
     * can't tell "still looking" apart from "looked, found nothing" — both
     * are null — and the player needs that distinction to show "Lyrics not
     * available" only once it actually means that.
     */
    private val _lyricsChecked = MutableStateFlow(false)
    val lyricsChecked: StateFlow<Boolean> = _lyricsChecked.asStateFlow()

    private var lyricsJob: Job? = null
    private val manualLyricsJobs = mutableMapOf<LyricsSource, Job>()

    private val _lyricsProviderStates = MutableStateFlow(
        LyricsSource.entries.associateWith { LyricsProviderState.NOT_FETCHED },
    )
    val lyricsProviderStates: StateFlow<Map<LyricsSource, LyricsProviderState>> =
        _lyricsProviderStates.asStateFlow()

    /** Completed hits are retained for the playing track so choosing one is instant. */
    private val lyricsProviderResults = ConcurrentHashMap<LyricsSource, LyricsRepository.Result>()

    private data class LyricsRequest(
        val videoId: String,
        val title: String,
        val artist: String,
        val durationMs: Long,
        val album: String?,
    )

    private var currentLyricsRequest: LyricsRequest? = null
    private var lyricsGeneration = 0L
    private var selectedLyricsSource: LyricsSource? = null

    /**
     * What the loaded lyrics are for. Both the track *and* the settings that
     * chose them, so switching a source on or off re-runs the lookup rather
     * than leaving the last answer sitting on a player that would now find a
     * different one.
     */
    private var lyricsFor: Pair<String, Set<LyricsSource>>? = null

    /**
     * Called as the playing track changes; cheap no-op when already loaded.
     *
     * [localUri] is the file this track plays from when it is on the device,
     * and it is tried before the network: a downloaded track had its lyrics
     * fetched once already and written into its own file (see `LyricsTag`), so
     * asking the same servers again is a round trip to arrive at a string that
     * is on disk — and one that fails outright with the connection off, which
     * is what made a downloaded song show nothing offline.
     */
    fun loadLyrics(
        videoId: String,
        title: String,
        artist: String,
        durationMs: Long,
        album: String? = null,
        localUri: String? = null,
    ) {
        val sources = if (AppSettings.syncedLyrics.value) {
            AppSettings.lyricsSources.value
        } else {
            emptySet()
        }
        val ownPluginLyrics=AppSettings.syncedLyrics.value && com.music.streamx.data.lyrics.PluginLyrics.supports(videoId)
        val key = videoId to sources
        if (lyricsFor == key) return
        // The duration lands a beat after the track, and a database match needs
        // it. Turned away here rather than inside the job below: claiming the
        // lookup first and giving it up asynchronously means the very re-trigger
        // that carries the duration can arrive while the claim still stands, be
        // dropped as a duplicate, and leave the track marked as being looked up
        // by nobody — which is what left a paused track loading for ever, since
        // pausing is when the duration is most likely to arrive a frame late.
        if (localUri == null && durationMs <= 0L && !ownPluginLyrics) return
        lyricsFor = key
        lyricsGeneration += 1
        val generation = lyricsGeneration
        currentLyricsRequest = LyricsRequest(videoId, title, artist, durationMs, album)
        selectedLyricsSource = null
        manualLyricsJobs.values.forEach(Job::cancel)
        manualLyricsJobs.clear()
        lyricsProviderResults.clear()
        _lyricsProviderStates.value =
            LyricsSource.entries.associateWith { LyricsProviderState.NOT_FETCHED }
        _lyrics.value = null
        _lyricsSource.value = null
        lyricsJob?.cancel()
        if (sources.isEmpty() && !ownPluginLyrics) {
            // Switched off, or every source unticked. Nothing to look up, and
            // nothing to say about it — the player drops the lyric strip
            // rather than reporting a track with no lyrics.
            _lyricsChecked.value = true
            return
        }
        _lyricsChecked.value = false
        lyricsJob = viewModelScope.launch {
            // The file first, and without the duration gate below: a length is
            // only needed to *match* a track against a stranger's database, and
            // nothing is being matched here — these lyrics were written into
            // this exact file, for this exact recording.
            if (localUri != null) {
                EmbeddedLyrics.forUri(getApplication(), localUri)?.let { embedded ->
                    _lyrics.value = embedded
                    // No source to name: what the file records is the lyrics,
                    // not which of the eight services they came from months ago.
                    _lyricsSource.value = null
                    _lyricsChecked.value = true
                    return@launch
                }
            }
            if(ownPluginLyrics)com.music.streamx.data.lyrics.PluginLyrics.forTrack(videoId)?.let { own ->
                if(generation!=lyricsGeneration)return@launch
                _lyrics.value=own
                _lyricsSource.value=LyricsSource.APPLE_MUSIC_PLUGIN
                _lyricsChecked.value=true
                return@launch
            }
            if(sources.isEmpty()) {_lyricsChecked.value=true;return@launch}
            if (durationMs <= 0L) {
                // Duration arrives a beat after the track does; wait for it.
                lyricsFor = null
                return@launch
            }
            val found = LyricsRepository.lyrics(
                videoId, title, artist, durationMs, album, sources,
                AppSettings.lyricsSourceOrder.value, AppSettings.prioritizeSyllableSync.value,
                onSourceStarted = { source -> providerStarted(generation, source) },
                onSourceResult = { source, result ->
                    providerFinished(generation, source, result)
                },
                onSourceCancelled = { source -> providerCancelled(generation, source) },
            )
            val selected = selectedLyricsSource?.let(lyricsProviderResults::get) ?: found
            _lyrics.value = selected?.lines
            _lyricsSource.value = selected?.source
            _lyricsChecked.value = true
        }
    }

    /**
     * Selects a provider from the player drawer. Completed hits are applied
     * from memory; misses are inert; only an untouched provider goes online.
     */
    fun selectLyricsProvider(source: LyricsSource) {
        val request = currentLyricsRequest ?: return
        when (_lyricsProviderStates.value[source]) {
            LyricsProviderState.FOUND -> {
                selectedLyricsSource = source
                lyricsProviderResults[source]?.let(::showLyricsResult)
            }
            LyricsProviderState.FETCHING -> {
                // Apply it as soon as the already-running automatic attempt completes.
                selectedLyricsSource = source
            }
            LyricsProviderState.NOT_FETCHED, null -> {
                selectedLyricsSource = source
                fetchLyricsProvider(request, lyricsGeneration, source)
            }
            LyricsProviderState.NOT_FOUND -> Unit
        }
    }

    private fun fetchLyricsProvider(
        request: LyricsRequest,
        generation: Long,
        source: LyricsSource,
    ) {
        if (manualLyricsJobs[source]?.isActive == true) return
        manualLyricsJobs[source] = viewModelScope.launch {
            LyricsRepository.lyrics(
                videoId = request.videoId,
                title = request.title,
                artist = request.artist,
                durationMs = request.durationMs,
                album = request.album,
                sources = setOf(source),
                order = listOf(source),
                prioritizeSyllableSync = false,
                onSourceStarted = { provider -> providerStarted(generation, provider) },
                onSourceResult = { provider, result ->
                    providerFinished(generation, provider, result)
                },
                onSourceCancelled = { provider -> providerCancelled(generation, provider) },
            )
        }
    }

    private fun providerStarted(generation: Long, source: LyricsSource) {
        if (generation != lyricsGeneration) return
        _lyricsProviderStates.update { it + (source to LyricsProviderState.FETCHING) }
    }

    private fun providerFinished(
        generation: Long,
        source: LyricsSource,
        result: LyricsRepository.Result?,
    ) {
        if (generation != lyricsGeneration) return
        if (result == null) {
            _lyricsProviderStates.update { it + (source to LyricsProviderState.NOT_FOUND) }
            return
        }
        lyricsProviderResults[source] = result
        _lyricsProviderStates.update { it + (source to LyricsProviderState.FOUND) }
        if (selectedLyricsSource == source) showLyricsResult(result)
    }

    private fun providerCancelled(generation: Long, source: LyricsSource) {
        if (generation != lyricsGeneration) return
        _lyricsProviderStates.update { states ->
            if (states[source] == LyricsProviderState.FETCHING) {
                states + (source to LyricsProviderState.NOT_FETCHED)
            } else {
                states
            }
        }
        // A tap may have selected a provider while the priority race was still
        // using it. If that race then cancels the loser, honour the tap with a
        // dedicated request instead of leaving the row stuck at "Fetching".
        if (selectedLyricsSource == source) {
            currentLyricsRequest?.let { fetchLyricsProvider(it, generation, source) }
        }
    }

    private fun showLyricsResult(result: LyricsRepository.Result) {
        _lyrics.value = result.lines
        _lyricsSource.value = result.source
        _lyricsChecked.value = true
    }

    private val _account = MutableStateFlow<Account?>(null)
    val account: StateFlow<Account?> = _account.asStateFlow()

    /**
     * The channels this login can act as. Empty until the picker asks for
     * them — it is one more request per sign-in and nothing else on the
     * settings page needs the answer.
     */
    private val _channels = MutableStateFlow<List<AccountChannel>>(emptyList())
    val channels: StateFlow<List<AccountChannel>> = _channels.asStateFlow()

    private val _channelsLoading = MutableStateFlow(false)
    val channelsLoading: StateFlow<Boolean> = _channelsLoading.asStateFlow()

    /**
     * The chosen channel's [AccountChannel.key], or null while the app is
     * acting as whichever channel YouTube Music serves by default.
     */
    private val _selectedChannelKey = MutableStateFlow(
        authStore.activeSession?.activeProfileId ?: authStore.channelPageId ?: authStore.channelDataSyncId,
    )
    val selectedChannelKey: StateFlow<String?> = _selectedChannelKey.asStateFlow()

    private val _selectedChannelName = MutableStateFlow(
        authStore.activeSession?.profiles?.firstOrNull { it.profileId == authStore.activeProfileId }?.name
            ?: authStore.channelName,
    )
    val selectedChannelName: StateFlow<String?> = _selectedChannelName.asStateFlow()

    /** Source of truth for Google sessions and their YouTube identities. */
    private val _googleAccounts = MutableStateFlow(authStore.sessions)
    val googleAccounts: StateFlow<List<GoogleAccountSession>> = _googleAccounts.asStateFlow()
    private val _activeAccountId = MutableStateFlow(authStore.activeSession?.accountId)
    val activeAccountId: StateFlow<String?> = _activeAccountId.asStateFlow()
    private val _activeProfileId = MutableStateFlow(authStore.activeProfileId)
    val activeProfileId: StateFlow<String?> = _activeProfileId.asStateFlow()

    private val _history = MutableStateFlow<UiState<List<Song>>>(UiState.Loading)
    val history: StateFlow<UiState<List<Song>>> = _history.asStateFlow()
    private val _historyShelves = MutableStateFlow<List<HomeShelf>>(emptyList())
    val historyShelves: StateFlow<List<HomeShelf>> = _historyShelves.asStateFlow()

    private val _library = MutableStateFlow<UiState<LibraryPage>>(UiState.Loading)
    val library: StateFlow<UiState<LibraryPage>> = _library.asStateFlow()

    /** In-memory cache is partitioned by account and profile; it is never shared. */
    private data class ListenerSnapshot(
        val account: Account?, val library: UiState<LibraryPage>,
        val history: UiState<List<Song>>, val playlists: List<UserPlaylist>,
        val owned: Map<String, Boolean>,
    )
    private val listenerCache = mutableMapOf<String, ListenerSnapshot>()

    /**
     * Album / artist / playlist pages, as a stack — opening an artist from an
     * album page and pressing back returns to the album, not to search.
     */
    private val _detailStack = MutableStateFlow<List<DetailPage>>(emptyList())
    val detailStack: StateFlow<List<DetailPage>> = _detailStack.asStateFlow()

    /** UI-side continuation fetches that can be handed to the playback service. */
    private val fillInJobs = mutableMapOf<String, Job>()

    private val _releaseLibrary = MutableStateFlow<Map<String, LibraryState>>(emptyMap())

    /**
     * Library state of releases that are only a card on some page — the artist
     * page's top release — keyed by browse id. They have no page of their own on
     * the stack to carry it, so it is read off the release and kept here.
     */
    val releaseLibrary: StateFlow<Map<String, LibraryState>> = _releaseLibrary.asStateFlow()

    /** Reads whether the release [browseId] is saved, once; a no-op if already known. */
    fun loadReleaseLibrary(browseId: String) {
        if (usingExtension) return
        if (usingStreamX) return
        if (browseId in _releaseLibrary.value) return
        viewModelScope.launch {
            YtMusicRepository.releaseLibraryState(browseId).getOrNull()?.let { state ->
                _releaseLibrary.value += (browseId to state)
            }
        }
    }

    /** Saves the release [browseId] to the library or takes it out, as [toggleLibrary] does for a page. */
    fun toggleReleaseLibrary(browseId: String) {
        if (usingExtension) return
        if (usingStreamX) return
        if (!requireSignIn()) return
        viewModelScope.launch {
            val current = _releaseLibrary.value[browseId]
                ?: YtMusicRepository.releaseLibraryState(browseId).getOrNull()
                ?: return@launch
            val target = !current.saved
            _releaseLibrary.value += (browseId to current.copy(saved = target))
            if (YtMusicRepository.setSaved(current.playlistId, target).isSuccess) {
                libraryStale = true
            } else {
                _releaseLibrary.value += (browseId to current)
            }
        }
    }


    /** Set once per launch if GitHub has a release newer than this build. */
    val updateAvailable: StateFlow<AppUpdateChecker.UpdateInfo?> = AppUpdateChecker.available

    // ---- Ratings, library and playlists -------------------------------------

    /**
     * Ratings this session has set, which win over whatever the library feed
     * last said.
     *
     * Kept apart from the library rather than folded into it because the two
     * answer different questions: Liked Music is what YouTube knew when the
     * page was fetched, and this is what the user has done since. Layering
     * them ([likeStatuses]) means a tap shows immediately without the library
     * having to be re-fetched, and a later refresh can't undo it.
     */
    /** Every rating known for this account: the library's, then this session's. */
    val likeStatuses: StateFlow<Map<String, LikeStatus>> =
        combine(_library, LikeState.overrides) { library, overrides ->
            val liked = (library as? UiState.Success)?.data?.likedSongs
                ?.associate { it.videoId to LikeStatus.LIKE }
                .orEmpty()
            liked + overrides
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    fun likeStatusOf(videoId: String): LikeStatus =
        likeStatuses.value[videoId] ?: LikeStatus.INDIFFERENT

    /**
     * Sets (or clears) the thumbs rating on [videoId].
     *
     * Written to the screen first and rolled back if YouTube refuses. A rating
     * is a one-tap, low-stakes action taken while a song is playing; waiting
     * on a round trip before the heart fills reads as the tap not having
     * registered, and people tap again.
     */
    private val extensionLikesPending = mutableSetOf<String>()
    fun setLike(videoId: String, status: LikeStatus) {
        val sourceKey = SourceRegistry.parseTrackKey(videoId)
        val extensionConfig = sourceKey?.let { SourceRegistry.config(it.first) }
            ?.takeIf { it.kind == SourceKind.APK_PLUGIN }
        if (extensionConfig != null) {
            if (!requireSignIn() || status == LikeStatus.DISLIKE || !extensionLikesPending.add(videoId)) return
            val previous = likeStatusOf(videoId)
            val identity = listenerKey()
            LikeState.set(videoId, status)
            viewModelScope.launch {
                try {
                    com.music.streamx.data.extensions.ApkCatalogue(extensionConfig).save("track", sourceKey.second, status == LikeStatus.LIKE)
                    if (identity == listenerKey()) { libraryStale = true; loadLibrary() }
                } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                catch (_: Exception) { if (identity == listenerKey()) LikeState.set(videoId, previous) }
                finally { extensionLikesPending.remove(videoId) }
            }
            return
        }
        if (!requireSignIn()) return
        if (usingStreamX) {
            val client = runCatching { StreamXClient.current() }.getOrNull() ?: return
            val identity = listenerKey()
            val previous = likeStatusOf(videoId)
            if (previous == status) return
            LikeState.set(videoId, status)
            viewModelScope.launch {
                if (identity != listenerKey()) return@launch
                val result = StreamXClient.result { client.rate(videoId, status) }
                if (identity != listenerKey()) return@launch
                result.onSuccess { libraryStale = true; if (status != LikeStatus.LIKE) dropFromLikedLists(videoId) }
                    .onFailure { LikeState.set(videoId, previous) }
            }
            return
        }
        val previous = likeStatusOf(videoId)
        if (previous == status) return
        LikeState.set(videoId, status)
        viewModelScope.launch {
            YtMusicRepository.rate(videoId, status).fold(
                onSuccess = {
                    // Liked Music is now out of date either way.
                    libraryStale = true
                    if (status != LikeStatus.LIKE) dropFromLikedLists(videoId)
                    // Clearing the heart means forgetting the song, not
                    // demoting it — see [forgetFromLibrary].
                    val unliked = previous == LikeStatus.LIKE &&
                        status == LikeStatus.INDIFFERENT
                    if (unliked) forgetFromLibrary(videoId)
                },
                onFailure = {
                    LikeState.set(videoId, previous)
                },
            )
        }
    }

    /**
     * Takes an un-liked track out of the library as well, and reports whether
     * it did.
     *
     * Liking and saving are two independent flags on YouTube's side, and
     * clearing only the first leaves the song saved — still feeding the
     * Library tab's Artists shelf, still in the library feeds, with nowhere
     * left in this app to reach it and finish the job. Clearing the heart
     * reads as "forget this song", so it clears both.
     *
     * The token is fetched here rather than taken from [songMenu] because the
     * heart in the player never opens a menu, so there is often nothing
     * cached to take. One extra request, on an action nobody performs in bulk.
     * A song that was never saved has no removal token and this is a no-op.
     */
    private suspend fun forgetFromLibrary(videoId: String): Boolean {
        val menu = YtMusicRepository.songMenu(videoId).getOrNull() ?: return false
        val token = menu.removeFromLibraryToken?.takeIf { menu.inLibrary } ?: return false
        if (YtMusicRepository.setLibraryStatus(token).isFailure) return false
        // The menu may be the one on screen; don't leave it offering a
        // removal that has already happened.
        _songMenu.value = _songMenu.value?.copy(inLibrary = false)
        return true
    }

    /**
     * Takes an un-liked track out of the lists that exist *because* it was
     * liked — the Library tab's Liked Music section, and the Liked Music page
     * itself if it happens to be open.
     *
     * Marking the library stale isn't enough on its own: that only acts when
     * the tab is next opened, and un-liking is nearly always done from inside
     * one of these two lists, looking straight at the row. Leaving it there
     * reads as the tap not having worked — the menu says "Like" again while
     * the song sits in Liked Music.
     *
     * Only ever removes. A track liked from somewhere else doesn't get spliced
     * into a list that YouTube orders for itself; the next fetch places it.
     */
    private fun dropFromLikedLists(videoId: String) {
        val library = (_library.value as? UiState.Success)?.data
        if (library != null && library.likedSongs.any { it.videoId == videoId }) {
            _library.value = UiState.Success(
                library.copy(likedSongs = library.likedSongs.filterNot { it.videoId == videoId }),
            )
        }
        _detailStack.value = _detailStack.value.map { page ->
            val songs = (page.songs as? UiState.Success)?.data
            if (page.browseId != YtMusicRepository.LIKED_MUSIC || songs == null) {
                page
            } else {
                page.copy(songs = UiState.Success(songs.filterNot { it.videoId == videoId }))
            }
        }
    }

    /** The heart: liked becomes neutral, anything else becomes liked. */
    fun toggleLike(videoId: String) = setLike(
        videoId,
        if (likeStatusOf(videoId) == LikeStatus.LIKE) LikeStatus.INDIFFERENT else LikeStatus.LIKE,
    )

    /** As [toggleLike], for the thumb-down. */
    fun toggleDislike(videoId: String): LikeStatus? {
        if (usingExtension) return null
        if (!requireSignIn()) return null
        val previous = likeStatusOf(videoId)
        setLike(
            videoId,
            if (previous == LikeStatus.DISLIKE) {
                LikeStatus.INDIFFERENT
            } else {
                LikeStatus.DISLIKE
            },
        )
        return previous
    }

    /**
     * Saves the album or playlist [browseId] to the library, or takes it out.
     *
     * Written to the screen first and rolled back if YouTube refuses, for the
     * same reason [setLike] is: it is one tap on a page the user is looking at,
     * and a control that waits on a round trip before it changes reads as a tap
     * that missed.
     *
     * A page with no [DetailPage.library] is one YouTube never offered to save
     * — a local page, an auto-playlist, a generated mix — and the UI has no
     * control on it to have been tapped, so this is a no-op rather than a guess.
     */
    fun toggleLibrary(browseId: String) {
        val extension = com.music.streamx.data.extensions.ApkCatalogue.parseBrowseId(browseId)
        if (extension != null) {
            if (!requireSignIn()) return
            val config = SourceRegistry.config(extension.configId) ?: return
            val current = _detailStack.value.firstOrNull { it.browseId == browseId }?.library ?: return
            if(current.saved && !current.canRemove) return
            val identity = listenerKey()
            setSavedOnPage(browseId, !current.saved)
            viewModelScope.launch {
                runCatching { com.music.streamx.data.extensions.ApkCatalogue(config).save(extension.kind,extension.id,!current.saved) }
                    .onSuccess { if (identity == listenerKey()) { libraryStale = true; loadLibrary() } }
                    .onFailure { if (identity == listenerKey()) setSavedOnPage(browseId,current.saved) }
            }
            return
        }
        if (usingStreamX) return
        if (!requireSignIn()) return
        val current = _detailStack.value.firstOrNull { it.browseId == browseId }?.library ?: return
        val target = !current.saved
        setSavedOnPage(browseId, target)
        viewModelScope.launch {
            if (YtMusicRepository.setSaved(current.playlistId, target).isSuccess) {
                // The Library tab's Albums/Playlists shelf is now out of date.
                libraryStale = true
            } else {
                setSavedOnPage(browseId, current.saved)
            }
        }
    }

    /**
     * Subscribes to the artist page [browseId]'s channel, or unsubscribes.
     *
     * The release equivalent is [toggleLibrary], and this behaves the same way:
     * optimistic, rolled back on refusal, and a no-op on a page whose header
     * never offered a subscribe button — a signed-out response among them.
     */
    private val pendingArtistFollows = mutableSetOf<String>()
    fun toggleSubscription(browseId: String) {
        if (usingExtension) return
        if (usingStreamX) {
            if (!requireSignIn()) return
            val current = _detailStack.value.firstOrNull { it.browseId == browseId }?.subscription ?: return
            if (!pendingArtistFollows.add(browseId)) return
            val identity = listenerKey()
            val target = !current.subscribed
            setSubscribedOnPage(browseId, target)
            viewModelScope.launch {
                try {
                    val result = StreamXClient.result { StreamXClient.current().followArtist(browseId, target) }
                    if (identity != listenerKey()) return@launch
                    if (result.isSuccess) { libraryStale = true; loadLibrary() }
                    else setSubscribedOnPage(browseId, current.subscribed)
                } finally { pendingArtistFollows.remove(browseId) }
            }
            return
        }
        if (!requireSignIn()) return
        val current = _detailStack.value
            .firstOrNull { it.browseId == browseId }?.subscription ?: return
        val target = !current.subscribed
        setSubscribedOnPage(browseId, target)
        viewModelScope.launch {
            if (YtMusicRepository.setSubscribed(current.channelId, target).isSuccess) {
                // The Library tab's Subscriptions shelf is now out of date.
                libraryStale = true
            } else {
                setSubscribedOnPage(browseId, current.subscribed)
            }
        }
    }

    /** As [setSavedOnPage], for the artist header's subscribe button. */
    private fun setSubscribedOnPage(browseId: String, subscribed: Boolean) {
        _detailStack.value = _detailStack.value.map { page ->
            val subscription = page.subscription
            if (page.browseId != browseId || subscription == null) {
                page
            } else {
                page.copy(subscription = subscription.copy(subscribed = subscribed))
            }
        }
    }

    /**
     * Restates whether a page is saved. By id rather than by index: the user may
     * have pushed or popped pages while the write was in flight.
     */
    private fun setSavedOnPage(browseId: String, saved: Boolean) {
        _detailStack.value = _detailStack.value.map { page ->
            val library = page.library
            if (page.browseId != browseId || library == null) {
                page
            } else {
                page.copy(library = library.copy(saved = saved))
            }
        }
    }

    /**
     * The open track menu's account state, or null while it is still being
     * fetched. Only one menu can be open at a time, so one slot is enough.
     */
    private val _songMenu = MutableStateFlow<SongMenu?>(null)
    val songMenu: StateFlow<SongMenu?> = _songMenu.asStateFlow()

    private var songMenuJob: Job? = null

    /** In-flight liked-library continuation sync — see [syncLikedMusic]. */
    private var likedSyncJob: Job? = null

    /**
     * Loads the account state behind an opening track menu — the library
     * tokens, and any rating the response happens to state.
     *
     * The rating is only ever taken when it *adds* something: a LIKE or a
     * DISLIKE the library couldn't have told us, such as a disliked track or
     * one liked past the tenth page of Liked Music. An INDIFFERENT is
     * discarded.
     *
     * That asymmetry is not fussiness. This lookup reads a watch queue, and a
     * watch queue routinely renders a liked track with no rating on it at all;
     * believing that silence downgraded songs sitting in Liked Music to
     * "not liked" a beat after their menu opened — the label changing under
     * the user, with no request sent and nothing removed.
     */
    fun loadSongMenu(videoId: String?) {
        songMenuJob?.cancel()
        _songMenu.value = null
        if (videoId == null || !_signedIn.value || usingStreamX) return
        if (usingExtension) {
            val sourceKey = SourceRegistry.parseTrackKey(videoId) ?: return
            val config = SourceRegistry.config(sourceKey.first) ?: return
            val identity = listenerKey()
            songMenuJob = viewModelScope.launch {
                runCatching { com.music.streamx.data.extensions.ApkCatalogue(config).saved("track",sourceKey.second) }
                    .onSuccess { if (identity == listenerKey() && videoId !in extensionLikesPending) LikeState.set(videoId, if (it) LikeStatus.LIKE else LikeStatus.INDIFFERENT) }
            }
            return
        }
        songMenuJob = viewModelScope.launch {
            val menu = YtMusicRepository.songMenu(videoId).getOrNull() ?: return@launch
            _songMenu.value = menu
            LikeState.rememberStated(videoId, menu.likeStatus)
        }
    }

    /** The account's own playlists, for the picker and the library tab. */
    private val _playlists = MutableStateFlow<List<UserPlaylist>>(emptyList())
    val playlists: StateFlow<List<UserPlaylist>> = _playlists.asStateFlow()

    private val _playlistsLoading = MutableStateFlow(false)
    val playlistsLoading: StateFlow<Boolean> = _playlistsLoading.asStateFlow()
    private val _playlistsError = MutableStateFlow<String?>(null)
    val playlistsError: StateFlow<String?> = _playlistsError.asStateFlow()

    private fun youtubeSyncIdentity(): String? {
        val session = authStore.activeSession ?: return null
        if (!authStore.isSignedIn || Innertube.cookie != session.cookie) return null
        return "${session.accountId}:${authStore.activeProfileId ?: session.activeProfileId.orEmpty()}"
    }

    private fun youtubeSyncAccountName(): String {
        val session = authStore.activeSession ?: return ""
        return session.profiles.firstOrNull { it.profileId == (authStore.activeProfileId ?: session.activeProfileId) }?.name
            ?.ifBlank { null } ?: session.name.ifBlank { session.email }
    }

    internal val playlistTransfer = com.music.streamx.data.playlistsync.PlaylistTransferCoordinator(
        app, viewModelScope, ::youtubeSyncIdentity,
        onChanged = { libraryStale=true; loadPlaylists(); loadLibrary() },
    )

    val playlistSync = com.music.streamx.data.playlistsync.PlaylistSyncCoordinator(
        app, viewModelScope, ::youtubeSyncIdentity, ::youtubeSyncAccountName,
        onChanged = { if (!usingStreamX) { libraryStale = true; loadPlaylists() } },
    )

    /** Re-fetched rather than cached for the session: playlists are edited here. */
    private var playlistLoadJob: Job? = null
    fun loadPlaylists(song: Song? = null) {
        if (!_signedIn.value) return
        playlistLoadJob?.cancel()
        val identity = listenerKey()
        _playlistsLoading.value = true
        _playlistsError.value = null
        playlistLoadJob = viewModelScope.launch {
            (if(usingExtension) extensionRequest { it.playlists(song) } else if (usingStreamX) StreamXClient.result { StreamXClient.current().playlists() } else YtMusicRepository.userPlaylists()).onSuccess { if (identity == listenerKey()) _playlists.value = it }.onFailure { if (identity == listenerKey()) _playlistsError.value = it.friendly() }
            if (identity == listenerKey()) _playlistsLoading.value = false
        }
    }

    /**
     * The library feed's Playlists shelf, rewritten by [edit].
     *
     * Every playlist edit has to do this by hand, because the library tab reads
     * `_library` and nothing else — [playlists] is the picker's list, not the
     * tab's — so a rename that only updated that list left the card on screen
     * still bearing the old name.
     *
     * Re-fetching instead is what this replaces, and it did not work: YouTube's
     * `FEmusic_liked_playlists` is eventually consistent, and a fetch fired the
     * moment an edit returns reliably answers with the state from *before* it.
     * So the edit was applied, the feed denied it, and the denial is what
     * reached the screen — the bug this exists to fix. The re-fetch still
     * happens, via [libraryStale], once the tab is next opened and the feed has
     * caught up.
     *
     * A shelf that isn't there yet is created by [edit] returning rows for it
     * (a first playlist has no shelf to add to), and one left empty is dropped —
     * see [LibraryScreen], which draws the create tile with or without a shelf.
     */
    private fun editPlaylistShelf(edit: (List<ShelfItem>) -> List<ShelfItem>) {
        val page = (_library.value as? UiState.Success)?.data ?: return
        val existing = page.shelves.firstOrNull { it.title == YtMusicRepository.PLAYLISTS_SHELF }
        val items = edit(existing?.items.orEmpty())
        if (items == existing?.items) return
        val shelves = when {
            existing == null && items.isEmpty() -> return
            // No shelf yet: this is the account's first playlist, so the feed
            // has never had one to send. Leads the page, as the feed orders it.
            existing == null ->
                listOf(HomeShelf(YtMusicRepository.PLAYLISTS_SHELF, items)) + page.shelves
            // Emptied by deleting the last playlist. Dropped rather than left as
            // a heading over nothing; the create tile is drawn either way.
            items.isEmpty() -> page.shelves.filterNot { it === existing }
            else -> page.shelves.map { if (it === existing) existing.copy(items = items) else it }
        }
        _library.value = UiState.Success(page.copy(shelves = shelves))
    }

    /**
     * Restates a playlist's name everywhere it is currently drawn: its card in
     * the library, the picker's list, and its own open page — header and top
     * bar both, which read [DetailPage.title].
     */
    private fun setPlaylistTitle(playlist: UserPlaylist, title: String) {
        _playlists.value = _playlists.value.map {
            if (it.playlistId == playlist.playlistId) it.copy(title = title) else it
        }
        editPlaylistShelf { items ->
            items.map { if (it.browseId == playlist.browseId) it.copy(title = title) else it }
        }
        _detailStack.value = _detailStack.value.map {
            if (it.browseId == playlist.browseId) it.copy(title = title) else it
        }
    }

    /**
     * Adds [song] to every playlist in [playlists] — the picker's ticked rows —
     * and reports how each went, once, when they all have.
     *
     * Not optimistic, unlike a rating: the picker closes on the tap and there is
     * nothing left of it to update, and a playlist that shows a track it turned
     * out not to have taken is worse than one that shows it a moment late. The
     * playlist's own page is the exception — see [addOne].
     *
     * YouTube itself has no objection to a duplicate row, so whether the track
     * is already there is checked here, against the playlist's open page or a
     * fresh fetch of it, and a real duplicate is never sent.
     *
     * One at a time rather than all at once: each check reads the playlist it
     * is about, and a burst of parallel edits is exactly what YouTube answers
     * with a rate limit. The duplicate check is per playlist, so a track
     * already in one of them is skipped there and still added to the rest.
     */
    fun addToPlaylists(
        playlists: List<UserPlaylist>,
        song: Song,
        onResult: (added: Int, alreadyThere: Int, failed: Int) -> Unit = { _, _, _ -> },
    ) {
        if (!requireSignIn() || playlists.isEmpty()) return
        viewModelScope.launch {
            val outcomes = playlists.map { addOne(it, song) }
            onResult(
                outcomes.count { it == AddOutcome.ADDED },
                outcomes.count { it == AddOutcome.ALREADY_THERE },
                outcomes.count { it == AddOutcome.FAILED },
            )
        }
    }

    private enum class AddOutcome { ADDED, ALREADY_THERE, FAILED }

    private suspend fun addOne(playlist: UserPlaylist, song: Song): AddOutcome {
        if(usingExtension) {
            val identity=listenerKey()
            return extensionRequest { it.addToPlaylist(playlist.browseId,song) }.fold({
                if(identity==listenerKey()){libraryStale=true;appendToOpenPlaylist(playlist.browseId,song,null)}
                AddOutcome.ADDED
            },{AddOutcome.FAILED})
        }
        if (usingStreamX) {
            val identity = listenerKey()
            return StreamXClient.result { StreamXClient.current().addToPlaylist(playlist.playlistId, listOf(song.videoId)) }.fold(
                {
                    if (identity == listenerKey()) { libraryStale = true; appendToOpenPlaylist(playlist.browseId, song, song.videoId) }
                    AddOutcome.ADDED
                }, { AddOutcome.FAILED })
        }
        val openSongs = (_detailStack.value.firstOrNull { it.browseId == playlist.browseId }
            ?.songs as? UiState.Success)?.data
        val known = openSongs
            ?: YtMusicRepository.allSongs(playlist.browseId).getOrNull()
        if (known?.any { it.videoId == song.videoId } == true) return AddOutcome.ALREADY_THERE
        return YtMusicRepository.addToPlaylist(playlist.playlistId, listOf(song.videoId)).fold(
            onSuccess = { added ->
                com.music.streamx.data.playlistsync.PlaylistTransferEvents.changed("youtube")
                libraryStale = true
                // The playlist's page may be open behind the picker — it is
                // reachable from a row's own menu on it — so the track goes
                // into it for the same reason [addSuggestedSong] does.
                appendToOpenPlaylist(playlist.browseId, song, added[song.videoId])
                AddOutcome.ADDED
            },
            onFailure = { AddOutcome.FAILED },
        )
    }

    /**
     * Creates a playlist, seeded with [song] when the flow started from a
     * track's menu. Extensions create and add separately, and report partial
     * success so a failed add does not repeat playlist creation.
     */
    fun createPlaylist(title: String, privacy: PlaylistPrivacy, song: Song? = null, coverUrl: String? = null, onResult: (Result<Unit>) -> Unit = {}) {
        if (!requireSignIn()) return
        val name = title.trim().ifBlank { text(R.string.new_playlist) }
        val identity = listenerKey()
        viewModelScope.launch {
            if(usingExtension) {
                if(identity!=listenerKey())return@launch
                extensionRequest { it.createPlaylist(name) }.fold({ createdPlaylist ->
                    val (key,warning)=createdPlaylist
                    if(identity!=listenerKey())return@fold
                    val created=UserPlaylist(key,name,"",coverUrl ?: song?.thumbnailUrl)
                    coverUrl?.takeIf { it.startsWith("file:") }?.let { artwork ->
                        getApplication<Application>().getSharedPreferences("playlist-artwork",0).edit().putString(created.browseId,artwork).apply()
                    }
                    _playlists.value=listOf(created)+_playlists.value.filterNot {it.playlistId==key}
                    val added=song==null || addOne(created,song)==AddOutcome.ADDED
                    libraryStale=true;loadLibrary()
                    onResult(when { !added -> Result.failure(IllegalStateException("Playlist created, but the track could not be added. Select the existing playlist to retry.")); warning.isNotBlank() -> Result.failure(IllegalStateException(warning)); else -> Result.success(Unit) })
                }, {if(identity==listenerKey())onResult(Result.failure(it))})
                return@launch
            }
            (if (usingStreamX) StreamXClient.result { StreamXClient.current().createPlaylist(name, listOfNotNull(song?.videoId), coverUrl) }
            else YtMusicRepository.createPlaylist(title = name, privacy = privacy, videoIds = listOfNotNull(song?.videoId))).fold(
                onSuccess = { playlistId ->
                    if (identity != listenerKey()) return@fold
                    // Nothing to look up for a playlist this account has just
                    // made: it is the owner by construction, so its card is
                    // editable the moment it appears rather than one request
                    // after someone holds it.
                    setPlaylistOwned("VL$playlistId", true)
                    libraryStale = true
                    val created = UserPlaylist(
                        playlistId = playlistId,
                        title = name,
                        // Only what this request itself establishes. Both
                        // surfaces that draw it leave a blank one out, so an
                        // unseeded playlist gets a card of just its name rather
                        // than a guess at what the feed will call it.
                        subtitle = if (song != null) "1 song" else "",
                        thumbnailUrl = coverUrl?.takeIf { it.isNotBlank() } ?: song?.thumbnailUrl,
                    )
                    coverUrl?.takeIf { it.startsWith("file:") }?.let { artwork ->
                        getApplication<Application>().getSharedPreferences("playlist-artwork",0).edit().putString(created.browseId,artwork).apply()
                    }
                    // Drawn from what was just sent rather than waited for: the
                    // library feed does not have this playlist yet, and the
                    // fetch that used to run here answered without it — see
                    // [editPlaylistShelf]. Leads the shelf because it is the
                    // newest, which is the order the feed itself comes in.
                    _playlists.value = listOf(created) +
                        _playlists.value.filterNot { it.playlistId == created.playlistId }
                    editPlaylistShelf { items ->
                        listOf(
                            ShelfItem(
                                title = created.title,
                                subtitle = created.subtitle,
                                thumbnailUrl = created.thumbnailUrl,
                                videoId = null,
                                browseId = created.browseId,
                            ),
                        ) + items.filterNot { it.browseId == created.browseId }
                    }
                },
                onFailure = {},
            )
        }
    }

    fun createPlaylistWithVideoIds(
        title: String,
        privacy: PlaylistPrivacy,
        videoIds: List<String>,
        songs: List<Song> = emptyList(),
        /** [savedLocally] is true when the playlist went to this device, not YouTube Music. */
        onResult: ((browseId: String?, title: String, savedLocally: Boolean) -> Unit)? = null,
    ) {
        val name = title.trim().ifBlank { text(R.string.new_playlist) }
        viewModelScope.launch {
            if (usingStreamX && _signedIn.value) {
                StreamXClient.result { StreamXClient.current().createPlaylist(name, videoIds) }.fold(
                    { id -> libraryStale = true; loadPlaylists(); onResult?.invoke("VL$id", name, false) },
                    { onResult?.invoke(null, name, false) },
                )
                return@launch
            }
            if (!usingStreamX && authStore.isSignedIn) {
                val initialBatch = videoIds.take(50)
                YtMusicRepository.createPlaylist(
                    title = name,
                    privacy = privacy,
                    videoIds = initialBatch,
                ).fold(
                    onSuccess = { playlistId ->
                        if (videoIds.size > 50) {
                            videoIds.drop(50).chunked(50).forEach { chunk ->
                                YtMusicRepository.addToPlaylist(playlistId, chunk)
                            }
                        }
                        setPlaylistOwned("VL$playlistId", true)
                        libraryStale = true
                        val created = UserPlaylist(
                            playlistId = playlistId,
                            title = name,
                            subtitle = "${videoIds.size} songs",
                            thumbnailUrl = null,
                        )
                        _playlists.value = listOf(created) +
                            _playlists.value.filterNot { it.playlistId == created.playlistId }
                        editPlaylistShelf { items ->
                            listOf(
                                ShelfItem(
                                    title = created.title,
                                    subtitle = created.subtitle,
                                    thumbnailUrl = created.thumbnailUrl,
                                    videoId = null,
                                    browseId = created.browseId,
                                ),
                            ) + items.filterNot { it.browseId == created.browseId }
                        }
                        onResult?.invoke(created.browseId, created.title, false)
                    },
                    onFailure = {
                        val local = com.music.streamx.data.spotify.LocalPlaylistStore.savePlaylist(name, songs)
                        onResult?.invoke(local.browseId, local.title, true)
                    },
                )
            } else {
                val local = com.music.streamx.data.spotify.LocalPlaylistStore.savePlaylist(name, songs)
                onResult?.invoke(local.browseId, local.title, true)
            }
        }
    }

    /**
     * Drops [song] from the playlist page it is being read on, and takes the
     * row out from under the reader rather than waiting for a re-fetch.
     */
    fun removeFromPlaylist(browseId: String, song: Song) {
        com.music.streamx.data.extensions.ApkCatalogue.parseBrowseId(browseId)?.let {target ->
            if(!requireSignIn())return
            val config=SourceRegistry.config(target.configId) ?: return
            val identity=listenerKey()
            viewModelScope.launch {
                try {
                    com.music.streamx.data.extensions.ApkCatalogue(config).removePlaylistTrack(browseId,song)
                    if(identity!=listenerKey())return@launch
                    libraryStale=true
                    val refreshed=com.music.streamx.data.extensions.ApkCatalogue(config).detail(browseId)
                    if(identity==listenerKey()) _detailStack.value=_detailStack.value.map {if(it.browseId==browseId)refreshed else it}
                } catch(cancelled: kotlinx.coroutines.CancellationException) {throw cancelled}
                catch(error: Exception) {if(identity==listenerKey())android.widget.Toast.makeText(getApplication(),error.friendly(),android.widget.Toast.LENGTH_LONG).show()}
            }
            return
        }
        val setVideoId = song.setVideoId ?: if (usingStreamX) song.videoId else return
        if (!requireSignIn()) return
        val playlistId = browseId.removePrefix("VL")
        val identity = listenerKey()
        viewModelScope.launch {
            if (identity != listenerKey()) return@launch
            (if (usingStreamX) StreamXClient.result { StreamXClient.current().removeFromPlaylist(playlistId, song.videoId) }
            else YtMusicRepository.removeFromPlaylist(playlistId, listOf(setVideoId to song.videoId))).fold(
                onSuccess = {
                    if (identity != listenerKey()) return@fold
                    libraryStale = true
                    _detailStack.value = _detailStack.value.map { page ->
                        val songs = (page.songs as? UiState.Success)?.data
                        if (page.browseId != browseId || songs == null) {
                            page
                        } else {
                            page.copy(
                                songs = UiState.Success(
                                    songs.filterNot { it.setVideoId == setVideoId || (usingStreamX && it.videoId == song.videoId) },
                                ),
                            )
                        }
                    }
                },
                onFailure = {},
            )
        }
    }

    /**
     * Adds one of [DetailPage.suggestedSongs] to the playlist it was suggested
     * for: out of that section, and into the track list above it.
     *
     * Both halves, because either alone is a worse answer than doing nothing.
     * Only removing it — which is what this used to do — reads as the track
     * having been discarded rather than added: it leaves the Suggested list and
     * turns up nowhere, and the playlist it was added to looks unchanged until
     * the page is closed and reopened. Only adding it would leave YouTube still
     * suggesting a track that is now in the playlist.
     *
     * The row goes in complete, per-entry id included, because
     * [YtMusicRepository.addToPlaylist] reports the one it was just filed
     * under — so "Remove from this playlist" works on it immediately rather
     * than after a re-fetch. A response that named no id still adds the row;
     * it just can't offer to take it back out yet.
     */
    fun addSuggestedSong(browseId: String, song: Song) {
        if (!requireSignIn()) return
        val playlistId = browseId.removePrefix("VL")
        viewModelScope.launch {
            YtMusicRepository.addToPlaylist(playlistId, listOf(song.videoId)).fold(
                onSuccess = { added ->
                    com.music.streamx.data.playlistsync.PlaylistTransferEvents.changed("youtube")
                    libraryStale = true
                    _detailStack.value = _detailStack.value.map { page ->
                        if (page.browseId != browseId) {
                            page
                        } else {
                            page.copy(
                                suggestedSongs = page.suggestedSongs
                                    .filterNot { it.videoId == song.videoId },
                            )
                        }
                    }
                    appendToOpenPlaylist(browseId, song, added[song.videoId])
                },
                onFailure = {},
            )
        }
    }

    /**
     * Puts [song] at the end of the playlist page at [browseId], if that page
     * is open — where YouTube itself puts it, so the order survives the next
     * fetch.
     *
     * An empty playlist counts as open: it renders as an empty-state message, and the
     * first track added to one has to replace that message rather than be
     * dropped for want of a list to join. Only that message, though — any other
     * error is a page that failed to load, whose real contents are unknown, and
     * answering it with a one-track listing would be a playlist invented out of
     * a network failure. A page still loading is left alone too: the fetch in
     * flight is newer than this and will land with the addition already in it.
     */
    private fun appendToOpenPlaylist(browseId: String, song: Song, setVideoId: String?) {
        val added = song.copy(setVideoId = setVideoId)
        _detailStack.value = _detailStack.value.map { page ->
            if (page.browseId != browseId) return@map page
            val songs = when (val state = page.songs) {
                is UiState.Success -> state.data
                is UiState.Error -> if (state.message == text(R.string.no_tracks_here)) emptyList() else return@map page
                UiState.Loading -> return@map page
            }
            // Already there — a track added twice is two real entries on
            // YouTube's side, but a duplicate row from a double tap is not
            // something the user asked for.
            if (songs.any { it.videoId == song.videoId }) return@map page
            page.copy(
                songs = UiState.Success(
                    songs + added.copy(
                        thumbnailUrl = added.thumbnailUrl ?: page.thumbnailUrl,
                    ),
                ),
            )
        }
    }

    /**
     * Renames a playlist, and says so everywhere it is named — see
     * [setPlaylistTitle]. Renaming is nearly always done from the playlist's
     * own page or its card, so there is always something on screen still
     * showing the old name.
     */
    fun renamePlaylist(playlist: UserPlaylist, title: String) {
        if (!requireSignIn()) return
        val name = title.trim()
        if (name.isBlank() || name == playlist.title) return
        val identity = listenerKey()
        viewModelScope.launch {
            if (identity != listenerKey()) return@launch
            (if (usingStreamX) StreamXClient.result { StreamXClient.current().renamePlaylist(playlist.playlistId, name) } else YtMusicRepository.renamePlaylist(playlist.playlistId, name)).fold(
                onSuccess = {
                    if (identity != listenerKey()) return@fold
                    setPlaylistTitle(playlist, name)
                    libraryStale = true
                },
                onFailure = {},
            )
        }
    }

    /**
     * The playlist's entries as they stand on YouTube, for the reorder sheet —
     * see [YtMusicRepository.playlistEntries] for why not the open page's list.
     */
    fun loadPlaylistEntries(playlist: UserPlaylist, onResult: (Result<List<Song>>) -> Unit) {
        if (!requireSignIn()) return onResult(Result.failure(IllegalStateException("signed out")))
        viewModelScope.launch {
            onResult(if (usingStreamX) StreamXClient.result {
                (StreamXClient.current().detail(playlist.browseId, playlist.title, playlist.subtitle, playlist.thumbnailUrl).songs as UiState.Success).data
            } else YtMusicRepository.playlistEntries(playlist.browseId))
        }
    }

    /**
     * Saves a new running order for [playlist], sent as the fewest moves that
     * get from [original] to [reordered], and puts the open page — if this
     * playlist's is — into that order straight away rather than after a
     * re-fetch the feed may answer with the old order.
     */
    fun reorderPlaylist(
        playlist: UserPlaylist,
        original: List<Song>,
        reordered: List<Song>,
        onResult: (Boolean) -> Unit = {},
    ) {
        if (!requireSignIn() || usingStreamX) return onResult(false)
        viewModelScope.launch {
            YtMusicRepository.reorderPlaylist(
                playlist.playlistId,
                current = original.mapNotNull { it.setVideoId },
                target = reordered.mapNotNull { it.setVideoId },
            ).fold(
                onSuccess = {
                    libraryStale = true
                    _detailStack.value = _detailStack.value.map { page ->
                        if (page.browseId != playlist.browseId || page.songs !is UiState.Success) {
                            page
                        } else {
                            page.copy(songs = UiState.Success(reordered.withArtwork(page.thumbnailUrl)))
                        }
                    }
                    onResult(true)
                },
                onFailure = { onResult(false) },
            )
        }
    }

    fun deletePlaylist(playlist: UserPlaylist) {
        if (!requireSignIn()) return
        val identity = listenerKey()
        viewModelScope.launch {
            if (identity != listenerKey()) return@launch
            // A plugin's playlist is deleted by its own extension, through the same
            // library-removal call its "Remove from library" uses.
            val plugin = com.music.streamx.data.extensions.ApkCatalogue.parseBrowseId(playlist.browseId)
            (when {
                plugin != null -> runCatching {
                    val config = SourceRegistry.config(plugin.configId) ?: error("Extension is unavailable")
                    com.music.streamx.data.extensions.ApkCatalogue(config).removeLibraryItem(playlist.browseId)
                }
                usingStreamX -> StreamXClient.result { StreamXClient.current().deletePlaylist(playlist.playlistId) }
                else -> YtMusicRepository.deletePlaylist(playlist.playlistId)
            }).fold(
                onSuccess = {
                    if (identity != listenerKey()) return@fold
                    _playlists.value = _playlists.value
                        .filterNot { it.playlistId == playlist.playlistId }
                    // The card in the library tab, which is the surface the
                    // deletion was almost certainly ordered from — and which the
                    // re-fetch that used to stand in for this left in place; see
                    // [editPlaylistShelf].
                    editPlaylistShelf { items ->
                        items.filterNot { it.browseId == playlist.browseId }
                    }
                    // Its page may be the one open; a deleted playlist has
                    // nothing left to show.
                    _detailStack.value = _detailStack.value
                        .filterNot { it.browseId == playlist.browseId }
                    libraryStale = true
                },
                // Said, not swallowed: a delete that silently does nothing leaves
                // the playlist on screen with no hint why.
                onFailure = {
                    if (plugin != null && identity == listenerKey())
                        android.widget.Toast.makeText(getApplication(), it.friendly(), android.widget.Toast.LENGTH_LONG).show()
                },
            )
        }
    }

    /**
     * Whether [browseId] is a playlist this account can be asked to edit.
     *
     * Only ever yes for a playlist [playlistOwned] has confirmed the account
     * made. "In this account's library" is not the same thing and cannot stand
     * in for it: `FEmusic_liked_playlists` lists a playlist saved from someone
     * else in exactly the shape it lists one this account created, so a lookup
     * in [playlists] alone called a stranger's playlist editable and the menus
     * offered Rename and Delete on it — neither of which YouTube would have
     * honoured.
     *
     * Strict rather than permissive-until-proven, so that every surface gives
     * the same answer for the same playlist. The permissive version was right on
     * a playlist's own page — where the page load supplies the answer — and
     * wrong on a card until that page had been opened once, which is a menu that
     * changes its mind about what a playlist is depending on where it is held.
     */
    fun editablePlaylist(browseId: String?): UserPlaylist? {
        if (browseId == null || (!usingStreamX && _playlistOwned.value[browseId] != true)) return null
        return _playlists.value.firstOrNull { it.browseId == browseId }
    }

    /**
     * Which playlists in this account's library the account actually made, by
     * browse id — see
     * [com.music.streamx.data.innertube.InnertubeParser.parsePlaylistOwned].
     * An id absent from the map is one nothing has asked about yet, which is not
     * the same as a no.
     *
     * Observable, because the answer routinely arrives after whatever wanted it
     * is already on screen: a card's menu opens with nothing fetched, and the
     * rows that depend on this appear as [resolvePlaylistOwnership] answers.
     */
    private val _playlistOwned = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val playlistOwned: StateFlow<Map<String, Boolean>> = _playlistOwned.asStateFlow()

    /**
     * Finds out who made the playlist at [browseId], if it isn't already known.
     *
     * Only the playlist's own page states this, so a surface that has no page —
     * a card in the library, a search result — has to ask for one. Which is why
     * this is on demand rather than swept up front: the alternative is a request
     * per playlist every time the library loads, for a question most of them
     * will never be asked.
     *
     * Silent about anything that isn't a playlist in this account's library.
     * Nothing else can be renamed or deleted whatever the answer, so asking
     * would be a request spent to rule out what was never on offer.
     */
    fun resolvePlaylistOwnership(browseId: String?) {
        if (usingExtension) return
        if (!_signedIn.value || browseId == null) return
        if (browseId in _playlistOwned.value || browseId in ownershipInFlight) return
        if (_playlists.value.none { it.browseId == browseId }) return
        if (usingStreamX) { setPlaylistOwned(browseId, true); return }
        ownershipInFlight += browseId
        viewModelScope.launch {
            YtMusicRepository.playlistOwned(browseId).onSuccess { owned ->
                if (owned != null) setPlaylistOwned(browseId, owned)
            }
            // Released either way. A failed lookup that stayed marked would
            // never be retried, leaving Rename off the user's own playlist for
            // the rest of the session over one dropped request.
            ownershipInFlight -= browseId
        }
    }

    /** Guards against a second lookup while the first is still out. */
    private val ownershipInFlight = mutableSetOf<String>()

    private fun setPlaylistOwned(browseId: String, owned: Boolean) {
        _playlistOwned.value = _playlistOwned.value + (browseId to owned)
    }


    /**
     * Guards every account write. All of them are signed-in-only, and the UI
     * hides them for guests — this is the backstop for a session that expired
     * between the menu opening and the tap.
     */
    private fun requireSignIn(): Boolean = _signedIn.value

    /**
     * Whether the library needs re-fetching. Set by every write above and
     * acted on when the tab is next opened, for the same reason [homeStale]
     * exists: rearranging a page under whoever is reading it is worse than
     * showing it a moment out of date.
     */
    private var libraryStale = false

    /** Call when the library tab becomes visible. */
    fun onLibraryShown() {
        loadPlaylists()
        if (!libraryStale) return
        libraryStale = false
        if (_library.value is UiState.Success) refresh(Feed.LIBRARY)
    }

    init {
        SearchHistory.setScope(searchHistoryScope())
        viewModelScope.launch {
            com.music.streamx.data.cloudstream.CloudLibrary.revision.drop(1).collect {
                if (usingCloud) { loadLibrary(); loadPlaylists() }
            }
        }
        viewModelScope.launch {
            // The all-sources playlists switch reloads Library with or without them.
            AppSettings.multiSourcePlaylists.drop(1).collect { if (_library.value !is UiState.Loading) refresh(Feed.LIBRARY) }
        }
        viewModelScope.launch {
            // Turning an extra Home feed on or off reloads Home with its section.
            combine(SourceRegistry.extraFeedIds, SourceRegistry.multiFeed) { ids, on -> ids to on }.drop(1).collect {
                if (_home.value !is UiState.Loading) refresh(Feed.HOME)
            }
        }
        viewModelScope.launch {
            combine(SourceRegistry.catalogue, StreamXSession.state, SourceRegistry.selectedExtensionId, com.music.streamx.data.cloudstream.CloudStream.revision) { kind, session, extension, _ ->
                Triple(kind, if (kind == SourceKind.CLOUDSTREAM) com.music.streamx.data.cloudstream.CloudLibrary.identity() else if (kind == SourceKind.STREAMX) session.scope else "youtube", extension)
            }.distinctUntilChanged().drop(1).collect {
                _signedIn.value = if (usingExtension) false else if (usingStreamX) (usingCloud || StreamXSession.state.value.personalAccount) else authStore.isSignedIn
                clearListenerState()
                clearDetail()
                _selectedMoodGenre.value = null
                searchCache.evictAll()
                newestRequestId.incrementAndGet()
                searchSession = null
                _searchLoadingMore.value = false
                _results.value = null
                _suggestions.value = emptyList()
                _typeaheadResults.value = emptyList()
                if (usingExtension) {
                    _searchSource.value = SearchSource.EXTENSIONS
                    if (_filter.value !in listOf(SearchFilter.ALL, SearchFilter.SONGS)) _filter.value = SearchFilter.ALL
                    runSearch()
                } else if (_searchSource.value != SearchSource.LIBRARY) {
                    _searchSource.value = if (usingStreamX) SearchSource.STREAMX else SearchSource.YOUTUBE
                    if (usingStreamX && _filter.value == SearchFilter.VIDEOS) _filter.value = SearchFilter.ALL
                    runSearch()
                }
                SearchHistory.setScope(searchHistoryScope())
                loadHome()
                loadExplore()
                loadLibrary()
                loadAccount()
                loadPlaylists()
            }
        }
        startSearchPipeline()
        startSuggestPipeline()
        startTypeaheadMediaPipeline()
        loadHome()
        loadExplore()
        if (_signedIn.value || usingExtension) {
            loadLibrary()
            loadAccount()
            loadPlaylists()
        }
        viewModelScope.launch {
            // drop(1): the current value is just the count so far, not a play.
            PlaybackTracker.registeredPlays.drop(1).collect { homeStale = true }
        }
        viewModelScope.launch {
            BoundedContextRequests.snapshot.collect { snapshot ->
                snapshot?.let(::applyBoundedContextSnapshot)
            }
        }
        viewModelScope.launch {
            AppSettings.filterNonMusicAudio.drop(1).collect {
                if (_detailStack.value.any { page -> page.browseId == "local:all" }) {
                    reloadLocalDetail("local:all")
                }
            }
        }
        viewModelScope.launch {
            // Emptied from Settings while the folder sits open underneath.
            AudioCache.contentsChanged.drop(1).collect {
                if (_detailStack.value.any { page -> page.browseId == CACHE_FOLDER_BROWSE_ID }) {
                    reloadLocalDetail(CACHE_FOLDER_BROWSE_ID)
                }
            }
        }
        viewModelScope.launch {
            AppSettings.webdavUrl.drop(1).collect {
                reloadRemoteDetail(com.music.streamx.data.webdav.WebDavConfig.BROWSE_ID)
            }
        }
        viewModelScope.launch {
            combine(
                AppSettings.smbHost,
                AppSettings.smbShare,
                AppSettings.smbBasePath,
                AppSettings.smbUsername,
                AppSettings.smbPassword,
            ) { fields -> fields.toList() }
                .drop(1)
                .debounce(300)
                .collect { reloadRemoteDetail(com.music.streamx.data.smb.SmbConfig.BROWSE_ID) }
        }
        viewModelScope.launch {
            // A leftover APK only means "Install Now" for the session that
            // downloaded it — see AppUpdateChecker.clearCache.
            AppUpdateChecker.clearCache(getApplication())
            AppUpdateChecker.check()
        }
    }

    /**
     * Whether a play has been registered since the home feed was last fetched.
     *
     * The feed leads with listening history, so it's out of date the moment a
     * track starts — but re-fetching there would rearrange the page under
     * whoever is reading it, and the tab is usually in the background anyway.
     * It's re-fetched when the tab is next opened instead.
     */
    private var homeStale = false

    /** Call when the home tab becomes visible. */
    fun onHomeShown() {
        if (!homeStale) return
        homeStale = false
        // A first load already in flight will pick the new play up by itself.
        if (_home.value is UiState.Success) refresh(Feed.HOME)
    }

    private suspend fun <T> extensionRequest(block: suspend (com.music.streamx.data.extensions.ApkCatalogue) -> T): Result<T> {
        return try {
            val config = SourceRegistry.selectedExtensionId.value?.let(SourceRegistry::config)
                ?: error("Select an installed source extension in Sources")
            Result.success(block(com.music.streamx.data.extensions.ApkCatalogue(config)))
        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (e: Exception) { Result.failure(e) }
    }

    fun refreshSelectedExtension() {
        if (!usingExtension) return
        extensionRevision.incrementAndGet()
        searchCache.evictAll()
        newestRequestId.incrementAndGet()
        _account.value = null
        loadAccount(); loadHome(); loadExplore(); loadLibrary()
        if (_searchSource.value == SearchSource.EXTENSIONS && _query.value.isNotBlank()) runSearch()
    }

    private suspend fun fetchExtensionHome(identity: String?) {
        val generation = homeLoadGeneration.get()
        val result = extensionRequest { it.feed() }
        if (!isCurrentHomeLoad(identity, generation)) return
        _home.value = result.fold({ shelves ->
            if (shelves.isEmpty()) UiState.Error("This account has no feed collections yet") else UiState.Success(shelves)
        }, { UiState.Error(it.friendly()) })
        _homePendingShelves.value = 0
        _homeRecentlyPlayedLoading.value = false
        _homeLoadingMore.value = false
    }

    private fun loadAccount() {
        if (usingExtension) {
            val identity = listenerKey()
            viewModelScope.launch {
                val next = extensionRequest { it.account() }
                if (identity == listenerKey()) {
                    _account.value = next.getOrNull()
                    _signedIn.value = next.isSuccess
                }
            }
            return
        }
        if (usingCloud) {
            val cloudAccount = com.music.streamx.data.cloudstream.CloudStream.read(com.music.streamx.data.cloudstream.CloudStream.PROFILE)
            _account.value = Account(cloudAccount.accountName.ifBlank { "CloudStream" }, "", cloudAccount.accountPhoto.ifBlank { null })
            return
        }
        if (usingStreamX) {
            val session = StreamXSession.state.value
            _account.value = if (session.personalAccount) Account(session.name, "", session.photo) else null
            if (session.personalAccount) viewModelScope.launch {
                val result = StreamXClient.result { StreamXClient.current().account() }
                if (listenerKey() == "streamx:${session.scope}") result.onSuccess { _account.value = it }
            }
            return
        }
        val identity = listenerKey()
        viewModelScope.launch {
            val account = YtMusicRepository.account().getOrNull()
            if (identity != listenerKey()) return@launch
            _account.value = account
            // A channel picked in the in-app browser arrives as ids and nothing
            // else — the page's `ytcfg` never says what the channel is called.
            // The account menu, asked *as* that channel, answers with its name,
            // which is what the settings row needs to show.
            if (account != null && _selectedChannelKey.value != null) {
                _selectedChannelName.value = account.name
                val accountId = _activeAccountId.value
                val profileId = _activeProfileId.value
                val session = accountId?.let { id -> authStore.sessions.firstOrNull { it.accountId == id } }
                val updated = session?.let { saved -> saved.copy(
                    name = account.name, email = account.email,
                    profiles = saved.profiles.map { profile ->
                        if (profile.profileId == profileId) profile.copy(
                            name = account.name, handle = account.email, avatar = account.thumbnailUrl,
                        ) else profile
                    },
                ) }
                if (updated != null) {
                    authStore.upsertSession(updated, activate = false)
                    _googleAccounts.value = authStore.sessions
                }
            }
        }
    }

    /**
     * A feed that can be pulled down to refresh. Tracked per feed rather than
     * as one flag: a pull on Library while Home is still refreshing in the
     * background shouldn't leave the wrong tab showing a loader.
     */
    enum class Feed { HOME, EXPLORE, LIBRARY }

    private val _refreshing = MutableStateFlow(emptySet<Feed>())
    val refreshing: StateFlow<Set<Feed>> = _refreshing.asStateFlow()

    /**
     * Re-fetches [feed] in place. Unlike the `load*` entry points this leaves
     * the current content on screen rather than dropping back to the loading
     * state — a refresh that swapped the page for a spinner would be a worse
     * experience than the stale content it replaces.
     */
    fun refresh(feed: Feed) {
        if (feed in _refreshing.value) return
        if (feed == Feed.LIBRARY && !_signedIn.value && !usingExtension) return
        val identity = listenerKey()
        _refreshing.value = _refreshing.value + feed
        viewModelScope.launch {
            when (feed) {
                Feed.HOME -> refreshHome(identity)
                Feed.EXPLORE -> fetchExplore()
                Feed.LIBRARY -> { fetchLibrary(identity); appendSourcePlaylists(identity) }
            }
            _refreshing.value = _refreshing.value - feed
        }
    }

    /**
     * Asks the current source for its Radio page. A source without one answers
     * with an error, and the tab stays away; one that had a page keeps showing
     * it through a failed refresh rather than losing the tab.
     */
    fun loadRadio() {
        if (!usingExtension) {
            _extensionRadio.value = null
            return
        }
        val identity = listenerKey()
        viewModelScope.launch {
            val result = extensionRequest { it.feedPage("radio") }
            if (identity != listenerKey()) return@launch
            _extensionRadio.value = result.fold(
                { UiState.Success(it.shelves) },
                { _extensionRadio.value?.takeIf { state -> state is UiState.Success } },
            )
        }
    }

    fun loadExplore() {
        _extensionSearchLanding.value = emptyList()
        _extensionFullSearch.value = false
        _extensionBrowseTitle.value = "Browse"
        _extensionBrowseGrid.value = true
        if (usingExtension) _extensionBrowse.value = UiState.Loading
        _explore.value = UiState.Loading
        viewModelScope.launch { fetchExplore() }
    }

    private suspend fun fetchExplore() {
        val identity = listenerKey()
        if (usingExtension) {
            val result = extensionRequest { it.feedPage("browse") }
            if (identity == listenerKey()) {
                _extensionBrowse.value = result.fold({ UiState.Success(it.shelves) }, { UiState.Error(it.friendly()) })
                result.getOrNull()?.let { page ->
                    _extensionBrowseTitle.value = page.title.ifBlank { "Browse" }
                    _extensionBrowseGrid.value = page.categoryGrid
                }
            }
            val landing = extensionRequest { it.feedPage("search_landing") }
            if (identity == listenerKey()) {
                _extensionSearchLanding.value = landing.getOrNull()?.shelves?.flatMap { it.items }.orEmpty()
                _extensionFullSearch.value = landing.getOrNull()?.fullSearch == true
            }
            return
        }
        if (usingStreamX) {
            val result = StreamXClient.result { StreamXClient.current().explore() }
            if (identity == listenerKey()) _explore.value = result.fold({ UiState.Success(it) }, { UiState.Error(it.friendly()) })
            return
        }
        val state = YtMusicRepository.moodAndGenres().fold(
            onSuccess = { sections ->
                if (sections.isEmpty()) {
                    UiState.Error(text(R.string.nothing_to_explore))
                } else {
                    UiState.Success(sections)
                }
            },
            onFailure = { UiState.Error(it.friendly()) },
        )
        if (identity != listenerKey()) return
        _explore.value = state
        (state as? UiState.Success)?.data?.let(::loadMoodGenreArtwork)
    }

    /**
     * Category buttons do not carry thumbnails themselves. Resolve a small
     * number at a time from the shelves they open, so the grid gains real art
     * without saturating the browse client or delaying the category list.
     */
    private fun loadMoodGenreArtwork(sections: List<MoodGenreSection>) {
        viewModelScope.launch {
            val limiter = Semaphore(4)
            coroutineScope {
                sections.flatMap(MoodGenreSection::items).forEach { item ->
                    launch {
                        val artwork = limiter.withPermit {
                            YtMusicRepository.moodGenreArtwork(item.browseId, item.params).getOrNull()
                        } ?: return@launch
                        val current = (_explore.value as? UiState.Success)?.data ?: return@launch
                        _explore.value = UiState.Success(current.map { section ->
                            section.copy(items = section.items.map { currentItem ->
                                if (currentItem.browseId == item.browseId && currentItem.params == item.params) {
                                    currentItem.copy(thumbnailUrl = artwork)
                                } else {
                                    currentItem
                                }
                            })
                        })
                    }
                }
            }
        }
    }

    fun openMoodGenre(item: MoodGenre) {
        if (item.browseId.startsWith(StreamXClient.BROWSE_PREFIX)) {
            openDetail(item.browseId, item.title, thumbnailUrl = item.thumbnailUrl, type = if (item.browseId.contains(":album:")) BrowseType.ALBUM else BrowseType.ARTIST)
            return
        }
        _selectedMoodGenre.value = item
        _moodGenreShelves.value = UiState.Loading
        viewModelScope.launch {
            _moodGenreShelves.value = YtMusicRepository.moodGenreShelves(item.browseId, item.params).fold(
                onSuccess = { shelves ->
                    if (shelves.isEmpty()) UiState.Error(text(R.string.nothing_to_explore))
                    else UiState.Success(shelves)
                },
                onFailure = { UiState.Error(it.friendly()) },
            )
        }
    }

    fun closeMoodGenre(): Boolean {
        if (_selectedMoodGenre.value == null) return false
        _selectedMoodGenre.value = null
        return true
    }

    /** Tapping a tab should leave any pushed page behind. */
    fun clearDetail() {
        if (_detailStack.value.isNotEmpty()) _detailStack.value = emptyList()
    }

    private suspend fun fetchStreamXHome(identity: String?) {
        val generation = homeLoadGeneration.get()
        val client = runCatching { StreamXClient.current() }.getOrElse {
            if (isCurrentHomeLoad(identity, generation)) _home.value = UiState.Error(it.friendly())
            return
        }
        val next = StreamXClient.result { client.home() }
        if (!isCurrentHomeLoad(identity, generation)) return
        _home.value = next.fold({ UiState.Success(it) }, { UiState.Error(it.friendly()) })
        _homePendingShelves.value = 0
        _homeRecentlyPlayedLoading.value = false
        homeContinuation = null
        if (next.isFailure) return
        _homePendingShelves.value = 3
        try {
            val artwork = (_home.value as? UiState.Success)?.data.orEmpty().flatMap { it.items }.mapNotNull { it.thumbnailUrl }.distinct()
            val sections = client.homeSections(artwork)
            if (isCurrentHomeLoad(identity, generation)) {
                val current = (_home.value as? UiState.Success)?.data.orEmpty()
                _home.value = UiState.Success((current + sections).distinctBy { it.title })
            }
        } finally {
            if (isCurrentHomeLoad(identity, generation)) _homePendingShelves.value = 0
        }
    }

    fun loadHome() {
        if (usingExtension) {
            val identity = listenerKey()
            homeLoadGeneration.incrementAndGet()
            homeContinuation = null
            _home.value = UiState.Loading
            _homePendingShelves.value = 0
            _homeRecentlyPlayedLoading.value = false
            _homeLoadingMore.value = false
            viewModelScope.launch { fetchExtensionHome(identity) }
            return
        }
        if (usingStreamX) {
            val identity = listenerKey()
            homeLoadGeneration.incrementAndGet()
            _home.value = UiState.Loading
            homeContinuation = null
            _homePendingShelves.value = 0
            _homeRecentlyPlayedLoading.value = false
            _homeLoadingMore.value = false
            viewModelScope.launch { fetchStreamXHome(identity) }
            return
        }
        val identity = listenerKey()
        val generation = homeLoadGeneration.incrementAndGet()
        _home.value = UiState.Loading
        homeContinuation = null
        homeSeenTitles.clear()
        _homeLoadingMore.value = false
        _homeRecentlyPlayedLoading.value = _signedIn.value
        // The core feed plus one browse per supplement. Recently played is left
        // out: it has its own skeleton at the head of the page rather than the
        // one at the tail.
        _homePendingShelves.value = 1 + YtMusicRepository.HOME_SUPPLEMENT_BROWSE_IDS.size
        viewModelScope.launch {
            launch {
                try {
                    YtMusicRepository.home()
                        .onSuccess { feed ->
                            if (!isCurrentHomeLoad(identity, generation)) return@onSuccess
                            homeContinuation = feed.continuation
                            publishHomeShelves(feed.shelves)
                        }
                        .onFailure { failure ->
                            if (isCurrentHomeLoad(identity, generation) && _home.value !is UiState.Success) {
                                _home.value = UiState.Error(failure.friendly())
                            }
                        }
                } finally {
                    homeShelfRequestSettled(identity, generation)
                }
            }
            if (_signedIn.value) {
                launch {
                    YtMusicRepository.homeRecentlyPlayed()
                        .onSuccess { shelf ->
                            if (isCurrentHomeLoad(identity, generation)) {
                                _homeRecentlyPlayedLoading.value = false
                                shelf?.let { publishHomeShelves(listOf(it), prepend = true) }
                            }
                        }
                        .onFailure {
                            if (isCurrentHomeLoad(identity, generation)) _homeRecentlyPlayedLoading.value = false
                        }
                }
            }
            YtMusicRepository.HOME_SUPPLEMENT_BROWSE_IDS.forEach { browseId ->
                launch {
                    try {
                        YtMusicRepository.homeSupplement(browseId).onSuccess { shelves ->
                            if (isCurrentHomeLoad(identity, generation)) publishHomeShelves(shelves)
                        }
                    } finally {
                        homeShelfRequestSettled(identity, generation)
                    }
                }
            }
        }
    }

    /**
     * One of the parallel first-page requests has finished. Ignored once a newer
     * load has taken over, which has already reset the count for its own fan-out.
     */
    private fun homeShelfRequestSettled(identity: String?, generation: Long) {
        if (!isCurrentHomeLoad(identity, generation)) return
        _homePendingShelves.value = (_homePendingShelves.value - 1).coerceAtLeast(0)
    }

    private fun isCurrentHomeLoad(identity: String?, generation: Long) =
        identity == listenerKey() && generation == homeLoadGeneration.get()

    private fun publishHomeShelves(shelves: List<HomeShelf>, prepend: Boolean = false) {
        val existing = (_home.value as? UiState.Success)?.data.orEmpty()
        if (prepend) {
            // YouTube's core home can also contain a stale "Recently played"
            // shelf. The dedicated history endpoint wins and replaces it.
            val replacing = shelves.map { it.title.lowercase(Locale.ROOT) }.toSet()
            homeSeenTitles.addAll(replacing)
            _home.value = UiState.Success(shelves + existing.filterNot {
                it.title.lowercase(Locale.ROOT) in replacing
            })
            return
        }
        val added = shelves.withoutRepeatsOf(existing)
            .filter { homeSeenTitles.add(it.title.lowercase(Locale.ROOT)) }
        if (added.isNotEmpty()) _home.value = UiState.Success(existing + added)
    }

    /** Refreshes the core feed without blanking the current Play page first. */
    private suspend fun refreshHome(identity: String?) {
        refreshMainHome(identity)
        appendExtraFeeds(identity)
    }

    /**
     * The extra Home feeds the user turned on in Sources, each appended as its
     * own section after the main feed. Every shelf carries "From <service>" on
     * its strapline, which each skin's shelf renderer already shows, so the
     * sections read as separate without a new shelf type. One feed failing, or
     * being offline, leaves the others and the main feed alone.
     */
    private suspend fun appendExtraFeeds(identity: String?) = coroutineScope {
        val extras = SourceRegistry.activeExtraFeeds()
        if (extras.isEmpty()) return@coroutineScope
        val feeds = extras.map { config ->
            async {
                val shelves = try {
                    if (config.kind == SourceKind.YOUTUBE) YtMusicRepository.home().getOrThrow().shelves
                    else com.music.streamx.data.extensions.ApkCatalogue(config).feed()
                }
                    catch (e: kotlinx.coroutines.CancellationException) { throw e }
                    catch (_: Exception) { emptyList() }
                shelves.take(EXTRA_FEED_SHELVES).map { it.copy(subtitle = "From ${config.displayName}") }
            }
        }.awaitAll().flatten()
        if (feeds.isEmpty() || identity != listenerKey()) return@coroutineScope
        // A main feed that failed still leaves the extra sections to show.
        val existing = (_home.value as? UiState.Success)?.data ?: emptyList()
        _home.value = UiState.Success(existing + feeds)
    }

    private suspend fun refreshMainHome(identity: String?) = coroutineScope {
        if (usingExtension) { homeLoadGeneration.incrementAndGet(); fetchExtensionHome(identity); return@coroutineScope }
        if (usingStreamX) { fetchStreamXHome(identity); return@coroutineScope }
        val recent = if (_signedIn.value) async { YtMusicRepository.homeRecentlyPlayed() } else null
        YtMusicRepository.home().onSuccess { feed ->
            if (identity != listenerKey()) return@onSuccess
            homeContinuation = feed.continuation
            homeSeenTitles.clear()
            val shelves = feed.shelves.withoutRepeatsOf(emptyList())
                .filter { homeSeenTitles.add(it.title.lowercase(Locale.ROOT)) }
            if (shelves.isNotEmpty()) _home.value = UiState.Success(shelves)
        }
        recent?.await()?.onSuccess { shelf ->
            if (identity == listenerKey()) shelf?.let { publishHomeShelves(listOf(it), prepend = true) }
        }
    }

    /**
     * Called as the Home list nears its end. A no-op while a page is already
     * in flight, once the feed is exhausted, or before the first page has
     * loaded — [homeContinuation] covers all three by construction.
     */
    fun loadMoreHome() {
        val token = homeContinuation ?: return
        if (_homeLoadingMore.value) return
        val identity = listenerKey()
        _homeLoadingMore.value = true
        viewModelScope.launch {
            YtMusicRepository.moreHome(token).onSuccess { feed ->
                if (identity == listenerKey()) {
                    val existing = (_home.value as? UiState.Success)?.data ?: emptyList()
                    val added = feed.shelves.withoutRepeatsOf(existing)
                        .filter { homeSeenTitles.add(it.title.lowercase(Locale.ROOT)) }
                    // A page with nothing new signals the feed has looped back on
                    // itself rather than run dry with a token still attached —
                    // treat it the same as exhausted so scrolling can't spin here.
                    homeContinuation = feed.continuation.takeIf { added.isNotEmpty() }
                    if (added.isNotEmpty()) _home.value = UiState.Success(existing + added)
                }
            }
            if (identity == listenerKey()) _homeLoadingMore.value = false
        }
    }

    fun loadLibrary() {
        if (!_signedIn.value && !usingExtension) return
        val identity = listenerKey()
        _library.value = UiState.Loading
        viewModelScope.launch { fetchLibrary(identity); appendSourcePlaylists(identity) }
    }

    /**
     * With [AppSettings.multiSourcePlaylists] on, a "Playlists" section for each
     * other connected plugin, after the library of whichever one leads Home.
     * A plugin that is signed out or fails is left out; the rest still show.
     */
    private suspend fun appendSourcePlaylists(identity: String?) = coroutineScope {
        if (!AppSettings.multiSourcePlaylists.value) return@coroutineScope
        val installed = com.music.streamx.data.extensions.ApkExtensions.installed.value
        val plugins = SourceRegistry.configs.value.filter { config ->
            SourceRegistry.feedEligible(config) && config.id != SourceRegistry.selectedExtensionId.value &&
                !com.music.streamx.data.extensions.SflxBridge.handles(config) &&
                installed.any { it.packageName == config.extensionPackage && it.sourceAuthority != null }
        }
        if (plugins.isEmpty()) return@coroutineScope
        val shelves = plugins.map { config ->
            async {
                val playlists = try { com.music.streamx.data.extensions.ApkCatalogue(config).playlists() }
                    catch (e: kotlinx.coroutines.CancellationException) { throw e }
                    catch (_: Exception) { emptyList() }
                playlists.takeIf { it.isNotEmpty() }?.let { rows ->
                    HomeShelf("Playlists", rows.map { ShelfItem(it.title, it.subtitle, it.thumbnailUrl, null, it.browseId) },
                        subtitle = "From ${config.displayName}")
                }
            }
        }.awaitAll().filterNotNull()
        if (shelves.isEmpty() || identity != listenerKey()) return@coroutineScope
        val page = (_library.value as? UiState.Success)?.data ?: LibraryPage(emptyList(), emptyList(), emptyList())
        _library.value = UiState.Success(page.copy(shelves = page.shelves + shelves))
    }

    private suspend fun fetchLibrary(identity: String?) {
        if (usingExtension) {
            val result = extensionRequest { it.library() }
            if (identity != listenerKey()) return
            _library.value = result.fold({ UiState.Success(it) }, { UiState.Error(it.friendly()) })
            result.onSuccess { LikeState.seedLiked(it.likedSongs.map { song -> song.videoId }.toSet()) }
            return
        }
        if (usingStreamX) {
            val result = StreamXClient.result { StreamXClient.current().library() }
            if (identity != listenerKey()) return
            result.onSuccess { LikeState.seedLiked(it.likedSongs.map { song -> song.videoId }.toSet()) }
            _library.value = result.fold({ UiState.Success(it) }, { UiState.Error(it.friendly()) })
            return
        }
        val next = YtMusicRepository.library().fold(
            onSuccess = { page ->
                // Liked Music is published with just its first page on the tab;
                // the rest of the collection is synced into LikeState here, in
                // this ViewModel's scope, so it is cancelled with the screen and
                // a liked track past the first page still reads as liked.
                page.likedContinuation?.let { token -> syncLikedMusic(identity, token) }
                if (page.isEmpty) UiState.Error(text(R.string.library_empty))
                else UiState.Success(page.copy(likedContinuation = null))
            },
            onFailure = { UiState.Error(it.friendly()) },
        )
        if (identity == listenerKey()) _library.value = next
    }

    /**
     * Follows Liked Music's continuation chain to exhaustion, seeding each
     * page's ids into [LikeState] so every liked track reads as liked.
     *
     * Scoped to [viewModelScope] — a re-fetch of the library cancels and
     * replaces it, and it dies with the screen. It only ever seeds ids, never
     * retaining the full songs for pages already behind the tab.
     *
     * [identity] is the listener this token belongs to, checked before every
     * page: [LikeState] is a single shared map, not scoped per account, so a
     * sync still in flight when the listener switches must stop rather than
     * go on seeding the old account's likes into the new one's session.
     */
    private fun syncLikedMusic(identity: String?, token: String) {
        likedSyncJob?.cancel()
        likedSyncJob = viewModelScope.launch {
            YtMusicRepository.syncLikedMusic(token) { next ->
                if (identity != listenerKey()) null else YtMusicRepository.moreSongs(next).getOrNull()
            }
        }
    }

    /**
     * The account's listening history.
     *
     * Loaded on each visit rather than cached: it is a page whose whole subject
     * is what happened most recently, and one that opened showing the state it
     * was in last time would be answering a different question. Guests get the
     * signed-out message straight away, since there is no account to have a
     * history on.
     */
    fun loadHistory() {
        if (!_signedIn.value && !usingExtension) {
            _history.value = UiState.Error(text(R.string.history_sign_in_required))
            return
        }
        val identity = listenerKey()
        _history.value = UiState.Loading
        _historyShelves.value = emptyList()
        viewModelScope.launch {
            if (usingExtension) {
                val page=extensionRequest { it.history() }
                if(identity==listenerKey()) {
                    _historyShelves.value=page.getOrNull()?.shelves.orEmpty()
                    _history.value=page.fold({if(it.songs.isEmpty() && it.shelves.isEmpty()) UiState.Error(text(R.string.history_empty)) else UiState.Success(it.songs)}, {UiState.Error(it.friendly())})
                }
                return@launch
            }
            val next = (if (usingStreamX) StreamXClient.result { StreamXClient.current().history() } else YtMusicRepository.history()).fold(
                onSuccess = { songs ->
                    if (songs.isEmpty()) UiState.Error(text(R.string.history_empty))
                    else UiState.Success(songs)
                },
                onFailure = { UiState.Error(it.friendly()) },
            )
            if (identity == listenerKey()) _history.value = next
        }
    }

    /** Recent searches, kept on device as rich entity records. */
    val searchHistory: StateFlow<List<SearchHistoryEntity>> = SearchHistory.recent

    /**
     * Records a full entity payload in the history — used when the user taps
     * a suggestion, result row, or typeahead card so the recents list shows
     * real artwork and metadata instead of raw text.
     */
    fun recordEntity(entity: SearchHistoryEntity) = SearchHistory.record(entity)

    /**
     * Records a track entity from a search hit.
     * Checks _typeaheadResults first (live media while typing), then _results
     * (committed search). Falls back to recording the raw query if no song data
     * is available.
     */
    fun recordSearch() {
        val q = _query.value.trim()
        if (q.isEmpty()) return
        // Prefer typeahead results — they are live and always available while typing.
        val fromTypeahead = _typeaheadResults.value.firstOrNull {
            it is SearchResult.TopTrack || it is SearchResult.Track
        }
        if (fromTypeahead is SearchResult.Track) {
            recordEntity(SearchHistoryEntity(
                id = fromTypeahead.song.videoId,
                title = fromTypeahead.song.title,
                subtitle = listOfNotNull(fromTypeahead.song.artist).joinToString(" · "),
                artworkUrl = fromTypeahead.song.thumbnailUrl,
                entityType = EntityType.TRACK,
            ))
            return
        }
        if (fromTypeahead is SearchResult.TopTrack) {
            recordEntity(SearchHistoryEntity(
                id = fromTypeahead.song.videoId,
                title = fromTypeahead.song.title,
                subtitle = listOfNotNull(fromTypeahead.song.artist).joinToString(" · "),
                artworkUrl = fromTypeahead.song.thumbnailUrl,
                entityType = EntityType.TRACK,
            ))
            return
        }
        // Fall back to committed search results.
        val topResult = (_results.value as? UiState.Success)?.data?.firstOrNull {
            it is SearchResult.TopTrack || it is SearchResult.Track
        }
        if (topResult is SearchResult.Track) {
            recordEntity(SearchHistoryEntity(
                id = topResult.song.videoId,
                title = topResult.song.title,
                subtitle = listOfNotNull(topResult.song.artist).joinToString(" · "),
                artworkUrl = topResult.song.thumbnailUrl,
                entityType = EntityType.TRACK,
            ))
        } else if (topResult is SearchResult.TopTrack) {
            recordEntity(SearchHistoryEntity(
                id = topResult.song.videoId,
                title = topResult.song.title,
                subtitle = listOfNotNull(topResult.song.artist).joinToString(" · "),
                artworkUrl = topResult.song.thumbnailUrl,
                entityType = EntityType.TRACK,
            ))
        } else {
            // Fallback: record as a raw-text entity for backwards compatibility
            recordEntity(SearchHistoryEntity(
                id = "q:$q",
                title = q,
                subtitle = "",
                artworkUrl = null,
                entityType = EntityType.TRACK,
            ))
        }
    }

    /**
     * Runs a term the user picked out of a list rather than typed — a recent
     * search, or one of [suggestions] — and floats it to the top of the
     * history. Picking is as deliberate as submitting, so it searches on the
     * spot.
     *
     * Recording happens downstream via [recordSearch] when the user actually
     * interacts with a result (taps a song, album, or artist card), which
     * ensures the saved entity carries real artwork and metadata instead of
     * a blank placeholder.
     */
    fun searchFor(term: String) {
        _query.value = term
        searchSubmitted = true
        _suggestions.value = emptyList()
        _typeaheadResults.value = emptyList()
        // Picking a suggestion or history item is deliberate, but it must NOT
        // pollute the user's YTMusic server-side search history — only an
        // explicit keyboard submission should do that.  Use the anonymous
        // endpoint so YouTube does not log this query against the account.
        runSearch(anonymous = true)
    }

    fun removeSearch(id: String) = SearchHistory.remove(id)

    fun clearSearchHistory() = SearchHistory.clear()

    fun onQueryChange(newValue: String) {
        _query.value = newValue
        if (newValue.isBlank()) {
            newestRequestId.incrementAndGet()
            searchSession = null
            _searchLoadingMore.value = false
            searchSubmitted = false
            _suggestions.value = emptyList()
            _typeaheadResults.value = emptyList()
            _results.value = null
            addonSearchJob?.cancel()
            _addonResults.value = null
            // The list is about to switch from search results to recent
            // searches (or the empty state) — without this it can keep
            // whatever scroll offset the results list was left at, landing
            // the new, much shorter list somewhere other than the top.
            _searchScrollReset.value += 1
            return
        }
        // Reset the submission gate so typeahead pipelines fire again.
        searchSubmitted = false
        // The Library source answers from the device on every keystroke (see
        // [libraryResults]); YouTube's completions have nothing to add to it.
        if (_searchSource.value == SearchSource.LIBRARY || _searchSource.value == SearchSource.EXTENSIONS) return
        // While typing, surface text completions — the pipeline already feeds
        // them through [suggestRequests] and publishes results via typeahead.
        suggestRequests.tryEmit(newValue)
    }

    /**
     * Commits the current query text: clears suggestions/typeahead, runs the
     * full search, and records the term in history for future recall.
     */
    fun submitSearch() {
        val q = _query.value.trim()
        if (q.isEmpty() || _searchSource.value == SearchSource.LIBRARY) return
        searchSubmitted = true
        _suggestions.value = emptyList()
        _typeaheadResults.value = emptyList()
        if (_searchSource.value == SearchSource.ADDONS) {
            runAddonSearch(q)
            return
        }
        runSearch(anonymous = false)
    }

    /**
     * Asks every enabled addon for [q] in parallel. A slow or failing addon
     * only costs its own rows; the list is the rest of them, round-robin so
     * no single catalogue buries the others.
     */
    private fun runAddonSearch(q: String) {
        SearchHistory.recordAddonQuery(q)
        addonSearchJob?.cancel()
        _searchScrollReset.value += 1
        val addons = SourceRegistry.active().filterIsInstance<AddonSource>()
        if (addons.isEmpty()) {
            _addonResults.value = UiState.Error(text(R.string.search_addons_none))
            return
        }
        _addonResults.value = UiState.Loading
        addonSearchJob = viewModelScope.launch {
            val perAddon = coroutineScope {
                addons.map { addon ->
                    async {
                        withTimeoutOrNull(ADDON_SEARCH_TIMEOUT_MS) {
                            runCatching { addon.search(q, limit = ADDON_SEARCH_LIMIT) }.getOrDefault(emptyList())
                        } ?: emptyList()
                    }
                }.map { it.await() }
            }
            val merged = buildList {
                val longest = perAddon.maxOfOrNull { it.size } ?: 0
                for (i in 0 until longest) perAddon.forEach { rows -> rows.getOrNull(i)?.let(::add) }
            }
            _addonResults.value = UiState.Success(merged)
        }
    }

    /**
     * Points the Search tab at YouTube Music or at the Local Music folder.
     *
     * The query carries over: whatever is in the field is answered again by
     * the source just picked, so flipping is a way to compare the two rather
     * than a reset.
     */
    fun setSearchSource(source: SearchSource) {
        if (_searchSource.value == source) return
        // Invalidate both first-page and pagination responses from the old source.
        newestRequestId.incrementAndGet()
        searchSession = null
        _searchLoadingMore.value = false
        _results.value = null
        _searchSource.value = source
        if (source == SearchSource.EXTENSIONS && _filter.value !in listOf(SearchFilter.ALL, SearchFilter.SONGS)) _filter.value = SearchFilter.ALL
        // Search only: the tab never changes what fills Home (that is Sources → Home).
        _suggestions.value = emptyList()
        _typeaheadResults.value = emptyList()
        _searchScrollReset.value += 1
        when (source) {
            SearchSource.LIBRARY -> loadLibrarySongs()
            SearchSource.STREAMX, SearchSource.YOUTUBE, SearchSource.EXTENSIONS -> submitSearch()
            SearchSource.ADDONS -> if (_query.value.isNotBlank()) submitSearch()
        }
    }

    /**
     * Reads the Local Music folder for the Library source. Re-read on every
     * switch to it, so files added since — or a permission granted since —
     * are in the next answer; the last list stays up meanwhile.
     */
    fun loadLibrarySongs() {
        viewModelScope.launch {
            val context = getApplication<Application>()
            _librarySongs.value = if (!LocalMediaRepository.hasStoragePermission(context)) {
                UiState.Error(text(R.string.storage_required_read))
            } else {
                runCatching { LocalMediaRepository.getLocalMusic(context) }.fold(
                    onSuccess = { songs ->
                        if (songs.isEmpty()) UiState.Error(text(R.string.no_local_audio_found))
                        else UiState.Success(songs)
                    },
                    onFailure = { UiState.Error(text(R.string.no_local_audio_found)) },
                )
            }
        }
    }

    fun onFilterChange(value: SearchFilter) {
        if (_filter.value == value) return
        _filter.value = value
        searchSubmitted = true
        runSearch()
    }

    /**
     * A search asked for, as a request the pipeline below decides what to do
     * with.
     *
     * [requestId] is what makes a late answer harmless: a response is only
     * written to the screen if its id is still the newest one asked for.
     */
    private data class SearchRequest(
        val query: String,
        val filter: SearchFilter,
        val requestId: Long,
        val anonymous: Boolean = true,
    )

    private fun searchCacheScope(): String {
        val plugins = if (_searchSource.value == SearchSource.EXTENSIONS) SourceRegistry.configs.value
            .filter { it.kind == SourceKind.APK_PLUGIN && it.enabled && it.isComplete }
            .joinToString { "${it.id}:${it.extensionCertificate}:${it.extensionAuthority}:" + com.music.streamx.data.extensions.ApkExtensions.installed.value.firstOrNull { apk -> apk.packageName == it.extensionPackage }?.versionCode } else ""
        return "${listenerKey()}:${_searchSource.value}:$plugins"
    }
    private fun cacheKey(query: String, filter: SearchFilter) = "${searchCacheScope()}:${filter.name}:$query"

    /**
     * The results of the longest earlier query this one starts with — near
     * enough to leave up while the narrower search runs.
     */
    private fun prefixMatch(query: String, filter: SearchFilter): List<SearchResult>? {
        val prefix = "${searchCacheScope()}:${filter.name}:"
        return searchCache.snapshot()
            .filterKeys { it.startsWith(prefix) && query.startsWith(it.removePrefix(prefix), true) }
            .maxByOrNull { it.key.length }
            ?.value
            ?.rows
    }

    private fun runSearch(anonymous: Boolean = true) {
        val query = _query.value
        if (query.isBlank() || _searchSource.value == SearchSource.LIBRARY) {
            // Nothing in flight can still be waiting to overwrite this: the
            // id it would be checked against has already moved past it.
            newestRequestId.incrementAndGet()
            searchSession = null
            _searchLoadingMore.value = false
            _results.value = null
            return
        }
        val id = newestRequestId.incrementAndGet()
        _searchScrollReset.value += 1
        searchSession = null
        _searchLoadingMore.value = false
        searchRequests.tryEmit(SearchRequest(query, _filter.value, id, anonymous))
    }

    private suspend fun searchExtensions(query: String, filter: SearchFilter): Result<YtMusicRepository.SearchPage> {
        if (!_extensionFullSearch.value && filter != SearchFilter.ALL && filter != SearchFilter.SONGS) return Result.success(YtMusicRepository.SearchPage(emptyList(), null))
        val plugins = SourceRegistry.configs.value.filter { it.kind == SourceKind.APK_PLUGIN && (it.enabled || it.id == SourceRegistry.selectedExtensionId.value) && it.isComplete && (SourceRegistry.selectedExtensionId.value == null || SourceRegistry.selectedExtensionId.value == it.id) }
        if (plugins.isEmpty()) return Result.failure(IllegalStateException("Enable a source APK in Settings → Skins & Plugins"))
        if (_extensionFullSearch.value && plugins.size == 1) {
            return extensionRequest { it.searchPage(query, filter) }.map { YtMusicRepository.SearchPage(it, null) }
        }
        val gate = Semaphore(3)
        val replies = coroutineScope {
            plugins.map { config -> async(Dispatchers.IO) {
                gate.withPermit {
                    try {
                        Result.success(checkNotNull(SourceRegistry.instance(config.id)).search(query, limit = 50))
                    } catch (e: kotlinx.coroutines.CancellationException) { throw e }
                    catch (e: Exception) { Result.failure<List<Song>>(e) }
                }
            } }.map { it.await() }
        }
        if (replies.all { it.isFailure }) return Result.failure(checkNotNull(replies.first().exceptionOrNull()))
        val rows = replies.flatMap { it.getOrNull().orEmpty() }.distinctBy { it.videoId }.map { SearchResult.Track(it) }
        return Result.success(YtMusicRepository.SearchPage(rows, null))
    }

    /**
     * The search pipeline, started once and left running for the lifetime of
     * the view model.
     *
     * The point of it being one long-lived collector is that a new search no
     * longer cancels the request before it out of a fresh coroutine.
     * Cancelling a call mid-flight tears down its socket, and on a pooled HTTP
     * client that is felt by whatever picks that connection up next — which is
     * how one search could end in "Software caused connection abort" for a
     * request that was never itself in any trouble.
     *
     * There is no debounce here any more, and nothing to absorb: a search is
     * only ever asked for by a deliberate act — the search button, a
     * suggestion or history row, a filter tab — so the request that arrives is
     * already the one the user meant, and making them wait out a timer for it
     * would be a delay with nothing behind it. Typing asks
     * [startSuggestPipeline] for completions instead and leaves the results
     * alone.
     */
    private fun startSearchPipeline() = viewModelScope.launch {
        searchRequests
            .collectLatest { request ->
                if (request.requestId != newestRequestId.get() ||
                    _searchSource.value == SearchSource.LIBRARY
                ) return@collectLatest
                val key = cacheKey(request.query, request.filter)
                // Something to look at immediately: the exact answer if this
                // query has been run before, otherwise the closest earlier
                // one. Only fall back to a spinner with neither.
                val exact = searchCache.get(key)
                val cached = exact?.rows ?: prefixMatch(request.query, request.filter)
                _results.value = cached?.let { UiState.Success(it) } ?: UiState.Loading
                // An explicit submit still has to reach the server even when
                // an anonymous lookup cached the same query: that call is what
                // records it in the account's history.
                if (exact != null && request.anonymous) {
                    searchSession = SearchSession(key, request.requestId, request.filter, exact.continuation)
                    return@collectLatest
                }

                val result = if (_searchSource.value == SearchSource.EXTENSIONS) searchExtensions(request.query, request.filter) else if (_searchSource.value == SearchSource.STREAMX) StreamXClient.result {
                    YtMusicRepository.SearchPage(StreamXClient.current().search(request.query, request.filter), null)
                } else YtMusicRepository.searchPage(request.query, request.filter, request.anonymous)
                // A search that has been superseded shouldn't land on screen,
                // whether it succeeded or failed.
                if (request.requestId != newestRequestId.get()) return@collectLatest
                _results.value = result.fold(
                    onSuccess = { page -> published(page, key, request.requestId) },
                    onFailure = { failure -> UiState.Error(failure.friendly()) },
                )
            }
    }

    /**
     * Continues the visible search only when the list reaches its end. This is
     * deliberately separate from the first-page request: waiting for every
     * continuation was the reason a search sat on a spinner for seconds.
     */
    fun loadMoreSearchResults() {
        val session = searchSession ?: return
        val token = session.continuation ?: return
        if (_searchLoadingMore.value) return
        _searchLoadingMore.value = true
        viewModelScope.launch {
            val next = YtMusicRepository.searchContinuation(token, session.filter)
            val stillCurrent = searchSession == session && session.requestId == newestRequestId.get()
            if (stillCurrent) {
                next.onSuccess { page ->
                    val current = (_results.value as? UiState.Success)?.data.orEmpty()
                    val merged = (current + page.rows).distinctBy(::searchResultKey)
                    searchCache.put(session.key, SearchCacheEntry(merged, page.continuation))
                    searchSession = session.copy(continuation = page.continuation)
                    _results.value = UiState.Success(merged)
                }
                _searchLoadingMore.value = false
            }
        }
    }

    /**
     * The typeahead pipeline, alongside [startSearchPipeline] and for the same
     * structural reason — one long-lived collector rather than a coroutine per
     * keystroke, so a lookup the user has typed past doesn't take a pooled
     * socket down with it.
     *
     * This one *does* debounce, and that isn't the timer that was taken off the
     * search. It's two orders of magnitude shorter, and it's paid for by the
     * request behind it being a few hundred bytes rather than a full page of
     * results — a burst of keystrokes shouldn't each cost a round trip, but the
     * gap has to be short enough that the list is up before the next letter is
     * typed. Nothing is waiting on it either way: the row the user typed is
     * already on screen from the keystroke itself.
     *
     * A failure is left on the floor. There is no worthwhile way to report
     * "couldn't suggest anything" in a list of suggestions, and the typed text
     * is standing there as a working first row regardless.
     */
    @OptIn(FlowPreview::class)
    private fun startSuggestPipeline() = viewModelScope.launch {
        // Whether a list for [input] is still wanted. False once the field has
        // moved on: typed further, or searched — which empties [_suggestions],
        // and a late answer writing to it would reopen the suggestions over
        // the results the user is by then reading.
        fun stillWanted(input: String, requestId: Long) =
            _query.value == input && !searchSubmitted &&
                _searchSource.value == SearchSource.YOUTUBE &&
                requestId == newestRequestId.get()

        suggestRequests
            .debounce(SUGGEST_DEBOUNCE_MS)
            .collectLatest { input ->
                val requestId = newestRequestId.get()
                if (!stillWanted(input, requestId)) return@collectLatest
                val fetched = YtMusicRepository.searchSuggestions(input).getOrNull()
                    ?: return@collectLatest
                // Asked again on the way back; the field is live throughout.
                if (!stillWanted(input, requestId)) return@collectLatest
                _suggestions.value = listOf(input) +
                    fetched.filterNot { it.equals(input, ignoreCase = true) }
            }
    }

    /**
     * Parallel pipeline that fetches live media results (tracks, artists,
     * albums) for the current query text. Runs alongside [startSuggestPipeline]
     * with its own debounce so a fast typist doesn't saturate the network.
     */
    @OptIn(FlowPreview::class)
    private fun startTypeaheadMediaPipeline() = viewModelScope.launch {
        suggestRequests
            .debounce(TYPEAHEAD_MEDIA_DEBOUNCE_MS)
            .collectLatest { input ->
                if (input.isBlank()) {
                    _typeaheadResults.value = emptyList()
                    return@collectLatest
                }
                // Only show media results while the user is still typing — not
                // reading committed search results. The query-text check on its
                // own isn't enough: a late suggestion callback can repopulate
                // _suggestions after submission, and we must not re-open the
                // typeahead dropdown under an already-committed search.
                if (searchSubmitted || _searchSource.value == SearchSource.LIBRARY || _searchSource.value == SearchSource.EXTENSIONS) {
                    return@collectLatest
                }
                val requestId = newestRequestId.get()
                val result = if (usingStreamX) StreamXClient.result {
                    YtMusicRepository.SearchPage(StreamXClient.current().search(input, limit = TYPEAHEAD_MAX_RESULTS), null)
                }.getOrNull() else YtMusicRepository.searchTypeahead(input).getOrNull()
                // A late response must not repopulate a submitted or switched search.
                if (_query.value != input || searchSubmitted ||
                    (_searchSource.value == SearchSource.LIBRARY || _searchSource.value == SearchSource.EXTENSIONS) ||
                    requestId != newestRequestId.get()
                ) return@collectLatest
                // Cap results so the dropdown doesn't grow unbounded.
                _typeaheadResults.value = result?.rows.orEmpty().take(TYPEAHEAD_MAX_RESULTS)
            }
    }

    /** Caches and publishes the initial result page without waiting for later pages. */
    private fun published(
        page: YtMusicRepository.SearchPage,
        key: String,
        requestId: Long,
    ): UiState<List<SearchResult>> {
        val rows = page.rows
        if (rows.isEmpty()) return UiState.Error(text(R.string.no_results))
        searchCache.put(key, SearchCacheEntry(rows, page.continuation))
        searchSession = SearchSession(key, requestId, _filter.value, page.continuation)
        if (_searchSource.value == SearchSource.YOUTUBE) prefetchTopResult(rows)
        return UiState.Success(rows)
    }

    private fun searchResultKey(row: SearchResult): String = when (row) {
        is SearchResult.TopTrack -> "v:${row.song.videoId}"
        is SearchResult.Track -> "v:${row.song.videoId}"
        is SearchResult.Browse -> "b:${row.item.browseId}"
    }

    /**
     * The enabled non-YouTube sources, asked at the same time and returned
     * split at YouTube's own place in the order.
     *
     * The split is what makes the Sources screen's ordering visible where it
     * matters most. A library server ranked above YouTube puts its own copies
     * at the top of the results — which is the whole point of ranking it there —
     * and one ranked below appears under them instead.
     *
     * Only the Songs filter fans out: albums, artists and playlists are
     * browse-shaped, and [MusicSource] deliberately answers for tracks only.
     *
     * Asked of the playback list rather than every enabled source, so a result
     * offered here is one this connection's ceiling would actually let play —
     * a row that can only be tapped into a YouTube stream is a lie about where
     * the track is coming from.
     */
    private suspend fun sourceResults(
        query: String,
        filter: SearchFilter,
    ): Pair<List<SearchResult>, List<SearchResult>> = coroutineScope {
        if (filter != SearchFilter.SONGS) return@coroutineScope emptyList<SearchResult>() to emptyList()
        val active = SourceRegistry.activeForPlayback()
        val youtubeRank = active.indexOfFirst { it.kind == SourceKind.YOUTUBE }
            .let { if (it < 0) active.size else it }

        val answers = active
            .filter { it.kind != SourceKind.YOUTUBE }
            .map { source ->
                source to async {
                    // Per-source, so one slow or unreachable server delays the
                    // results by at most this much rather than for as long as
                    // its socket takes to give up.
                    runCatching {
                        withTimeout(SOURCE_SEARCH_TIMEOUT_MS) { source.search(query, SOURCE_SEARCH_LIMIT) }
                    }.getOrDefault(emptyList())
                }
            }

        val above = mutableListOf<SearchResult>()
        val below = mutableListOf<SearchResult>()
        answers.forEach { (source, job) ->
            val rows = job.await().map { SearchResult.Track(it) }
            val rank = active.indexOfFirst { it.configId == source.configId }
            if (rank in 0 until youtubeRank) above += rows else below += rows
        }
        above to below
    }

    /**
     * Warms the stream URL for the top song result the instant results land,
     * not when it's tapped. [AudioCache] gives a head start to whatever's
     * already queued; a fresh search has nothing queued yet, and the top
     * result is overwhelmingly what gets tapped — see [play][MainActivity.play].
     * The exact search result is warmed because video tracks now play their
     * own audio by default; catalogue matching is only requested manually.
     */
    private fun prefetchTopResult(rows: List<SearchResult>) {
        val song = rows.firstNotNullOfOrNull {
            when (it) {
                is SearchResult.TopTrack -> it.song
                is SearchResult.Track -> it.song
                is SearchResult.Browse -> null
            }
        } ?: return
        viewModelScope.launch {
            runCatching {
                val audio = song
                // A source-backed row resolves through its own source already
                // and never takes the YouTube path — warming either half of
                // this for one would be work nothing asks for.
                if (SourceRegistry.parseTrackKey(audio.videoId) != null) return@runCatching
                // JioSaavn first, on the same reasoning as the queue's
                // read-ahead: it is the copy that will actually be played if it
                // has the track, so warming YouTube's URL instead warms the one
                // that loses. Pinned through [StreamChoice] so playback opens
                // this very stream rather than racing for it again — see
                // [SourceResolver.prefetchSubstitute], which requires it.
                val warmed = SourceResolver.prefetchSubstitute(
                    TrackMatcher.Target(
                        title = audio.title,
                        artist = audio.artist,
                        durationSec = TrackMatcher.secondsOf(audio.durationText),
                        album = audio.albumName,
                        isExplicit = audio.isExplicit,
                        isVideo = audio.isVideo,
                    ),
                )
                if (warmed != null) {
                    StreamChoice.remember(audio.videoId, warmed, substituted = true)
                    return@runCatching
                }
                // Disabled, or hasn't got it: the tap path falls back to
                // YouTube, so that is what is worth having ready.
                StreamResolver.resolve(audio.videoId)
            }
        }
    }

    companion object {
        /** Shelves taken from each extra Home feed, so one service cannot bury the rest. */
        private const val EXTRA_FEED_SHELVES = 6
        /** Per-addon budget for the Addons search tab; a slow addon is left out, not waited on. */
        private const val ADDON_SEARCH_TIMEOUT_MS = 12_000L
        private const val ADDON_SEARCH_LIMIT = 20

        /** How many Spotify tracks are looked up on YouTube Music at once. */
        private const val SPOTIFY_MATCH_PARALLELISM = 6

        /**
         * How long a keystroke waits before the typeahead is asked about it.
         *
         * Not the search's timer — searches aren't on a timer any more. This
         * one only stops a fast typist spending a round trip per letter, so it
         * wants to be as short as it can be while still collapsing a burst:
         * long enough that "cold" isn't four lookups, short enough that the
         * list is up by the time the thumb has left the key.
         */
        const val SUGGEST_DEBOUNCE_MS = 180L

        /**
         * Debounce for the parallel media-search pipeline. Slightly longer than
         * text suggestions so it doesn't fire on every single keystroke — a
         * full search is heavier than a suggestion request, and the UI only
         * needs a few results to fill the dropdown.
         */
        const val TYPEAHEAD_MEDIA_DEBOUNCE_MS = 350L

        /**
         * Maximum number of live media results shown in the typeahead dropdown.
         * Enough to give variety without making the list unscrollable.
         */
        const val TYPEAHEAD_MAX_RESULTS = 15

        const val SEARCH_CACHE_ENTRIES = 100

        /**
         * How long any one source gets to answer a search.
         *
         * Short on purpose: these run alongside the YouTube search, and their
         * only job is to be *there* when it lands. A home server reached over
         * a VPN that takes eight seconds has effectively not answered, and
         * holding the whole result list for it would make search feel worse
         * for the sake of results the user can still get by searching again.
         */
        const val SOURCE_SEARCH_TIMEOUT_MS = 4000L

        /** Enough to be worth scrolling, short enough not to bury YouTube's own rows. */
        const val SOURCE_SEARCH_LIMIT = 12

        /**
         * What a page with an empty listing says.
         *
         * Named because it is a state one can be got *out* of, not just a
         * message: an own playlist with nothing in it lands here, and adding the
         * first track to it has to be able to tell "this page is empty" apart
         * from "this page failed to load" — see [appendToOpenPlaylist].
         */

        /**
         * What a downloaded playlist's page says once the files under it are
         * gone.
         *
         * A record here outlives the folder it names — the user is expected to
         * manage Downloads with a file manager — so this is a state its page has
         * to be able to reach, not an error. Named because three places say it:
         * the page, its refresh, and the long-press menu that queues it without
         * opening it.
         */

        fun browseTypeOf(browseId: String, fallback: BrowseType = BrowseType.OTHER): BrowseType = when {
            browseId.startsWith("apk:") -> when (com.music.streamx.data.extensions.ApkCatalogue.parseBrowseId(browseId)?.kind) {
                "artist" -> BrowseType.ARTIST; "album" -> BrowseType.ALBUM; else -> BrowseType.PLAYLIST
            }
            // Built for playlists, but an album downloaded whole opens through
            // the same prefix, and its page should read as the album it is.
            browseId.startsWith(Downloads.PLAYLIST_PREFIX) ->
                Downloads.recordIdOf(browseId)
                    ?.let { Downloads.collections.value[it] }
                    ?.takeIf { !it.playlist }
                    ?.let { BrowseType.ALBUM }
                    ?: BrowseType.PLAYLIST
            browseId.startsWith(LocalGroups.PREFIX) -> LocalGroups.typeOf(browseId) ?: fallback
            browseId.startsWith("UC") -> BrowseType.ARTIST
            browseId.startsWith("MPREb") || browseId.startsWith("VLOLAK") || browseId.startsWith("OLAK") -> BrowseType.ALBUM
            browseId.startsWith("VL") || browseId.startsWith("PL") -> BrowseType.PLAYLIST
            else -> fallback
        }
    }

    /**
     * A remote file library behind a `local:` page: how to list it and what
     * an empty listing says. One entry per library keeps the detail loader,
     * the refresher and the queue collector from each repeating the switch —
     * a third library adds one line here and nothing anywhere else.
     */
    private data class RemoteLibrary(
        val emptyRes: Int,
        val songs: suspend () -> List<Song>,
    )

    private fun remoteLibrary(browseId: String): RemoteLibrary? = when (browseId) {
        com.music.streamx.data.webdav.WebDavConfig.BROWSE_ID ->
            RemoteLibrary(
                R.string.webdav_empty,
                com.music.streamx.data.webdav.WebDavRepository::getSongs,
            )
        com.music.streamx.data.smb.SmbConfig.BROWSE_ID ->
            RemoteLibrary(
                R.string.smb_empty,
                com.music.streamx.data.smb.SmbRepository::getSongs,
            )
        else -> null
    }

    private suspend fun remoteSongsState(remote: RemoteLibrary): UiState<List<Song>> =
        com.music.streamx.data.remote.RemoteListing.state(runCatching { remote.songs() }, text(remote.emptyRes))

    /**
     * Re-reads an open remote-library page after its server settings change.
     * A no-op when the page isn't open — the next visit lists fresh anyway.
     */
    private fun reloadRemoteDetail(browseId: String) {
        if (_detailStack.value.any { page -> page.browseId == browseId }) {
            reloadLocalDetail(browseId)
        }
    }

    fun loadMoreStreamXCatalogue(browseId: String) {
        val original = _detailStack.value.lastOrNull { it.browseId == browseId } ?: return
        val extension = com.music.streamx.data.extensions.ApkCatalogue.parseBrowseId(browseId)
        if (extension != null) {
            if (original.catalogueLoading) return
            val offset = original.nextCataloguePage ?: if (original.songs is UiState.Error) 0 else return
            val identity = listenerKey()
            _detailStack.value = _detailStack.value.map { if (it.browseId == browseId) it.copy(catalogueLoading = true, catalogueError = null) else it }
            viewModelScope.launch {
                val result = try {
                    val config = SourceRegistry.config(extension.configId) ?: error("Extension is unavailable")
                    Result.success(com.music.streamx.data.extensions.ApkCatalogue(config).detail(browseId, original.title, offset))
                } catch (e: kotlinx.coroutines.CancellationException) { throw e }
                catch (e: Exception) { Result.failure<DetailPage>(e) }
                if (identity != listenerKey()) return@launch
                _detailStack.value = _detailStack.value.map { page ->
                    if (page.browseId != browseId) page else result.fold({ next ->
                        val songs = ((page.songs as? UiState.Success)?.data.orEmpty() + (next.songs as? UiState.Success)?.data.orEmpty()).distinctBy { it.videoId }
                        val oldItems = page.sections.flatMap { it.items }
                        val items = (oldItems + next.sections.flatMap { it.items }).distinctBy { it.browseId ?: it.videoId }
                        val progress = songs.size > (page.songs as? UiState.Success)?.data.orEmpty().size || items.size > oldItems.size
                        page.copy(songs = UiState.Success(songs), catalogueLoading = false, catalogueError = null,
                            sections = (page.sections + next.sections).groupBy {it.title}.map { (_, shelves) ->
                                shelves.first().copy(items=shelves.flatMap {it.items}.distinctBy {it.browseId ?: it.videoId})
                            },
                            nextCataloguePage = next.nextCataloguePage.takeIf { progress })
                    }, { page.copy(catalogueLoading = false, catalogueError = it.friendly()) })
                }
            }
            return
        }
        if (!browseId.contains(":catalogue:") || original.catalogueLoading) return
        val nextPage = original.nextCataloguePage ?: if (original.songs is UiState.Error) 1 else return
        val identity = listenerKey()
        _detailStack.value = _detailStack.value.map {
            if (it.browseId == browseId) it.copy(catalogueLoading = true, catalogueError = null) else it
        }
        viewModelScope.launch {
            val result = StreamXClient.result { StreamXClient.current().cataloguePage(browseId, original.title, nextPage) }
            if (identity != listenerKey()) return@launch
            _detailStack.value = _detailStack.value.map { current ->
                if (current.browseId != browseId) current else result.fold({ batch ->
                    val oldSongs = (current.songs as? UiState.Success)?.data.orEmpty()
                    val newSongs = (batch.songs as? UiState.Success)?.data.orEmpty()
                    val songs = (oldSongs + newSongs).distinctBy { it.videoId }
                    val oldItems = current.sections.flatMap { it.items }
                    val entries = (oldItems + batch.sections.flatMap { it.items }).distinctBy { it.browseId }
                    val progressed = songs.size > oldSongs.size || entries.size > oldItems.size
                    batch.copy(songs = UiState.Success(songs),
                        sections = if (entries.isEmpty()) emptyList() else listOf(HomeShelf(current.title, entries)),
                        nextCataloguePage = batch.nextCataloguePage.takeIf { progressed })
                }, { current.copy(catalogueLoading = false, catalogueError = it.friendly()) })
            }
        }
    }

    suspend fun libraryRemovalInfo(browseId: String): com.music.streamx.data.extensions.ApkCatalogue.LibraryRemovalInfo? {
        val target=com.music.streamx.data.extensions.ApkCatalogue.parseBrowseId(browseId) ?: return null
        val config=SourceRegistry.config(target.configId) ?: return null
        return com.music.streamx.data.extensions.ApkCatalogue(config).libraryRemovalInfo(browseId)
    }
    suspend fun removeLibraryItem(browseId: String): Result<Unit> {
        val identity=listenerKey()
        val target=com.music.streamx.data.extensions.ApkCatalogue.parseBrowseId(browseId) ?: return Result.failure(IllegalArgumentException("Invalid library item"))
        val config=SourceRegistry.config(target.configId) ?: return Result.failure(IllegalStateException("Extension is unavailable"))
        val result=try {com.music.streamx.data.extensions.ApkCatalogue(config).removeLibraryItem(browseId);Result.success(Unit)}
            catch(cancelled: kotlinx.coroutines.CancellationException) {throw cancelled}
            catch(error: Exception) {Result.failure<Unit>(error)}
        if(identity==listenerKey()) {
            if(result.isSuccess) {
                _detailStack.value=_detailStack.value.filterNot {it.browseId==browseId}
                _playlists.value=_playlists.value.filterNot {it.browseId==browseId}
            }
            // A multi-album artist removal can fail after partial completion.
            // Always re-read the service rather than pretending nothing changed.
            loadLibrary()
            result.exceptionOrNull()?.let {android.widget.Toast.makeText(getApplication(),it.friendly(),android.widget.Toast.LENGTH_LONG).show()}
        }
        return result
    }

    /** Read a skin's collection in place without pushing a detail screen. */
    suspend fun loadSkinLibraryCollection(browseId: String): List<ShelfItem>? {
        val target = com.music.streamx.data.extensions.ApkCatalogue.parseBrowseId(browseId) ?: return null
        val config = SourceRegistry.config(target.configId) ?: error("Extension is unavailable")
        val catalogue = com.music.streamx.data.extensions.ApkCatalogue(config)
        val identity = listenerKey()
        val items = mutableListOf<ShelfItem>()
        var offset = 0
        repeat(200) {
            val page = catalogue.detail(browseId, offset = offset)
            check(identity == listenerKey()) { "Account changed" }
            items += page.sections.flatMap { it.items }
            val next = page.nextCataloguePage ?: return items.distinctBy { it.browseId ?: it.videoId ?: it.title }
            check(next > offset) { "Repeated collection page" }
            offset = next
        }
        error("Too many collection pages")
    }

    fun openDetail(
        browseId: String,
        title: String,
        subtitle: String = "",
        thumbnailUrl: String? = null,
        type: BrowseType = BrowseType.OTHER,
    ) {
        // A fast double tap used to push two identical loading pages and launch
        // two identical browse requests. Besides wasting the connection, both
        // completions then raced to update every matching browse id in the
        // stack. The page is pushed synchronously, so this closes that window
        // without suppressing a deliberate revisit after the first page loads.
        if (_detailStack.value.lastOrNull()?.let {
                it.browseId == browseId && it.songs is UiState.Loading
            } == true
        ) return
        com.music.streamx.data.extensions.ApkCatalogue.parseBrowseId(browseId)?.let { target ->
            val identity = listenerKey()
            _detailStack.value += DetailPage(browseId, title, subtitle, thumbnailUrl, UiState.Loading, type = type)
            viewModelScope.launch {
                val result = try {
                    val config = SourceRegistry.config(target.configId) ?: error("Extension is unavailable")
                    // Its track menu asks whether this extension can remove tracks.
                    if (target.kind == "playlist") com.music.streamx.data.sources.ApkExtensionSource.learnCapabilities(config)
                    Result.success(com.music.streamx.data.extensions.ApkCatalogue(config).detail(browseId, title))
                } catch (e: kotlinx.coroutines.CancellationException) { throw e }
                catch (e: Exception) { Result.failure<DetailPage>(e) }
                if (identity != listenerKey()) return@launch
                // A plugin playlist's page is the one place that says whether the
                // listener made it, the same as a YouTube one's — see [editablePlaylist].
                result.getOrNull()?.owned?.let { setPlaylistOwned(browseId, it) }
                _detailStack.value = _detailStack.value.map { page ->
                    if (page.browseId != browseId) page else result.getOrElse { page.copy(songs = UiState.Error(it.friendly())) }
                }
            }
            return
        }
        if (browseId.startsWith(SPOTIFY_PAGE_PREFIX)) {
            openSpotifyPage(browseId, title, subtitle, thumbnailUrl)
            return
        }
        if (browseId.removePrefix("VL").startsWith(StreamXClient.BROWSE_PREFIX)) {
            val identity = listenerKey()
            _detailStack.value += DetailPage(browseId, title, subtitle, thumbnailUrl, UiState.Loading, type = type)
            viewModelScope.launch {
                val next = StreamXClient.result { StreamXClient.current().detail(browseId, title, subtitle, thumbnailUrl) }
                if (identity != listenerKey()) return@launch
                _detailStack.value = _detailStack.value.map { page ->
                    if (page.browseId != browseId) page else next.getOrElse { page.copy(songs = UiState.Error(it.friendly())) }
                }
            }
            return
        }
        val resolved = browseTypeOf(browseId, type)
        _detailStack.value += DetailPage(
            browseId = browseId,
            title = title,
            subtitle = subtitle,
            thumbnailUrl = thumbnailUrl,
            songs = UiState.Loading,
            type = resolved,
        )
        viewModelScope.launch {
            var sections = emptyList<HomeShelf>()
            // Callers that open an artist from a track — the player, the
            // long-press menu — only have that track's cover art and its full
            // credit ("A, B & C") to hand, so the page swaps in the artist's
            // own picture and name once they arrive.
            var artwork: String? = null
            var name: String? = null
            /**
             * The credit line, when the page had to supply its own.
             *
             * Only a link tapped outside the app arrives with neither — see
             * [com.music.streamx.playback.MusicLink]. Every other caller was
             * looking at a card that already said this.
             */
            var credit: String? = null
            /** Set when the track list carries on past its first response. */
            var more: String? = null
            /** Tracks YouTube offers to round the playlist out — see [DetailPage.suggestedSongs]. */
            var suggested: List<Song> = emptyList()
            /** Whether this release is already saved — see [DetailPage.library]. */
            var library: LibraryState? = null
            /** YouTube's own "About" blurb — see [DetailPage.description]. */
            var description: String? = null
            /** Artist header stats — see [DetailPage.subscriberCountText]. */
            var subscriberCountText: String? = null
            var monthlyListenerCount: String? = null
            /** Whether this artist is subscribed to — see [DetailPage.subscription]. */
            var subscription: SubscriptionState? = null
            val localPlaylist = com.music.streamx.data.spotify.LocalPlaylistStore.getPlaylist(browseId)
            val remote = remoteLibrary(browseId)
            // A release downloaded whole and opened by its YouTube id — the
            // Playlists shelf, a search hit — used to wait on the network for
            // tracks already on the device, and showed an error with no
            // connection at all. Its downloaded copy goes up first; the
            // online listing replaces it if and when that arrives.
            val onDevice = if (localPlaylist == null && remote == null && !browseId.startsWith("local:")) {
                downloadedCopyOf(browseId)
            } else {
                emptyList()
            }
            if (onDevice.isNotEmpty()) {
                _detailStack.value = _detailStack.value.map {
                    if (it.browseId == browseId && it.songs is UiState.Loading) it.copy(songs = UiState.Success(onDevice)) else it
                }
            }
            if (localPlaylist != null) {
                name = localPlaylist.title
                credit = getApplication<Application>().getString(R.string.local_playlist_subtitle, localPlaylist.songs.size)
                artwork = localPlaylist.songs.firstOrNull { !it.thumbnailUrl.isNullOrBlank() }?.thumbnailUrl
            }
            val local = localPageState(browseId)
            val state = when {
                local != null -> local
                resolved == BrowseType.ARTIST -> {
                    YtMusicRepository.artistPage(browseId).fold(
                        onSuccess = { page ->
                            sections = page.sections
                            artwork = page.thumbnailUrl
                            name = page.name
                            description = page.description
                            subscriberCountText = page.subscriberCountText
                            monthlyListenerCount = page.monthlyListenerCount
                            subscription = page.subscription
                            if (page.songs.isEmpty()) {
                                UiState.Error(text(R.string.no_tracks_here))
                            } else {
                                UiState.Success(page.songs.withArtwork(thumbnailUrl ?: artwork))
                            }
                        },
                        onFailure = { UiState.Error(it.friendly()) },
                    )
                }
                else -> {
                    YtMusicRepository.browseSongs(browseId).fold(
                        onSuccess = { page ->
                            // Free here — the page that returned these rows is
                            // the one thing that states who made the playlist,
                            // so its own menu never has to go and ask. Recorded
                            // even when the listing came back empty.
                            page.owned?.let { setPlaylistOwned(browseId, it) }
                            // Only for the caller that had nothing: a card's own
                            // title is what the user just tapped, and must not
                            // be swapped for the header's wording underneath them.
                            page.header?.let { header ->
                                if (title.isBlank()) name = header.title
                                // Album cards often only carry the artist, while
                                // the page header also carries the release year.
                                // Prefer that richer line so the year appears
                                // directly below the artist on the album page.
                                if (resolved == BrowseType.ALBUM && header.subtitle.isNotBlank()) {
                                    credit = header.subtitle
                                } else if (subtitle.isBlank()) {
                                    credit = header.subtitle
                                }
                                if (thumbnailUrl == null) artwork = header.thumbnailUrl
                            }
                            description = page.description
                            if (page.songs.isEmpty()) {
                                UiState.Error(text(R.string.no_tracks_here))
                            } else {
                                more = page.continuation
                                suggested = page.suggested.withArtwork(thumbnailUrl ?: artwork)
                                library = page.library
                                UiState.Success(page.songs.withArtwork(thumbnailUrl ?: artwork))
                            }
                        },
                        onFailure = { UiState.Error(it.friendly()) },
                    )
                }
            }
            // Update by id — the user may have pushed another page meanwhile.
            // A page showing its downloaded copy takes only a real listing: a
            // failed or empty fetch leaves the downloaded tracks up.
            _detailStack.value = _detailStack.value.map {
                if (it.browseId == browseId &&
                    (it.songs is UiState.Loading || (onDevice.isNotEmpty() && state is UiState.Success))
                ) {
                    it.copy(
                        songs = state,
                        sections = sections,
                        thumbnailUrl = artwork ?: it.thumbnailUrl,
                        title = name ?: it.title,
                        subtitle = credit ?: it.subtitle,
                        suggestedSongs = suggested,
                        continuation = more,
                        library = library,
                        description = description,
                        subscriberCountText = subscriberCountText,
                        monthlyListenerCount = monthlyListenerCount,
                        subscription = subscription,
                    )
                } else {
                    it
                }
            }
            // Only once the first page is on screen: [fillIn] appends to it,
            // and has nothing to append to before this.
            more?.let { fillIn(browseId, it, thumbnailUrl ?: artwork) }
        }
    }

    /**
     * A Spotify playlist opened as an ordinary playlist page.
     *
     * Spotify's rows go up as soon as they are read, each marked as still
     * waiting on its YouTube Music version (see [isMatchPending]); the matches
     * then arrive in order and swap the rows in place, so the page is usable
     * before the last song has been found.
     */
    private fun openSpotifyPage(browseId: String, title: String, subtitle: String, thumbnailUrl: String?) {
        _detailStack.value += DetailPage(
            browseId = browseId,
            title = title,
            subtitle = subtitle,
            thumbnailUrl = thumbnailUrl,
            songs = UiState.Loading,
            type = BrowseType.PLAYLIST,
        )
        viewModelScope.launch {
            val playlistId = browseId.removePrefix(SPOTIFY_PAGE_PREFIX)
            fun open() = _detailStack.value.any { it.browseId == browseId }
            fun setSongs(songs: UiState<List<Song>>) {
                _detailStack.value = _detailStack.value.map {
                    if (it.browseId == browseId) it.copy(songs = songs) else it
                }
            }
            // The list only had a thumbnail; the full-size cover replaces it
            // once this page has asked for it.
            launch {
                val cover = runCatching { SpotifyLibrary.cover(playlistId) }.getOrNull() ?: return@launch
                _detailStack.value = _detailStack.value.map {
                    if (it.browseId == browseId) it.copy(thumbnailUrl = cover) else it
                }
            }
            val tracks = runCatching {
                SpotifyLibrary.tracks(playlistId) { soFar ->
                    setSongs(UiState.Success(soFar.map { it.asPendingSong() }))
                }
            }.getOrElse {
                setSongs(UiState.Error(it.message ?: text(R.string.failed)))
                return@launch
            }
            if (tracks.isEmpty()) {
                setSongs(UiState.Error(text(R.string.spotify_empty_tracks)))
                return@launch
            }
            val gate = Semaphore(SPOTIFY_MATCH_PARALLELISM)
            coroutineScope {
                tracks.forEachIndexed { index, track ->
                    launch {
                        gate.withPermit {
                            if (!open()) return@withPermit
                            val match = runCatching { SpotifyImporter.matchTrack(track) }.getOrNull()
                            val found = match?.copy(thumbnailUrl = match.thumbnailUrl ?: track.imageUrl)
                                ?: track.asPendingSong().let {
                                    it.copy(videoId = SPOTIFY_MISSING_PREFIX + track.id)
                                }
                            _detailStack.value = _detailStack.value.map { page ->
                                val list = (page.songs as? UiState.Success<List<Song>>)?.data
                                if (page.browseId == browseId && list != null && index < list.size) {
                                    page.copy(songs = UiState.Success(list.toMutableList().also { it[index] = found }))
                                } else page
                            }
                        }
                    }
                }
            }
        }
    }

    private fun SpotifyTrack.asPendingSong() = Song(
        videoId = SPOTIFY_PENDING_PREFIX + id,
        title = title,
        artist = artist,
        thumbnailUrl = imageUrl,
        durationText = durationMs.takeIf { it > 0 }?.let { ms ->
            "%d:%02d".format(ms / 60000, ms / 1000 % 60)
        },
        albumName = album,
    )

    /**
     * Pushes one album, playlist or artist out of a device folder as a page of
     * its own, with the rows the folder already had in hand — so it opens on
     * its tracks rather than on a skeleton while the folder is read again.
     * [reloadLocalDetail] keeps it current from there, like any `local:` page.
     */
    fun openLocalGroup(group: LocalGroup) {
        if (_detailStack.value.lastOrNull()?.browseId == group.browseId) return
        _detailStack.value += DetailPage(
            browseId = group.browseId,
            title = group.title,
            subtitle = group.subtitle,
            thumbnailUrl = group.thumbnailUrl,
            songs = if (group.songs.isEmpty()) UiState.Error(text(R.string.no_tracks_here)) else UiState.Success(group.songs),
            type = group.type,
        )
    }

    fun reloadLocalDetail(browseId: String) {
        viewModelScope.launch {
            val state = localPageState(browseId) ?: return@launch
            _detailStack.value = _detailStack.value.map {
                if (it.browseId == browseId) {
                    it.copy(songs = state)
                } else it
            }
        }
    }

    /**
     * The listing behind a page whose tracks come from this device or a file
     * server rather than YouTube — a device folder, a release downloaded whole,
     * a playlist in the app's own store, a remote library, or one album or
     * artist grouped out of a folder — or null for every other page.
     *
     * The one place each of those is read, so opening a page, refreshing it
     * and queueing it from a menu can't disagree about what is on it.
     */
    private suspend fun localPageState(browseId: String): UiState<List<Song>>? {
        val context = getApplication<Application>()
        fun nonEmpty(songs: List<Song>, empty: () -> String): UiState<List<Song>> =
            if (songs.isEmpty()) UiState.Error(empty()) else UiState.Success(songs)

        com.music.streamx.data.spotify.LocalPlaylistStore.getPlaylist(browseId)?.let { playlist ->
            return nonEmpty(playlist.songs) { text(R.string.spotify_import_empty_playlist) }
        }
        remoteLibrary(browseId)?.let { return remoteSongsState(it) }
        LocalGroups.folderOf(browseId)?.let { folderId ->
            // Read off a fresh listing of the folder it came from: a grouping
            // has nothing of its own to read, and whatever the folder says
            // when it can't be listed is what this page says too.
            return when (val folder = localPageState(folderId)) {
                is UiState.Success -> nonEmpty(LocalGroups.songsOf(browseId, folder.data)) { text(R.string.no_tracks_here) }
                null -> UiState.Error(text(R.string.no_tracks_here))
                else -> folder
            }
        }
        return when {
            Downloads.recordIdOf(browseId) != null ->
                nonEmpty(downloadedPlaylist(browseId)) { text(R.string.downloaded_playlist_empty) }
            browseId == "local:downloads" ->
                nonEmpty(Downloads.getDownloadedSongs(context)) { "No downloaded tracks" }
            browseId == CACHE_FOLDER_BROWSE_ID -> cachedSongsState()
            browseId == "local:all" -> if (!LocalMediaRepository.hasStoragePermission(context)) {
                UiState.Error(text(R.string.storage_required_read))
            } else {
                nonEmpty(LocalMediaRepository.getLocalMusic(context)) { text(R.string.no_local_audio_found) }
            }
            else -> null
        }
    }

    /**
     * The tracks of the downloaded playlist [browseId] names that are still on
     * disk, in the order the playlist had.
     *
     * The download record already names those files, so opening this page must
     * not scan every unrelated download first. [Downloads.getCollectionSongs]
     * verifies only this collection and still collapses aliases that point to
     * the same saved file.
     *
     * Empty is the honest answer for a record whose files have all been deleted
     * from under it, and callers turn that into an empty-state message rather than
     * into a blank page.
     */
    private suspend fun downloadedPlaylist(browseId: String): List<Song> {
        val id = Downloads.recordIdOf(browseId) ?: return emptyList()
        return Downloads.getCollectionSongs(getApplication(), id)
    }

    /**
     * The downloaded copy of the release YouTube calls [browseId], if there
     * is one. A playlist is filed under whichever id it was downloaded from,
     * which may or may not carry the `VL` its page id does.
     */
    private suspend fun downloadedCopyOf(browseId: String): List<Song> {
        val bare = browseId.removePrefix("VL")
        val id = Downloads.collections.value.keys.firstOrNull { it.removePrefix("VL") == bare } ?: return emptyList()
        return Downloads.getCollectionSongs(getApplication(), id)
    }

    /**
     * The Cached songs folder: the YouTube and JioSaavn tracks the song cache
     * is holding, most recently played first. See [AudioCache.cachedSongs].
     */
    private suspend fun cachedSongsState(): UiState<List<Song>> {
        val songs = AudioCache.cachedSongs().map { it.song }
        return if (songs.isEmpty()) UiState.Error(text(R.string.cached_songs_empty)) else UiState.Success(songs)
    }

    /**
     * Follows a detail page's continuations in the background, appending each
     * page to what is already being read.
     *
     * A playlist of a few hundred tracks is several round trips, and taking
     * them before showing anything meant a spinner for all of them. Growing
     * the list underneath the reader is also what makes it safe to keep
     * following continuations however deep the playlist runs — nobody is
     * waiting on the last one — so a playlist past YouTube's ~1000-track,
     * ten-page shelf still loads to the end instead of stopping there.
     *
     * Stops the moment the page leaves the stack: there is no one to append
     * for. A page that adds nothing new (below) is the other exit, for a feed
     * that loops back on itself instead of running dry.
     */
    private fun fillIn(browseId: String, token: String, artworkFallback: String?) {
        fillInJobs[browseId]?.cancel()
        fillInJobs[browseId] = viewModelScope.launch {
            // Publish and draw the first response before spending the shared
            // connection on page two. On a long playlist the continuation can
            // otherwise start in the same main-loop turn as the state update,
            // making a ready first screen feel as if it were still loading.
            delay(150)
            var next: String? = token
            while (next != null) {
                val fetched = YtMusicRepository.moreSongs(next).getOrNull() ?: return@launch
                // Repository calls may finish after cancellation. Do not let
                // an in-flight UI page append after the playback service has
                // taken the continuation over.
                currentCoroutineContext().ensureActive()
                // The same page can be on the stack twice (opened again from
                // inside itself). One walk per browseId grows every copy; a
                // second walk replaces the first, so each copy merges by key
                // and only takes a token from a page that taught it something.
                val stack = _detailStack.value
                if (stack.none { it.browseId == browseId }) return@launch
                var progressed = false
                val updated = stack.map { current ->
                    if (current.browseId != browseId) return@map current
                    val existing = (current.songs as? UiState.Success)?.data ?: return@map current
                    val known = existing.mapTo(HashSet()) { it.contextEntryKey() }
                    val added = fetched.songs
                        .filter { known.add(it.contextEntryKey()) }
                        .withArtwork(artworkFallback)
                    val knownVideoIds = (existing + added).mapTo(HashSet()) { it.videoId }
                    // Suggestions can arrive on a later page than the real
                    // tracks, once the playlist's own continuation runs dry —
                    // see parsePlaylistShelf — so they're tracked separately
                    // rather than folded into [known].
                    val knownSuggested = current.suggestedSongs.mapTo(HashSet()) { it.videoId }
                    val addedSuggested = fetched.suggested
                        .filter { it.videoId !in knownVideoIds && knownSuggested.add(it.videoId) }
                        .withArtwork(artworkFallback)
                    if (added.isEmpty() && addedSuggested.isEmpty()) return@map current
                    progressed = true
                    current.copy(
                        songs = UiState.Success(existing + added),
                        suggestedSongs = current.suggestedSongs + addedSuggested,
                        continuation = fetched.continuation,
                    )
                }
                // A page with nothing new for any copy means the feed has looped
                // back rather than run dry with a token still attached.
                if (!progressed) return@launch
                _detailStack.value = updated
                next = fetched.continuation
            }
        }.also { job ->
            job.invokeOnCompletion {
                if (fillInJobs[browseId] === job) fillInJobs.remove(browseId)
            }
        }
    }

    /**
     * Applies the service-owned canonical context to its matching open page.
     *
     * The service only materialises a small queue window, while its registry
     * keeps the complete canonical list. Mirroring that registry here avoids a
     * page that appears to stop growing as soon as playback starts.
     */
    private fun applyBoundedContextSnapshot(snapshot: BoundedContextRequests.Snapshot) {
        val browseId = snapshot.request.source.id ?: return
        val stack = _detailStack.value
        val index = stack.indexOfLast { it.browseId == browseId }
        if (index < 0) return
        val page = stack[index]
        if (page.type != BrowseType.ALBUM && page.type != BrowseType.PLAYLIST) return
        // Exactly one loader walks the continuation. While the service owns
        // it the page's own fill-in stops; when the service gives up (or never
        // took it — a party, a Cast race) the page resumes from the newest token.
        if (snapshot.serviceOwned) {
            if (snapshot.continuationToken != null) fillInJobs.remove(browseId)?.cancel()
        } else if (snapshot.released && snapshot.continuationToken != null &&
            fillInJobs[browseId]?.isActive != true
        ) {
            fillIn(browseId, snapshot.continuationToken, page.thumbnailUrl)
        }
        val existing = (page.songs as? UiState.Success)?.data ?: return
        val known = existing.mapTo(HashSet()) { it.contextEntryKey() }
        val added = snapshot.songs
            .filter { known.add(it.contextEntryKey()) }
            .withArtwork(page.thumbnailUrl)
        // Never move the page's token backwards: a page whose own fill-in is
        // ahead of the service would otherwise hand a later tap a token for
        // songs it already has, which the registry rejects as a duplicate page.
        val continuation = if (snapshot.songs.size >= existing.size) snapshot.continuationToken else page.continuation
        if (added.isEmpty() && page.continuation == continuation) return
        _detailStack.value = stack.toMutableList().also {
            it[index] = page.copy(
                songs = UiState.Success(existing + added),
                continuation = continuation,
            )
        }
    }

    /**
     * Returns the newest page snapshot for a bounded playback handoff. The
     * page's own fill-in keeps running until the service reports that it has
     * taken the continuation over (see [applyBoundedContextSnapshot]); the
     * tap may yet go down another path, and cancelling here left the page
     * permanently cut short whenever it did.
     */
    fun takePlaybackContext(browseId: String): DetailPage? {
        val stack = _detailStack.value
        val index = stack.indexOfLast { it.browseId == browseId }
        if (index < 0) return null
        val mirrored = BoundedContextRequests.snapshot.value
            ?.takeIf { it.request.source.id == browseId }
        if (mirrored != null) {
            applyBoundedContextSnapshot(mirrored)
            return _detailStack.value.getOrNull(index)
        }
        return stack[index]
    }

    /**
     * An album's track listing doesn't repeat the cover on every row — the
     * page carries it once — so rows arrive with no artwork and stay blank
     * through to the queue and the notification. Fall back to the page's.
     */
    private fun List<Song>.withArtwork(fallback: String?): List<Song> {
        if (fallback == null) return this
        return map { if (it.thumbnailUrl == null) it.copy(thumbnailUrl = fallback) else it }
    }

    /**
     * Home and Explore cards don't say what they point at, and an artist
     * fetched as an album only yields the five songs on its landing page.
     * YouTube's browse ids are prefixed by kind, so use that.
     *
     * Public because the long-press menus ask the same question of a card
     * before offering to queue what is behind it — an artist is not a running
     * order, so it gets no queue actions.
     */
    fun browseTypeOf(browseId: String, fallback: BrowseType = BrowseType.OTHER): BrowseType =
        Companion.browseTypeOf(browseId, fallback)

    /**
     * Every track behind an album or playlist, handed to [onResult] once it is
     * all in.
     *
     * What a long-press on a card is acting on. A card has nothing but a browse
     * id — its page was never opened, so there is no track list anywhere to
     * read — and "add this album to the queue" means the whole album, so this
     * follows continuations to the end rather than taking the first page.
     *
     * Runs in [viewModelScope], not the caller's: the sheet the tap came from
     * closes immediately, and a three-hundred-track playlist must not be
     * abandoned halfway because of it.
     */
    fun collectSongs(
        browseId: String,
        artworkFallback: String? = null,
        onResult: (Result<List<Song>>) -> Unit,
    ) {
        viewModelScope.launch {
            val context = getApplication<Application>()
            val remote = remoteLibrary(browseId)
            val result = when {
                com.music.streamx.data.extensions.ApkCatalogue.parseBrowseId(browseId) != null -> {
                    try {
                        val target = checkNotNull(com.music.streamx.data.extensions.ApkCatalogue.parseBrowseId(browseId))
                        val config = SourceRegistry.config(target.configId) ?: error("Extension is unavailable")
                        val catalogue = com.music.streamx.data.extensions.ApkCatalogue(config)
                        val tracks = mutableListOf<Song>()
                        var offset = 0
                        var pages = 0
                        do {
                            val page = catalogue.detail(browseId, offset = offset)
                            tracks += (page.songs as? UiState.Success)?.data.orEmpty()
                            val next = page.nextCataloguePage
                            if (next == null || next <= offset) break
                            offset = next
                        } while (++pages < 100)
                        Result.success(tracks.distinctBy { it.videoId })
                    } catch (e: kotlinx.coroutines.CancellationException) { throw e }
                    catch (e: Exception) { Result.failure<List<Song>>(e) }
                }
                remote != null -> runCatching {
                    remote.songs().ifEmpty { error(text(remote.emptyRes)) }
                }
                Downloads.recordIdOf(browseId) != null -> runCatching {
                    downloadedPlaylist(browseId).ifEmpty {
                        error(text(R.string.downloaded_playlist_empty))
                    }
                }
                browseId == "local:downloads" -> runCatching {
                    Downloads.getDownloadedSongs(context)
                        .ifEmpty { error("No downloaded tracks") }
                }
                browseId == CACHE_FOLDER_BROWSE_ID -> runCatching {
                    AudioCache.cachedSongs().map { it.song }
                        .ifEmpty { error(text(R.string.cached_songs_empty)) }
                }
                browseId == "local:all" -> runCatching {
                    if (!LocalMediaRepository.hasStoragePermission(context)) {
                        error(text(R.string.storage_required_read))
                    }
                    LocalMediaRepository.getLocalMusic(context)
                        .ifEmpty { error(text(R.string.no_local_audio_found)) }
                }
                browseId.removePrefix("VL").startsWith(StreamXClient.BROWSE_PREFIX) -> StreamXClient.result {
                    (StreamXClient.current().detail(browseId, "", "", artworkFallback).songs as UiState.Success).data
                }
                else -> when (val local = localPageState(browseId)) {
                    is UiState.Success -> Result.success(local.data)
                    is UiState.Error -> Result.failure(IllegalStateException(local.message))
                    UiState.Loading -> Result.failure(IllegalStateException(text(R.string.no_tracks_here)))
                    null -> YtMusicRepository.allSongs(browseId)
                }
            }
            onResult(result.map { it.withArtwork(artworkFallback) })
        }
    }

    /** Pops one page; returns false when there was nothing to pop. */
    fun closeDetail(): Boolean {
        val stack = _detailStack.value
        if (stack.isEmpty()) return false
        _detailStack.value = stack.dropLast(1)
        return true
    }

    /**
     * Loads the channel list, unless it is already in hand.
     *
     * @param force refetch even if it is — after a switch, since the list
     *   itself reports which channel is active.
     */
    fun loadChannels(force: Boolean = false) {
        if (usingStreamX) return
        if (!_signedIn.value) return
        if (_channelsLoading.value) return
        if (!force && _channels.value.isNotEmpty()) return
        viewModelScope.launch {
            _channelsLoading.value = true
            YtMusicRepository.accountChannels()
                .onSuccess {
                    _channels.value = it
                    persistDetectedProfiles(it)
                }
            _channelsLoading.value = false
        }
    }

    /**
     * Acts as [channel] from here on.
     *
     * Everything already on screen belongs to the channel being left — its
     * library, its hearts, its playlists — so the switch clears them and
     * refetches rather than letting the new identity's pages arrive one at a
     * time on top of the old one's.
     */
    fun selectChannel(channel: AccountChannel) {
        val accountId = _activeAccountId.value ?: return
        val profile = YouTubeProfile(
            profileId = profileId(channel.pageId, channel.dataSyncId, channel.name),
            name = channel.name, handle = channel.subtitle, avatar = channel.thumbnailUrl,
            pageId = channel.pageId, dataSyncId = channel.dataSyncId,
            isBrandAccount = channel.pageId != null,
        )
        selectProfile(accountId, profile.profileId, profile)
    }

    /**
     * A session lifted out of the in-app browser — a fresh sign-in, or the same
     * login now pointed at a different channel.
     *
     * One path for both because they differ in exactly one thing: whether the
     * cookie is new. What follows — adopt the identity the captured page
     * reported, remember it, and refetch everything that belongs to a listener
     * — is the same work either way.
     */
    fun onWebSession(
        session: CapturedSession,
        mode: WebSessionMode,
        onComplete: (Boolean) -> Unit = {},
    ) {
        if (!session.loggedIn) {
            onComplete(false)
            return
        }
        playlistTransfer.close()
        playlistSync.cancelWork()
        val oldAccountId = _activeAccountId.value
        val oldProfileId = _activeProfileId.value
        viewModelScope.launch {
            // Validate the exact cookie/profile pair before writing any of it.
            // The old implementation persisted first and unconditionally set
            // signedIn=true; a half-finished channel chooser therefore became
            // a durable broken "Personal" account.
            Innertube.cookie = session.cookie
            Innertube.adoptSessionScope(
                pageId = session.pageId,
                dataSyncId = session.dataSyncId,
                authUser = session.authUser,
                visitorData = session.visitorData,
                clientVersion = session.clientVersion,
                loggedIn = true,
            )
            Innertube.selectChannel(session.pageId, session.dataSyncId, session.authUser)

            val account = withTimeoutOrNull(20_000L) {
                var result = YtMusicRepository.account()
                if (result.isFailure) {
                    delay(750L)
                    result = YtMusicRepository.account()
                }
                result.getOrNull()
            }
            if (account == null) {
                restoreActiveSession(oldAccountId, oldProfileId)
                onComplete(false)
                return@launch
            }

            // A channel switch is another identity under the same Google
            // login, never a new Google account. For a fresh sign-in, matching
            // a non-empty email upgrades the existing entry instead of adding
            // a duplicate after cookies rotate.
            val accountId = when (mode) {
                WebSessionMode.SWITCH_CHANNEL -> oldAccountId
                WebSessionMode.SIGN_IN -> authStore.sessions.firstOrNull {
                    account.email.isNotBlank() && it.email.equals(account.email, ignoreCase = true)
                }?.accountId ?: sessionId(session.cookie, null)
            }
            if (accountId == null) {
                restoreActiveSession(oldAccountId, oldProfileId)
                onComplete(false)
                return@launch
            }
            val previous = authStore.sessions.firstOrNull { it.accountId == accountId }
            val selectedProfileId = profileId(session.pageId, session.dataSyncId, account.name)
            val profile = YouTubeProfile(
                profileId = selectedProfileId,
                name = account.name,
                handle = account.email,
                avatar = account.thumbnailUrl,
                pageId = session.pageId,
                dataSyncId = session.dataSyncId,
                authUser = session.authUser,
                isBrandAccount = session.pageId != null,
            )
            val hasRealIdentity = profile.pageId != null || profile.dataSyncId != null
            val profiles = previous?.profiles.orEmpty().filterNot { known ->
                known.profileId == profile.profileId ||
                    (hasRealIdentity && known.profileId.startsWith("profile:"))
            } + profile
            val stored = GoogleAccountSession(
                accountId = accountId,
                cookie = session.cookie,
                name = account.name,
                email = account.email,
                profiles = profiles,
                activeProfileId = profile.profileId,
            )

            val wasSignedIn = _signedIn.value
            if (wasSignedIn) cacheCurrentListener()
            authStore.upsertSession(stored)
            authStore.cookie = session.cookie // legacy compatibility only
            _googleAccounts.value = authStore.sessions
            _activeAccountId.value = accountId
            _activeProfileId.value = profile.profileId
            _selectedChannelKey.value = profile.profileId
            _selectedChannelName.value = account.name
            if (!usingStreamX) _account.value = account
            _channels.value = emptyList()
            _signedIn.value = if (usingExtension) false else if (usingStreamX) (usingCloud || StreamXSession.state.value.personalAccount) else true

            // Every resolver verdict and personalised page belongs to the
            // identity that was active before validation succeeded.
            StreamResolver.onSessionChanged()
            if (wasSignedIn) clearListenerState()
            reloadForAccount()
            loadChannels(force = true)
            onComplete(true)
        }
    }

    /** Put request signing back exactly as it was after a rejected candidate. */
    private fun restoreActiveSession(accountId: String?, selectedProfileId: String?) {
        val account = authStore.sessions.firstOrNull { it.accountId == accountId }
        val profile = account?.profiles?.firstOrNull { it.profileId == selectedProfileId }
        Innertube.cookie = account?.cookie
        Innertube.selectChannel(profile?.pageId, profile?.dataSyncId, profile?.authUser)
    }

    /** Selects an identity without ever allowing a response to replace it. */
    fun selectProfile(accountId: String, selectedProfileId: String, supplied: YouTubeProfile? = null) {
        val source = _googleAccounts.value.firstOrNull { it.accountId == accountId } ?: return
        val profile = supplied ?: source.profiles.firstOrNull { it.profileId == selectedProfileId } ?: return
        if (accountId == _activeAccountId.value && profile.profileId == _activeProfileId.value) return
        cacheCurrentListener()
        val index = source.profiles.indexOfFirst { it.profileId == profile.profileId }
        val updatedProfiles = if (index >= 0) {
            source.profiles.toMutableList().apply { set(index, profile) }
        } else {
            source.profiles + profile
        }
        val updated = source.copy(
            profiles = updatedProfiles,
            activeProfileId = profile.profileId,
        )
        authStore.upsertSession(updated)
        authStore.select(accountId, profile.profileId)
        _googleAccounts.value = authStore.sessions
        _activeAccountId.value = accountId
        _activeProfileId.value = profile.profileId
        authStore.cookie = updated.cookie
        Innertube.cookie = updated.cookie
        Innertube.selectChannel(profile.pageId, profile.dataSyncId, profile.authUser)
        _selectedChannelKey.value = profile.profileId
        _selectedChannelName.value = profile.name
        StreamResolver.onSessionChanged()
        clearListenerState(restoreCached = true)
        reloadForAccount()
    }

    /** Returns false at an edge, allowing the avatar to play its elastic cue. */
    fun stepProfile(forward: Boolean): Boolean {
        val target = adjacentProfile(_googleAccounts.value, _activeAccountId.value, _activeProfileId.value, forward)
            ?: return false
        selectProfile(target.first, target.second)
        return true
    }

    fun removeAccount(accountId: String) {
        val fallback = authStore.removeAccount(accountId)
        _googleAccounts.value = authStore.sessions
        if (fallback == null) { signOut(); return }
        selectProfile(fallback.accountId, fallback.activeProfileId ?: fallback.profiles.firstOrNull()?.profileId ?: return)
    }

    private fun persistDetectedProfiles(channels: List<AccountChannel>) {
        val accountId = _activeAccountId.value ?: return
        val current = authStore.sessions.firstOrNull { it.accountId == accountId } ?: return
        val detected = channels.map { channel -> YouTubeProfile(
            profileId(channel.pageId, channel.dataSyncId, channel.name), channel.name, channel.subtitle,
            channel.thumbnailUrl, channel.pageId, channel.dataSyncId,
            isBrandAccount = channel.pageId != null,
        ) }
        // A profile captured before its channel existed carries a provisional,
        // name-hash id (see profileId()) rather than the pageId/dataSyncId Google
        // reports here once the channel is live. Once Google actually reports a
        // channel, that placeholder is stale — it would have been listed here
        // too if it still lacked real ids — and it's dropped rather than kept
        // alongside the identity it was standing in for. An empty response
        // (channel not created yet, or a transient miss) must never drop it.
        val stalePlaceholder = if (detected.isEmpty()) emptySet() else current.profiles
            .filter { it.profileId.startsWith("profile:") }
            .map { it.profileId }
            .toSet()
        // Keep the captured active identity if Google's endpoint temporarily
        // omits it; an empty/partial response must never erase a selection.
        val profiles = (current.profiles.filter { known ->
            known.profileId !in stalePlaceholder && detected.none { it.profileId == known.profileId }
        } + detected)
        val selected = when {
            current.activeProfileId != null && current.activeProfileId !in stalePlaceholder -> current.activeProfileId
            current.activeProfileId in stalePlaceholder -> detected.singleOrNull()?.profileId
            else -> null
        } ?: profiles.firstOrNull()?.profileId
        authStore.upsertSession(current.copy(profiles = profiles, activeProfileId = selected), activate = false)
        _googleAccounts.value = authStore.sessions
    }

    /**
     * Drops everything on screen that belongs to the identity being left.
     *
     * The hearts, the playlists and the library are all answers to "who is
     * asking", so keeping them across a switch shows the new channel the old
     * one's music until each page happens to be refetched.
     */
    private fun cacheCurrentListener() {
        val key = listenerKey() ?: return
        listenerCache[key] = ListenerSnapshot(
            _account.value, _library.value, _history.value, _playlists.value, _playlistOwned.value,
        )
    }

    private fun searchHistoryScope(): String = if (usingCloud) "cloudstream:${com.music.streamx.data.cloudstream.CloudLibrary.identity()}" else if (usingExtension) "extension:${SourceRegistry.selectedExtensionId.value}" else if (usingStreamX) {
        val config = SourceRegistry.configs.value.firstOrNull { it.kind == SourceKind.STREAMX }
        "streamx:${config?.id}:${StreamXSession.state.value.userId ?: "guest"}"
    } else listenerKey() ?: "youtube"

    private fun listenerKey(): String? = if (usingCloud) "cloudstream:${com.music.streamx.data.cloudstream.CloudLibrary.identity()}" else if (usingExtension) "extension:${SourceRegistry.selectedExtensionId.value}:${extensionRevision.get()}" else if (usingStreamX) "streamx:${StreamXSession.state.value.scope}" else _activeAccountId.value?.let { accountId ->
        _activeProfileId.value?.let { profileId -> "$accountId:$profileId" }
    }

    private fun clearListenerState(restoreCached: Boolean = false) {
        _account.value = null
        likedSyncJob?.cancel()
        LikeState.clear()
        _playlistsLoading.value = false
        _playlistsError.value = null
        _playlists.value = emptyList()
        _playlistOwned.value = emptyMap()
        ownershipInFlight.clear()
        _songMenu.value = null
        _library.value = UiState.Loading
        _history.value = UiState.Loading
        _historyShelves.value = emptyList()
        if (restoreCached) listenerCache[listenerKey()]?.let { cached ->
            _account.value = cached.account
            _library.value = cached.library
            _history.value = cached.history
            _playlists.value = cached.playlists
            _playlistOwned.value = cached.owned
        }
    }

    /**
     * Everything that is "the signed-in listener's", refetched.
     *
     * The scope comes first, and inside one coroutine rather than beside them:
     * which channel the session acts as decides what "the library" and "the
     * history" even refer to, so loading them first and scoping second shows
     * the listener the wrong account's music and then silently disagrees with
     * itself.
     */
    private fun reloadForAccount() {
        viewModelScope.launch {
            Innertube.ensureSessionScope()
            loadHome()
            loadLibrary()
            loadAccount()
            loadPlaylists()
        }
    }

    fun signOut() {
        if (usingStreamX) { StreamXSession.signOut(); return }
        // In a multi-account install, sign out removes only the current Google
        // session and restores another one. A legacy single-account install
        // retains the familiar full sign-out behaviour.
        val current = _activeAccountId.value
        if (current != null && authStore.sessions.size > 1) {
            removeAccount(current)
            return
        }
        authStore.signOut()
        Innertube.cookie = null
        // The mirror image: verdicts reached with a session in hand say nothing
        // about what an anonymous walk will be told, and the clients stood down
        // for refusing the session deserve a fresh hearing without it.
        StreamResolver.onSessionChanged()
        _signedIn.value = false
        _account.value = null
        Innertube.selectChannel(null, null)
        _channels.value = emptyList()
        _selectedChannelKey.value = null
        _selectedChannelName.value = null
        _googleAccounts.value = emptyList()
        _activeAccountId.value = null
        _activeProfileId.value = null
        _library.value = UiState.Loading
        // Ratings and playlists belong to the account that just left; keeping
        // them would show the next signed-in user someone else's hearts.
        likedSyncJob?.cancel()
        LikeState.clear()
        _playlists.value = emptyList()
        _playlistOwned.value = emptyMap()
        ownershipInFlight.clear()
        _songMenu.value = null
        loadHome()
    }

    private fun Throwable.friendly(): String = when {
        message?.contains("resolve host", true) == true ||
            message?.contains("Unable to resolve", true) == true -> text(R.string.no_internet_connection)
        message?.contains("401") == true || message?.contains("403") == true ->
            text(R.string.youtube_request_rejected)
        else -> message ?: text(R.string.something_went_wrong)
    }

    private fun text(id: Int): String = getApplication<Application>().getString(id)
}
