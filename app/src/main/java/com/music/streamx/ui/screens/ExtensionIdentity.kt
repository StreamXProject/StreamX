package com.music.streamx.ui.screens

import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import com.music.streamx.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal fun extensionDisplayName(name: String,skin: Boolean): String =
    (if(skin) name.replace(Regex("(?i)\\s+skins?$"),"") else name).replaceFirstChar{it.titlecase()}

/** Preserve brand colors; package drawables must never pass through a tinted Icon. */
@Composable internal fun ExtensionLogo(packageName: String,version: Long=0,modifier: Modifier=Modifier) {
    // Brand artwork has its own proportions, independent of a skin's large card radius
    // and Android's launcher icon mask. Keep installed and downloadable identities identical.
    val brand = when (packageName.substringAfterLast('.')) {
        "amazonmusic" -> R.drawable.extension_logo_amazon_music
        "applemusic" -> R.drawable.extension_logo_apple_music
        "spotify", "spotui" -> R.drawable.extension_logo_spotui
        else -> null
    }
    if (brand != null) {
        Image(painterResource(brand), null,
            modifier.size(44.dp), contentScale = ContentScale.Fit)
        return
    }
    if(packageName=="com.music.streamx.cloudstream") {
        Icon(Icons.Rounded.Cloud,null,modifier.size(44.dp),
            tint=MaterialTheme.colorScheme.onSurface)
        return
    }
    val context=LocalContext.current
    val bitmap by produceState<ImageBitmap?>(null,packageName,version) {
        value=withContext(Dispatchers.IO) {
            try {
                val fallback=when(packageName.substringAfterLast('.')) {
                    "applemusic" -> R.drawable.extension_logo_apple_music
                    "spotui", "spotify" -> R.drawable.extension_logo_spotui
                    "comic" -> R.drawable.extension_logo_comic
                    "dot" -> R.drawable.extension_logo_dot
                    "looper" -> R.drawable.extension_logo_looper
                    "expressive" -> R.drawable.extension_logo_material_expressive
                    "material" -> R.drawable.extension_logo_material_ui_3
                    "meridian" -> R.drawable.extension_logo_meridian
                    else -> 0
                }
                val info=runCatching {context.packageManager.getApplicationInfo(packageName,0)}.getOrNull()
                val drawable=if((info==null || info.icon==0) && fallback!=0)ContextCompat.getDrawable(context,fallback)
                    else info?.let {context.packageManager.getApplicationIcon(it)}
                drawable?.toBitmap(144,144)?.asImageBitmap()
            } catch(cancelled: CancellationException) {throw cancelled}
            catch(_: Exception) {null}
        }
    }
    val size=modifier.size(44.dp)
    bitmap?.let{Image(it,null,size)} ?: Icon(Icons.Rounded.Extension,null,size.padding(8.dp),tint=MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable internal fun ExtensionIdentityRow(packageName: String,version: Long=0,title: String,subtitle: String?,
    onClick: ()->Unit,trailing: @Composable ()->Unit={}) {
    Row(Modifier.fillMaxWidth().clickable(onClick=onClick).heightIn(min=76.dp).padding(horizontal=20.dp,vertical=14.dp),
        verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)) {
        ExtensionLogo(packageName,version)
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)) {
            Text(title,style=MaterialTheme.typography.bodyLarge,color=MaterialTheme.colorScheme.onSurface,maxLines=2,overflow=TextOverflow.Ellipsis)
            subtitle?.let{Text(it,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)}
        }
        trailing()
    }
}
