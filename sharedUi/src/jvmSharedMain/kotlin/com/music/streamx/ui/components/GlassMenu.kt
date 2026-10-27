package com.music.streamx.ui.components

import androidx.compose.foundation.layout.offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawBehind
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Where the last finger came down, on the screen — so a menu opened by a tap
 * (a ••• button, the player's overflow) grows from the place it was asked for,
 * as iOS's do, without every button having to say where it is.
 */
object MenuAnchor {
    private var point: Offset? = null
    private var at = 0L

    fun record(screen: Offset) {
        point = screen
        at = System.nanoTime()
    }

    /** The last touch if it was recent enough to be the one that opened a menu. */
    fun recent(): Rect? = point?.takeIf { System.nanoTime() - at < 2_000_000_000L }?.let { Rect(it, Size.Zero) }
}

/** On a window's root: notes every touch for [MenuAnchor], taking nothing from it. */
fun Modifier.recordMenuAnchor(): Modifier = composed {
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    this
        .onGloballyPositioned { coordinates = it }
        .pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                coordinates?.takeIf { it.isAttached }?.let { MenuAnchor.record(it.localToScreen(down.position)) }
            }
        }
}

/** One of the actions across the top of a [GlassMenu] — Favourite, Share. */
data class GlassMenuAction(val icon: ImageVector, val label: String, val tint: Color? = null, val onClick: () -> Unit)

@Stable
private class GlassMenuState {
    var dark = true
}

private val LocalGlassMenu = staticCompositionLocalOf<GlassMenuState?> { null }

/** Whether this is being drawn inside a [GlassMenu]. */
@Composable
fun inGlassMenu(): Boolean = LocalGlassMenu.current != null

/**
 * iOS 26's context menu: a rounded pane of frosted glass that grows out of the
 * place it was asked for. The pane frosts what is under it; the rest of the
 * screen is left as it is, unless the menu came from a hold, which blurs it
 * all as iOS's peek does. Optional actions sit across its top; the rows below
 * are [GlassMenuRow]s and [GlassMenuSeparator]s.
 *
 * A finger — the one still down from the hold, or one pressed on the pane —
 * chooses from it: a capsule of light glides to the row under it, with a tick
 * at each, and lifting off a row runs it. Anywhere outside the pane closes it.
 */
