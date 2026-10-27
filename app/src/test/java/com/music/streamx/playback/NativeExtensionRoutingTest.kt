package com.music.streamx.playback

import org.junit.Assert.*
import org.junit.Test

class NativeExtensionRoutingTest {
    @Test fun nativeSongsKeepTheirRouteWhileStationsAndOtherProvidersRemainIndependent() {
        try {
            NativeExtensionRouting.update("test-apple", true)
            assertTrue(NativeExtensionRouting.enabled("test-apple", "songs:123"))
            assertFalse(NativeExtensionRouting.enabled("test-apple", "stations:ra.123"))
            assertFalse(NativeExtensionRouting.enabled("test-other", "songs:123"))
            NativeExtensionRouting.update("test-apple", false)
            assertFalse(NativeExtensionRouting.enabled("test-apple", "songs:123"))
        } finally { NativeExtensionRouting.update("test-apple", false) }
    }
}
