package com.music.streamx.ui.theme

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Typography
import androidx.compose.material3.Shapes
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.produceState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import com.music.streamx.ui.skins.LocalSkinLayout
import com.music.streamx.data.skins.SkinFontsLoader
import kotlinx.coroutines.CancellationException
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.streamx.data.skins.SkinRegistry
import com.music.streamx.data.skins.SkinSection
import com.music.streamx.data.skins.resolveSectionSkin
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.music.streamx.R

// Apple Music's signature red. No longer the primary accent, but kept for the
// spots (Replay's rank badge) that want that specific red regardless of theme.
val AccentRed = Color(0xFFFA2D48)

private val DarkColors = darkColorScheme(
    primary = Color.White,
    onPrimary = Color.Black,
    background = Color.Black,
    onBackground = Color.White,
    surface = Color(0xFF0D0D0F),
    onSurface = Color.White,
    surfaceVariant = Color(0xFF1C1C1E),
    onSurfaceVariant = Color(0xFF8E8E93),
    outline = Color(0xFF2C2C2E),
)

private val LightColors = lightColorScheme(
    primary = Color.Black,
    onPrimary = Color.White,
    background = Color.White,
    onBackground = Color.Black,
    surface = Color(0xFFF7F7F9),
    onSurface = Color.Black,
    surfaceVariant = Color(0xFFF2F2F7),
    onSurfaceVariant = Color(0xFF6E6E73),
    outline = Color(0xFFE5E5EA),
)

/**
 * SF Pro Display, the face Apple Music itself is set in. Only the weights the
 * type scale actually asks for are bundled; Compose synthesises nothing, so a
 * missing weight would silently fall back to the nearest one shipped.
 */
val SFProDisplay = FontFamily(
    Font(R.font.sf_pro_display_regular, FontWeight.W400),
    Font(R.font.sf_pro_display_medium, FontWeight.W500),
    Font(R.font.sf_pro_display_semibold, FontWeight.W600),
    Font(R.font.sf_pro_display_bold, FontWeight.W700),
    Font(R.font.sf_pro_display_heavy, FontWeight.W800),
)

// Heavy, tight typography — the backbone of the Apple Music look.
private val StreamXTypography = Typography(
    displayLarge = TextStyle(fontWeight = FontWeight.W800, fontSize = 34.sp, letterSpacing = (-0.8).sp),
    headlineLarge = TextStyle(fontWeight = FontWeight.W800, fontSize = 30.sp, letterSpacing = (-0.7).sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.W700, fontSize = 22.sp, letterSpacing = (-0.4).sp),
    titleLarge = TextStyle(fontWeight = FontWeight.W700, fontSize = 20.sp, letterSpacing = (-0.3).sp),
    titleMedium = TextStyle(fontWeight = FontWeight.W600, fontSize = 16.sp, letterSpacing = (-0.2).sp),
    bodyLarge = TextStyle(fontWeight = FontWeight.W400, fontSize = 16.sp),
    bodyMedium = TextStyle(fontWeight = FontWeight.W400, fontSize = 14.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.W600, fontSize = 12.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.W600, fontSize = 11.sp),
).withFamily(SFProDisplay)

/** Applies [family] to every style in the scale, so nothing is left on Roboto. */
private fun Typography.withFamily(family: FontFamily) = Typography(
    displayLarge = displayLarge.copy(fontFamily = family),
    displayMedium = displayMedium.copy(fontFamily = family),
    displaySmall = displaySmall.copy(fontFamily = family),
    headlineLarge = headlineLarge.copy(fontFamily = family),
    headlineMedium = headlineMedium.copy(fontFamily = family),
    headlineSmall = headlineSmall.copy(fontFamily = family),
    titleLarge = titleLarge.copy(fontFamily = family),
    titleMedium = titleMedium.copy(fontFamily = family),
    titleSmall = titleSmall.copy(fontFamily = family),
    bodyLarge = bodyLarge.copy(fontFamily = family),
    bodyMedium = bodyMedium.copy(fontFamily = family),
    bodySmall = bodySmall.copy(fontFamily = family),
    labelLarge = labelLarge.copy(fontFamily = family),
    labelMedium = labelMedium.copy(fontFamily = family),
    labelSmall = labelSmall.copy(fontFamily = family),
)

private val LocalSkinDarkTheme = androidx.compose.runtime.staticCompositionLocalOf { true }

private data class SkinRuntime(
    val fonts: FontFamily?,
    val renderer: com.music.streamx.ui.skins.SkinUiRenderer?,
)

