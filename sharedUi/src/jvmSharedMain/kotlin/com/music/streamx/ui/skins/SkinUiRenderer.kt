package com.music.streamx.ui.skins

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.lazy.LazyListScope
import com.music.streamx.data.model.*
import com.music.streamx.data.lyrics.*
import com.music.streamx.playback.PlaybackPosition
import com.music.streamx.ui.LyricsProviderState
import com.music.streamx.ui.components.BottomTab

/** Versioned, in-process UI ABI. Playback/account state remains owned by the host. */
const val SKIN_UI_API_VERSION = 1
interface SkinUiRenderer {
    val apiVersion: Int
    /** Resolve external runtime symbols before exposing any UI slot. */
    fun verifyCompatibility()
    fun mapIcon(vector: ImageVector): ImageVector
    fun icon(name: String): ImageVector?
    @Composable fun Player(
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
)
    @Composable fun MiniPlayer(song: Song, playing: Boolean, loading: Boolean, position: PlaybackPosition, durationMs: Long,
    saved: Boolean, onSave: () -> Unit, onPlayPause: () -> Unit, onExpand: () -> Unit,
    onNext: () -> Unit, onPrevious: () -> Unit, locked: Boolean, onBlocked: () -> Unit, modifier: Modifier = Modifier)
    @Composable fun HomeHeader(account: Account?, onAccount: () -> Unit, onLocal: () -> Unit, onHistory: () -> Unit, onExplore: () -> Unit)
    @Composable fun ReleaseHeader(page: DetailPage, songs: List<Song>, onPlay: () -> Unit, onShuffle: () -> Unit,
    onSearch: () -> Unit, onMore: ((List<Song>) -> Unit)?, onArtist: (String, String) -> Unit, onToggleLibrary: (() -> Unit)?, topPadding: androidx.compose.ui.unit.Dp)
    @Composable fun SleepTimer(minutes: Int?, afterTrack: Boolean, running: Boolean, presets: List<Int>,
    onStart: (Int) -> Unit, onAfterTrack: () -> Unit, onCancel: () -> Unit, onDismiss: () -> Unit)
    @Composable fun BottomNavigation(tabs: List<BottomTab>, selectedIndex: Int, onTabSelected: (Int) -> Unit)
    @Composable fun LibraryRow(item: ShelfItem, onClick: () -> Unit, onLongPress: () -> Unit)
    @Composable fun BottomFade(modifier: Modifier, withMiniPlayer: Boolean, pageColor: Color)
    @Composable fun LibraryStats(onOpen: () -> Unit)
    fun LazyListScope.FeedShelves(shelves: List<HomeShelf>, layout: SkinLayout, onClick: (ShelfItem, String) -> Unit,
        onLongPress: ((ShelfItem) -> Unit)?, shelfBrowseId: (HomeShelf) -> String?)
}
val LocalSkinRenderer = staticCompositionLocalOf<SkinUiRenderer?> { null }

/** Never leave empty renderer slots when an executable skin is absent or still loading. */
fun skinLayoutForRenderer(layout: SkinLayout, available: Boolean): SkinLayout = if (available) layout else layout.copy(
    home = FeedLayout.SHELVES, navigation = NavigationLayout.FLOATING, library = LibraryLayout.CARDS,
    icons = SkinIconPack.DEFAULT,
    player = if (layout.player == PlayerLayout.EXTENSION) PlayerLayout.IMMERSIVE else layout.player,
)
