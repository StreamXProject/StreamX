package com.music.streamx.ui

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.streamx.data.model.HomeShelf
import com.music.streamx.data.model.UiState
import com.music.streamx.ui.skins.LocalSkinRenderer
import com.music.streamx.ui.skins.SkinRadioTab

/** Apple Music's Radio tab, as far as the app shell needs it. [state] is null while there is no tab. */
internal class RadioTab(val state: UiState<List<HomeShelf>>?, val listState: LazyListState)

/**
 * Loads the current source's Radio page and says whether the bar gets the tab:
 * only for a skin that asks for one, and only while the source has the page.
 * Kept out of the app shell's composable, which is already at the size the JVM
 * allows a method to be. Hands [selectedTab] back to Home when the tab goes.
 */
@Composable
internal fun rememberRadioTab(viewModel: MainViewModel, extensionId: String?, homeLoaded: Boolean, selectedTab: Int, radioTabId: Int, onLeave: () -> Unit): RadioTab {
    // Asked again once Home lands: on a cold start the plugin may not be up for
    // the first request, and an error there would keep the tab away for good.
    LaunchedEffect(extensionId, homeLoaded) { viewModel.loadRadio() }
    val radio by viewModel.extensionRadio.collectAsStateWithLifecycle()
    val wanted = LocalSkinRenderer.current is SkinRadioTab && extensionId != null
    val state = radio.takeIf { wanted }
    val leave by rememberUpdatedState(onLeave)
    LaunchedEffect(state == null, selectedTab) { if (state == null && selectedTab == radioTabId) leave() }
    return RadioTab(state, rememberLazyListState())
}
