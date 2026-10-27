package com.music.streamx.ui.skins

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector

/** Semantic icon substitution across screens, dialogs and settings; default skins keep their icons. */

@Composable
fun SkinIcon(imageVector: ImageVector, contentDescription: String?, modifier: Modifier = Modifier, tint: Color = LocalContentColor.current) {
    androidx.compose.material3.Icon(LocalSkinRenderer.current?.mapIcon(imageVector) ?: imageVector,
        contentDescription, modifier, tint)
}
@Composable
fun SkinIcon(painter: Painter, contentDescription: String?, modifier: Modifier = Modifier, tint: Color = LocalContentColor.current) {
    androidx.compose.material3.Icon(painter, contentDescription, modifier, tint)
}
@Composable
fun SkinIcon(bitmap: ImageBitmap, contentDescription: String?, modifier: Modifier = Modifier, tint: Color = LocalContentColor.current) {
    androidx.compose.material3.Icon(bitmap, contentDescription, modifier, tint)
}

@Composable
fun skinSavePainter(saved: Boolean, fallback: Painter): Painter {
    val icon = LocalSkinRenderer.current?.icon(if (saved) "saved" else "save")
    return if (icon != null) androidx.compose.ui.graphics.vector.rememberVectorPainter(icon) else fallback
}
