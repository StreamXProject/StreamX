package com.music.streamx.ui.components

import com.music.streamx.ui.skins.SkinIcon as Icon
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ManageAccounts
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.music.streamx.R
import com.music.streamx.auth.GoogleAccountSession
import com.music.streamx.auth.YouTubeProfile
import com.music.streamx.data.settings.AppSettings
import com.music.streamx.data.sources.SourceRegistry
import com.music.streamx.data.sources.SourceKind
import com.music.streamx.data.streamx.StreamXSession
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials

/** Shared compact selector showing the account for the active catalogue. */
@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
fun AccountProfileSelector(
    accounts: List<GoogleAccountSession>,
    activeAccountId: String?,
    activeProfileId: String?,
    hazeState: HazeState,
    onSelect: (GoogleAccountSession, YouTubeProfile) -> Unit,
    onAddAccount: () -> Unit,
    onRemoveAccount: (GoogleAccountSession) -> Unit,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    extensionName: String? = null,
    extensionAccount: com.music.streamx.data.model.Account? = null,
    onManageExtension: (() -> Unit)? = null,
) {
    var managing by remember { mutableStateOf(false) }
    val catalogue by SourceRegistry.catalogue.collectAsStateWithLifecycle()
    val serverAccount by StreamXSession.state.collectAsStateWithLifecycle()
    val streamX = extensionName == null && catalogue == SourceKind.STREAMX
    val reduceDynamicBlur by AppSettings.reduceDynamicBlur.collectAsStateWithLifecycle()
    val shape = MaterialTheme.shapes.extraLarge
    Column(
        modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.scrim.copy(alpha = .48f))
            .clickable(onClick = onDismiss),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            color = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurface,
            shape = shape,
            modifier = Modifier
                .padding(top = 56.dp, start = 20.dp, end = 20.dp)
                .fillMaxWidth()
                .clip(shape)
                .then(
                    if (reduceDynamicBlur) {
                        Modifier.background(MaterialTheme.colorScheme.surface)
                    } else {
                        Modifier.optimizedHazeEffect(
                            state = hazeState,
                            style = HazeMaterials.thin(MaterialTheme.colorScheme.surface),
                        )
                    },
                )
                .clickable(onClick = {}),
        ) {
            LazyColumn {
                item { Text(if(extensionName != null) "$extensionName account" else stringResource(if (streamX) R.string.streamx_account else R.string.switch_account), style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(20.dp, 18.dp, 20.dp, 8.dp)) }
                if (extensionName != null) {
                    item {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            if (!extensionAccount?.thumbnailUrl.isNullOrBlank())
                                AsyncImage(extensionAccount?.thumbnailUrl,null,Modifier.size(38.dp).clip(MaterialTheme.shapes.extraLarge))
                            else Icon(Icons.Rounded.Person,null,Modifier.size(38.dp))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(extensionAccount?.name?.takeIf { it.isNotBlank() } ?: extensionName,
                                    maxLines=1,overflow=TextOverflow.Ellipsis)
                                if (!extensionAccount?.email.isNullOrBlank())
                                    Text(extensionAccount?.email.orEmpty(),style=MaterialTheme.typography.bodySmall,
                                        color=MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if(extensionAccount != null) Icon(Icons.Rounded.Check,stringResource(R.string.selected_account))
                        }
                    }
                    onManageExtension?.let { manage ->
                        item { SelectorAction(Icons.Rounded.ManageAccounts,stringResource(R.string.manage_accounts),manage) }
                    }
                } else if (streamX) {
                    item {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            if (!serverAccount.photo.isNullOrBlank()) {
                                AsyncImage(serverAccount.photo, null, Modifier.size(38.dp).clip(MaterialTheme.shapes.extraLarge))
                            } else {
                                Icon(Icons.Rounded.Person, null, Modifier.size(38.dp))
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(serverAccount.name.ifBlank { stringResource(R.string.streamx_account) },
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Icon(Icons.Rounded.Check, stringResource(R.string.selected_account))
                        }
                    }
                    item { SelectorAction(Icons.Rounded.ManageAccounts, stringResource(R.string.switch_account), onAddAccount) }
                    item { SelectorAction(Icons.Rounded.Person, stringResource(R.string.sign_out)) {
                        StreamXSession.signOut()
                        onDismiss()
                    } }
                } else {
                    accounts.forEach { account ->
                    item {
                        Text(account.email.ifBlank { account.name.ifBlank { stringResource(R.string.accounts) } },
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(20.dp, 12.dp, 20.dp, 4.dp))
                    }
                    items(account.profiles.size) { index ->
                        val profile = account.profiles[index]
                        ProfileRow(profile, account.accountId == activeAccountId && profile.profileId == activeProfileId,
                            managing, { onSelect(account, profile); onDismiss() }, { onRemoveAccount(account) })
                    }
                    }
                    item { SelectorAction(Icons.Rounded.Add, stringResource(R.string.add_account), onAddAccount) }
                    item { SelectorAction(Icons.Rounded.ManageAccounts, stringResource(R.string.manage_accounts)) { managing = !managing } }
                }
                item { SelectorAction(Icons.Rounded.Settings, stringResource(R.string.settings), onOpenSettings) }
            }
        }
    }
}

@Composable private fun ProfileRow(profile: YouTubeProfile, selected: Boolean, managing: Boolean,
    onClick: () -> Unit, onRemove: () -> Unit) {
    val description = stringResource(if (selected) R.string.selected_account else R.string.switch_account)
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = Role.RadioButton, onClick = if (managing) onRemove else onClick)
        .semantics { contentDescription = description }
        .padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (profile.avatar != null) AsyncImage(profile.avatar, null, Modifier.size(38.dp).clip(MaterialTheme.shapes.extraLarge))
        else Icon(Icons.Rounded.Person, null, Modifier.size(38.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) {
            Text(profile.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(profile.handle.ifBlank { stringResource(if (profile.isBrandAccount) R.string.brand_account else R.string.personal) },
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (selected && !managing) Icon(Icons.Rounded.Check, stringResource(R.string.selected_account), tint = MaterialTheme.colorScheme.primary)
        if (managing) Text(stringResource(R.string.sign_out), color = MaterialTheme.colorScheme.error)
    }
}

@Composable private fun SelectorAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable(role = Role.Button, onClick = onClick)
        .semantics { contentDescription = label }.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null); Spacer(Modifier.width(16.dp)); Text(label)
    }
}
