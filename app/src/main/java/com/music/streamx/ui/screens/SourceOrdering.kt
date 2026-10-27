package com.music.streamx.ui.screens

import com.music.streamx.ui.skins.SkinIcon as Icon
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.music.streamx.data.sources.SourceConfig
import com.music.streamx.data.sources.SourceKind

/**
 * The glyph a source is drawn with everywhere on the sources screen: brand
 * artwork for an installed extension and CloudStream, a Material symbol for
 * the kinds that have no package to borrow one from.
 */
@Composable
internal fun SourceGlyph(config: SourceConfig, modifier: Modifier = Modifier) {
    if (config.kind == SourceKind.APK_PLUGIN || config.kind == SourceKind.CLOUDSTREAM) {
        ExtensionLogo(
            if (config.kind == SourceKind.CLOUDSTREAM) "com.music.streamx.cloudstream" else config.extensionPackage,
            modifier = modifier,
        )
    } else {
        Icon(
            imageVector = when (config.kind) {
                SourceKind.STREAMX -> Icons.Rounded.Dns
                SourceKind.JIOSAAVN -> Icons.Rounded.GraphicEq
                SourceKind.YOUTUBE -> Icons.Rounded.PlayCircle
                SourceKind.CLOUDSTREAM -> Icons.Rounded.Cloud
                else -> Icons.Rounded.Extension
            },
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onBackground,
            modifier = modifier,
        )
    }
}

/**
 * The numbered list of sources, reordered by holding and dragging a row, inside a
 * plain [Column] rather than a lazy one: there are never more than a handful
 * of sources, and the screen that holds this already scrolls.
 *
 * The lifted row keeps two numbers — which id it is ([held]) and how far it
 * is drawn from its slot ([offset]). Each time it has travelled more than
 * half the height of its neighbour the two trade places in [order] and the
 * offset is reduced by exactly the distance the slot moved, so the row stays
 * under the finger. Nothing is written until the finger lifts: a drag passes
 * through every position on the way to the one it was meant for, and each of
 * those would otherwise rebuild the registry's instances.
 *
 * [configs] is the list as the registry has it right now. While nothing is
 * held it is mirrored into [order]; while something is, the local copy is the
 * truth and the registry's is ignored until [onReorder] has caught it up.
 */
@Composable
internal fun ReorderableSourceList(
    configs: List<SourceConfig>,
    onReorder: (List<String>) -> Unit,
    row: @Composable (position: Int, config: SourceConfig, rowDrag: Modifier) -> Unit,
) {
    val order = remember { mutableStateListOf<String>().apply { addAll(configs.map { it.id }) } }
    var held by remember { mutableStateOf<String?>(null) }
    var offset by remember { mutableFloatStateOf(0f) }
    /** Measured height of each row's slot, by id, so swaps can be decided without a layout query. */
    val heights = remember { mutableStateMapOf<String, Int>() }
    val byId = configs.associateBy { it.id }
    val commit by rememberUpdatedState(onReorder)

    // Mirror the registry while idle. Keyed on the id list so a toggle that
    // changes nothing about the order does not reset a list mid-drag.
    val ids = configs.map { it.id }
    LaunchedEffect(ids, held) {
        if (held == null && order.toList() != ids) {
            order.clear(); order.addAll(ids)
        }
    }

    fun settle() {
        val key = held ?: return
        // Moving down: once past half of the next row's height, trade places,
        // and keep going for a fast flick that crossed more than one.
        while (true) {
            val at = order.indexOf(key)
            if (at < 0) return
            val next = order.getOrNull(at + 1) ?: break
            val nextHeight = heights[next] ?: break
            if (offset > nextHeight / 2f) {
                order.add(at + 1, order.removeAt(at))
                offset -= nextHeight
            } else break
        }
        // Moving up, mirror image.
        while (true) {
            val at = order.indexOf(key)
            if (at < 0) return
            val previous = order.getOrNull(at - 1) ?: break
            val previousHeight = heights[previous] ?: break
            if (offset < -previousHeight / 2f) {
                order.add(at - 1, order.removeAt(at))
                offset += previousHeight
            } else break
        }
    }

    fun end() {
        if (held != null) commit(order.toList())
        held = null
        offset = 0f
    }

    Column(Modifier.fillMaxWidth()) {
        order.forEachIndexed { index, id ->
            // Keep the pointer handler attached to this source while its slot changes.
            key(id) {
                val config = byId[id] ?: return@forEachIndexed
                val dragging = held == id
                val start: () -> Unit = { held = id; offset = 0f }
                val drag: (Float) -> Unit = { delta -> offset += delta; settle() }

                // A long press lifts the row; a normal swipe still scrolls the screen.
                val rowDrag = Modifier.pointerInput(id) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { start() },
                        onDragEnd = ::end,
                        onDragCancel = {
                            // An interrupted gesture must not save a partial reorder.
                            held = null
                            offset = 0f
                        },
                        onDrag = { change, amount -> change.consume(); drag(amount.y) },
                    )
                }

                Column(
                    Modifier
                        .fillMaxWidth()
                        .onSizeChanged { heights[id] = it.height }
                        .zIndex(if (dragging) 1f else 0f)
                        .graphicsLayer { translationY = if (dragging) offset else 0f }
                        .then(
                            if (dragging) Modifier
                                .shadow(6.dp, RoundedCornerShape(12.dp), clip = false)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                            else Modifier,
                        ),
                ) {
                    row(index + 1, config, rowDrag)
                    if (index < order.lastIndex) RowDivider()
                }
            }
        }
    }
}
