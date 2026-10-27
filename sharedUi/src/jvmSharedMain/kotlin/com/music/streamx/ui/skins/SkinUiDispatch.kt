package com.music.streamx.ui.skins

import androidx.compose.runtime.*
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.lazy.LazyListScope
import com.music.streamx.data.model.*
import com.music.streamx.data.lyrics.*
import com.music.streamx.playback.PlaybackPosition
import com.music.streamx.ui.LyricsProviderState
import com.music.streamx.ui.components.BottomTab

@Composable
fun SkinPlayer(
    song: Song, isPlaying: Boolean, isLoading: Boolean, position: PlaybackPosition, durationMs: Long,
    queue: List<Song>, queueIndex: Int, hasPrevious: Boolean, hasNext: Boolean,
    repeatMode: Int, shuffleEnabled: Boolean, autoplayEnabled: Boolean, likeStatus: LikeStatus,
    accountName: String?, onToggleLike: () -> Unit, onPlayPause: () -> Unit,
    onNext: () -> Unit, onPrevious: () -> Unit, onSeekFraction: (Float) -> Unit, onSeek: (Long) -> Unit,
    onToggleShuffle: () -> Unit, onCycleRepeat: () -> Unit, onToggleAutoplay: () -> Unit,
    onJumpTo: (Int) -> Unit, onRemove: (Int) -> Unit, onMove: (Int, Int) -> Unit,
    onDragActive: (Boolean) -> Unit, onClear: () -> Unit, onOpenMenu: () -> Unit,
    onOpenArtist: (String?, String) -> Unit, onOpenPlaybackSource: () -> Unit, onListenTogether: () -> Unit,
    onCollapse: () -> Unit, onOpenTimer: () -> Unit, windowHeight: androidx.compose.ui.unit.Dp, onBlocked: () -> Unit, lyrics: List<LyricLine>?, lyricsSource: LyricsSource?,
    lyricsUnavailable: Boolean, lyricsProviderStates: Map<LyricsSource, LyricsProviderState>,
    onSelectLyricsProvider: (LyricsSource) -> Unit, lyricsOffsetOpen: Boolean,
    onDismissLyricsOffset: () -> Unit, modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Box(Modifier.weight(1f)) {
            LocalSkinRenderer.current?.Player(song, isPlaying, isLoading, position, durationMs, queue, queueIndex, hasPrevious, hasNext, repeatMode, shuffleEnabled, autoplayEnabled, likeStatus, accountName, onToggleLike, onPlayPause, onNext, onPrevious, onSeekFraction, onSeek, onToggleShuffle, onCycleRepeat, onToggleAutoplay, onJumpTo, onRemove, onMove, onDragActive, onClear, onOpenMenu, onOpenArtist, onOpenPlaybackSource, onListenTogether, onCollapse, onOpenTimer, windowHeight, onBlocked, lyrics, lyricsSource, lyricsUnavailable, lyricsProviderStates, onSelectLyricsProvider, lyricsOffsetOpen, onDismissLyricsOffset, Modifier.fillMaxSize())
        }

    }
}

@Composable
fun SkinMiniPlayer(song: Song, playing: Boolean, loading: Boolean, position: PlaybackPosition, durationMs: Long,
    saved: Boolean, onSave: () -> Unit, onPlayPause: () -> Unit, onExpand: () -> Unit,
    onNext: () -> Unit, onPrevious: () -> Unit, locked: Boolean, onBlocked: () -> Unit, modifier: Modifier = Modifier) { LocalSkinRenderer.current?.MiniPlayer(song, playing, loading, position, durationMs, saved, onSave, onPlayPause, onExpand, onNext, onPrevious, locked, onBlocked, modifier) }

@Composable
fun SkinHomeHeader(account: Account?, onAccount: () -> Unit, onLocal: () -> Unit, onHistory: () -> Unit, onExplore: () -> Unit) { LocalSkinRenderer.current?.HomeHeader(account, onAccount, onLocal, onHistory, onExplore) }

@Composable
fun SkinReleaseHeader(page: DetailPage, songs: List<Song>, onPlay: () -> Unit, onShuffle: () -> Unit,
    onSearch: () -> Unit, onMore: ((List<Song>) -> Unit)?, onArtist: (String, String) -> Unit, onToggleLibrary: (() -> Unit)?, topPadding: androidx.compose.ui.unit.Dp) { LocalSkinRenderer.current?.ReleaseHeader(page, songs, onPlay, onShuffle, onSearch, onMore, onArtist, onToggleLibrary, topPadding) }

@Composable
fun SkinSleepTimer(minutes: Int?, afterTrack: Boolean, running: Boolean, presets: List<Int>,
    onStart: (Int) -> Unit, onAfterTrack: () -> Unit, onCancel: () -> Unit, onDismiss: () -> Unit) { LocalSkinRenderer.current?.SleepTimer(minutes, afterTrack, running, presets, onStart, onAfterTrack, onCancel, onDismiss) }

@Composable
fun SkinBottomNavigation(tabs: List<BottomTab>, selectedIndex: Int, onTabSelected: (Int) -> Unit) { LocalSkinRenderer.current?.BottomNavigation(tabs, selectedIndex, onTabSelected) }

@Composable
fun SkinLibraryRow(item: ShelfItem, onClick: () -> Unit, onLongPress: () -> Unit) { LocalSkinRenderer.current?.LibraryRow(item, onClick, onLongPress) }

