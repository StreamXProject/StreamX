package com.music.streamx.ui.skins

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/** How long a finger rests on a glass control before a drag pulls the control rather than the page. */
private const val HOLD_MS = 160L

/** Whether the host's liquid glass is on, for controls that answer to it. Set by the host. */
val LocalSkinLiquidGlass = staticCompositionLocalOf { false }

/** The Clear ↔ Tinted setting, 0 to 1, for glass a skin draws itself. Set by the host. */
val LocalSkinGlassTint = androidx.compose.runtime.compositionLocalOf { 0.41f }

/**
 * iOS 26's touch on a glass control. Held, it swells; dragged, it follows the
 * finger on an elastic — giving less the further it is pulled — stretching
 * along the pull and narrowing across it; let go, it springs home with a
 * wobble. Only while liquid glass is on, and never in the way of the control
 * itself: a tap still lands, and a drag that starts at once still scrolls the
 * page around it.
 */
fun Modifier.liquidPress(enabled: Boolean = true): Modifier = composed {
    if (!enabled || !LocalSkinLiquidGlass.current) return@composed this
    val press = remember { Animatable(0f) }
    val pull = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    val scope = rememberCoroutineScope()
    this
        .pointerInput(Unit) {
            val reach = 70.dp.toPx()
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                scope.launch { press.animateTo(1f, spring(dampingRatio = .55f, stiffness = 650f)) }
                // As iOS decides it: a finger that sets off at once is
                // scrolling the page, and the button lets it; one that rested
                // on the button first is pulling the button, and the page stays
                // put. Movement inside the touch slop is never taken, so a tap
                // with a tremble in it still lands.
                val holdUntil = down.uptimeMillis + HOLD_MS
                val slop = viewConfiguration.touchSlop
                var claimed = false
                while (true) {
                    val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) break
                    val drag = change.position - down.position
                    if (!claimed && drag.getDistance() > slop) {
                        if (change.uptimeMillis < holdUntil) break
                        claimed = true
                    }
                    if (claimed && change.position != change.previousPosition) change.consume()
                    val length = drag.getDistance()
                    if (length > 0f) {
                        // Rubber band, stiff as Apple's: a fifth of the pull near the
                        // button, easing toward a ceiling of ~[reach] * .2.
                        val give = length * .2f / (1f + length / reach)
                        scope.launch { pull.snapTo(drag * (give / length)) }
                    }
                }
                scope.launch { press.animateTo(0f, spring(dampingRatio = .45f, stiffness = 420f)) }
                scope.launch { pull.animateTo(Offset.Zero, spring(dampingRatio = .5f, stiffness = 520f)) }
            }
        }
        .graphicsLayer {
            val swell = 1f + .1f * press.value
            val o = pull.value
            val length = o.getDistance()
            // Stretched along the pull, narrowed across it — split onto the two
            // axes by the pull's angle, so a diagonal tug stretches diagonally.
            val stretch = (length / 90.dp.toPx()).coerceAtMost(.14f)
            val cos2 = if (length > 0f) (o.x / length).let { it * it } else 0f
            val sin2 = 1f - cos2
            scaleX = swell * (1f + stretch * cos2 - stretch * .5f * sin2)
            scaleY = swell * (1f + stretch * sin2 - stretch * .5f * cos2)
            translationX = o.x
            translationY = o.y
        }
}

/**
 * The host's glass, for a skin's own surfaces over its own content: [source]
 * goes on what scrolls (a list), and [glass] and [frost] on what floats over it.
 * [glass] is the refracting liquid glass the nav bar uses, while it is on, else a
 * plain translucent surface; [frost] is a blurred, tinted band for a bar.
 */
interface SkinGlassBackdrop {
    val source: androidx.compose.ui.Modifier
    @androidx.compose.runtime.Composable fun glass(shape: androidx.compose.foundation.shape.CornerBasedShape): androidx.compose.ui.Modifier
    @androidx.compose.runtime.Composable fun frost(tint: androidx.compose.ui.graphics.Color): androidx.compose.ui.Modifier
    /**
     * Clear glass that only bends what is under it — no blur, no tint — with a
     * colour fringe at its edge: iOS 26's held switch knob. Null where the
     * platform can't refract (before Android 13); draw the knob yourself then.
     */
    @androidx.compose.runtime.Composable fun lens(shape: androidx.compose.foundation.shape.CornerBasedShape): androidx.compose.ui.Modifier? = null
}

/** Makes a fresh [SkinGlassBackdrop]; null where the host has none (older hosts, the desktop). */
val LocalSkinGlassFactory = staticCompositionLocalOf<@androidx.compose.runtime.Composable () -> SkinGlassBackdrop?> { { null } }
