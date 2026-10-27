package com.music.streamx.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.music.streamx.data.model.Account
import com.music.streamx.ui.skins.SkinHomeHeader
import com.music.streamx.ui.skins.SkinUiRenderer

/**
 * The skin's home header is pinned over the feed, so it used to sit on a solid
 * band of page colour that cut every card off as it scrolled up. It now gets
 * out of the way: scrolling the feed down slides it up off the screen, any
 * scroll back up brings it straight back ("quick return"), and what is behind
 * it fades into the page instead of ending on a hard edge.
 */
@Stable
internal class SkinHomeHeaderState(private val density: Density) {
    var heightPx by mutableIntStateOf(0)
    /** 0 when fully shown, down to -[heightPx] when slid away. */
    var offsetPx by mutableFloatStateOf(0f)

    fun contentPadding(fallback: Dp): Dp =
        // The header now carries 18dp of fade under itself; the feed still starts 12dp below its content.
        if (heightPx > 0) with(density) { heightPx.toDp() } - 6.dp else fallback

    /** Attach above the feed; it only watches the scroll, it never consumes any. */
    val scroll = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if (heightPx > 0) offsetPx = (offsetPx + available.y).coerceIn(-heightPx.toFloat(), 0f)
            return Offset.Zero
        }
    }
}

@Composable
internal fun rememberSkinHomeHeaderState(renderer: SkinUiRenderer?): SkinHomeHeaderState {
    val density = LocalDensity.current
    return remember(renderer, density) { SkinHomeHeaderState(density) }
}

/** Measures the whole skin header, including the status inset, for the feed's top padding. */
@Composable
internal fun SkinHomeToolbar(
    account: Account?,
    state: SkinHomeHeaderState,
    pageColor: Color,
    onAccount: () -> Unit,
    onLocal: () -> Unit,
    onHistory: () -> Unit,
    onExplore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Coming back to Home always shows the header, wherever the feed was left.
    LaunchedEffect(Unit) { state.offsetPx = 0f }
    val scrim = remember(pageColor) {
        Brush.verticalGradient(
            0f to pageColor,
            0.62f to pageColor.copy(alpha = 0.88f),
            1f to pageColor.copy(alpha = 0f),
        )
    }
    Box(modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth()
                .onSizeChanged { state.heightPx = it.height }
                .graphicsLayer { translationY = state.offsetPx },
        ) {
            Box(Modifier.fillMaxWidth()) {
                // Soft behind the header rather than a solid band: cards pass
                // under its lower edge instead of being cut off by it.
                Box(Modifier.matchParentSize().background(scrim))
                Column(Modifier.fillMaxWidth().statusBarsPadding().padding(bottom = 18.dp)) {
                    SkinHomeHeader(account, onAccount, onLocal, onHistory, onExplore)
                }
            }
        }
        // The status bar keeps a short fade of its own while the header is away,
        // so the clock never sits on top of a cover.
        Box(
            Modifier.align(Alignment.TopCenter).fillMaxWidth()
                .windowInsetsTopHeight(WindowInsets.statusBars.add(WindowInsets(top = 20.dp)))
                .graphicsLayer { alpha = if (state.heightPx > 0) (-state.offsetPx / state.heightPx).coerceIn(0f, 1f) else 0f }
                .background(Brush.verticalGradient(listOf(pageColor, pageColor.copy(alpha = 0.85f), pageColor.copy(alpha = 0f)))),
        )
    }
}
