package com.music.streamx.ui.skins

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable

/** Optional capability, separate from API 1 so existing renderer APKs remain binary-compatible. */
interface SkinThemeProvider {
    @Composable fun Colors(darkTheme: Boolean): ColorScheme
    fun typography(): Typography
    fun shapes(): Shapes
    @Composable fun Configuration(onDismiss: () -> Unit)
}

/** Reject stale APK implementations before their composables enter the host composition. */
fun validateSkinThemeImplementation(type: Class<*>) {
    listOf(SkinThemeProvider::class.java,SkinCompositionProvider::class.java,SkinSettingsProvider::class.java,SkinLibraryProvider::class.java,SkinSettingsPageProvider::class.java,SkinBrowseHeaderProvider::class.java,SkinBrowsePagesProvider::class.java).filter { it.isAssignableFrom(type) }.forEach { capability ->
    capability.methods.filter { java.lang.reflect.Modifier.isAbstract(it.modifiers) }.forEach { contract ->
        val implementation = type.getMethod(contract.name, *contract.parameterTypes)
        require(!java.lang.reflect.Modifier.isAbstract(implementation.modifiers)) {
            "Skin theme interface is incompatible. Update the skin and host together."
        }
    }
    }
}

/** Optional theme composition for skins with their own motion/component family. */
interface SkinCompositionProvider {
    @Composable fun Theme(colors: ColorScheme, typography: Typography, shapes: Shapes, content: @Composable () -> Unit)
}

/** Optional settings surfaces; callbacks, values and persistence remain owned by the host. */
interface SkinSettingsProvider {
    @Composable fun SettingsGroup(header: String?, footer: String?, topSpacing: androidx.compose.ui.unit.Dp, content: @Composable () -> Unit)
    @Composable fun SettingsDivider()
    @Composable fun SettingsRow(icon: androidx.compose.ui.graphics.vector.ImageVector?, iconPainter: androidx.compose.ui.graphics.painter.Painter?, title: String,
        subtitle: String?, subtitleContent: (@Composable ()->Unit)?, value: String?, badge: String?, enabled: Boolean,
        onClick: (() -> Unit)?, trailing: (@Composable () -> Unit)?)
}

/** Optional settings navigation; the supplied content retains host-owned settings/actions. */
interface SkinSettingsPageProvider {
    @Composable fun SettingsPage(query: String, onQuery: (String)->Unit,
        padding: androidx.compose.foundation.layout.PaddingValues, onBack: ()->Unit, content: @Composable ()->Unit)
}

/** Optional browse header actions, separate from the API 1 renderer contract. */
interface SkinBrowseHeaderProvider {
    @Composable fun BrowseHeader(account: com.music.streamx.data.model.Account?, onSearch: ()->Unit,
        onSettings: ()->Unit, onExplore: ()->Unit)
}

/** Stable semantic group identity; skin navigation must not depend on translated headings. */
val LocalSkinSettingsGroupKey=androidx.compose.runtime.staticCompositionLocalOf<String?>{null}
