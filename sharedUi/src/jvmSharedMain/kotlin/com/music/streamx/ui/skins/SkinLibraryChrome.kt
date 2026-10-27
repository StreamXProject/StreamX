package com.music.streamx.ui.skins

import androidx.compose.runtime.*
import androidx.compose.foundation.lazy.LazyListState
import com.music.streamx.data.model.ShelfItem
import com.music.streamx.data.model.Account

/** A library that owns its heading, toolbar and account button. */
interface SkinLibraryChrome

data class SkinLibraryActions(
    val navigation: SkinLibraryNavigation? = null,
    val loadCollection: (suspend (String) -> List<ShelfItem>?)? = null,
    val account: Account? = null,
    val onAccount: (() -> Unit)? = null,
    val onDownloads: (() -> Unit)? = null,
    val loading: Boolean = false,
)
val LocalSkinLibraryActions = staticCompositionLocalOf { SkinLibraryActions() }

/** Owned above detail navigation, so returning to a playlist list restores its location. */
class SkinLibraryNavigation {
    val categoryState = mutableStateOf<String?>(null)
    val queryState = mutableStateOf("")
    val listState = LazyListState()
    var collectionId by mutableStateOf<String?>(null)
    var collectionItems by mutableStateOf<List<ShelfItem>?>(null)
}

/** Optional feed heading supplied by a skin, sharing library account actions. */
interface SkinFeedHeaderProvider {
    @androidx.compose.runtime.Composable
    fun FeedHeader(title: String,actions: SkinLibraryActions)
}

/** A HomeHeader that owns the full width and natural height of the home toolbar. */
interface SkinHomeHeaderChrome
