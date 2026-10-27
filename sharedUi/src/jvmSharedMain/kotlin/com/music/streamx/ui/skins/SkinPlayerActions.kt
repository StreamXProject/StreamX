package com.music.streamx.ui.skins

import androidx.compose.runtime.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import com.music.streamx.data.lyrics.LyricLine
import com.music.streamx.data.model.Song
import com.music.streamx.playback.PlaybackPosition
import com.music.streamx.ui.player.*
import dev.chrisbanes.haze.HazeState

/** Optional host actions, without changing the API 1 renderer's binary contract. */
data class SkinPlayerActions(
    val startRadio: (() -> Unit)? = null,
    val setMixEnabled: ((Boolean) -> Unit)? = null,
)
val LocalSkinPlayerActions = staticCompositionLocalOf { SkinPlayerActions() }

/** Keep the original floating bar while using the skin accent for its selected tab. */
interface SkinAccentNavigation

/** The same timed/word-synced lyric surface used by the original player. */
@Composable
fun SkinSyncedLyrics(song: Song, artwork: String?, lines: List<LyricLine>, subLines: List<LyricLine>?,
    position: PlaybackPosition, looking: Boolean, playing: Boolean, onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier) {
    var selected by remember(song.videoId) { mutableStateOf<Int?>(null) }
    var share by remember(song.videoId) { mutableStateOf<LyricsShareRequest?>(null) }
    LyricsPanel(lines=lines,subLines=subLines,trackKey=song.videoId,playhead=rememberLyricPlayhead(position),
        looking=looking,isPlaying=playing,onSeekToLine=onSeek,controlsOpen=true,
        onRevealControls={},onHideControls={},canPick=true,onPickLine={selected=it},modifier=modifier)
    selected?.let { index -> lines.getOrNull(index)?.let { line ->
        AlertDialog(onDismissRequest={selected=null},title={Text("Lyrics")},text={Text(line.text)},
            confirmButton={TextButton(onClick={onSeek(line.timeMs);selected=null}){Text("Play from here")}},
            dismissButton={
                if(PlayerPlatform.host.lyricsShareAvailable)TextButton(onClick={
                    share=LyricsShareRequest(song,artwork,listOf(LyricsShareLine(line.text,subLines?.getOrNull(index)?.text)))
                    selected=null
                }){Text("Share line")}
                else TextButton(onClick={selected=null}){Text("Close")}
            })
    }}
    share?.let { request -> PlayerPlatform.host.LyricsShareSheet(remember {HazeState()},request){share=null} }
}
