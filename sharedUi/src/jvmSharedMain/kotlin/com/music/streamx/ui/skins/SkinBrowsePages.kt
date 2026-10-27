package com.music.streamx.ui.skins

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import com.music.streamx.data.model.DetailPage
import com.music.streamx.data.model.ShelfItem

/**
 * Optional: the skin draws the browse pages the host would otherwise draw in
 * its own look — artist pages, a shelf's "More" page, and the "Show all" grids
 * behind library and artist shelves. Data, navigation and every action stay
 * with the host; the skin only decides how cards, headings and the artist
 * header look.
 *
 * Separate from API 1 so existing renderer APKs stay binary-compatible.
 */
interface SkinBrowsePagesProvider {
    /**
     * One release, playlist or artist card. [fill] is true inside a grid cell
     * (the card should take the cell's width) and false in a horizontal row
     * (the card chooses its own width). [index] lets a skin vary its cards.
     */
    @Composable fun BrowseCard(item: ShelfItem, index: Int, fill: Boolean, onClick: () -> Unit, onLongPress: (() -> Unit)?, modifier: Modifier)

    /** A section title on an artist or category page. [onShowAll] is null when the row already shows everything. */
    @Composable fun BrowseSectionHeading(title: String, subtitle: String?, onShowAll: (() -> Unit)?)

    /** The title block at the top of a category ("More") or "Show all" page. */
    @Composable fun BrowsePageTitle(title: String, subtitle: String?, topPadding: Dp)

    /**
     * The whole artist header: photo, name and the play / shuffle / follow
     * row. [onPlay] and [onShuffle] are null until the artist's songs have
     * loaded; [onToggleSubscription] is null where following is not offered.
     */
    @Composable fun ArtistHeader(page: DetailPage, onPlay: (() -> Unit)?, onShuffle: (() -> Unit)?,
        onToggleSubscription: (() -> Unit)?, topPadding: Dp)
}
