package com.music.streamx.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.music.streamx.ui.components.backdrop.backdrops.layerBackdrop
import com.music.streamx.ui.components.backdrop.backdrops.rememberLayerBackdrop
import com.music.streamx.ui.skins.*

/** The player's own backdrop: never refract the unrelated home screen behind its dialog. */
@Composable fun ReferencePlayerGlass(content: @Composable ()->Unit) {
    val style=(LocalSkinRenderer.current as? SkinDefaultPlayerProvider)?.PlayerStyle()
    if(style==null){content();return}
    val useGlass = LocalLiquidGlassEnabled.current && isGlassSupported()
    val backdrop=rememberLayerBackdrop()
    CompositionLocalProvider(LocalAppBackdrop provides backdrop,
        LocalSkinPlayerBackdrop provides Modifier.layerBackdrop(backdrop),
        LocalSkinPlayerGlass provides { modifier,active ->
            val shape=RoundedCornerShape(50)
            if(active && useGlass)modifier.liquidGlass(shape)
            else if(active)modifier.clip(shape).background(Color.White.copy(alpha=.14f)).border(.5.dp,Color.White.copy(alpha=.16f),shape)
            else modifier
        }) {
        content()
    }
}
