package com.music.streamx

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import com.music.streamx.ui.components.floatingtabbar.FloatingTabBarScrollConnection
import org.junit.Assert.*
import org.junit.Test

class FloatingTabBarScrollTest {
    @Test fun layoutAdjustmentsCannotFoldOrExpandDock() {
        val connection=FloatingTabBarScrollConnection(scrollThresholdPx=50f)
        connection.onPostScroll(Offset(0f,-200f),Offset.Zero,NestedScrollSource.SideEffect)
        assertFalse(connection.isInline)
        connection.onPostScroll(Offset(0f,-60f),Offset.Zero,NestedScrollSource.UserInput)
        assertTrue(connection.isInline)
        connection.onPostScroll(Offset(0f,200f),Offset.Zero,NestedScrollSource.SideEffect)
        assertTrue(connection.isInline)
        connection.onPostScroll(Offset(0f,60f),Offset.Zero,NestedScrollSource.UserInput)
        assertFalse(connection.isInline)
    }
    @Test fun draggingBlankSpaceOrPastTheEndCannotFoldDock() {
        val connection=FloatingTabBarScrollConnection(scrollThresholdPx=50f)
        repeat(4) {connection.onPostScroll(Offset.Zero,Offset(0f,-200f),NestedScrollSource.UserInput)}
        assertFalse(connection.isInline)
    }
    @Test fun pullingDownPastTheTopExpandsDock() {
        val connection=FloatingTabBarScrollConnection(scrollThresholdPx=50f)
        connection.inline()
        connection.onPostScroll(Offset.Zero,Offset(0f,200f),NestedScrollSource.UserInput)
        assertFalse(connection.isInline)
    }
    @Test fun realContentMovementStillFoldsAndExpands() {
        val connection=FloatingTabBarScrollConnection(scrollThresholdPx=50f)
        connection.onPostScroll(Offset(0f,-60f),Offset.Zero,NestedScrollSource.UserInput)
        assertTrue(connection.isInline)
        connection.onPostScroll(Offset(0f,60f),Offset.Zero,NestedScrollSource.UserInput)
        assertFalse(connection.isInline)
    }
}
