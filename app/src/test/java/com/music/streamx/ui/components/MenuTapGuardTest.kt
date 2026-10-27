package com.music.streamx.ui.components

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MenuTapGuardTest {
    @Test fun `upward swipe cannot activate a row moving along with the finger`() {
        val guard = MenuTapGuard(24f)
        guard.down(800f, 2050f)
        guard.move(800f, 2000f)
        guard.move(800f, 1800f)
        assertFalse(guard.allowsTap)
        // Returning to the start must not turn a drag into a click.
        guard.move(800f, 2050f)
        assertFalse(guard.allowsTap)
    }

    @Test fun `next intentional tap works after a drag or cancellation`() {
        val guard = MenuTapGuard(24f)
        guard.down(100f, 200f)
        guard.move(100f, 100f)
        guard.down(100f, 200f)
        guard.move(104f, 204f)
        assertTrue(guard.allowsTap)
        guard.cancel()
        assertFalse(guard.allowsTap)
        guard.down(100f, 200f)
        assertTrue(guard.allowsTap)
    }
}
