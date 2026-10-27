package com.music.streamx.ui.components

import com.music.streamx.ui.components.SkinAlertDialog as AlertDialog
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.music.streamx.data.model.PlaylistPrivacy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.File
import java.util.UUID

/** Compact creation sheet; persistence and provider requests remain in the host. */
@Composable internal fun ReferencePlaylistForm(onDismiss:()->Unit, remoteCover:Boolean,
    onCreate:(String,PlaylistPrivacy,String?)->Unit) {
    var title by remember { mutableStateOf("") }
    var cover by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var remoteEditor by remember { mutableStateOf(false) }
    var url by remember { mutableStateOf("") }
    var importing by remember { mutableStateOf(false) }
    var submitted by remember { mutableStateOf(false) }
    val ownedFiles=remember { mutableListOf<File>() }
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    val focus=remember { FocusRequester() }
    val focusManager=LocalFocusManager.current
    val c=MaterialTheme.colorScheme
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if(uri!=null) scope.launch {
            importing=true;error=null
            val result=withContext(Dispatchers.IO) { runCatching {
                val directory=File(context.filesDir,"playlist-artwork").apply { mkdirs() }
                val file=File(directory,"${UUID.randomUUID()}.image")
                try {
                    context.contentResolver.openInputStream(uri).use { input ->
                        requireNotNull(input) { "Could not open the image" }
                        file.outputStream().use { output ->
                            val buffer=ByteArray(8192);var total=0
                            while(true) {
                                val read=input.read(buffer);if(read<0)break
                                total+=read;require(total<=16*1024*1024) { "Choose an image smaller than 16 MB" }
                                output.write(buffer,0,read)
                            }
                        }
                    }
                    require(file.length()>0) { "The selected image is empty" }
                    file
                } catch(e:Exception) {file.delete();throw e}
            } }
            result.fold({file -> ownedFiles+=file;cover=file.toURI().toString()}, {error=it.message ?: "Could not load the cover"})
            importing=false
        }
    }
    DisposableEffect(Unit) { onDispose {
        ownedFiles.filterNot { submitted && it.toURI().toString()==cover }.forEach { it.delete() }
    } }
    LaunchedEffect(Unit) { focus.requestFocus() }
    fun submit() {
        if(title.isNotBlank() && !importing) {
            submitted=true;focusManager.clearFocus()
            onCreate(title.trim(),PlaylistPrivacy.PRIVATE,cover)
        }
    }
    LazyColumn(Modifier.fillMaxWidth().fillMaxHeight(.92f).imePadding(),horizontalAlignment=Alignment.CenterHorizontally,
        contentPadding=PaddingValues(start=16.dp,end=16.dp,top=16.dp,bottom=24.dp)) {
        item {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                IconButton(onClick=onDismiss,modifier=Modifier.size(44.dp).clip(CircleShape).background(c.surfaceVariant)
                    .border(.5.dp,c.onSurface.copy(alpha=.14f),CircleShape)) {Icon(Icons.Rounded.Close,"Cancel new playlist",Modifier.size(25.dp))}
                Text("New Playlist",Modifier.weight(1f),textAlign=TextAlign.Center,style=MaterialTheme.typography.titleMedium.copy(fontSize=18.sp,fontWeight=FontWeight.SemiBold))
                IconButton(onClick=::submit,enabled=title.isNotBlank() && !importing,
                    modifier=Modifier.size(44.dp).clip(CircleShape).background(c.surfaceVariant).border(.5.dp,c.onSurface.copy(alpha=.14f),CircleShape)) {
                    Icon(Icons.Rounded.Check,"Save playlist",Modifier.size(27.dp))
                }
            }
        }
        item {
            Box(Modifier.padding(top=30.dp).size(180.dp).clip(RoundedCornerShape(8.dp)).background(c.surfaceVariant)
                .border(.5.dp,c.onSurface.copy(alpha=.08f),RoundedCornerShape(8.dp))
                .clickable(enabled=!importing) {if(remoteCover)remoteEditor=true else picker.launch("image/*")}
                .semantics { contentDescription="Choose playlist cover" },contentAlignment=Alignment.Center) {
                if(cover!=null)AsyncImage(cover,null,Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
                else Box(Modifier.size(64.dp).clip(CircleShape).background(c.primary),contentAlignment=Alignment.Center) {
                    if(importing)CircularProgressIndicator(Modifier.size(24.dp),color=androidx.compose.ui.graphics.Color.White,strokeWidth=2.dp)
                    else Icon(Icons.Outlined.Image,null,Modifier.size(28.dp),tint=androidx.compose.ui.graphics.Color.White)
                }
            }
        }
        item {
            Box(Modifier.padding(top=52.dp).fillMaxWidth().heightIn(min=50.dp),contentAlignment=Alignment.Center) {
                if(title.isEmpty())Text("Playlist Title",style=MaterialTheme.typography.titleLarge.copy(fontSize=22.sp,fontWeight=FontWeight.SemiBold),color=c.onSurfaceVariant.copy(alpha=.65f))
                BasicTextField(title,{title=it},singleLine=true,
                    textStyle=MaterialTheme.typography.titleLarge.copy(fontSize=22.sp,fontWeight=FontWeight.SemiBold,color=c.onSurface,textAlign=TextAlign.Center),
                    cursorBrush=SolidColor(c.primary),keyboardOptions=KeyboardOptions(imeAction=ImeAction.Done),keyboardActions=KeyboardActions(onDone={submit()}),
                    modifier=Modifier.fillMaxWidth().padding(vertical=10.dp).focusRequester(focus).semantics {contentDescription="Playlist title"})
            }
            HorizontalDivider(color=c.onSurface.copy(alpha=.1f),thickness=.5.dp)
        }
        if(cover!=null && !remoteCover)item {Text("Cover saved on this device",Modifier.padding(top=12.dp),style=MaterialTheme.typography.bodySmall,color=c.onSurfaceVariant)}
        error?.let { message -> item {Text(message,Modifier.padding(top=12.dp),color=c.error,style=MaterialTheme.typography.bodyMedium)} }
    }
    if(remoteEditor)AlertDialog(onDismissRequest={remoteEditor=false},title={Text("Playlist cover")},text={
        OutlinedTextField(url,{url=it},label={Text("Image URL")},singleLine=true)
    },confirmButton={TextButton(enabled=url.toHttpUrlOrNull()!=null,onClick={cover=url.trim();remoteEditor=false}){Text("Use cover")}},
        dismissButton={TextButton(onClick={remoteEditor=false}){Text("Cancel")}})
}
