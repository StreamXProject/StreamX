package com.music.streamx.data.skins

import org.junit.Assert.*
import org.junit.Test

class SkinRegistryTest {
    @Test fun onlyDefaultAppearanceIsBundled() {
        assertEquals(listOf("default"), SkinRegistry.builtIns.map { it.id })
    }
    @Test fun bundledPalettesRemainReadable() {
        SkinRegistry.builtIns.forEach(SkinRegistry::validate)
    }
    @Test(expected = IllegalArgumentException::class)
    fun unsupportedFormatsAreRejected() {
        SkinRegistry.validate(SkinSpec(version = 4, id = "custom", name = "Custom"))
    }
    @Test(expected = IllegalArgumentException::class)
    fun unreadableTextIsRejected() {
        SkinRegistry.validate(SkinSpec(id = "custom", name = "Custom", dark = SkinPalette(text = "#000000")))
    }
    @Test(expected = IllegalArgumentException::class)
    fun transparentOrMalformedColorsAreRejected() {
        SkinRegistry.validate(SkinSpec(id = "custom", name = "Custom", dark = SkinPalette(accent = "#00FFFFFF")))
    }
    @Test fun blackAndWhiteContrastIsTwentyOneToOne() {
        assertEquals(21.0, SkinRegistry.contrast("#000000", "#FFFFFF"), 0.001)
    }

    @Test fun spotuiAssetIsAValidExecutableSkin() {
        val root = generateSequence(java.io.File(System.getProperty("user.dir"))) { it.parentFile }
            .map { java.io.File(it, "extensions/skins/spotui/app/src/main/assets/streamx/skin.json") }
            .first { it.isFile }
        val skin = kotlinx.serialization.json.Json.decodeFromString<SkinSpec>(root.readText())
        SkinRegistry.validate(skin)
        assertEquals(3, skin.version)
        assertEquals(1, skin.renderer!!.api)
        assertEquals("com.music.streamx.skins.spotui.SpotuiRenderer", skin.renderer.entryPoint)
        assertEquals(com.music.streamx.ui.skins.NavigationLayout.FLAT, skin.layout.navigation)
        assertEquals(com.music.streamx.ui.skins.LibraryLayout.ROWS, skin.layout.library)
        listOf(skin.fonts!!.regular, skin.fonts.bold).forEach { path ->
            val font = java.io.File(root.parentFile.parentFile, path)
            assertTrue("Missing packaged font: $path", font.isFile)
            assertTrue(font.length() in 1..2_097_152)
        }
    }

    @Test fun looperAssetPassesTheHostsActualSkinValidation() {
        val asset=generateSequence(java.io.File(System.getProperty("user.dir"))) {it.parentFile}
            .map{java.io.File(it,"extensions/skins/looper/app/src/main/assets/streamx/skin.json")}.first{it.isFile}
        val skin=kotlinx.serialization.json.Json.decodeFromString<SkinSpec>(asset.readText())
        SkinRegistry.validate(skin)
        assertEquals("looper",skin.id)
        assertEquals("com.music.streamx.skins.looper.LooperRenderer",skin.renderer!!.entryPoint)
    }
    @Test fun dotAssetPassesTheHostsActualSkinValidation() {
        val asset = generateSequence(java.io.File(System.getProperty("user.dir"))) { it.parentFile }
            .map { java.io.File(it, "extensions/skins/dot/app/src/main/assets/streamx/skin.json") }.first { it.isFile }
        val skin = kotlinx.serialization.json.Json.decodeFromString<SkinSpec>(asset.readText())
        SkinRegistry.validate(skin)
        assertEquals("dot", skin.id)
        assertEquals("com.music.streamx.skins.dot.DotRenderer", skin.renderer!!.entryPoint)
        listOf(skin.fonts!!.regular, skin.fonts.bold).forEach { path ->
            val font = java.io.File(asset.parentFile.parentFile, path)
            assertTrue("Missing packaged font: $path", font.isFile)
            assertTrue(font.length() in 1..2_097_152)
        }
    }
    @Test fun v2CanOverrideOneScreenAndKeepDefaultsForTheRest() {
        val skin = kotlinx.serialization.json.Json.decodeFromString<SkinSpec>(
            """{"version":2,"id":"partial","name":"Partial","layout":{"settings":"FLAT"}}""")
        SkinRegistry.validate(skin)
        assertEquals(com.music.streamx.ui.skins.SettingsLayout.FLAT, skin.layout.settings)
        assertEquals(com.music.streamx.ui.skins.FeedLayout.SHELVES, skin.layout.home)
    }

    @Test(expected = IllegalArgumentException::class)
    fun v1CannotSilentlyEnableV2Layouts() {
        SkinRegistry.validate(SkinSpec(id = "legacy", name = "Legacy", layout = com.music.streamx.ui.skins.SkinLayout(
            navigation = com.music.streamx.ui.skins.NavigationLayout.FLAT)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun oversizedLayoutValuesAreRejected() {
        SkinRegistry.validate(SkinSpec(version = 2, id = "oversized", name = "Oversized",
            layout = com.music.streamx.ui.skins.SkinLayout(shelfWidthDp = 10000)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun fontPathsCannotEscapeTheAssetsDirectory() {
        SkinRegistry.validate(SkinSpec(version = 2, id = "unsafe", name = "Unsafe",
            fonts = SkinFonts("streamx/fonts/../../other.ttf", "streamx/fonts/bold.otf")))
    }

    @Test(expected = IllegalArgumentException::class)
    fun renderersRequireVersionThree() {
        SkinRegistry.validate(SkinSpec(version = 2, id = "old", name = "Old",
            renderer = SkinRendererSpec("com.example.Renderer")))
    }
    @Test(expected = IllegalArgumentException::class)
    fun unsupportedRendererAbiIsRejected() {
        SkinRendererSpec("com.example.Renderer", 999).validate()
    }
    @Test(expected = IllegalArgumentException::class)
    fun malformedEntryPointIsRejected() {
        SkinRendererSpec("../../Renderer").validate()
    }
    @Test(expected = IllegalArgumentException::class)
    fun extensionLayoutsCannotActivateWithoutTheirRenderer() {
        SkinRegistry.validate(SkinSpec(version = 3, id = "missing", name = "Missing",
            layout = com.music.streamx.ui.skins.SkinLayout(player = com.music.streamx.ui.skins.PlayerLayout.EXTENSION)))
    }
    @Test fun legacySkinsKeepTheOriginalLayout() {
        val skin = kotlinx.serialization.json.Json.decodeFromString<SkinSpec>("""{"id":"old","name":"Old"}""")
        SkinRegistry.validate(skin)
        assertEquals(com.music.streamx.ui.skins.SkinLayout(), skin.layout)
        assertNull(skin.fonts)
    }
    @Test fun missingRendererRestoresUsableDefaultSlots() {
        val configured = com.music.streamx.ui.skins.SkinLayout(
            player = com.music.streamx.ui.skins.PlayerLayout.EXTENSION,
            icons = com.music.streamx.ui.skins.SkinIconPack.EXTENSION,
            home = com.music.streamx.ui.skins.FeedLayout.QUICK_ACCESS,
            navigation = com.music.streamx.ui.skins.NavigationLayout.FLAT,
            library = com.music.streamx.ui.skins.LibraryLayout.ROWS)
        assertEquals(com.music.streamx.ui.skins.SkinLayout(), com.music.streamx.ui.skins.skinLayoutForRenderer(configured, false))
        assertEquals(configured, com.music.streamx.ui.skins.skinLayoutForRenderer(configured, true))
    }

}
