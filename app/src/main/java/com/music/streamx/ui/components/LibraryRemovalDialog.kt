package com.music.streamx.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

/** Shared by the saved tick and the library context menu. */
@Composable
internal fun LibraryRemovalDialog(title: String,message: String,onConfirm: ()->Unit,onDismiss: ()->Unit,
    anchor: androidx.compose.ui.geometry.Rect? = MenuAnchor.recent(),haze: dev.chrisbanes.haze.HazeState? = null) {
    // Apple's own: the question and one red pill, on a glass popover grown from
    // the button that asked; anywhere else cancels.
    if (com.music.streamx.ui.skins.LocalSkinRenderer.current is com.music.streamx.ui.skins.SkinArtworkPages) {
        GlassMenu(onDismiss = onDismiss, anchor = anchor, haze = haze) {
            // Apple's own words: the question alone, no second sentence.
            Text(message.substringBefore("?").takeIf { message.contains("?") }?.let { it.replace("Library", "library") + "?" }
                ?: message.ifBlank { "Are you sure you want to delete this from your library?" },
                Modifier.padding(start = 22.dp, end = 22.dp, top = 12.dp, bottom = 14.dp),
                fontSize = 17.sp, lineHeight = 22.sp, color = MaterialTheme.colorScheme.onBackground)
            LibraryRemovalConfirmButton(title, onConfirm)
        }
        return
    }
    Dialog(onDismissRequest=onDismiss) {
        val c=MaterialTheme.colorScheme
        val shape=RoundedCornerShape(8.dp)
        Column(Modifier.fillMaxWidth().widthIn(max=340.dp).background(c.surface,shape)
            .border(.5.dp,c.outlineVariant,shape).padding(16.dp),
            horizontalAlignment=Alignment.CenterHorizontally) {
            Text(title,style=MaterialTheme.typography.titleSmall.copy(fontWeight=FontWeight.Bold,fontSize=15.sp),textAlign=TextAlign.Center)
            Spacer(Modifier.height(12.dp))
            Text(message,style=MaterialTheme.typography.bodySmall.copy(fontSize=13.sp),textAlign=TextAlign.Center)
            Spacer(Modifier.height(16.dp))
            Button(onClick=onConfirm,modifier=Modifier.fillMaxWidth().height(36.dp),shape=RoundedCornerShape(6.dp),
                contentPadding=PaddingValues(0.dp),colors=ButtonDefaults.buttonColors(containerColor=Color(0xFFE00025),contentColor=Color.White)) {
                Text("OK",fontWeight=FontWeight.SemiBold,fontSize=13.sp)
            }
            Spacer(Modifier.height(8.dp))
            Button(onClick=onDismiss,modifier=Modifier.fillMaxWidth().height(36.dp),shape=RoundedCornerShape(6.dp),
                contentPadding=PaddingValues(0.dp),colors=ButtonDefaults.buttonColors(containerColor=Color(0xFF7C7C80),contentColor=Color.White)) {
                Text("Cancel",fontSize=13.sp)
            }
        }
    }
}

/** Registers with the glass pane, which handles touch selection before clickable sees it. */
@Composable
internal fun LibraryRemovalConfirmButton(title: String, onConfirm: () -> Unit) {
    val targets = LocalHeldMenuTargets.current
    val target = remember { Any() }
    val confirm by rememberUpdatedState(onConfirm)
    DisposableEffect(targets, target) {
        onDispose { targets?.rows?.remove(target) }
    }
    Box(
        Modifier.padding(start = 12.dp, end = 12.dp, bottom = 10.dp)
            .fillMaxWidth().height(50.dp)
            .clip(CircleShape).background(Color.White.copy(alpha = .14f))
            .onGloballyPositioned { coordinates ->
                targets?.rows?.set(target, coordinates.screenRect() to { confirm() })
            }
            .clickable(onClick = onConfirm),
        contentAlignment = Alignment.Center,
    ) {
        Text(title, fontSize = 17.sp, color = Color(0xFFFF6B6B))
    }
}