// Shared by nested section themes for this composition's lifetime. SkinSpec includes
// the package version, certificate and asset configuration, so a changed skin gets
// a fresh runtime. Keep the renderer and fonts together: either changing after a
// page's first frame causes its typography and settings layout to jump.
private val LocalSkinRuntimes = androidx.compose.runtime.staticCompositionLocalOf<
    MutableMap<com.music.streamx.data.skins.SkinSpec, SkinRuntime>?
> { null }


@Composable
internal fun SkinSectionTheme(section: SkinSection, content: @Composable () -> Unit) {
    StreamXTheme(darkTheme = LocalSkinDarkTheme.current, section = section) {
        if(section == SkinSection.SETTINGS) androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.background(MaterialTheme.colorScheme.background)) { content() }
        else content()
    }
}

@Composable
internal fun sectionSkin(section: SkinSection): com.music.streamx.data.skins.SkinSpec {
    val main by SkinRegistry.active.collectAsStateWithLifecycle()
    val installed by SkinRegistry.skins.collectAsStateWithLifecycle()
    val overrides by SkinRegistry.sectionOverrides.collectAsStateWithLifecycle()
    return resolveSectionSkin(main, installed, overrides, section)
}

@Composable
internal fun sectionRenderer(section: SkinSection): com.music.streamx.ui.skins.SkinUiRenderer? {
    val skin = sectionSkin(section)
    val context = LocalContext.current
    LocalSkinRuntimes.current?.get(skin)?.let { return it.renderer }
    val renderer by androidx.compose.runtime.key(skin) { produceState<com.music.streamx.ui.skins.SkinUiRenderer?>(null, skin) {
        value = null
        value = try { com.music.streamx.data.skins.SkinUiLoader.load(context,skin) }
        catch(e: CancellationException) { throw e }
        catch(e: Exception) { null }
    }
    }
    return renderer
}

