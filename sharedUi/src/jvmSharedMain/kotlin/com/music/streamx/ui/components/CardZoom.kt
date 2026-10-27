@file:OptIn(ExperimentalSharedTransitionApi::class)

package com.music.streamx.ui.components

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.music.streamx.ui.skins.LocalSkinRenderer
import com.music.streamx.ui.skins.SkinArtworkPages

/**
 * Apple Music's zoom from a card into the page it opens: the card's artwork
 * grows into the page's cover, and shrinks back into the same card on the way
 * out. Every card is registered all the time, keyed by its shelf as well as
 * what it opens — the same station can sit in two shelves at once — so its
 * place is already known when it is tapped; joining only on the tap left the
 * zoom nothing to start from, and it snapped. The page picks the tapped key.
 */
object CardZoom {
    private const val SEPARATOR = '\u0000'

    /** Shelf and browse id of the card last opened, joined. */
    var origin by mutableStateOf<String?>(null)
        private set

    fun open(shelf: String?, browseId: String?) {
        origin = browseId?.let { "${shelf.orEmpty()}$SEPARATOR$it" }
    }

    internal fun key(shelf: String?, browseId: String?) = browseId?.let { "${shelf.orEmpty()}$SEPARATOR$it" }

    /** The tapped card's key, while [browseId] is the page it opened. */
    internal fun keyFor(browseId: String?) = origin?.takeIf { browseId != null && it.substringAfter(SEPARATOR) == browseId }
}

/** The layout the app's pages share elements across; null where nothing zooms. */
val LocalCardZoomLayout = staticCompositionLocalOf<SharedTransitionScope?> { null }

/** The page slot a card or cover is composed in, for the zoom to animate against. */
val LocalCardZoomPage = staticCompositionLocalOf<AnimatedVisibilityScope?> { null }

/** The shelf a card sits in, so a tap can say which of a repeated card it was. */
val LocalCardZoomShelf = staticCompositionLocalOf<String?> { null }

// A soft spring, close to UIKit's zoom: quick out of the card, settling into the cover.
private val ZoomBounds = BoundsTransform { _: Rect, _: Rect ->
    spring(dampingRatio = 0.86f, stiffness = Spring.StiffnessMediumLow)
}

@Composable
private fun Modifier.zoomBounds(key: String?, corner: Dp): Modifier {
    val layout = LocalCardZoomLayout.current ?: return this
    val page = LocalCardZoomPage.current ?: return this
    if (key == null || LocalSkinRenderer.current !is SkinArtworkPages) return this
    return with(layout) {
        this@zoomBounds.sharedBounds(
            sharedContentState = rememberSharedContentState(key),
            animatedVisibilityScope = page,
            boundsTransform = ZoomBounds,
            clipInOverlayDuringTransition = OverlayClip(RoundedCornerShape(corner)),
        )
    }
}

/** On a card's artwork: registered under its shelf and target, ready to zoom when tapped. */
@Composable
fun Modifier.cardZoomSource(browseId: String?, corner: Dp = 12.dp): Modifier =
    zoomBounds(CardZoom.key(LocalCardZoomShelf.current, browseId), corner)

/** On a page's cover: the end the tapped card zooms into. */
@Composable
fun Modifier.cardZoomTarget(browseId: String?, corner: Dp = 12.dp): Modifier =
    zoomBounds(CardZoom.keyFor(browseId), corner)
