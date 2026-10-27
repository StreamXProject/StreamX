package com.music.streamx.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.music.streamx.data.settings.AppSettings

/** The account's picture as shown in Settings: the chosen one, the account's own, or a placeholder. */
@Composable
fun ProfilePhotoThumb(url: String?, modifier: Modifier = Modifier) {
    Box(modifier.clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
        if (url != null) AsyncImage(url, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Icon(Icons.Rounded.Person, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * Pick the profile's picture: a photo from the device, one of the drawn
 * avatars, a surprise, or back to the account's own.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfilePhotoPicker(accountPhoto: String?, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val current by AppSettings.profilePhoto.collectAsStateWithLifecycle()
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) ProfileAvatars.savePhoto(context, uri)?.let { AppSettings.setProfilePhoto(it); onDismiss() }
    }
    val choose: (ProfileAvatars.Avatar) -> Unit = { avatar ->
        AppSettings.setProfilePhoto(ProfileAvatars.save(context, avatar))
        onDismiss()
    }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        // Content scrolling must never drag or settle the sheet toward Hidden.
        sheetGesturesEnabled = false,
        dragHandle = null,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Profile picture",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            IconButton(onClick = onDismiss) {
                Icon(Icons.Rounded.Close, contentDescription = "Close profile picture picker")
            }
        }
        androidx.compose.foundation.lazy.LazyColumn(Modifier.fillMaxWidth().fillMaxHeight(0.9f)) {
            item {
                Column(Modifier.padding(horizontal = 20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ProfilePhotoThumb(current ?: accountPhoto, Modifier.size(72.dp))
                        Spacer(Modifier.size(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Shown on this device only.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                            Icon(Icons.Rounded.PhotoLibrary, null); Spacer(Modifier.size(8.dp)); Text("Choose photo")
                        }
                        TextButton(onClick = { choose(ProfileAvatars.all.random()) }) {
                            Icon(Icons.Rounded.Refresh, null); Spacer(Modifier.size(8.dp)); Text("Surprise me")
                        }
                    }
                    if (current != null) TextButton(onClick = { AppSettings.setProfilePhoto(null); onDismiss() }) {
                        Text(if (accountPhoto != null) "Use account photo" else "Remove picture", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            ProfileAvatars.all.groupBy { it.group }.forEach { (group, avatars) ->
                item(key = group) {
                    Text("$group · ${avatars.size}", Modifier.padding(start = 20.dp, top = 18.dp, bottom = 10.dp),
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    androidx.compose.foundation.lazy.LazyRow(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(avatars.size) { index ->
                            val avatar = avatars[index]
                            Column(Modifier.size(width = 84.dp, height = 108.dp).clickable { choose(avatar) }, horizontalAlignment = Alignment.CenterHorizontally) {
                                Canvas(Modifier.size(84.dp).clip(CircleShape)
                                    .border(.5.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = .1f), CircleShape)) { avatar.draw(this) }
                                Text(avatar.label, Modifier.padding(top = 4.dp), style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}