@Composable
fun GlassMenu(
    onDismiss: () -> Unit,
    anchor: Rect?,
    top: List<GlassMenuAction> = emptyList(),
    /**
     * The page's blur, when the menu is opened over the page's own window: the
     * pane is then drawn on the page and frosted from it. Without it the menu
     * takes a window of its own, over the player or anything else.
     */
    haze: dev.chrisbanes.haze.HazeState? = null,
    /**
     * The pane's frost from elsewhere — a skin's own glass backdrop — for a menu
     * drawn over content the page's blur can't reach. Drawn on the page, as [haze].
     */
    frost: (@Composable () -> Modifier)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val dark = MaterialTheme.colorScheme.background.luminance() < .5f
    val state = remember { GlassMenuState() }
    state.dark = dark
    val held = remember { HeldPointer.isDown }
    val screen = androidx.compose.ui.platform.LocalWindowInfo.current.containerSize
    val targets = remember { HeldMenuTargets() }
    val releasesAtOpen = remember { HeldPointer.releases }
    val haptics = LocalHapticFeedback.current
    val dismiss by rememberUpdatedState(onDismiss)
    LaunchedEffect(targets) {
        snapshotFlow { targets.hovered }.collect { if (it != null) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
    }
    LaunchedEffect(targets) {
        snapshotFlow { HeldPointer.releases }.collect { count ->
            if (count != releasesAtOpen + 1) return@collect
            val at = HeldPointer.releasedAt ?: return@collect
            targets.rows.values.firstOrNull { it.first.contains(at) }?.second?.invoke()
        }
    }
    val pop = remember { Animatable(0f) }
    LaunchedEffect(Unit) { pop.animateTo(1f, spring(dampingRatio = .74f, stiffness = 520f)) }
    val scope = rememberCoroutineScope()
    val close: () -> Unit = { scope.launch { pop.animateTo(0f, tween(120)); dismiss() } }

    val body: @Composable (overlay: Boolean) -> Unit = { overlay ->
        val density = androidx.compose.ui.platform.LocalDensity.current
        val tint = com.music.streamx.ui.skins.LocalSkinGlassTint.current
        // The frost is the window's own: its background blurs what is beneath
        // the pane, so this is only the colour laid on that.
        val pane = if (dark) Color(0xFF2A2A2C).copy(alpha = .38f + .42f * tint) else Color(0xFFF7F7F7).copy(alpha = .45f + .4f * tint)
        val rim = if (dark) Color.White.copy(alpha = .16f) else Color.Black.copy(alpha = .07f)
        val radius = 30.dp
        val shape = RoundedCornerShape(radius)
        val margin = with(density) { 14.dp.roundToPx() }
        val gap = with(density) { 8.dp.roundToPx() }
        val paneWidth = with(density) { minOf(260.dp.roundToPx(), screen.width - margin * 2) }
        var paneHeight by remember { mutableStateOf(0) }
        // Where the pane sits on the screen: off the anchor's nearer edge, below
        // it when it fits, above when it doesn't, inside the safe band always.
        val safeTop = with(density) { 44.dp.roundToPx() }
        val safeBottom = screen.height - with(density) { 34.dp.roundToPx() }
        val a = anchor ?: Rect(Offset(screen.width - margin.toFloat(), safeTop.toFloat()), Size.Zero)
        val right = a.center.x > screen.width / 2f
        val x = (if (right) a.right - paneWidth else a.left).roundToInt().coerceIn(margin, (screen.width - margin - paneWidth).coerceAtLeast(margin))
        val h = paneHeight.coerceAtMost(safeBottom - safeTop)
        val y = when {
            a.bottom + gap + h <= safeBottom -> a.bottom.roundToInt() + gap
            a.top - gap - h >= safeTop -> a.top.roundToInt() - gap - h
            else -> (a.center.y - h / 2f).roundToInt()
        }.coerceIn(safeTop, (safeBottom - h).coerceAtLeast(safeTop))
        if (!overlay) GlassMenuWindow(dark, blurScreen = held, x = x, y = y, width = paneWidth, maxHeight = safeBottom - safeTop,
            radiusPx = with(density) { radius.toPx() }, alpha = pop.value.coerceIn(0f, 1f), pane = pane)
        var rootOrigin by remember { mutableStateOf(Offset.Zero) }
        val pivotX = ((a.center.x - x) / paneWidth).coerceIn(0f, 1f)
        val pivotY = if (paneHeight > 0) ((a.center.y - y) / paneHeight).coerceIn(0f, 1f) else 0f
        var paneOrigin by remember { mutableStateOf(Offset.Zero) }
        val selection = remember { Animatable(Rect.Zero, Rect.VectorConverter) }
        val selectionAlpha = remember { Animatable(0f) }
        val hovered = targets.hovered?.let { targets.rows[it]?.first }
        LaunchedEffect(hovered) {
            if (hovered == null) { selectionAlpha.animateTo(0f, tween(140)); return@LaunchedEffect }
            if (selectionAlpha.value == 0f) selection.snapTo(hovered)
            launch { selectionAlpha.animateTo(1f, tween(90)) }
            selection.animateTo(hovered, spring(dampingRatio = .82f, stiffness = 700f))
        }
        val pill = if (dark) Color.White.copy(alpha = .16f) else Color.Black.copy(alpha = .08f)
        CompositionLocalProvider(LocalGlassMenu provides state, LocalHeldMenuTargets provides targets,
            LocalContentColor provides if (dark) Color.White else Color.Black) {
            if (overlay) {
                // On the page: everything outside the pane closes it; a hold blurs the page as iOS's peek does.
                androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()
                    .onGloballyPositioned { rootOrigin = it.localToScreen(Offset.Zero) }
                    .then(if (held && haze != null) Modifier.optimizedHazeEffect(haze, dev.chrisbanes.haze.HazeStyle(
                        backgroundColor = MaterialTheme.colorScheme.background, tints = listOf(dev.chrisbanes.haze.HazeTint(Color.Black.copy(alpha = if (dark) .25f else .08f))),
                        blurRadius = 24.dp, noiseFactor = 0f)) { alpha = pop.value.coerceIn(0f, 1f) } else Modifier)
                    .pointerInput(Unit) { awaitEachGesture { awaitFirstDown(requireUnconsumed = false); close() } })
                com.music.streamx.ui.skins.SkinBackHandler(true) { close() }
            }
            Column(
                Modifier
                    .then(if (overlay) Modifier.offset { androidx.compose.ui.unit.IntOffset(x - rootOrigin.x.roundToInt(), y - rootOrigin.y.roundToInt()) } else Modifier)
                    .width(with(density) { paneWidth.toDp() })
                    .onGloballyPositioned { paneOrigin = it.localToScreen(Offset.Zero); paneHeight = it.size.height }
                    .graphicsLayerCompat(pop.value, pivotX, pivotY)
                    .clip(shape)
                    // On the page the pane frosts what's under it itself; in a window of
                    // its own the window does, or (before Android 12) nothing can.
                    .then(if (overlay && frost != null) frost() else if (overlay && haze != null) Modifier.optimizedHazeEffect(haze, dev.chrisbanes.haze.HazeStyle(
                        backgroundColor = MaterialTheme.colorScheme.background, tints = listOf(dev.chrisbanes.haze.HazeTint(pane)),
                        blurRadius = 26.dp, noiseFactor = 0f)) else Modifier)
                    .background(if (overlay || frostedWindow()) Color.Transparent else pane)
                    .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = if (dark) .06f else .22f), Color.Transparent)))
                    .border(0.5.dp, rim, shape)
                    // The capsule under the finger, drawn once and moved, not lit per row.
                    .drawBehind {
                        val r = selection.value.translate(-paneOrigin)
                        if (selectionAlpha.value > 0f && r.width > 0f) {
                            val inset = 8.dp.toPx()
                            val rr = Rect(r.left + inset, r.top + 2.dp.toPx(), r.right - inset, r.bottom - 2.dp.toPx())
                            drawRoundRect(pill.copy(alpha = pill.alpha * selectionAlpha.value), rr.topLeft, rr.size,
                                androidx.compose.ui.geometry.CornerRadius(minOf(rr.height / 2, 22.dp.toPx())))
                        }
                    }
                    // A finger on the pane picks the same way the held one does:
                    // slide to a row, lift to run it.
                    .pointerInput(targets) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                            targets.local = paneOrigin + down.position
                            down.consume()
                            var last = down.position
                            while (true) {
                                val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: break
                                change.consume()
                                last = change.position
                                if (!change.pressed) break
                                targets.local = paneOrigin + last
                            }
                            val at = paneOrigin + last
                            targets.local = null
                            targets.rows.values.firstOrNull { it.first.contains(at) }?.second?.invoke()
                        }
                    },
            ) {
                if (top.size >= 2) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                        top.forEach { action -> TopAction(action, Modifier.weight(1f)) }
                    }
                    GlassMenuSeparator()
                }
                Column(Modifier.verticalScroll(rememberScrollState()).padding(vertical = 6.dp), content = content)
            }
        }
    }
    if (haze != null || frost != null) {
        androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) { body(true) }
    } else {
        Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) { body(false) }
    }
}

