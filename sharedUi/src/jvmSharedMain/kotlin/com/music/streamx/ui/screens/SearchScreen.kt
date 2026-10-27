package com.music.streamx.ui.screens

import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import com.music.streamx.ui.skins.liquidPress
import androidx.compose.runtime.derivedStateOf
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.draw.drawBehind
import com.music.streamx.ui.skins.SkinIcon as Icon
import com.music.streamx.ui.components.contextClick
import androidx.compose.ui.unit.Dp
import com.music.streamx.sharedui.resources.*
import org.jetbrains.compose.resources.stringResource
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import com.music.streamx.ui.components.longPressMenuClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.NorthWest
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import coil3.compose.AsyncImage
import com.music.streamx.data.model.BrowseItem
import com.music.streamx.data.model.BrowseType
import com.music.streamx.data.model.ROW_ART_PX
import com.music.streamx.data.model.SearchFilter
import com.music.streamx.data.model.artworkAt
import com.music.streamx.data.model.SearchResult
import com.music.streamx.data.model.Song
import com.music.streamx.data.model.playingRowMatcher
import com.music.streamx.data.model.UiState
import com.music.streamx.data.model.SearchHistoryEntity
import com.music.streamx.data.model.EntityType
import com.music.streamx.ui.components.MessageState
import com.music.streamx.ui.components.PAGE_GUTTER
import com.music.streamx.ui.components.RowMoreButton
import com.music.streamx.ui.components.PlayingAccent
import com.music.streamx.ui.components.ROW_DIVIDER_INSET
import com.music.streamx.ui.components.SearchField
import com.music.streamx.ui.components.SongRow
import com.music.streamx.ui.components.SearchPlayingBars
import com.music.streamx.ui.components.thumbnailBorder
import com.music.streamx.ui.components.songListSkeleton
import com.music.streamx.ui.haptics.Haptic
import com.music.streamx.ui.haptics.rememberHaptics
import java.util.Locale

