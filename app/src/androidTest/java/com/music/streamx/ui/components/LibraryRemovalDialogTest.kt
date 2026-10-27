package com.music.streamx.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LibraryRemovalDialogTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun physicalTapOnGlassConfirmationInvokesDeleteOnce() {
        var confirmations = 0
        compose.setContent {
            MaterialTheme {
                GlassMenu(onDismiss = {}, anchor = Rect(Offset(100f, 200f), Offset(100f, 200f))) {
                    LibraryRemovalConfirmButton("Delete from Library") { confirmations++ }
                }
            }
        }
        // Touch injection is deliberate: performClick bypasses the pane's pointer handler.
        compose.onNodeWithText("Delete from Library").performTouchInput { click() }
        compose.runOnIdle { assertEquals(1, confirmations) }
    }
}
