package com.music.streamx.ui.components

import androidx.compose.runtime.Composable

@Composable
internal actual fun GlassMenuWindow(dark: Boolean, blurScreen: Boolean, x: Int, y: Int, width: Int, maxHeight: Int, radiusPx: Float, alpha: Float, pane: androidx.compose.ui.graphics.Color) = Unit

internal actual fun frostedWindow(): Boolean = false
