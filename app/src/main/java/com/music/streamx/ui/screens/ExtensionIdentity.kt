package com.music.streamx.ui.screens

import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal fun extensionDisplayName(name: String,skin: Boolean): String =
    (if(skin) name.replace(Regex("(?i)\\s+skins?$"),"") else name).replaceFirstChar{it.titlecase()}

/** Artwork belongs to extensions: installed APK first, repository URL otherwise. */
@Composable internal fun ExtensionLogo(
    packageName: String,
    version: Long = 0,
    modifier: Modifier = Modifier,
    iconUrl: String? = null,
) {
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(null, packageName, version) {
        value = withContext(Dispatchers.IO) {
            try {
                val info = context.packageManager.getApplicationInfo(packageName, 0)
                if (info.icon == 0) null else context.packageManager.getApplicationIcon(info).toBitmap(144, 144).asImageBitmap()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { null }
        }
    }
    val url = remember(iconUrl) { iconUrl?.let(com.music.streamx.data.extensions.ExtensionRepository::safeIconUrl) }
    val size = modifier.size(44.dp)
    when {
        bitmap != null -> Image(requireNotNull(bitmap), null, size, contentScale = ContentScale.Fit)
        url != null -> {
            var loaded by remember(url) { mutableStateOf(false) }
            Box(size, contentAlignment = Alignment.Center) {
                if (!loaded) Icon(Icons.Rounded.Extension, null, Modifier.fillMaxSize().padding(8.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                coil3.compose.AsyncImage(
                    model = remember(context, url) {
                        coil3.request.ImageRequest.Builder(context).data(url)
                            .memoryCacheKey(url).diskCacheKey(url).build()
                    },
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                    onLoading = { loaded = false },
                    onSuccess = { loaded = true },
                    onError = { loaded = false },
                )
            }
        }
        else -> Icon(Icons.Rounded.Extension, null, size.padding(8.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
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
