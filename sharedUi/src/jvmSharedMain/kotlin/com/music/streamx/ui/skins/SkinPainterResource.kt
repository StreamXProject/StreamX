package com.music.streamx.ui.skins

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import com.music.streamx.sharedui.resources.*
import org.jetbrains.compose.resources.DrawableResource

/** Resource icons join the same semantic pack as Material icons; brand artwork is preserved. */
@Composable
fun skinPainterResource(resource: DrawableResource): Painter {
    val semantic = when (resource) {
        Res.drawable.fav -> "save"
        Res.drawable.ic_player_next -> "next"
        Res.drawable.ic_player_previous -> "previous"
        Res.drawable.ic_player_play -> "play"
        Res.drawable.ic_player_pause -> "pause"
        else -> null
    }
    val vector = semantic?.let { LocalSkinRenderer.current?.icon(it) }
    return if (vector != null) rememberVectorPainter(vector) else org.jetbrains.compose.resources.painterResource(resource)
}
