package com.music.streamx.ui.skins

import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.serialization.Serializable

/** Versioned, native layout choices. The host owns data, navigation and all actions. */
@Serializable
enum class FeedLayout { SHELVES, QUICK_ACCESS }
@Serializable
enum class NavigationLayout { FLOATING, FLAT }
@Serializable
enum class LibraryLayout { CARDS, ROWS }
@Serializable
enum class SettingsLayout { GROUPED, FLAT }
@Serializable
enum class PlayerLayout { IMMERSIVE, CLASSIC, EXTENSION }
@Serializable
enum class SkinIconPack { DEFAULT, EXTENSION }

@Serializable
data class SkinLayout(
    val icons: SkinIconPack = SkinIconPack.DEFAULT,
    val home: FeedLayout = FeedLayout.SHELVES,
    val navigation: NavigationLayout = NavigationLayout.FLOATING,
    val library: LibraryLayout = LibraryLayout.CARDS,
    val settings: SettingsLayout = SettingsLayout.GROUPED,
    val player: PlayerLayout = PlayerLayout.IMMERSIVE,
    val artworkRadiusDp: Int = 8,
    val shelfWidthDp: Int = 160,
    val searchRadiusDp: Int = 11,
    val miniPlayerRadiusDp: Int = 28,
) {
    fun validate() {
        require(artworkRadiusDp in 0..32) { "Artwork radius must be 0–32 dp" }
        require(shelfWidthDp in 112..220) { "Shelf width must be 112–220 dp" }
        require(searchRadiusDp in 0..32) { "Search radius must be 0–32 dp" }
        require(miniPlayerRadiusDp in 0..32) { "Mini player radius must be 0–32 dp" }
    }
}

val LocalSkinLayout = staticCompositionLocalOf { SkinLayout() }

/** Player controls remain light in both app modes, so the player uses the skin's dark palette. */
data class SkinPlayerPalette(
    val background: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Black,
    val surface: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color(0xFF282828),
)
val LocalSkinPlayerPalette = staticCompositionLocalOf { SkinPlayerPalette() }
