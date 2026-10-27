package com.music.streamx.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.music.streamx.ui.components.backdrop.backdrops.rememberLayerBackdrop
import com.music.streamx.ui.skins.liquidPress
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * The bottom bar's liquid glass as a two- or three-way switch, as Apple Music's
 * search sets its source: a glass capsule, and a glass pill on the choice that
 * glides — or is dragged — to another, swelling as it goes like the tab bar's.
 * With liquid glass off it is the same shape in plain fills.
 */
@Composable
fun GlassSegmentedControl(options: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val dark = MaterialTheme.colorScheme.background.luminance() < .5f
    val glassOn = LocalLiquidGlassEnabled.current && isGlassSupported()
    // Nothing behind the control is worth refracting but the page colour, so the
    // glass samples a layer of its own: its rim, shade and lens are the bar's.
    val layer = rememberLayerBackdrop()
    val capsule = RoundedCornerShape(percent = 50)
    val count = options.size.coerceAtLeast(1)
    val position = remember { Animatable(selectedIndex.toFloat()) }
    val swell = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val select by rememberUpdatedState(onSelect)
    LaunchedEffect(selectedIndex) { position.animateTo(selectedIndex.toFloat(), spring(dampingRatio = .72f, stiffness = 420f)) }
    BoxWithConstraints(
        modifier.fillMaxWidth().height(46.dp)
            .then(if (glassOn) Modifier.liquidGlass(capsule, layer)
                else Modifier.clip(capsule).background(if (dark) Color(0xFF1C1C1E) else Color(0xFFE3E3E8)).border(.5.dp, Color.White.copy(alpha = .08f), capsule))
            .padding(4.dp),
    ) {
        val segment = maxWidth / count
        val segmentPx = constraints.maxWidth.toFloat() / count
        // The pill: one glass drop that moves, swelling while it's carried.
        Box(
            Modifier
                .offset { IntOffset((position.value * segmentPx).roundToInt(), 0) }
                .width(segment).fillMaxHeight()
                .graphicsLayer { val s = 1f + .08f * swell.value; scaleX = s; scaleY = s }
                .then(if (glassOn) Modifier.liquidGlass(capsule, layer).background(Color.White.copy(alpha = if (dark) .1f else .5f), capsule)
                    else Modifier.clip(capsule).background(if (dark) Color(0xFF3A3A3C) else Color.White)),
        )
        Row(
            Modifier.fillMaxSize().liquidPress().pointerInput(count, segmentPx) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    scope.launch { swell.animateTo(1f, spring(dampingRatio = .6f, stiffness = 600f)) }
                    var x = down.position.x
                    var moved = false
                    while (true) {
                        val e = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                        if (!e.pressed) break
                        if (kotlin.math.abs(e.position.x - down.position.x) > viewConfiguration.touchSlop) moved = true
                        x = e.position.x
                        // Carried with the finger, centred on it, within the capsule.
                        if (moved) scope.launch { position.snapTo((x / segmentPx - .5f).coerceIn(0f, count - 1f)) }
                    }
                    val target = (x / segmentPx).toInt().coerceIn(0, count - 1)
                    scope.launch { swell.animateTo(0f, spring(dampingRatio = .5f, stiffness = 420f)) }
                    scope.launch { position.animateTo(target.toFloat(), spring(dampingRatio = .72f, stiffness = 420f)) }
                    if (target != selectedIndex) select(target)
                }
            },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            options.forEachIndexed { index, label ->
                Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                    Text(label, fontSize = 15.sp, fontWeight = if (index == selectedIndex) FontWeight.SemiBold else FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onBackground, maxLines = 1)
                }
            }
        }
    }
}
