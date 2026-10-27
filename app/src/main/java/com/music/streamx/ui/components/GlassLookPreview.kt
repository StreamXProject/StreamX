package com.music.streamx.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Sort
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.music.streamx.ui.components.backdrop.backdrops.layerBackdrop
import com.music.streamx.ui.components.backdrop.backdrops.rememberLayerBackdrop
import com.music.streamx.ui.components.backdrop.drawBackdrop
import com.music.streamx.ui.components.backdrop.effects.blur
import com.music.streamx.ui.components.backdrop.effects.lens
import com.music.streamx.ui.components.backdrop.highlight.Highlight
import com.music.streamx.ui.components.backdrop.shadow.Shadow
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * iOS 26's Liquid Glass settings, together: a picture with a glass button and
 * search bar laid over it, drawn live at the [tint] being chosen, and the Clear
 * ↔ Tinted slider beneath, whose thumb is a glass lens itself while held.
 */
@Composable
fun GlassLookPreview(tint: Float, onTint: (Float) -> Unit, modifier: Modifier = Modifier) {
    val scene = rememberLayerBackdrop()
    val pill = RoundedCornerShape(percent = 50)
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(26.dp))) {
            // The picture behind the glass: sky, sun, and bands of a building, drawn
            // so it has the edges and colours the glass needs to show what it does.
            Box(Modifier.fillMaxSize().layerBackdrop(scene).drawBehind {
                drawRect(Brush.verticalGradient(listOf(Color(0xFF5FA8E8), Color(0xFFBFDDF2), Color(0xFF3F6B3A), Color(0xFF233A22))))
                drawCircle(Color(0xFFFFF4D6), size.minDimension * .09f, Offset(size.width * .22f, size.height * .2f))
                for (i in 0..5) {
                    val y = size.height * (.24f + i * .085f)
                    drawRoundRect(Color(0xFFE9E3CF), Offset(size.width * .42f, y), Size(size.width * .7f, size.height * .03f), CornerRadius(20f))
                    drawRect(Color(0xFF7FA9C9).copy(alpha = .8f), Offset(size.width * .44f, y + size.height * .035f), Size(size.width * .7f, size.height * .035f))
                }
                drawRect(Color(0xFF2B2B2B), Offset(0f, size.height * .86f), Size(size.width, size.height * .14f))
            })
            Row(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(start = 18.dp, end = 18.dp, bottom = 22.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(56.dp).glassSample(scene, pill, tint), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Sort, null, tint = Color.White, modifier = Modifier.size(26.dp))
                }
                Spacer(Modifier.width(12.dp))
                Row(Modifier.weight(1f).height(56.dp).glassSample(scene, pill, tint).padding(horizontal = 18.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Search, null, tint = Color.White, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("Search", color = Color.White.copy(alpha = .75f), fontSize = 17.sp)
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        GlassTintSlider(tint, onTint)
    }
}

/** The app's glass at a given tint, over the preview's own picture. */
@Composable
private fun Modifier.glassSample(scene: com.music.streamx.ui.components.backdrop.Backdrop, shape: RoundedCornerShape, tint: Float): Modifier {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val blurPx = with(density) { 8.dp.toPx() }
    val lensH = with(density) { 22.dp.toPx() }
    val lensA = with(density) { 24.dp.toPx() }
    return drawBackdrop(
        backdrop = scene,
        shape = { shape },
        effects = {
            blur(blurPx * (.3f + .7f * tint))
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) lens(lensH, lensA, depthEffect = true, chromaticAberration = true)
        },
        highlight = { Highlight.Default },
        shadow = { Shadow.Default },
        onDrawSurface = { drawRect(Color(0xFF1C1C1E).copy(alpha = .12f + .68f * tint)) },
    )
}

/**
 * The Clear ↔ Tinted slider: a glyph at each end, a thin track, three marks,
 * and a capsule thumb that turns to clear glass, bending the track, while held.
 */
