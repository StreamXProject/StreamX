package com.music.streamx.data.skins

import org.junit.Assert.*
import org.junit.Test

class SkinSectionsTest {
    private val main = SkinSpec(id="main",name="Main")
    private val looper = SkinSpec(id="looper",name="Looper")
    private val material = SkinSpec(id="material",name="Material")
    private val installed = listOf(SkinRegistry.builtIns.first(),main,looper,material)

    @Test fun sectionsFollowTheMainSkinInitially() {
        SkinSection.entries.forEach { assertEquals(main,resolveSectionSkin(main,installed,emptyMap(),it)) }
    }
    @Test fun eachSectionCanUseADifferentSkin() {
        val choices=mapOf(SkinSection.HOME to looper.id,SkinSection.PLAYER to material.id,SkinSection.MINI_PLAYER to "default",SkinSection.SETTINGS to main.id)
        assertEquals(looper,resolveSectionSkin(main,installed,choices,SkinSection.HOME))
        assertEquals(material,resolveSectionSkin(main,installed,choices,SkinSection.PLAYER))
        assertEquals("default",resolveSectionSkin(main,installed,choices,SkinSection.MINI_PLAYER).id)
        assertEquals(main,resolveSectionSkin(main,installed,choices,SkinSection.SETTINGS))
    }
    @Test fun missingSkinFallsBackWithoutChangingOtherSections() {
        val choices=mapOf(SkinSection.PLAYER to "uninstalled",SkinSection.SETTINGS to looper.id)
        assertEquals(main,resolveSectionSkin(main,installed,choices,SkinSection.PLAYER))
        assertEquals(looper,resolveSectionSkin(main,installed,choices,SkinSection.SETTINGS))
    }
    @Test fun changingMainSkinPreservesExplicitChoices() {
        val choices=mapOf(SkinSection.PLAYER to looper.id)
        assertEquals(looper,resolveSectionSkin(material,installed,choices,SkinSection.PLAYER))
        assertEquals(material,resolveSectionSkin(material,installed,choices,SkinSection.HOME))
    }
}
