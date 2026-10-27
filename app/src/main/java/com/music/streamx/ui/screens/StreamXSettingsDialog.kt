package com.music.streamx.ui.screens

import com.music.streamx.ui.components.SkinAlertDialog as AlertDialog
import com.music.streamx.ui.skins.SkinIcon as Icon
import androidx.compose.foundation.layout.*
import dev.chrisbanes.haze.hazeSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Dns
import com.music.streamx.data.streamx.StreamXServer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.streamx.R
import com.music.streamx.data.streamx.StreamXAccountSession
import com.music.streamx.data.streamx.StreamXSession
import com.music.streamx.ui.components.FrostedTopBar
import com.music.streamx.ui.components.topBarContentPadding

/** StreamX account controls share the Spotify extension's settings layout. */
@Composable
internal fun StreamXSettingsDialog(onOpenAccount: () -> Unit, onSelectServer: (StreamXServer) -> Unit, onAddServer: () -> Unit, onEditServer: (StreamXServer) -> Unit, onDismiss: () -> Unit) {
    val session by StreamXSession.state.collectAsStateWithLifecycle()
    val servers by StreamXSession.servers.collectAsStateWithLifecycle()
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Box(Modifier.fillMaxSize()) {
                StreamXSettingsContent(
                    session = session,
                    onOpenAccount = onOpenAccount,
                    servers = servers,
                    onSelectServer = onSelectServer,
                    onAddServer = onAddServer,
                    onEditServer = onEditServer,
                    onRemoveServer = { StreamXSession.removeServer(it.id) },
                    onSignOut = { StreamXSession.signOut(); onDismiss() },
                )
                FrostedTopBar(title = "StreamX", scrolled = false, onBack = onDismiss,
                    transparentBackdrop = true, artworkPageChrome = true)
            }
        }
    }
}

@Composable
internal fun StreamXSettingsContent(
    session: StreamXAccountSession,
    onOpenAccount: () -> Unit,
    servers: List<StreamXServer>,
    onSelectServer: (StreamXServer) -> Unit,
    onAddServer: () -> Unit,
    onEditServer: (StreamXServer) -> Unit = {},
    onRemoveServer: (StreamXServer) -> Unit = {},
    onSignOut: () -> Unit,
) {
    val connected = session.token.isNotBlank() && !session.expired
    var editing by remember { mutableStateOf<StreamXServer?>(null) }
    val haze = remember { dev.chrisbanes.haze.HazeState() }
    var removing by remember { mutableStateOf<StreamXServer?>(null) }
    removing?.let { server ->
        AlertDialog(onDismissRequest = { removing = null }, title = { Text(stringResource(R.string.streamx_servers_remove_title)) },
            text = { Text(server.name) }, confirmButton = { TextButton(onClick = { onRemoveServer(server); removing = null }) { Text(stringResource(R.string.streamx_servers_remove)) } },
            dismissButton = { TextButton(onClick = { removing = null }) { Text(stringResource(android.R.string.cancel)) } })
    }
    Column(Modifier.fillMaxSize().hazeSource(haze).verticalScroll(rememberScrollState()).navigationBarsPadding()) {
        Spacer(Modifier.height(topBarContentPadding()))
        Text("StreamX", style = MaterialTheme.typography.displayLarge,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 14.dp))
        SettingsGroup {
            SettingsRow(icon = Icons.Rounded.AccountCircle,
                title = stringResource(if (connected) R.string.streamx_listen_as else R.string.streamx_account),
                subtitle = session.name.takeIf { it.isNotBlank() }
                    ?: stringResource(if (connected) R.string.streamx_setup_shared_access else R.string.not_signed_in),
                onClick = onOpenAccount)
            RowDivider()
            SettingsRow(title = stringResource(if (connected) R.string.streamx_open_account else R.string.streamx_setup_submit_login),
                onClick = onOpenAccount)
            if (connected) {
                RowDivider()
                SettingsRow(title = stringResource(R.string.sign_out), onClick = onSignOut)
            }
        }
        SettingsGroup(header = stringResource(R.string.streamx_servers_settings)) {
            servers.forEachIndexed { index, server ->
                if (index > 0) RowDivider()
                var menu by remember(server.id) { mutableStateOf(false) }
                SettingsRow(icon = Icons.Rounded.Dns, title = server.name,
                    subtitle = server.endpoint,
                    onClick = { onSelectServer(server) }, trailing = {
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            if (server.endpoint == session.endpoint) Text(stringResource(R.string.streamx_servers_active),
                                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Box {
                                IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, contentDescription = "Server options") }
                                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                    DropdownMenuItem(text = { Text(stringResource(R.string.streamx_servers_edit)) }, onClick = { menu = false; editing = server })
                                    DropdownMenuItem(text = { Text(stringResource(R.string.streamx_servers_remove)) }, enabled = server.endpoint != session.endpoint,
                                        onClick = { menu = false; removing = server })
                                }
                            }
                        }
                    })
            }
            if (servers.isNotEmpty()) RowDivider()
            SettingsRow(icon = Icons.Rounded.Add, title = stringResource(R.string.streamx_servers_add), onClick = onAddServer)
        }
        Spacer(Modifier.height(24.dp))
    }
    editing?.let { server ->
        com.music.streamx.ui.components.ServerEditorHost(
            hazeState = haze, title = "Edit StreamX server", description = "Update this saved server. Test checks its catalogue connection.",
            fields = listOf(com.music.streamx.ui.components.FieldConfig(server.name, "Server name"), com.music.streamx.ui.components.FieldConfig(server.endpoint, "https://your-server")),
            canSubmit = { it[0].isNotBlank() && (it[1].startsWith("https://") || it[1].startsWith("http://")) }, testFailedRes = R.string.webdav_test_failed,
            onTest = { values -> com.music.streamx.data.streamx.StreamXClient.result { com.music.streamx.data.streamx.StreamXClient.validateEndpoint(values[1].trim()) } },
            onSave = { values ->
                val endpoint = values[1].trim().trimEnd('/')
                StreamXSession.rememberServer(server.id, endpoint, values[0])
                if (server.endpoint == session.endpoint && endpoint != server.endpoint) {
                    StreamXSession.connect(endpoint)
                    com.music.streamx.data.sources.SourceRegistry.configureStreamX(endpoint, values[0])
                    com.music.streamx.data.settings.AppSettings.setStreamXEndpoint(endpoint)
                }
                editing = null
            }, onDismiss = { editing = null })
    }

}
