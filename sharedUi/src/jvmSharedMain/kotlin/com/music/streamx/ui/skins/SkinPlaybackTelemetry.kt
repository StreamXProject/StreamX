package com.music.streamx.ui.skins

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.streamx.data.NerdStats

/** Optional capability: the renderer places stats inside its artwork, so no footer is needed. */
interface SkinArtworkStatsProvider

/** Encoded audio from telemetry; output PCM precision is a different measurement. */
@Composable
fun rememberSkinTrackFormat(): String {
    val stats by NerdStats.current.collectAsStateWithLifecycle()
    return stats?.trackFormatLabel().orEmpty()
}

internal fun NerdStats.Snapshot.trackFormatLabel(): String = buildList {
    NerdStats.codecLabel(mimeType)?.let(::add)
    if (isLossless) bitDepth?.let { add("$it-bit") }
    sampleRateHz?.let { add("${it / 1000.0} kHz") }
    bitrateKbps?.let { add("$it kbps") }
}.joinToString(" · ")

/** Use inside the artwork Box; this never reserves space outside the cover. */
@Composable
fun SkinArtworkStats(modifier: Modifier = Modifier) {
    val enabled by skinPlayerSettings().showNerdStats.collectAsStateWithLifecycle()
    val stats by NerdStats.current.collectAsStateWithLifecycle()
    if (!enabled) return
    val value = stats ?: return
    val detail = value.playbackStatsLabel()
    if (detail.isBlank()) return
    Text(detail, modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        style = MaterialTheme.typography.labelSmall.copy(shadow = androidx.compose.ui.graphics.Shadow(
            color = Color.Black, blurRadius = 6f)), color = Color.White,
        textAlign = TextAlign.Center, maxLines = 2)
}

internal fun NerdStats.Snapshot.playbackStatsLabel(): String = listOfNotNull(
    sourceName,
    trackFormatLabel().takeIf { it.isNotBlank() }
        ?: container?.takeIf { it == "Web player" || it == "Native player" },
    channels?.let { if (it == 2) "Stereo" else "$it channels" },
).joinToString(" · ")

@Composable
internal fun SkinPlaybackStats(modifier: Modifier = Modifier) {
    val enabled by skinPlayerSettings().showNerdStats.collectAsStateWithLifecycle()
    val stats by NerdStats.current.collectAsStateWithLifecycle()
    if (!enabled) return
    val value = stats ?: return
    val detail = value.playbackStatsLabel()
    if (detail.isNotBlank()) Text(detail, modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 4.dp),
        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center, maxLines = 2)
}