private fun Modifier.graphicsLayerCompat(p: Float, pivotX: Float, pivotY: Float) = this.then(
    Modifier.graphicsLayer {
        scaleX = .6f + .4f * p
        scaleY = .6f + .4f * p
        alpha = p.coerceIn(0f, 1f)
        transformOrigin = TransformOrigin(pivotX, pivotY)
    },
)

/** Whether the window itself frosts what's under the pane. */
internal expect fun frostedWindow(): Boolean

/** The blur behind the menu's window, where the platform has one. */
@Composable
internal expect fun GlassMenuWindow(dark: Boolean, blurScreen: Boolean, x: Int, y: Int, width: Int, maxHeight: Int, radiusPx: Float, alpha: Float, pane: Color)

@Composable
private fun TopAction(action: GlassMenuAction, modifier: Modifier) {
    val color = LocalContentColor.current
    val target = remember { Any() }
    val targets = LocalHeldMenuTargets.current
    if (targets != null) DisposableEffect(targets) { onDispose { targets.rows.remove(target) } }
    Column(modifier
        .clip(RoundedCornerShape(18.dp))
        .then(if (targets != null) Modifier.onGloballyPositioned { targets.rows[target] = it.screenRect() to action.onClick } else Modifier)
        .clickable(onClick = action.onClick)
        .padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        com.music.streamx.ui.skins.SkinIcon(action.icon, null, Modifier.size(24.dp), tint = action.tint ?: color)
        Text(action.label, Modifier.padding(top = 5.dp), color = color, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

/**
 * One choice in a [GlassMenu]: the glyph at the start, the label, and an
 * optional grey line under it ("Go to Album" / the album's name).
 */
@Composable
fun GlassMenuRow(
    icon: ImageVector?,
    label: String,
    onClick: (() -> Unit)?,
    detail: String? = null,
    value: String? = null,
    tint: Color? = null,
    destructive: Boolean = false,
    enabled: Boolean = true,
    loading: Boolean = false,
    /**
     * A pick-one row, as Apple's view and sort menus have: a column for a ✓
     * before the glyph, filled on the chosen row. Null leaves no column.
     */
    checked: Boolean? = null,
) {
    val base = LocalContentColor.current
    // iOS's destructive red, the same for the glyph and the words.
    val color = when {
        destructive -> Color(0xFFFF453A)
        else -> base
    }.copy(alpha = if (enabled) 1f else .35f)
    val target = remember { Any() }
    val targets = LocalHeldMenuTargets.current
    if (targets != null) DisposableEffect(targets) { onDispose { targets.rows.remove(target) } }
    Row(
        Modifier.fillMaxWidth()
            .then(if (targets != null && onClick != null && enabled) Modifier.onGloballyPositioned { targets.rows[target] = it.screenRect() to onClick } else Modifier)
            .then(if (onClick != null) Modifier.clickable(enabled = enabled, onClick = onClick) else Modifier)
            .padding(start = if (checked != null) 14.dp else 22.dp, end = 22.dp, top = if (detail != null) 9.dp else 13.dp, bottom = if (detail != null) 9.dp else 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (checked != null) Box(Modifier.width(22.dp), contentAlignment = Alignment.CenterStart) {
            if (checked) Text("✓", color = color, fontSize = 17.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
        }
        if (icon != null || checked == null) Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
            if (icon != null) com.music.streamx.ui.skins.SkinIcon(icon, null, Modifier.size(22.dp),
                tint = (if (destructive) color else tint ?: color).copy(alpha = color.alpha))
        }
        if (icon != null || checked == null) Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(label, color = color, fontSize = 17.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (!detail.isNullOrBlank()) Text(detail, color = base.copy(alpha = .5f), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        when {
            loading -> androidx.compose.material3.CircularProgressIndicator(Modifier.size(16.dp), color = base.copy(alpha = .5f), strokeWidth = 2.dp)
            value != null -> Text(value, Modifier.padding(start = 8.dp), color = base.copy(alpha = .55f), fontSize = 15.sp)
        }
    }
}

/** The hairline between groups of a [GlassMenu]. */
@Composable
fun GlassMenuSeparator() {
    val color = LocalContentColor.current
    Box(Modifier.padding(horizontal = 20.dp, vertical = 5.dp).fillMaxWidth().height(0.5.dp).background(color.copy(alpha = .16f)))
}
