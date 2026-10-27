package com.music.streamx.ui.components

import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.window.DialogWindowProvider

/**
 * The menu's window is the pane itself, placed on the screen where the pane
 * goes: its background is a rounded shape the platform blurs what's beneath
 * through (Android 12+), which is the frost. A menu from a hold also blurs the
 * whole screen behind it; one from a tap leaves the page as it is.
 */
@Composable
internal actual fun GlassMenuWindow(dark: Boolean, blurScreen: Boolean, x: Int, y: Int, width: Int, maxHeight: Int, radiusPx: Float, alpha: Float, pane: androidx.compose.ui.graphics.Color) {
    val window = (LocalView.current.parent as? DialogWindowProvider)?.window ?: return
    val density = LocalDensity.current
    DisposableEffect(window, dark, blurScreen, pane) {
        window.setWindowAnimations(0)
        window.setFormat(android.graphics.PixelFormat.TRANSLUCENT)
        window.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN)
        window.setBackgroundDrawable(GradientDrawable().apply {
            cornerRadius = radiusPx
            // The pane's own tint: the platform blurs through this shape.
            setColor(if (frostedWindow()) pane.toArgb() else android.graphics.Color.TRANSPARENT)
        })
        if (Build.VERSION.SDK_INT >= 31) {
            window.setBackgroundBlurRadius(with(density) { 28.dp.roundToPx() })
        }
        if (blurScreen) {
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            if (Build.VERSION.SDK_INT >= 31) {
                window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                window.attributes = window.attributes.apply { blurBehindRadius = with(density) { 30.dp.roundToPx() } }
                window.setDimAmount(if (dark) .22f else .1f)
            } else {
                window.setDimAmount(if (dark) .5f else .28f)
            }
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            window.setDimAmount(0f)
        }
        onDispose { }
    }
    SideEffect {
        val params = window.attributes
        if (params.x != x || params.y != y || params.width != width || params.alpha != alpha || params.gravity != (Gravity.TOP or Gravity.START)) {
            window.attributes = params.apply {
                gravity = Gravity.TOP or Gravity.START
                this.x = x
                this.y = y
                this.width = width
                height = ViewGroup.LayoutParams.WRAP_CONTENT
                this.alpha = alpha
            }
        }
    }
}

internal actual fun frostedWindow(): Boolean = Build.VERSION.SDK_INT >= 31
