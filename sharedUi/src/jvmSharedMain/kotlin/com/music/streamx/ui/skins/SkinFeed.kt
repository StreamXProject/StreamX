package com.music.streamx.ui.skins
import androidx.compose.foundation.lazy.LazyListScope
import com.music.streamx.data.model.*
fun LazyListScope.skinFeedShelves(shelves: List<HomeShelf>, layout: SkinLayout, onClick: (ShelfItem, String) -> Unit,
    onLongPress: ((ShelfItem) -> Unit)?, shelfBrowseId: (HomeShelf) -> String?, renderer: SkinUiRenderer?) {
    renderer?.run { FeedShelves(shelves, layout, onClick, onLongPress, shelfBrowseId) }
}

/** Optional library presentation. Source navigation and all mutations stay in the host. */
interface SkinLibraryProvider {
    @androidx.compose.runtime.Composable
    fun Library(shelves: List<HomeShelf>, links: List<ShelfItem>, deviceItems: List<ShelfItem>,
        contentPadding: androidx.compose.foundation.layout.PaddingValues,
        onItem: (ShelfItem, String) -> Unit, onLongPress: ((ShelfItem) -> Unit)?,
        onShowAll: (HomeShelf) -> Unit, onNewPlaylist: (() -> Unit)?)
}

/** A library presentation that also handles shelves returned by an APK music plugin. */
interface SkinPluginLibraryProvider : SkinLibraryProvider
