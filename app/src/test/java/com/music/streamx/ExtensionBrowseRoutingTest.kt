package com.music.streamx
import com.music.streamx.data.extensions.ApkCatalogue
import org.junit.Test
import org.junit.Assert.*

class ExtensionBrowseRoutingTest {
    @Test fun browseCategoriesKeepNestedUriAndUseShelfPages() {
        val target=ApkCatalogue.parseBrowseId("apk:source:collection:browse:spotify:page:made-for-you")!!
        assertEquals("browse:spotify:page:made-for-you",target.id)
        assertTrue(target.isBrowseCategory)
    }
    @Test fun simpleCollectionsAndPlaylistsKeepTheirOwnScreens() {
        for(key in listOf("apk:source:collection:liked","apk:source:collection:home:section","apk:source:playlist:playlist123"))
            assertFalse(ApkCatalogue.parseBrowseId(key)!!.isBrowseCategory)
    }
}
