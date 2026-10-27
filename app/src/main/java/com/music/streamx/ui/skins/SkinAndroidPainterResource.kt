package com.music.streamx.ui.skins

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import com.music.streamx.R

@Composable
fun skinAndroidPainterResource(id: Int): Painter {
    val semantic = when (id) {
        R.drawable.fav, R.drawable.ic_widget_heart -> "save"
        R.drawable.ic_widget_heart_filled -> "saved"
        R.drawable.ic_widget_next -> "next"
        R.drawable.ic_widget_previous -> "previous"
        R.drawable.ic_widget_play -> "play"
        R.drawable.ic_widget_pause -> "pause"
        R.drawable.ic_widget_shuffle -> "shuffle"
        else -> null
    }
    val vector = semantic?.let { LocalSkinRenderer.current?.icon(it) }
    return if (vector != null) rememberVectorPainter(vector) else androidx.compose.ui.res.painterResource(id)
}
