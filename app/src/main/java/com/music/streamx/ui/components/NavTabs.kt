package com.music.streamx.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import com.music.streamx.ui.skins.LocalSkinRenderer
import com.music.streamx.ui.skins.SkinNavigationLabels
import com.music.streamx.ui.skins.SkinNavigationProvider

/**
 * The tabs as the bar draws them, with the skin's glyphs and names applied by
 * tab id and laid out in the bar's own order.
 *
 * Tab ids never move — 0 Home, 1 Explore, 2 Library, 3 Search — so everything
 * that switches on them keeps working when a fifth, Radio (4), is added. The
 * bar is the one place that orders them the way Apple Music does, Radio
 * between New and Library, and its positions are mapped back to ids here.
 */
@Immutable
class NavTabs internal constructor(val tabs: List<BottomTab>, private val order: List<Int>) {
    /** Where tab [id] sits in [tabs]. */
    fun position(id: Int): Int = order.indexOf(id).coerceAtLeast(0)

    /** The tab id at bar position [position]. */
    fun id(position: Int): Int = order.getOrElse(position) { position }
}

@Composable
fun rememberNavTabs(tabs: List<BottomTab>): NavTabs {
    val skin = LocalSkinRenderer.current
    return remember(tabs, skin) {
        val icons = skin as? SkinNavigationProvider
        val labels = skin as? SkinNavigationLabels
        val dressed = tabs.mapIndexed { id, tab ->
            tab.copy(icon = icons?.navigationIcon(id) ?: tab.icon, label = labels?.navigationLabel(id) ?: tab.label)
        }
        val order = if (tabs.size > 4) listOf(0, 1, 4, 2, 3) else tabs.indices.toList()
        NavTabs(order.map { dressed[it] }, order)
    }
}

