package com.music.streamx.ui.screens

import com.music.streamx.ui.skins.SkinIcon as Icon
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import com.music.streamx.ui.components.cardZoomTarget
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import com.music.streamx.ui.skins.skinAndroidPainterResource as painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.music.streamx.R
import com.music.streamx.data.model.*
import com.music.streamx.ui.components.MessageState
import com.music.streamx.ui.components.PAGE_GUTTER
import com.music.streamx.ui.components.SongRow
import com.music.streamx.ui.components.longPressMenuClickable
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.collect

/** A bounded page at a time; keeps the loaded queue and scroll position in the detail stack. */
@Composable
internal fun StreamXCatalogueScreen(
    page: DetailPage,
    listState: LazyListState,
    contentPadding: PaddingValues,
    currentSong: Song?,
    isPlaying: Boolean,
    onSongClick: (List<Song>, Int) -> Unit,
    onSongLongPress: (Song) -> Unit,
    onSongSwipe: (Song) -> Unit,
    onBrowseClick: (ShelfItem) -> Unit,
    onBrowseLongPress: ((ShelfItem) -> Unit)?,
    likeStatuses: Map<String, LikeStatus>,
    onToggleFavorite: ((Song) -> Unit)?,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val songs = (page.songs as? UiState.Success)?.data.orEmpty()
    val entries = page.sections.flatMap { it.items }
    LaunchedEffect(listState, page.browseId, page.nextCataloguePage, page.catalogueLoading, page.catalogueError) {
        if (page.nextCataloguePage == null || page.catalogueLoading || page.catalogueError != null) return@LaunchedEffect
        snapshotFlow {
            val layout = listState.layoutInfo
            layout.visibleItemsInfo.lastOrNull()?.index?.let { it >= layout.totalItemsCount - 6 } == true
        }.distinctUntilChanged().collect { nearEnd -> if (nearEnd) onLoadMore() }
    }
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
    LazyColumn(state = listState, contentPadding = contentPadding, modifier = modifier.fillMaxSize()) {
        item(key = "heading") {
            // The card's own art first: it is there before the page loads, so the
            // zoom from that card lands on its picture rather than a blank panel.
            // Held once shown — the loaded page names its art by another address,
            // and swapping to it reloaded the picture the zoom had just landed on.
            val art = page.thumbnailUrl ?: songs.firstNotNullOfOrNull { it.thumbnailUrl }
                ?: entries.firstNotNullOfOrNull { it.thumbnailUrl }
            val held = remember(page.browseId) { mutableStateOf<String?>(null) }
            if (held.value == null) held.value = art
            CatalogueHero(page.title, held.value,
                onPlay = if (songs.isNotEmpty()) ({ onSongClick(songs, 0) }) else null, browseId = page.browseId)
        }
        when (val state = page.songs) {
            is UiState.Loading -> item { Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
            is UiState.Error -> item { MessageState(state.message, actionLabel = stringResource(R.string.retry), onAction = onLoadMore) }
            is UiState.Success -> {
                itemsIndexed(songs, key = { index, song -> "song:$index:${song.videoId}" }) { index, song ->
                    SongRow(song = song, onClick = { onSongClick(songs, index) },
                        onLongPress = { onSongLongPress(song) }, onSwipeToQueue = { onSongSwipe(song) },
                        isCurrent = song.isSameTrackAs(currentSong), isPlaying = isPlaying && song.isSameTrackAs(currentSong),
                        trailingContent = {
                            val liked = likeStatuses[song.videoId] == LikeStatus.LIKE
                            IconButton(onClick = { onToggleFavorite?.invoke(song) }, enabled = onToggleFavorite != null) {
                                Icon(com.music.streamx.ui.skins.skinSavePainter(liked, painterResource(R.drawable.fav)), stringResource(if (liked) R.string.streamx_remove_favourite else R.string.streamx_add_favourite),
                                    modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onBackground.copy(alpha = if (liked) 1f else .3f))
                            }
                        })
                }
                itemsIndexed(entries, key = { index, entry -> "entry:$index:${entry.browseId ?: entry.title}" }) { _, entry ->
                    Row(Modifier.fillMaxWidth().longPressMenuClickable(onClick = { onBrowseClick(entry) },
                        onLongClick = onBrowseLongPress?.let { { it(entry) } }).padding(horizontal = PAGE_GUTTER, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        AsyncImage(entry.thumbnailUrl, null, Modifier.size(64.dp).clip(RoundedCornerShape(10.dp)), contentScale = ContentScale.Crop)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(entry.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onBackground)
                            if (entry.subtitle.isNotBlank()) Text(entry.subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                        }
                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            else -> Unit
        }
        if (page.catalogueLoading) item(key = "loading") {
            Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(24.dp)) }
        }
        page.catalogueError?.let { error -> item(key = "error") {
            MessageState(error, actionLabel = stringResource(R.string.retry), onAction = onLoadMore)
        } }
    }
    }
}

/** Reuses loaded catalogue artwork; a static gradient keeps the scrolling cost low. */
@Composable
private fun CatalogueHero(title: String, artwork: String?, onPlay: (() -> Unit)?, browseId: String? = null) {
    // The card a tapped station or mix zooms into, as a release's sleeve does.
    Box(Modifier.padding(horizontal = PAGE_GUTTER, vertical = 16.dp)
        .then(Modifier.cardZoomTarget(browseId, 26.dp))
        .fillMaxWidth().height(280.dp).clip(RoundedCornerShape(26.dp))
        .background(MaterialTheme.colorScheme.surfaceVariant)) {
        if (artwork != null) AsyncImage(artwork.artworkAt(HEADER_ART_PX), null,
            Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
            listOf(Color.Black.copy(alpha = .06f), Color.Black.copy(alpha = .25f), Color.Black.copy(alpha = .9f)))))
        Column(Modifier.align(Alignment.BottomStart).padding(22.dp)) {
            Text(title, style = MaterialTheme.typography.headlineLarge, color = Color.White)
            if (onPlay != null) {
                Spacer(Modifier.height(14.dp))
                Button(onClick = onPlay, shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)) {
                    Icon(Icons.Rounded.PlayArrow, null, Modifier.size(22.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.play))
                }
            }
        }
    }
}