@Composable
fun StreamXTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    section: SkinSection = SkinSection.HOME,
    overrideSkin: com.music.streamx.data.skins.SkinSpec? = null,
    content: @Composable () -> Unit,
) {
    val skin = overrideSkin ?: sectionSkin(section)
    val context = LocalContext.current
    val runtimes = LocalSkinRuntimes.current ?: remember { mutableMapOf() }
    val runtime by androidx.compose.runtime.key(skin) { produceState<SkinRuntime?>(runtimes[skin], skin) {
        runtimes[skin]?.let { value = it; return@produceState }
        value = try {
            SkinRuntime(SkinFontsLoader.load(context, skin), com.music.streamx.data.skins.SkinUiLoader.load(context, skin))
                .also { runtimes[skin] = it }
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            android.util.Log.w("SkinUi", "Skin could not be loaded; restoring the default appearance", e)
            if(overrideSkin == null) {
                if (SkinRegistry.sectionOverrides.value.containsKey(section)) SkinRegistry.selectSection(section,null)
                else SkinRegistry.select("default")
            }
            null
        }
    }
    }
    val fontFamily = runtime?.fonts
    val renderer = runtime?.renderer
    val fallbackTypography = remember(fontFamily, skin.version) {
        if (skin.version >= 2) StreamXTypography.withFamily(fontFamily ?: SFProDisplay).copy(
            displayLarge = StreamXTypography.displayLarge.copy(fontFamily = fontFamily ?: SFProDisplay, fontSize = 28.sp, letterSpacing = 0.sp),
            headlineLarge = StreamXTypography.headlineLarge.copy(fontFamily = fontFamily ?: SFProDisplay, fontSize = 26.sp, letterSpacing = 0.sp),
        ) else StreamXTypography
    }
    val palette = if (darkTheme) skin.dark else skin.light
    val fallbackColors = remember(skin, darkTheme) {
        val base = if (darkTheme) DarkColors else LightColors
        if (skin.id == "default" || palette == null) base else {
            fun color(value: String) = Color(android.graphics.Color.parseColor(value))
            val onAccent = if (SkinRegistry.contrast(palette.accent, "#000000") >= SkinRegistry.contrast(palette.accent, "#FFFFFF")) Color.Black else Color.White
            base.copy(
                primary = color(palette.accent), onPrimary = onAccent,
                secondary = color(palette.accent), onSecondary = onAccent,
                surfaceTint = color(palette.accent),
                background = color(palette.background), onBackground = color(palette.text),
                surface = color(palette.surface), onSurface = color(palette.text),
                surfaceVariant = color(palette.surfaceVariant), onSurfaceVariant = color(palette.secondary),
                surfaceContainerLow = if (skin.version >= 2) color(palette.surface) else base.surfaceContainerLow,
                surfaceContainer = if (skin.version >= 2) color(palette.surface) else base.surfaceContainer,
                surfaceContainerHigh = if (skin.version >= 2) color(palette.surfaceVariant) else base.surfaceContainerHigh,
                surfaceContainerHighest = if (skin.version >= 2) color(palette.surfaceVariant) else base.surfaceContainerHighest,
            )
        }
    }
    val fallbackShapes = remember(skin.cornerRadiusDp) {
        skin.cornerRadiusDp?.let { radius ->
            Shapes(
                extraSmall = RoundedCornerShape(minOf(radius, 4).dp),
                small = RoundedCornerShape(minOf(radius, 8).dp),
                medium = RoundedCornerShape(radius.dp),
                large = RoundedCornerShape((radius + 4).dp),
                extraLarge = RoundedCornerShape((radius + 10).dp),
            )
        } ?: Shapes()
    }
    val themeProvider = (renderer as? com.music.streamx.ui.skins.SkinThemeProvider).takeUnless { renderer is com.music.streamx.ui.skins.SkinUseHostTheme }
    val colors = (themeProvider?.Colors(darkTheme) ?: fallbackColors).let { palette ->
        if (renderer is com.music.streamx.ui.skins.SkinDefaultPlayerProvider) palette.copy(
            // Off switches sit on grouped surfaceVariant cards. Their track and
            // outline need separate tones instead of disappearing into the card.
            surfaceContainerHighest = if (darkTheme) Color(0xFF3A3A3C) else Color(0xFFD1D1D6),
            outline = Color(0xFF8E8E93),
            outlineVariant = if (darkTheme) Color(0xFF48484A) else Color(0xFFC7C7CC),
        ) else palette
    }
    val typography = (themeProvider?.typography() ?: fallbackTypography).let { base ->
        fontFamily?.let { base.withFamily(it) } ?: base
    }
    val shapes = themeProvider?.shapes() ?: fallbackShapes
    androidx.compose.runtime.SideEffect {
        fun hex(color: Color) = "#%06X".format(color.toArgb() and 0xffffff)
        val palette = com.music.streamx.data.skins.SkinPalette(
            hex(colors.background), hex(colors.surface), hex(colors.surfaceContainerHigh),
            hex(colors.onSurface), hex(colors.onSurfaceVariant), hex(colors.primary))
        SkinRegistry.runtimePalettes[skin.id to darkTheme] = palette
        if(section == SkinSection.HOME) SkinRegistry.runtimeAppearance = Triple(skin.id, darkTheme, palette)
    }
    val playerPalette = remember(skin) {
        com.music.streamx.ui.skins.SkinPlayerPalette(
            Color(android.graphics.Color.parseColor(skin.dark.background)),
            Color(android.graphics.Color.parseColor(skin.dark.surfaceVariant)))
    }
    CompositionLocalProvider(LocalSkinDarkTheme provides darkTheme,
        LocalSkinRuntimes provides runtimes,
        androidx.compose.material3.LocalContentColor provides colors.onBackground,
        com.music.streamx.ui.skins.LocalSkinRenderer provides renderer,
        LocalSkinLayout provides com.music.streamx.ui.skins.skinLayoutForRenderer(skin.layout, renderer != null),
        com.music.streamx.ui.skins.LocalSkinPlayerPalette provides playerPalette) {
        val readyContent: @Composable () -> Unit = {
            if (runtime != null || skin.renderer == null) content()
            else androidx.compose.foundation.layout.Box(
                androidx.compose.ui.Modifier
                    .fillMaxSize()
                    .background(colors.background),
            )
        }
        val composition=renderer as? com.music.streamx.ui.skins.SkinCompositionProvider
        if(composition!=null) composition.Theme(colors,typography,shapes,readyContent)
        else MaterialTheme(colorScheme = colors, typography = typography, shapes = shapes, content = readyContent)
    }
}

/**
 * Draws the status and navigation bar glyphs dark or light.
 *
 * `enableEdgeToEdge()` decides this from the *system* dark-mode setting, which
 * is the wrong input the moment the in-app theme disagrees with it: Light theme
 * on a phone in dark mode left white icons on a white bar, invisible. The bars
 * have to follow the content the app is actually painting. The player supplies
 * its own stable light-icon value and paints contrast behind it; every other
 * surface follows the theme. Hence a parameter rather than reading it here.
 */
@Composable
fun SystemBarIcons(dark: Boolean) {
    val view = LocalView.current
    if (view.isInEditMode) return
    val window = findWindow(view) ?: return
    SideEffect {
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = dark
            isAppearanceLightNavigationBars = dark
        }
    }
}
