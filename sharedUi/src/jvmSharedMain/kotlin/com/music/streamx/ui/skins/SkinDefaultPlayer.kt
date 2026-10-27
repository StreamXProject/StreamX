package com.music.streamx.ui.skins

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier

/** Small visual overrides for skins that reuse the complete original player. */
data class SkinDefaultPlayerStyle(val fullCover: Boolean=false,val liquidGlass: Boolean=true)
interface SkinDefaultPlayerProvider {
    @Composable fun PlayerStyle(): SkinDefaultPlayerStyle
}
interface SkinSolidNavigation
/** Tab glyphs by tab id, numbered as for [SkinNavigationLabels]. */
interface SkinNavigationProvider {
    fun navigationIcon(index: Int): androidx.compose.ui.graphics.vector.ImageVector?
}
/** Android supplies real refracting glass; other platforms keep the same bounded hit shape. */
val LocalSkinPlayerGlass = staticCompositionLocalOf<@Composable (Modifier,Boolean)->Modifier> {{modifier,_->modifier}}

/** Keep the host's typography/shapes while still offering a skin configuration page. */
interface SkinUseHostTheme

/**
 * A theme-owning skin whose album and playlist pages still take their colour
 * from the artwork, as Apple Music's do since iOS 26.4, instead of the skin's
 * flat background.
 */
interface SkinArtworkPages

/** A skin whose bar carries Apple Music's Radio tab whenever the source has a Radio page. */
interface SkinRadioTab

/**
 * Tab names a skin uses in place of the host's, by tab id rather than by place
 * in the bar: 0 Home, 1 Explore, 2 Library, 3 Search, 4 Radio. Null keeps the host's.
 */
interface SkinNavigationLabels {
    fun navigationLabel(index: Int): String?
}

/** Capture only the player background, never the glass controls sampling it. */
val LocalSkinPlayerBackdrop=staticCompositionLocalOf<Modifier> {Modifier}
