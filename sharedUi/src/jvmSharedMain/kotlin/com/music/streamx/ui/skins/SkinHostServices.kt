package com.music.streamx.ui.skins

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import com.music.streamx.data.model.Song
import com.music.streamx.data.lyrics.*
import com.music.streamx.ui.LyricsProviderState
import com.music.streamx.ui.haptics.Haptics
import com.music.streamx.ui.player.*
import dev.chrisbanes.haze.HazeState

/** Host-owned services exposed to renderers; no skin layout or artwork is implemented here. */
fun skinPlayerSettings(): PlayerSettingsSource = PlayerSettings
@Composable fun rememberSkinRemoteArtwork(song: Song?): String? = rememberRemoteArtworkUrl(song)
@Composable fun rememberSkinCanvas(song: Song) = rememberCanvasArtwork(song)
@Composable fun rememberSkinControlsLocked() = rememberControlsLocked()
@Composable fun rememberSkinOutputPicker(onOpen: () -> Unit) = rememberOutputPicker(onOpen)
@Composable fun SkinBackHandler(enabled: Boolean, onBack: () -> Unit) = PlayerBackHandler(enabled, onBack)
fun skinLyricsPosition(position: Long, offset: Int) = adjustedLyricsPosition(position, offset)
fun skinLyricsSeek(time: Long, offset: Int) = adjustedLyricsSeekTarget(time, offset)

data class SkinLyricsTranslation(val subLines: List<LyricLine>?, val showingTranslation: Boolean,
    val toggleTranslation: () -> Unit, val toggleRomanization: () -> Unit)
@Composable
fun rememberSkinLyricsTranslation(trackId: String, lines: List<LyricLine>?, source: LyricsSource?, unavailable: Boolean,
    loading: String, haptics: Haptics): SkinLyricsTranslation {
    val state = rememberLyricsTranslation(trackId, lines, source, unavailable, loading, haptics)
    return SkinLyricsTranslation(state.subLines, state.showingTranslation, state.toggleTranslation, state.toggleRomanization)
}
@Composable fun SkinAudioOutput(haze: HazeState, account: String?, onDismiss: () -> Unit, onPipeline: () -> Unit, onCast: () -> Unit) =
    AudioOutputSheet(haze, account, onDismiss, onPipeline, onCast)
@Composable fun SkinLyricsProviders(haze: HazeState, source: LyricsSource?, states: Map<LyricsSource, LyricsProviderState>,
    onSelect: (LyricsSource) -> Unit, onDismiss: () -> Unit) = LyricsProviderSheet(haze, source, states, onSelect, onDismiss)
@Composable fun SkinAudioPipeline(haze: HazeState, playing: Boolean, onDismiss: () -> Unit) = AudioPipelineDialog(haze, playing, onDismiss)
@Composable fun SkinLyricsOffset(haze: HazeState, onDismiss: () -> Unit) = LyricsOffsetSheet(haze, onDismiss)

/** Current resolved cover, provided by the host for optional artwork-derived themes. */
object SkinTrackArtwork {
    val url = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
}
@Composable fun rememberSkinArtworkColor(url: String?): androidx.compose.ui.graphics.Color? =
    com.music.streamx.ui.theme.rememberArtworkThemeSeed(url)

/** What the listener pinned to the top of Library, by browse id, most recent first as the host keeps them. */
@Composable fun rememberSkinPinnedItems(): List<String> {
    val pinned by com.music.streamx.ui.AppUi.host.pinnedPlaylists.collectAsStateWithLifecycle()
    return pinned
}
