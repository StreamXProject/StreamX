package com.music.streamx.ui.skins

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.music.streamx.data.model.Song

/** One of the menu's leading actions — play next, add to queue — which a skin may lay out on their own. */
data class SkinMenuAction(val icon: ImageVector, val label: String, val onClick: () -> Unit)

/**
 * A track's long-press menu, drawn by the skin. Which rows exist and what they
 * do stay the host's; the skin draws the surface the menu sits on, its header,
 * each row and the breaks between groups. A skin that implements this is shown
 * the menu in place of the host's sheet and popup, and owns its own window.
 */
interface SkinSongMenuProvider {
    /**
     * The whole menu. [primary] are the queue actions, kept out of [rows] so
     * they can sit apart; [onOpenTrack] opens the track's album, null where it
     * has none.
     */
    @Composable fun SongMenu(song: Song, onDismiss: () -> Unit, onOpenTrack: (() -> Unit)?,
        primary: List<SkinMenuAction>, rows: @Composable ColumnScope.() -> Unit)

    /**
     * One row. [detail] is a second line (the album name under "Open album"),
     * [value] a trailing state (the sleep timer's time left), [tint] the icon's
     * colour where it carries state, and [loading] a row still waiting to know
     * whether it belongs.
     */
    @Composable fun SongMenuRow(icon: ImageVector, label: String, detail: String?, value: String?, tint: Color?,
        enabled: Boolean, loading: Boolean, onClick: (() -> Unit)?)

    @Composable fun SongMenuSeparator()
}