@Composable
private fun GlassTintSlider(value: Float, onValue: (Float) -> Unit) {
    val track = rememberLayerBackdrop()
    val lift = remember { Animatable(0f) }
    // A small lean in the way it's carried, and a bounce when let go: iOS's
    // physics, not a stretch.
    val lean = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val set by rememberUpdatedState(onValue)
    val accent = Color(0xFF0A84FF)
    // The thumb follows the finger itself while held; the setting only follows it.
    val shown = remember { Animatable(value) }
    var dragging by remember { androidx.compose.runtime.mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(value, dragging) { if (!dragging) shown.animateTo(value, spring(dampingRatio = .52f, stiffness = 650f)) }
    Row(Modifier.fillMaxWidth().height(60.dp).clip(RoundedCornerShape(30.dp)).background(Color(0xFF1C1C1E)).padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically) {
        GlassGlyph(filled = false)
        BoxWithConstraints(Modifier.weight(1f).fillMaxHeight().padding(horizontal = 12.dp)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    // Claimed at once, so the page around it can't take the drag for a scroll.
                    down.consume()
                    dragging = true
                    scope.launch { lift.animateTo(1f, spring(dampingRatio = .6f, stiffness = 600f)) }
                    val follow = spring<Float>(dampingRatio = .82f, stiffness = 1400f)
                    var last = -1f
                    fun moveTo(x: Float) {
                        val f = (x / size.width).coerceIn(0f, 1f)
                        scope.launch { shown.animateTo(f, follow) }
                        if (kotlin.math.abs(f - last) > .004f) { last = f; set(f) }
                    }
                    moveTo(down.position.x)
                    try {
                        while (true) {
                            val e = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                            if (!e.pressed) break
                            e.consume()
                            moveTo(e.position.x)
                            val step = e.position.x - e.previousPosition.x
                            scope.launch { lean.snapTo((lean.value * .6f + step / 6.dp.toPx() * .4f).coerceIn(-1f, 1f)) }
                        }
                    } finally {
                        dragging = false
                        set(last.coerceAtLeast(0f))
                        scope.launch { shown.animateTo(last.coerceAtLeast(0f), spring(dampingRatio = .45f, stiffness = 500f)) }
                        scope.launch { lift.animateTo(0f, spring(dampingRatio = .55f, stiffness = 420f)) }
                        scope.launch { lean.animateTo(0f, spring(dampingRatio = .4f, stiffness = 420f)) }
                    }
                }
            }) {
            val w = constraints.maxWidth.toFloat()
            Box(Modifier.fillMaxSize().layerBackdrop(track).drawBehind {
                val y = size.height / 2
                drawRoundRect(Color.White.copy(alpha = .14f), Offset(0f, y - 3.dp.toPx()), Size(size.width, 6.dp.toPx()), CornerRadius(3.dp.toPx()))
                drawRoundRect(accent, Offset(0f, y - 3.dp.toPx()), Size(size.width * shown.value, 6.dp.toPx()), CornerRadius(3.dp.toPx()))
                for (i in 0..2) drawCircle(Color.White.copy(alpha = .3f), 1.5.dp.toPx(), Offset(size.width * (.17f + i * .33f), y + 9.dp.toPx()))
            })
            // The thumb: white at rest, clear refracting glass while held.
            val thumbW = 52.dp
            val thumbH = 30.dp
            Box(Modifier
                .offset { IntOffset(((w - thumbW.toPx()) * shown.value).roundToInt(), 0) }
                .align(Alignment.CenterStart)
                .size(thumbW, thumbH)
                .graphicsLayer { val s = 1f + .25f * lift.value; val k = kotlin.math.abs(lean.value); scaleX = s * (1f + .04f * k); scaleY = s * (1f - .02f * k) }
                .then(if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    val d = androidx.compose.ui.platform.LocalDensity.current
                    val h = with(d) { 10.dp.toPx() }
                    val a = with(d) { 14.dp.toPx() }
                    Modifier.drawBackdrop(backdrop = track, shape = { RoundedCornerShape(percent = 50) },
                        effects = { lens(h, a, depthEffect = true, chromaticAberration = true) },
                        highlight = { Highlight.Default }, shadow = { Shadow.Default },
                        onDrawSurface = { drawRect(Color.White.copy(alpha = 1f - lift.value)) })
                } else Modifier.clip(RoundedCornerShape(percent = 50)).background(Color.White)))
        }
        GlassGlyph(filled = true)
    }
}

/** Two overlapping rounded squares: outlined for Clear, solid for Tinted. */
@Composable
private fun GlassGlyph(filled: Boolean) {
    Box(Modifier.size(28.dp, 24.dp).drawBehind {
        val s = Size(size.width * .7f, size.height * .7f)
        val r = CornerRadius(5.dp.toPx())
        val stroke = Stroke(1.6.dp.toPx())
        drawRoundRect(Color.White.copy(alpha = .75f), Offset(0f, 0f), s, r, style = stroke)
        if (filled) drawRoundRect(Color.White.copy(alpha = .75f), Offset(size.width * .3f, size.height * .3f), s, r)
        else drawRoundRect(Color.White.copy(alpha = .75f), Offset(size.width * .3f, size.height * .3f), s, r, style = stroke)
    })
}

