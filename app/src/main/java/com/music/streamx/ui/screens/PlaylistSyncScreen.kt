package com.music.streamx.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import com.music.streamx.ui.components.BottomFadeScrim
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.streamx.R
import com.music.streamx.data.playlistsync.*
import com.music.streamx.data.extensions.ApkExtensions
import com.music.streamx.ui.components.FrostedTopBar
import com.music.streamx.ui.components.topBarContentPadding
import com.music.streamx.ui.components.PillTextField
import com.music.streamx.ui.components.lightweightLiquidGlass

@Composable
internal fun LegacyPlaylistSyncScreen(coordinator: PlaylistSyncCoordinator, onSignIn: () -> Unit, onDismiss: () -> Unit) {
    val ui by coordinator.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        if (coordinator.state.value.sources.isEmpty() && !coordinator.state.value.busy) coordinator.open()
    }
    val back: () -> Unit = {
        if (ui.stage == SyncStage.REVIEW && !ui.busy) coordinator.configure() else { coordinator.close(); onDismiss() }
    }
    BackHandler(onBack = back)
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().navigationBarsPadding()) {
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(top = topBarContentPadding(), bottom = 24.dp)) {
                item {
                    Text("Linked playlists", style = MaterialTheme.typography.displayLarge, modifier = Modifier.padding(horizontal = 20.dp))
                    Text("Manage existing add-only sync links to YouTube Music.", color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
                }
                if (ui.connected) item {
                    Row(Modifier.fillMaxWidth().padding(horizontal=20.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(ui.accountName, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton(enabled = !ui.busy, onClick = onSignIn) { Text(stringResource(R.string.playlist_sync_reconnect)) }
                    }
                }
                if (!ui.connected) item {
                    SyncCard {
                        Text(stringResource(R.string.playlist_sync_connect_description))
                        Button(onClick = onSignIn, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.sign_in_youtube_music)) }
                    }
                }
                if (ui.stage == SyncStage.CONFIGURE) item {
                    SyncCard {
                        Text("Spotify", style = MaterialTheme.typography.titleMedium)
                        Text(if (ui.spotifyAccount.isNotBlank()) ui.spotifyAccount else if (ui.spotifyInstalled) "Connect your Spotify account to load its playlists" else "Install the Spotify extension in Skins & plugins",
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        ui.spotifyError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                        Row {
                            if (ui.spotifyInstalled) TextButton(enabled = !ui.busy, onClick = {
                                ApkExtensions.installed.value.firstOrNull { it.packageName == "com.music.streamx.extensions.spotify" }?.let(ApkExtensions::showSettings)
                            }) { Text("Manage account") }
                            TextButton(enabled = !ui.busy, onClick = { coordinator.open() }) { Text("Refresh playlists") }
                        }
                        Text("Copy Spotify playlists to YouTube Music. Review track matches before syncing.", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (ui.busy) item {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                        Text(ui.progress.ifBlank { stringResource(R.string.playlist_sync_loading) })
                    }
                }
                ui.error?.let { error -> item { Text(error, color = MaterialTheme.colorScheme.error) } }
                when (ui.stage) {
                    SyncStage.CONFIGURE -> {
                        item { Text(stringResource(R.string.playlist_sync_source), style = MaterialTheme.typography.labelLarge, color=MaterialTheme.colorScheme.onSurfaceVariant, modifier=Modifier.padding(horizontal=20.dp)) }
                        items(ui.sources, key = { it.browseId }) { source ->
                            Row(Modifier.fillMaxWidth().clickable(enabled = !ui.busy) { coordinator.selectSource(source) }
                                .padding(horizontal=16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = source == ui.source, enabled = !ui.busy, onClick = { coordinator.selectSource(source) })
                                Column {
                                    Text(source.title)
                                    if (source.provider.isNotBlank()) Text(source.provider, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                        if (ui.sources.isEmpty() && !ui.busy) item { Text(stringResource(R.string.playlist_sync_no_sources)) }
                        if (ui.source != null && ui.connected) {
                            item {
                                SyncCard {
                                    Text(stringResource(R.string.playlist_sync_destination), style = MaterialTheme.typography.titleMedium)
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        RadioButton(ui.destinationId == null, enabled = !ui.busy,
                                            onClick = { coordinator.destination(null, ui.source?.title.orEmpty()) })
                                        Text(stringResource(R.string.playlist_sync_create_new))
                                    }
                                    if (ui.destinationId == null) PillTextField(value = ui.destinationTitle,
                                        onValueChange = coordinator::title, placeholder = stringResource(R.string.playlist_sync_name), enabled = !ui.busy)
                                    ui.destinations.forEach { playlist ->
                                        Row(Modifier.fillMaxWidth().clickable(enabled = !ui.busy) { coordinator.destination(playlist.playlistId, playlist.title) },
                                            verticalAlignment = Alignment.CenterVertically) {
                                            RadioButton(ui.destinationId == playlist.playlistId, enabled = !ui.busy,
                                                onClick = { coordinator.destination(playlist.playlistId, playlist.title) })
                                            Text(playlist.title)
                                        }
                                    }
                                    // A saved mapping can still be resumed if the library picker omits it.
                                    if (ui.destinationId != null && ui.destinations.none { it.playlistId == ui.destinationId }) Text(ui.destinationTitle)
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(stringResource(R.string.playlist_sync_keep), Modifier.weight(1f))
                                        com.music.streamx.ui.components.SkinSwitch(ui.keepSynchronized, enabled = !ui.busy, onCheckedChange = coordinator::keep)
                                    }
                                    Text(stringResource(R.string.playlist_sync_keep_description), style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Button(onClick = coordinator::prepare, enabled = !ui.busy && ui.destinationTitle.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                                        Text(stringResource(R.string.playlist_sync_review))
                                    }
                                }
                            }
                        }
                        if (ui.links.isNotEmpty()) {
                            item { Text(stringResource(R.string.playlist_sync_linked), style = MaterialTheme.typography.titleLarge) }
                            items(ui.links, key = { it.id }) { link ->
                                SyncCard {
                                    Text(link.source.title, style = MaterialTheme.typography.titleMedium)
                                    Text("→ ${link.destinationTitle}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    if (link.needsReview > 0) Text(stringResource(R.string.playlist_sync_needs_review, link.needsReview))
                                    link.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(stringResource(R.string.playlist_sync_keep), Modifier.weight(1f))
                                        com.music.streamx.ui.components.SkinSwitch(link.keepSynchronized, enabled = !ui.busy, onCheckedChange = { coordinator.pause(link, it) })
                                    }
                                    Row {
                                        TextButton(enabled = !ui.busy, onClick = { coordinator.selectSource(link.source) }) { Text(stringResource(R.string.playlist_sync_sync_again)) }
                                        TextButton(enabled = !ui.busy, onClick = { coordinator.forget(link) }) { Text(stringResource(R.string.playlist_sync_unlink)) }
                                    }
                                }
                            }
                        }
                    }
                    SyncStage.REVIEW -> {
                        item {
                            SyncCard {
                                Text("${ui.source?.title.orEmpty()} → ${ui.destinationTitle}", style = MaterialTheme.typography.titleMedium)
                                Text(stringResource(R.string.playlist_sync_counts,
                                    ui.matches.count { it.selectedId != null }, ui.matches.count { it.selectedId == null && !it.excluded && it.candidates.isNotEmpty() },
                                    ui.matches.count { it.candidates.isEmpty() && it.selectedId == null && !it.excluded }, ui.matches.count { it.excluded }))
                                Text(stringResource(R.string.playlist_sync_confirm_description), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        itemsIndexed(ui.matches) { index, match ->
                            var expanded by remember(match.source.videoId) { mutableStateOf(false) }
                            SyncCard {
                                Text(match.source.title, style = MaterialTheme.typography.titleMedium)
                                Text(match.source.artist, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(when {
                                    match.excluded -> stringResource(R.string.playlist_sync_skipped)
                                    match.selectedId != null -> match.candidates.firstOrNull { it.videoId == match.selectedId }?.let { "${it.title} · ${it.artist}" }
                                        ?: stringResource(R.string.playlist_sync_saved_match)
                                    match.candidates.isEmpty() -> stringResource(R.string.playlist_sync_unmatched)
                                    else -> stringResource(R.string.playlist_sync_uncertain)
                                }, style = MaterialTheme.typography.bodySmall)
                                TextButton(enabled = !ui.busy, onClick = { expanded = !expanded }) { Text(stringResource(R.string.playlist_sync_choose)) }
                                if (expanded) {
                                    match.candidates.forEach { candidate ->
                                        Row(Modifier.fillMaxWidth().clickable(enabled = !ui.busy) { coordinator.choose(index, candidate.videoId) },
                                            verticalAlignment = Alignment.CenterVertically) {
                                            RadioButton(match.selectedId == candidate.videoId, enabled = !ui.busy,
                                                onClick = { coordinator.choose(index, candidate.videoId) })
                                            Column { Text(candidate.title); Text("${candidate.artist} · ${candidate.durationText.orEmpty()}", style = MaterialTheme.typography.bodySmall) }
                                        }
                                    }
                                    TextButton(enabled = !ui.busy, onClick = { coordinator.choose(index, null) }) { Text(stringResource(R.string.playlist_sync_skip)) }
                                }
                            }
                        }
                        item {
                            Button(onClick = coordinator::apply, modifier = Modifier.fillMaxWidth(), enabled = !ui.busy && ui.matches.any { it.selectedId != null }) {
                                Text(stringResource(R.string.playlist_sync_confirm))
                            }
                        }
                    }
                    SyncStage.COMPLETE -> item {
                        SyncCard {
                            Text(stringResource(R.string.playlist_sync_complete), style = MaterialTheme.typography.headlineMedium)
                            Text(stringResource(R.string.playlist_sync_complete_description))
                            Button(onClick = { coordinator.open() }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.playlist_sync_linked)) }
                        }
                    }
                }
            }
            }
            BottomFadeScrim(modifier=Modifier.rotate(180f))
            FrostedTopBar(title = "Linked playlists", scrolled = false, onBack = back, transparentBackdrop = true, artworkPageChrome = true)
        }
    }
}

@Composable
private fun SyncCard(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp).fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(24.dp)).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
}
