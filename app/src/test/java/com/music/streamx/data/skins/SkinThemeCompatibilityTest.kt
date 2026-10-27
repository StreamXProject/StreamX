package com.music.streamx.data.skins

import com.music.streamx.ui.skins.SkinThemeProvider
import com.music.streamx.ui.skins.validateSkinThemeImplementation
import org.junit.Assert.*
import org.junit.Test

class SkinThemeCompatibilityTest {
    abstract class MissingThemeMethods: SkinThemeProvider
    abstract class MissingComposition: com.music.streamx.ui.skins.SkinCompositionProvider
    @Test(expected=IllegalArgumentException::class)
    fun incompleteThemeCompositionIsRejectedBeforeUse() {validateSkinThemeImplementation(MissingComposition::class.java)}
    abstract class MissingSettings: com.music.streamx.ui.skins.SkinSettingsProvider
    @Test(expected=IllegalArgumentException::class)
    fun incompleteSettingsCapabilityIsRejectedBeforeUse() {validateSkinThemeImplementation(MissingSettings::class.java)}
    abstract class MissingLibrary: com.music.streamx.ui.skins.SkinLibraryProvider
    @Test(expected=IllegalArgumentException::class)
    fun incompleteLibraryCapabilityIsRejectedBeforeUse() {validateSkinThemeImplementation(MissingLibrary::class.java)}
    abstract class MissingSettingsPage: com.music.streamx.ui.skins.SkinSettingsPageProvider
    @Test(expected=IllegalArgumentException::class)
    fun incompleteSettingsPageIsRejectedBeforeUse() {validateSkinThemeImplementation(MissingSettingsPage::class.java)}
    abstract class MissingBrowseHeader: com.music.streamx.ui.skins.SkinBrowseHeaderProvider
    @Test(expected=IllegalArgumentException::class)
    fun incompleteBrowseHeaderIsRejectedBeforeUse() {validateSkinThemeImplementation(MissingBrowseHeader::class.java)}
    class LegacyRendererWithoutTheme
    @Test(expected=IllegalArgumentException::class)
    fun incompleteThemeIsRejectedBeforeComposition() {validateSkinThemeImplementation(MissingThemeMethods::class.java)}
    @Test fun legacySkinsDoNotNeedTheOptionalThemeContract() {validateSkinThemeImplementation(LegacyRendererWithoutTheme::class.java)}
    @Test fun publishedComposableMethodNameStaysStable() {
        assertTrue(SkinThemeProvider::class.java.methods.any {it.name=="Colors"})
        assertFalse(SkinThemeProvider::class.java.methods.any {it.name=="colors"})
    }
}