@Composable
fun SearchScreen(
    query: String,
    onQueryChange: (String) -> Unit,
    filter: SearchFilter,
    currentSong: Song?,
    isPlaying: Boolean,
    onFilterChange: (SearchFilter) -> Unit,
    results: UiState<List<SearchResult>>?,
    loadingMore: Boolean,
    onLoadMore: () -> Unit,
    listState: LazyListState,
    scrollResetTrigger: Int,
    focusRequested: Boolean,
    onFocusHandled: () -> Unit,
    onSongClick: (List<Song>, Int) -> Unit,
    onSongLongPress: (Song) -> Unit,
    onSongSwipe: (Song) -> Unit,
    onTopResultPlay: (Song) -> Unit,
    onTopResultPlaylist: (Song) -> Unit,
    onBrowseClick: (BrowseItem) -> Unit,
    /**
     * Holding an album or playlist hit rather than tapping it — the same menu
     * the shelves open, so a release found by searching can go on the queue
     * without a trip through its page.
     */
    onBrowseLongPress: ((BrowseItem) -> Unit)? = null,
    history: List<SearchHistoryEntity>,
    suggestions: List<String>,
    typeaheadResults: List<SearchResult>,
    onSubmit: () -> Unit,
    onSuggestionClick: (String) -> Unit,
    onHistoryClick: (SearchHistoryEntity) -> Unit,
    onHistoryRemove: (String) -> Unit,
    onHistoryClear: () -> Unit,
    /** Long-press handler for typeahead rows — opens the song actions sheet. */
    onTypeaheadLongPress: ((Song) -> Unit)? = null,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues,
    /**
     * Room above the search field. The phone's frosted bar floats over the top
     * of the tab, so the field has to clear it; the desktop has no such bar.
     */
    topPadding: Dp,
    /**
     * Whether the page draws its own search field. The desktop's sidebar has
     * the one search box the window needs, so the page there is results only.
     */
    showField: Boolean = true,
    /**
     * Whether the page is searching the device's Local Music folder instead of
     * YouTube. The phone's top bar flips this; the desktop never does.
     */
    searchingLibrary: Boolean = false,
    supportsVideos: Boolean = true,
    tracksOnly: Boolean = false,
    extensionName: String? = null,
    /** Whether the page is showing the enabled addons' combined results; see [addonResults]. */
    searchingAddons: Boolean = false,
    /** The Addons source's tracks for the submitted query; null until one is submitted. */
    addonResults: UiState<List<Song>>? = null,
    /** Words searched in the Addons tab before, newest first; shown while the field is empty. */
    addonHistory: List<String> = emptyList(),
    onAddonHistoryClick: (String) -> Unit = {},
    onAddonHistoryRemove: (String) -> Unit = {},
    onAddonHistoryClear: () -> Unit = {},
    /** The Library source's tracks for [query]; null while nothing is typed. */
    libraryResults: UiState<List<Song>>? = null,
    /** Files on the device are a running order to play through, not a station seed. */
    onLibrarySongClick: (List<Song>, Int) -> Unit = onSongClick,
    /**
     * The YouTube / Library switcher, full width at the head of the page. Up
     * only while the field is empty: once something is typed the page is
     * about the results, and the switcher would just push them down.
     */
    sourceSwitcher: (@Composable () -> Unit)? = null,
    extensionCategories: List<com.music.streamx.data.model.ShelfItem> = emptyList(),
    onExtensionCategoryClick: (com.music.streamx.data.model.ShelfItem) -> Unit = {},
    /**
     * Apple's own header: the large title and account, the field under it with
     * a ✕ to leave while searching, and Plugin | Library only while typing.
     * [appleSources] are the two names, [appleSource] which is picked.
     */
    appleAccount: (@Composable () -> Unit)? = null,
    appleSourceSwitch: (@Composable () -> Unit)? = null,
) {
    val appleSearch = com.music.streamx.ui.skins.LocalSkinRenderer.current is com.music.streamx.ui.skins.SkinArtworkPages
    // Apple's field in use: focused, or holding a query. Its recents show only then.
    var appleActive by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    // Tapping the search tab from the nav bar sets focusRequested;
    // respond by focusing the field and opening the keyboard.
    LaunchedEffect(focusRequested) {
        if (focusRequested && showField) {
            focusRequester.requestFocus()
            keyboardController?.show()
            onFocusHandled()
        }
    }
    // Search keeps one list state while its contents change. Reset it for each
    // new request so choosing a recent search cannot inherit the history's
    // previous scroll position (or a previous result page's position).
    LaunchedEffect(scrollResetTrigger) {
        if (scrollResetTrigger > 0) listState.scrollToItem(0)
    }
    // A non-empty suggestion list means the field is mid-edit — see
    // MainViewModel.suggestions. Nothing below it is worth showing while it is
    // up: the results are for whatever was searched before this edit began,
    // and so are the filter tabs above them.
    val suggesting = suggestions.isNotEmpty() && !searchingLibrary && !searchingAddons
    // Live media results arrive from the parallel typeahead pipeline; show
    // them only while the user is still typing (suggestions visible), so they
    // appear as a dropdown beneath the text completions rather than floating
    // after the search has committed.
    val showTypeahead = typeaheadResults.isNotEmpty() && suggesting
    LaunchedEffect(listState, results, loadingMore, searchingLibrary, searchingAddons) {
        if (results !is UiState.Success || searchingLibrary || searchingAddons) return@LaunchedEffect
        snapshotFlow {
            val layout = listState.layoutInfo
            (layout.visibleItemsInfo.lastOrNull()?.index ?: -1) to layout.totalItemsCount
        }.collect { (lastVisible, total) ->
            if (!loadingMore && total > 0 && lastVisible >= total - 4) onLoadMore()
        }
    }
    // Apple's head floats over the list on frosted glass rather than sitting above it.
    val appleHead = appleSearch && appleAccount != null
    val glassBackdrop = if (appleHead) com.music.streamx.ui.skins.LocalSkinGlassFactory.current() else null
    var appleHeadPx by remember { mutableStateOf(0) }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val appleScrolled by remember(listState) { derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0 } }
    val frostAlpha by androidx.compose.animation.core.animateFloatAsState(if (appleScrolled) 1f else 0f, label = "searchFrost")
    Box(modifier.fillMaxSize()) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Search field and filter tabs stay fixed at the top, outside the
        // scrolling list, so they're always reachable rather than scrolling
        // away with the results or recent searches beneath them.
        // The FrostedTopBar is visible on this tab (showing "Search"), so we
        // clear it fully — status bar inset + bar height + breathing gap — so
        // the search field sits cleanly below the bar instead of overlapping it.
        if (!appleHead)
        Column(modifier = Modifier.padding(top = topPadding)) {
            if (showField) {
                SearchField(
                    query = query,
                    onQueryChange = onQueryChange,
                    onSubmit = onSubmit,
                    focusRequester = focusRequester,
                    placeholder = if (searchingLibrary) {
                        stringResource(Res.string.search_library_hint)
                    } else if (searchingAddons) {
                        stringResource(Res.string.search_addons_hint)
                    } else {
                        stringResource(Res.string.search_hint)
                    },
                    modifier = Modifier.padding(start = PAGE_GUTTER, end = PAGE_GUTTER, bottom = 4.dp),
                )
            }
            if (sourceSwitcher != null) {
                AnimatedVisibility(
                    visible = query.isEmpty(),
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically(),
                ) {
                    Box(Modifier.padding(start = PAGE_GUTTER, end = PAGE_GUTTER, bottom = 4.dp)) {
                        sourceSwitcher()
                    }
                }
            }
            // The filters only mean something once there is a result set to narrow;
            // they stay up for an empty or failed search too, or picking a filter
            // that finds nothing would take away the control needed to leave it.
            // They are YouTube's categories, so the Library source has none.
            if (results != null && !suggesting && !searchingLibrary && !searchingAddons) {
                SearchFilterTabs(filter = filter, onFilterChange = onFilterChange, supportsVideos = supportsVideos, tracksOnly = tracksOnly)
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth().then(glassBackdrop?.source ?: Modifier),
            contentPadding = PaddingValues(
                top = if (appleHead) with(density) { appleHeadPx.toDp() } else 0.dp,
                bottom = contentPadding.calculateBottomPadding(),
            ),
        ) {
            when {
                searchingLibrary || searchingAddons -> librarySearchResults(
                    query = query.trim(),
                    results = if (searchingAddons) addonResults else libraryResults,
                    addons = searchingAddons,
                    addonHistory = addonHistory,
                    onAddonHistoryClick = onAddonHistoryClick,
                    onAddonHistoryRemove = onAddonHistoryRemove,
                    onAddonHistoryClear = onAddonHistoryClear,
                    currentSong = currentSong,
                    isPlaying = isPlaying,
                    onSongClick = onLibrarySongClick,
                    onSongLongPress = onSongLongPress,
                    onSongSwipe = onSongSwipe,
                )
                suggesting -> {
                    searchSuggestions(
                        suggestions = suggestions,
                        // Picking one is done typing, so the keyboard comes down
                        // with it and the results get the whole screen.
                        onClick = { term ->
                            onSuggestionClick(term)
                            focusManager.clearFocus()
                        },
                        onFill = onQueryChange,
                    )
                    if (showTypeahead) {
                        searchTypeaheadDropdown(
                            typeaheadResults = typeaheadResults,
                            onSongClick = { song -> onTopResultPlay(song) },
                            onSongLongPress = onTypeaheadLongPress,
                            onBrowseClick = { item ->
                                onBrowseClick(item)
                            },
                            currentSong = currentSong,
                            isPlaying = isPlaying,
                        )
                    }
                }
                // Apple's landing is the categories alone, untitled; the field in use is the recents alone.
                results == null && appleSearch && appleActive -> {
                    if(history.isNotEmpty()) recentSearches(history,currentSong,isPlaying,onHistoryClick,onHistoryRemove,onHistoryClear)
                }
                results == null && appleSearch -> {
                    items(extensionCategories.chunked(2)) { pair ->
                        Row(Modifier.fillMaxWidth().padding(horizontal=PAGE_GUTTER,vertical=6.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                            pair.forEach { entry -> BrowseCategoryTile(entry,{onExtensionCategoryClick(entry)},null,Modifier.weight(1f)) }
                            if(pair.size==1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
                results == null -> {
                    if(history.isNotEmpty()) recentSearches(history,currentSong,isPlaying,onHistoryClick,onHistoryRemove,onHistoryClear)
                    if(extensionCategories.isNotEmpty()) {
                        // Apple Music calls this grid "Browse Categories".
                        item { Text(if(com.music.streamx.ui.skins.LocalSkinRenderer.current is com.music.streamx.ui.skins.SkinArtworkPages) "Browse Categories" else "Browse all",
                            style=MaterialTheme.typography.headlineSmall,modifier=Modifier.padding(horizontal=PAGE_GUTTER,vertical=16.dp)) }
                        items(extensionCategories.chunked(2)) { pair ->
                            Row(Modifier.fillMaxWidth().padding(horizontal=PAGE_GUTTER,vertical=6.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                                pair.forEach { entry -> BrowseCategoryTile(entry,{onExtensionCategoryClick(entry)},null,Modifier.weight(1f)) }
                                if(pair.size==1) Spacer(Modifier.weight(1f))
                            }
                        }
                    } else if(history.isEmpty()) item { MessageState(extensionName?.let { if(tracksOnly) "Search songs on $it." else "Search $it." } ?: if(tracksOnly) "Search songs from your enabled plugins." else stringResource(Res.string.search_empty)) }
                }
                results is UiState.Loading -> songListSkeleton(circular = filter == SearchFilter.ARTISTS)
                results is UiState.Error -> item { MessageState(results.message) }
                results is UiState.Success -> {
                    val tracks = results.data
                        .mapNotNull { row -> when (row) {
                            is SearchResult.TopTrack -> row.song
                            is SearchResult.Track -> row.song
                            is SearchResult.Browse -> null
                            else -> null
                        } }
                    val isCurrentRow = playingRowMatcher(tracks, currentSong)
                    val topResult = results.data.filterIsInstance<SearchResult.TopTrack>().firstOrNull()
                    if (appleSearch) {
                        // Apple's results are one list in the order they rank, songs,
                        // artists, albums and playlists mixed, each saying what it is.
                        val rows = results.data
                        itemsIndexed(rows, key = { index, row -> "apple:$index:" + when (row) {
                            is SearchResult.TopTrack -> row.song.videoId
                            is SearchResult.Track -> row.song.videoId
                            is SearchResult.Browse -> row.item.browseId
                        } }) { index, row ->
                            val last = index == rows.lastIndex
                            when (row) {
                                is SearchResult.TopTrack, is SearchResult.Track -> {
                                    val song = if (row is SearchResult.TopTrack) row.song else (row as SearchResult.Track).song
                                    AppleSearchRow(song.thumbnailUrl, song.title, "Song · " + song.artist, false, true,
                                        isCurrentRow(song), !last,
                                        { onSongClick(tracks, tracks.indexOf(song).coerceAtLeast(0)) }, { onSongLongPress(song) })
                                }
                                is SearchResult.Browse -> {
                                    val type = when (row.item.type) {
                                        BrowseType.ARTIST -> "Artist"
                                        BrowseType.ALBUM -> "Album"
                                        BrowseType.PLAYLIST -> "Playlist"
                                        else -> null
                                    }
                                    val sub = row.item.subtitle.takeIf { it.isNotBlank() }
                                    val line = when {
                                        type == null -> sub.orEmpty()
                                        sub == null || sub.equals(type, true) -> type
                                        sub.startsWith(type, true) -> sub
                                        else -> "$type · $sub"
                                    }
                                    AppleSearchRow(row.item.thumbnailUrl, row.item.title, line, row.item.type == BrowseType.ARTIST, false,
                                        false, !last, { onBrowseClick(row.item) }, onBrowseLongPress?.let { { it(row.item) } })
                                }
                            }
                        }
                    }
                    if (!appleSearch && filter == SearchFilter.ALL && topResult != null) {
                        item(key = "search:top-result:${topResult.song.videoId}") {
                            TopResultCard(
                                song = topResult.song,
                                onPlay = { onTopResultPlay(topResult.song) },
                                onPlaylist = { onTopResultPlaylist(topResult.song) },
                                onLongPress = { onSongLongPress(topResult.song) },
                            )
                        }
                    }
                    if (!appleSearch) searchSections(results.data, filter).forEach { section ->
                        section.title?.let { title ->
                            item(key = "search-section:$title") {
                                Text(
                                    text = title,
                                    modifier = Modifier.padding(
                                        start = PAGE_GUTTER,
                                        end = PAGE_GUTTER,
                                        top = 16.dp,
                                        bottom = 6.dp,
                                    ),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                        itemsIndexed(
                            items = section.rows,
                            key = { index, row ->
                                when (row) {
                                    is SearchResult.TopTrack -> "top_${row.song.videoId}_$index"
                                    is SearchResult.Track -> "track_${row.song.videoId}_$index"
                                    is SearchResult.Browse -> "browse_${row.item.browseId}_$index"
                                }
                            },
                        ) { index, row ->
                            when (row) {
                                is SearchResult.TopTrack -> Unit
                                is SearchResult.Track -> SongRow(
                                    song = row.song,
                                    onClick = {
                                        onSongClick(tracks, tracks.indexOf(row.song).coerceAtLeast(0))
                                    },
                                    onLongPress = { onSongLongPress(row.song) },
                                    onSwipeToQueue = { onSongSwipe(row.song) },
                                    isCurrent = isCurrentRow(row.song),
                                    isPlaying = isPlaying && isCurrentRow(row.song),
                                    searchPlayingStyle = true,
                                    activeTint = PlayingAccent,
                                )
                                is SearchResult.Browse -> BrowseRow(
                                    item = row.item,
                                    onClick = { onBrowseClick(row.item) },
                                    onLongPress = onBrowseLongPress?.let { { it(row.item) } },
                                )
                            }
                            if (index < section.rows.lastIndex) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(start = ROW_DIVIDER_INSET),
                                    thickness = 0.5.dp,
                                    color = MaterialTheme.colorScheme.outline,
                                )
                            }
                        }
                    }
                    if (loadingMore) {
                        songListSkeleton(
                            count = 3,
                            keyPrefix = "skeleton:search:more",
                            circular = filter == SearchFilter.ARTISTS,
                        )
                    }
                }
            }
        }
    }
    if (appleHead) Box(Modifier.fillMaxWidth().onSizeChanged { appleHeadPx = it.height }) {
        // Frosted once something passes beneath it, as Apple's bar is; clear at rest.
        Box(Modifier.matchParentSize().graphicsLayer { alpha = frostAlpha }
            .then(glassBackdrop?.frost(MaterialTheme.colorScheme.background) ?: Modifier.background(MaterialTheme.colorScheme.background.copy(alpha = .94f))))
        AppleSearchHeader(query, onQueryChange, onSubmit, focusRequester, appleAccount!!, listState, appleSourceSwitch, { appleActive = it },
            showTabs = results != null && !suggesting && !searchingLibrary && !searchingAddons,
            tabs = { SearchFilterTabs(filter = filter, onFilterChange = onFilterChange, supportsVideos = supportsVideos, tracksOnly = tracksOnly) })
    }
    }
}

private data class SearchSection(val title: String?, val rows: List<SearchResult>)

/** The unfiltered page is useful only when its mixed result types are readable at a glance. */
private fun searchSections(rows: List<SearchResult>, filter: SearchFilter): List<SearchSection> {
    if (filter != SearchFilter.ALL) return listOf(SearchSection(null, rows))
    return listOf(
        SearchSection("Songs", rows.filterIsInstance<SearchResult.Track>()),
        SearchSection("Artists", rows.filterIsInstance<SearchResult.Browse>().filter { it.item.type == BrowseType.ARTIST }),
        SearchSection("Albums", rows.filterIsInstance<SearchResult.Browse>().filter { it.item.type == BrowseType.ALBUM }),
        SearchSection("Playlists", rows.filterIsInstance<SearchResult.Browse>().filter { it.item.type == BrowseType.PLAYLIST }),
        SearchSection("More", rows.filterIsInstance<SearchResult.Browse>().filter { it.item.type == BrowseType.OTHER }),
    ).filter { it.rows.isNotEmpty() }
}

/**
 * The Library source's page: the Local Music folder's tracks that match, as
 * plain song rows. An artist or album hit needs no row of its own — every
 * track under that name matches too, so they are already here.
 */
private fun LazyListScope.librarySearchResults(
    query: String,
    results: UiState<List<Song>>?,
    addons: Boolean,
    addonHistory: List<String>,
    onAddonHistoryClick: (String) -> Unit,
    onAddonHistoryRemove: (String) -> Unit,
    onAddonHistoryClear: () -> Unit,
    currentSong: Song?,
    isPlaying: Boolean,
    onSongClick: (List<Song>, Int) -> Unit,
    onSongLongPress: (Song) -> Unit,
    onSongSwipe: (Song) -> Unit,
) {
    when (results) {
        null -> if (addons && query.isEmpty() && addonHistory.isNotEmpty()) {
            recentAddonSearches(addonHistory, onAddonHistoryClick, onAddonHistoryRemove, onAddonHistoryClear)
        } else item(key = "library:empty") {
            MessageState(
                stringResource(if (addons) Res.string.search_addons_empty else Res.string.search_library_empty),
            )
        }
        UiState.Loading -> songListSkeleton(keyPrefix = "skeleton:search:library")
        is UiState.Error -> item(key = "library:error") { MessageState(results.message) }
        is UiState.Success -> if (results.data.isEmpty()) {
            item(key = "library:no-match") {
                MessageState(
                    if (addons) {
                        stringResource(Res.string.search_addons_no_results, query)
                    } else {
                        stringResource(Res.string.search_library_no_results, query)
                    },
                )
            }
        } else {
            val tracks = results.data
            val isCurrentRow = playingRowMatcher(tracks, currentSong)
            itemsIndexed(tracks, key = { index, song -> "${if (addons) "addon" else "library"}_${song.videoId}_$index" }) { index, song ->
                SongRow(
                    song = song,
                    onClick = { onSongClick(tracks, index) },
                    onLongPress = { onSongLongPress(song) },
                    onSwipeToQueue = { onSongSwipe(song) },
                    isCurrent = isCurrentRow(song),
                    isPlaying = isPlaying && isCurrentRow(song),
                    searchPlayingStyle = true,
                    activeTint = PlayingAccent,
                    showSourceQuality = addons,
                )
                if (index < tracks.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(start = ROW_DIVIDER_INSET),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            }
        }
    }
}

/** The All response carries its highest-confidence music hit as a promoted card. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TopResultCard(
    song: Song,
    onPlay: () -> Unit,
    onPlaylist: () -> Unit,
    onLongPress: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = PAGE_GUTTER, end = PAGE_GUTTER, top = 18.dp, bottom = 8.dp)
            .longPressMenuClickable(onClick = onPlay, onLongClick = onLongPress)
            .contextClick(onLongPress),
    ) {
        Text(
            text = "Top result",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = song.thumbnailUrl?.artworkAt(ROW_ART_PX),
                contentDescription = null,
                modifier = Modifier.size(72.dp).clip(RoundedCornerShape(10.dp)),
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = song.artist,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            RowMoreButton(
                onClick = onLongPress,
                tint = MaterialTheme.colorScheme.onSurface,
                iconSize = 24.dp,
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = onPlay,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
            ) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text(stringResource(Res.string.play))
            }
            OutlinedButton(
                onClick = onPlaylist,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
            ) {
                Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text(stringResource(Res.string.playlist_action))
            }
        }
    }
}

/**
 * What YouTube would complete the half-typed query to, in place of the results
 * while it is being typed.
 *
 * The first row is the text as typed, put there by the view model rather than
 * taken from YouTube's answer, so running exactly what was asked for is always
 * the nearest row to the keyboard rather than something the thumb has to aim
 * past.
 */
private fun LazyListScope.searchSuggestions(
    suggestions: List<String>,
    onClick: (String) -> Unit,
    onFill: (String) -> Unit,
) {
    // This is a list-level inset rather than padding hidden inside the first
    // row. It keeps the gap under the field stable even when that row changes
    // its text or icon treatment.
    item(key = "suggestions:top-inset") { Spacer(Modifier.height(8.dp)) }
    // Skip the echo of the typed text (element 0) — it's already visible in
    // the search field itself — and cap at N so the list stays compact above
    // the playable-media cards.
    itemsIndexed(suggestions.drop(1).take(3), key = { _, term -> "suggest:$term" }) { _, term ->
        SuggestionRow(
            term = term,
            isQueryAction = false,
            onFill = { onFill(term) },
            onClick = { onClick(term) },
        )
    }
}

/**
 * One typeahead row: tap the text to search it, or the arrow to put it in the
 * field and carry on typing — the pair YouTube, Google and every mobile
 * keyboard's own suggestion strip use, and the reason a longer completion
 * isn't a dead end when it's only nearly right.
 */
@Composable
private fun SuggestionRow(
    term: String,
    isQueryAction: Boolean,
    onFill: (() -> Unit)?,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(
                start = PAGE_GUTTER,
                end = 8.dp,
                top = 6.dp,
                bottom = 6.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Rounded.Search,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(16.dp))
        Text(
            // The first row is the deliberate action to search the exact text
            // in the field, not a server-provided completion. Naming it makes
            // the otherwise duplicated wording read as intentional.
            text = if (isQueryAction) """${stringResource(Res.string.search)} "$term"""" else term,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (onFill != null) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onFill),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.NorthWest,
                    contentDescription = stringResource(Res.string.recent_search_edit, term),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        } else {
            // Match the arrow button's full touch target, not only its width.
            // A width-only spacer made the first row shorter than the ones
            // below, so its vertical rhythm looked visibly uneven.
            Spacer(Modifier.size(40.dp))
        }
    }
}

/**
 * Hybrid dropdown shown beneath text suggestions while typing: a horizontal
 * divider, then live media rows (cover art + title + subtitle) that play on
 * tap and open the song menu on long-press.
 */
private fun LazyListScope.searchTypeaheadDropdown(
    typeaheadResults: List<SearchResult>,
    onSongClick: (Song) -> Unit,
    onSongLongPress: ((Song) -> Unit)?,
    onBrowseClick: (BrowseItem) -> Unit,
    currentSong: Song?,
    isPlaying: Boolean,
) {
    val isCurrentRow = playingRowMatcher(
        typeaheadResults.mapNotNull { (it as? SearchResult.Track)?.song ?: (it as? SearchResult.TopTrack)?.song },
        currentSong,
    )
    item(key = "typeahead:divider") {
        HorizontalDivider(
            modifier = Modifier.padding(start = PAGE_GUTTER, end = PAGE_GUTTER),
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
        )
    }
    items(typeaheadResults, key = { result ->
        when (result) {
            is SearchResult.Track -> "ta:track:${result.song.videoId}"
            is SearchResult.Browse -> "ta:browse:${result.item.browseId}"
            is SearchResult.TopTrack -> "ta:top:${result.song.videoId}"
        }
    }) { result ->
        when (result) {
            is SearchResult.Track -> TypeaheadSongRow(
                song = result.song,
                onClick = { onSongClick(result.song) },
                onLongPress = onSongLongPress?.let { { it(result.song) } },
                isCurrent = isCurrentRow(result.song),
                isPlaying = isPlaying && isCurrentRow(result.song),
            )
            is SearchResult.Browse -> BrowseRow(
                item = result.item,
                onClick = { onBrowseClick(result.item) },
                onLongPress = onSongLongPress?.let { { /* browse long-press not applicable */ } },
            )
            is SearchResult.TopTrack -> TypeaheadSongRow(
                song = result.song,
                onClick = { onSongClick(result.song) },
                onLongPress = onSongLongPress?.let { { it(result.song) } },
                isCurrent = isCurrentRow(result.song),
                isPlaying = isPlaying && isCurrentRow(result.song),
            )
        }
    }
}

/**
 * A single media row inside the typeahead dropdown: 52dp cover art, title,
 * and artist/album subtitle. Tap plays the track; long-press opens the menu.
 */
@Composable
private fun TypeaheadSongRow(
    song: Song,
    onClick: () -> Unit,
    onLongPress: (() -> Unit)? = null,
    isCurrent: Boolean = false,
    isPlaying: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .longPressMenuClickable(onClick = onClick, onLongClick = onLongPress)
            .contextClick(onLongPress)
            .padding(horizontal = PAGE_GUTTER, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(52.dp)) {
            AsyncImage(
                model = song.artworkAt(ROW_ART_PX),
                contentDescription = null,
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .thumbnailBorder(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
            if (isCurrent && isPlaying) SearchPlayingBars(Modifier.align(Alignment.Center))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.titleMedium,
                color = if (isCurrent) PlayingAccent else MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = listOfNotNull(song.artist).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * What was searched for before, shown in place of the results while the field
 * is empty — the same spot Spotify and Apple Music put it, and the reason the
 * blank search page isn't just a sentence any more.
 */
private fun LazyListScope.recentSearches(
    history: List<SearchHistoryEntity>,
    currentSong: Song?,
    isPlaying: Boolean,
    onClick: (SearchHistoryEntity) -> Unit,
    onRemove: (String) -> Unit,
    onClear: () -> Unit,
) {
    item(key = "recent:header") {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = PAGE_GUTTER, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (com.music.streamx.ui.skins.LocalSkinRenderer.current is com.music.streamx.ui.skins.SkinArtworkPages) "Recently Searched"
                    else stringResource(Res.string.recent_searches),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = stringResource(Res.string.clear),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(percent = 50))
                    .clickable(onClick = onClear)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
    }
    items(history, key = { "recent:${it.id}" }) { entity ->
        RecentSearchEntityRow(
            entity = entity,
            isCurrent = entity.entityType == EntityType.TRACK && entity.id == currentSong?.videoId,
            isPlaying = isPlaying,
            onClick = { onClick(entity) },
            onRemove = { onRemove(entity.id) },
        )
    }
}

/** The Addons tab's recents: just the words that were searched, no song behind them. */
private fun LazyListScope.recentAddonSearches(
    history: List<String>,
    onClick: (String) -> Unit,
    onRemove: (String) -> Unit,
    onClear: () -> Unit,
) {
    item(key = "addon-recent:header") {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = PAGE_GUTTER, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (com.music.streamx.ui.skins.LocalSkinRenderer.current is com.music.streamx.ui.skins.SkinArtworkPages) "Recently Searched"
                    else stringResource(Res.string.recent_searches),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = stringResource(Res.string.clear),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(percent = 50))
                    .clickable(onClick = onClear)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
    }
    items(history, key = { "addon-recent:$it" }) { term ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onClick(term) }
                .padding(start = PAGE_GUTTER, end = 8.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Rounded.History,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(16.dp))
            Text(
                text = term,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable { onRemove(term) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = stringResource(Res.string.recent_search_remove, term),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

/**
 * Spotify-style entity row: square thumbnail, bold title, subtitle with type,
 * and a removal button. Tapping navigates to the entity or plays it directly.
 */
@Composable
private fun RecentSearchEntityRow(
    entity: SearchHistoryEntity,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = PAGE_GUTTER, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(52.dp)) {
            AsyncImage(
                model = entity.artworkUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .thumbnailBorder(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
            if (isCurrent && isPlaying) {
                SearchPlayingBars(Modifier.align(Alignment.Center))
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = entity.title,
                style = MaterialTheme.typography.titleMedium,
                color = if (isCurrent) PlayingAccent else MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = entity.subtitle.ifBlank { entity.entityType.name.lowercase(Locale.ROOT).replaceFirstChar { it.uppercase(Locale.ROOT) } },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .clickable(onClick = onRemove),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.Close,
                contentDescription = stringResource(Res.string.recent_search_remove, entity.title),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BrowseRow(item: BrowseItem, onClick: () -> Unit, onLongPress: (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .longPressMenuClickable(onClick = onClick, onLongClick = onLongPress)
            .contextClick(onLongPress)
            .padding(horizontal = PAGE_GUTTER, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = item.thumbnailUrl.artworkAt(ROW_ART_PX),
            contentDescription = null,
            modifier = Modifier
                .size(52.dp)
                .clip(
                    if (item.type == BrowseType.ARTIST) CircleShape
                    else RoundedCornerShape(8.dp),
                )
                .thumbnailBorder(
                    if (item.type == BrowseType.ARTIST) CircleShape
                    else RoundedCornerShape(8.dp),
                )
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = item.subtitle.ifBlank { item.type.name.lowercase(Locale.ROOT).replaceFirstChar { it.uppercase(Locale.ROOT) } },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Filter pills rather than a tab row: squarish rounded rectangles, the selected
 * one inverted. They scroll horizontally so a long label set never squeezes the
 * text, and the gutter padding sits inside the scroll so it scrolls with them.
 */
@Composable
private fun SearchFilterTabs(filter: SearchFilter, onFilterChange: (SearchFilter) -> Unit, supportsVideos: Boolean, tracksOnly: Boolean) {
    val haptics = rememberHaptics()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = PAGE_GUTTER, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val apple = com.music.streamx.ui.skins.LocalSkinRenderer.current is com.music.streamx.ui.skins.SkinArtworkPages
        // Apple's own order: what people look for first, then the rest.
        val order = if (apple) listOf("ALL", "ARTISTS", "ALBUMS", "SONGS", "PLAYLISTS") else emptyList()
        SearchFilter.entries.sortedBy { order.indexOf(it.name).let { i -> if (i < 0) 99 else i } }.filter { (!tracksOnly || it == SearchFilter.ALL || it == SearchFilter.SONGS) && (supportsVideos || it != SearchFilter.VIDEOS) }.forEach { entry ->
            val selected = entry == filter
            // Apple's strip: plain words, the chosen one on a capsule of the accent.
            if (apple) {
                Text(
                    text = if (entry == SearchFilter.ALL) "Top Results" else entry.label,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
                        .clickable { if (!selected) haptics.play(Haptic.Select); onFilterChange(entry) }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    fontSize = 17.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (selected) Color.White else MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                )
                return@forEach
            }
            Box(
                modifier = Modifier
                    .clip(FILTER_PILL_SHAPE)
                    .background(
                        if (selected) MaterialTheme.colorScheme.onBackground
                        else MaterialTheme.colorScheme.surfaceVariant,
                    )
                    // Only the pill that isn't already selected has anything to
                    // report — re-tapping the current filter changes nothing, so
                    // buzzing for it would be feedback for a no-op.
                    .clickable {
                        if (!selected) haptics.play(Haptic.Select)
                        onFilterChange(entry)
                    }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text(
                    text = entry.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected) MaterialTheme.colorScheme.background
                    else MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                )
            }
        }
    }
}

/** Rounded, but well short of a capsule — the corner reads as a cut, not a curve. */
private val FILTER_PILL_SHAPE = RoundedCornerShape(12.dp)

/**
 * One row of Apple Music's results: the artwork (round for an artist), the
 * name, a grey line saying what it is and by whom, and ••• for a song or a
 * chevron for anything that opens a page. The hairline starts under the words.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppleSearchRow(
    art: String?,
    title: String,
    subtitle: String,
    round: Boolean,
    song: Boolean,
    current: Boolean,
    divider: Boolean,
    onClick: () -> Unit,
    onMore: (() -> Unit)?,
) {
    val hairline = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f)
    val shape = if (round) CircleShape else RoundedCornerShape(6.dp)
    Row(
        Modifier.fillMaxWidth()
            .longPressMenuClickable(onClick = onClick, onLongClick = onMore)
            .drawBehind {
                if (divider) {
                    val start = (PAGE_GUTTER + 55.dp + 15.dp).toPx()
                    drawLine(hairline, androidx.compose.ui.geometry.Offset(start, size.height),
                        androidx.compose.ui.geometry.Offset(size.width, size.height), 0.5.dp.toPx())
                }
            }
            .padding(start = PAGE_GUTTER, end = 6.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(art.artworkAt(ROW_ART_PX), null, contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            modifier = Modifier.size(55.dp).clip(shape).thumbnailBorder(shape).background(MaterialTheme.colorScheme.surfaceVariant))
        Spacer(Modifier.width(15.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 17.sp, color = if (current) PlayingAccent else MaterialTheme.colorScheme.onBackground,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (song) {
            if (onMore != null) RowMoreButton(onClick = onMore, hang = false, tint = MaterialTheme.colorScheme.onBackground)
        } else {
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, Modifier.padding(end = 8.dp).size(26.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * Apple Music's search head. At rest: "Search" large, the account beside it,
 * the field under them. Scrolled, the title goes and the field floats on liquid
 * glass. Typing: the title gives way, a ✕ beside the field leaves the search,
 * and a two-way switch picks where to look.
 */
@Composable
private fun AppleSearchHeader(
    query: String,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
    focusRequester: FocusRequester,
    account: @Composable () -> Unit,
    listState: LazyListState,
    sourceSwitch: (@Composable () -> Unit)?,
    onActive: (Boolean) -> Unit,
    showTabs: Boolean,
    tabs: @Composable () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val active = focused || query.isNotEmpty()
    LaunchedEffect(active) { onActive(active) }
    val scrolled by remember(listState) { derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 8 } }
    Column(Modifier.fillMaxWidth().statusBarsPadding()) {
        AnimatedVisibility(!active && !scrolled, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            Row(Modifier.fillMaxWidth().padding(start = PAGE_GUTTER, end = PAGE_GUTTER, top = 10.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text("Search", Modifier.weight(1f), fontSize = 34.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                account()
            }
        }
        Row(Modifier.fillMaxWidth().padding(start = PAGE_GUTTER, end = PAGE_GUTTER, top = if (active || scrolled) 8.dp else 0.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            SearchField(
                query = query,
                onQueryChange = onQueryChange,
                onSubmit = onSubmit,
                focusRequester = focusRequester,
                placeholder = "Artists, Songs, Lyrics and More",
                modifier = Modifier.weight(1f).onFocusChanged { focused = it.isFocused },
            )
            AnimatedVisibility(active, enter = fadeIn() + androidx.compose.animation.expandHorizontally(),
                exit = fadeOut() + androidx.compose.animation.shrinkHorizontally()) {
                Box(Modifier.padding(start = 10.dp).size(50.dp).liquidPress()
                    .clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .85f))
                    .clickable { onQueryChange(""); focusManager.clearFocus() },
                    contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Close, "Cancel", Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onBackground)
                }
            }
        }
        if (sourceSwitch != null) AnimatedVisibility(active && !showTabs, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            Box(Modifier.padding(start = PAGE_GUTTER, end = PAGE_GUTTER, bottom = 6.dp)) { sourceSwitch() }
        }
        if (showTabs) tabs()
    }
}
