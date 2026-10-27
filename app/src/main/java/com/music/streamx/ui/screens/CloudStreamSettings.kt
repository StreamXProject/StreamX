package com.music.streamx.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.rounded.Person
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.CheckCircle
import com.music.streamx.ui.components.PillTextField
import com.music.streamx.ui.components.lightweightLiquidGlass
import kotlinx.coroutines.delay
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import com.music.streamx.ui.components.BottomFadeScrim
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.music.streamx.data.cloudstream.CloudStream
import com.music.streamx.data.cloudstream.cloudStreamDatabase
import com.music.streamx.data.cloudstream.CloudStreamConfig
import com.music.streamx.ui.components.FrostedTopBar
import com.music.streamx.ui.components.topBarContentPadding
import kotlinx.coroutines.launch

@Composable
internal fun CloudStreamSettings(endpoint: String = CloudStream.PROFILE) {
    var config by remember(endpoint) { mutableStateOf(runCatching { CloudStream.read(endpoint) }.getOrDefault(CloudStreamConfig())) }
    val syncStatus by com.music.streamx.data.cloudstream.CloudLibrary.status.collectAsState()
    val scope = rememberCoroutineScope()
    var login by remember { mutableStateOf(false) }
    var terminal by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    SettingsGroup(header = "CloudStream", footer = "For owners and trusted friends. Browse your tracks from MongoDB and stream original files from Telegram on this device. Favourites and playlists save locally and sync to your CloudStream library.") {
        SettingsRow(icon = Icons.Rounded.Cloud, title = "Direct device streaming",
            subtitle = if (config.enabled) "Using Telegram + MongoDB" else "Not connected",
            trailing = { com.music.streamx.ui.components.SkinSwitch(checked = config.enabled, enabled = endpoint.isNotBlank(), onCheckedChange = {
                if (it) editing = true else runCatching { CloudStream.save(endpoint, config.copy(enabled = false)) }
                    .onSuccess { config = config.copy(enabled = false); error = null }
                    .onFailure { error = "Could not update encrypted CloudStream settings" }
            }) })
        RowDivider()
        SettingsRow(icon = Icons.Rounded.Person, title = config.accountName.ifBlank { "Sign in to your account" },
            subtitle = if (config.accountName.isBlank()) "Use your existing StreamX username and password" else "Manage account", enabled = config.mongoUri.isNotBlank(), onClick = { login = true })
        RowDivider()
        SettingsRow(title = "Connection details", enabled = endpoint.isNotBlank(), onClick = { editing = true })
        RowDivider()
        SettingsRow(title = "Connection activity", subtitle = "Connections, streaming and errors", onClick = { terminal = true })
        RowDivider()
        SettingsRow(title = "Sync library", subtitle = syncStatus, onClick = { scope.launch { com.music.streamx.data.cloudstream.CloudLibrary.sync() } })
    }
    error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(20.dp)) }
    if (login) CloudAccountDialog(config, onDismiss = { login = false }, onSaved = {
        config = it; CloudStream.save(CloudStream.PROFILE,it); login = false
    })
    if (terminal) Dialog(onDismissRequest = { terminal = false }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            val activityScroll = rememberScrollState()
            Box(Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxSize().verticalScroll(activityScroll).navigationBarsPadding()) {
                    Spacer(Modifier.height(topBarContentPadding()))
                    Text("Connection activity", style = MaterialTheme.typography.displayLarge, modifier = Modifier.padding(start = 20.dp,end = 20.dp,top = 8.dp,bottom = 14.dp))
                    CloudStreamTerminal()
                }
                BottomFadeScrim(modifier = Modifier.rotate(180f))
                FrostedTopBar(title = "Connection activity", scrolled = activityScroll.value > 0, onBack = { terminal = false }, transparentBackdrop = true, artworkPageChrome = true)
            }
        }
    }

    if (editing) CloudStreamConnectionDialog(config, onDismiss = { editing = false }, onSaved = {
        val saved = it.copy(profileId = it.profileId.ifBlank { java.util.UUID.randomUUID().toString() })
        CloudStream.save(CloudStream.PROFILE, saved); config = saved; editing = false
        com.music.streamx.data.sources.SourceRegistry.configureCloudStream()
    })
}

@Composable
internal fun CloudStreamConnectionDialog(initial: CloudStreamConfig, onDismiss: () -> Unit, onSaved: (CloudStreamConfig) -> Unit) {
    // Secrets stay out of rememberSaveable, logs, navigation and clipboard.
    var token by remember { mutableStateOf(initial.botToken) }
    var apiId by remember { mutableStateOf(initial.apiId) }
    var apiHash by remember { mutableStateOf(initial.apiHash) }
    var uri by remember { mutableStateOf(initial.mongoUri) }
    var database by remember { mutableStateOf(initial.database) }
    var collection by remember { mutableStateOf(initial.collection) }
    var libraries by remember { mutableStateOf<List<String>>(emptyList()) }
    var discovering by remember { mutableStateOf(false) }
    var advanced by remember { mutableStateOf(false) }
    var profileId by remember { mutableStateOf(initial.profileId.ifBlank { java.util.UUID.randomUUID().toString() }) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val connectionScroll = rememberScrollState()
    fun connect() {
        if (busy) return
        busy = true; error = null
        scope.launch {
            try {
                val value = initial.copy(botToken=token.trim(), apiId=apiId.trim(), apiHash=apiHash.trim(), mongoUri=uri.trim(), database=cloudStreamDatabase(uri, database), collection=collection.trim(), profileId=profileId.trim())
                CloudStream.verify(value)
                onSaved(value.copy(enabled = true))
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (failure: Exception) { error = failure.message ?: "Could not connect CloudStream" }
            finally { busy = false }
        }
    }
    Dialog(onDismissRequest = { if (!busy) onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Box(Modifier.fillMaxSize().imePadding()) {
                Column(Modifier.fillMaxSize().verticalScroll(connectionScroll).navigationBarsPadding().padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    Spacer(Modifier.height(topBarContentPadding()))
                    Text("Connection details", style = MaterialTheme.typography.displayLarge)
                    Text("Connect your database to find its saved bot settings and track libraries.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    CloudField("MongoDB URI", uri, { uri = it }, secret = true, enabled = !busy && !discovering)
                    CloudField("Database name (optional)", database, { database = it; libraries = emptyList(); collection = "" }, enabled = !busy && !discovering)
                    Text("Leave blank to use the database in the URI, or Stream by default. Database names are case-sensitive; use the same database as your bot.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = {
                        if (!discovering) scope.launch {
                            discovering = true; error = null
                            try {
                                val selectedDatabase = cloudStreamDatabase(uri, database)
                                val setup = CloudStream.engine("bootstrap", initial.copy(mongoUri=uri.trim(),database=selectedDatabase,collection=""))
                                database = selectedDatabase
                                token = setup.optString("bot_token").ifBlank { token }
                                apiId = setup.optString("api_id").ifBlank { apiId }
                                apiHash = setup.optString("api_hash").ifBlank { apiHash }
                                val found = setup.optJSONArray("collections")
                                libraries = if (found==null) emptyList() else (0 until found.length()).map(found::getString)
                                if (collection !in libraries) collection = libraries.singleOrNull().orEmpty()
                                val missing = buildList {
                                    if (token.isBlank()) add("BOT_TOKEN")
                                    if ((apiId.toIntOrNull() ?: 0) <= 0) add("API_ID")
                                    if (apiHash.isBlank()) add("API_HASH")
                                }
                                error = when {
                                    setup.has("settings_found") && !setup.optBoolean("settings_found") ->
                                        "MongoDB is reachable, but botsettings/bot_config was not found in this database. Check the database name above, including its capitalization, or enter Telegram details in Advanced settings."
                                    missing.isNotEmpty() ->
                                        "MongoDB is reachable, but botsettings/bot_config did not provide: ${missing.joinToString()}. Check the database name or enter these fields in Advanced settings."
                                    libraries.isEmpty() -> "MongoDB is reachable, but no audio track collections were found. Check the database name."
                                    else -> null
                                }
                                if (missing.isNotEmpty() && libraries.isNotEmpty()) advanced = true
                            } catch(cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                            catch(failure: Exception) { error=failure.message ?: "Could not read database settings" }
                            finally { discovering=false }
                        }
                    }, enabled = !busy && !discovering && uri.isNotBlank(), modifier=Modifier.fillMaxWidth().height(52.dp)) {
                        Text(if(discovering) "Reading database settings…" else "Load from database")
                    }
                    SettingsGroup(header = "Track library", footer = "Choose an existing track collection. Audio files and track documents stay unchanged.") {
                        SettingsRow(title = collection.ifBlank { "Find track libraries" }, subtitle = if (discovering) "Finding libraries…" else "Discover collections in this database", onClick = {
                            if (!discovering && !busy) scope.launch {
                                discovering = true; error = null
                                try {
                                    libraries = CloudStream.discover(CloudStreamConfig(token.trim(),apiId.trim(),apiHash.trim(),uri.trim(),cloudStreamDatabase(uri, database),collection))
                                    if (collection !in libraries) collection = libraries.singleOrNull().orEmpty()
                                    if (libraries.isEmpty()) error = "No collections containing audio tracks were found"
                                } catch (failure: Exception) { error = failure.message ?: "Could not discover libraries" }
                                finally { discovering = false }
                            }
                        })
                        libraries.forEach { name ->
                            RowDivider()
                            SettingsRow(title = name, subtitle = if (name == collection) "Selected" else null, onClick = { collection = name })
                        }
                    }
                    TextButton(onClick = { advanced = !advanced }) { Text(if (advanced) "Hide advanced settings" else "Advanced settings") }
                    if (advanced) {
                        CloudField("Telegram bot token",token,{ token=it },secret=true,enabled=!busy)
                        CloudField("Telegram API ID",apiId,{ apiId=it },numeric=true,enabled=!busy)
                        CloudField("Telegram API hash",apiHash,{ apiHash=it },secret=true,enabled=!busy)
                        CloudField("Track collection",collection,{ collection=it },enabled=!busy)
                        CloudField("Library profile ID",profileId,{ profileId=it },enabled=!busy)
                        Text("Use the same profile ID on your own devices to sync favourites and playlists. Library data uses streamx_app_library; the database user needs write access to that collection.", style = MaterialTheme.typography.bodySmall)
                    }
                    Text("The bot must have access to your source channel. Tracks need source_chat_id/source_message_id, cache_chat_id/cache_message_id, or source.chat_id/source.message_id. Get API ID and hash at my.telegram.org.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    Button(onClick = ::connect, enabled = !busy && !discovering && uri.isNotBlank() && collection.isNotBlank() &&
                            token.isNotBlank() && (apiId.toIntOrNull() ?: 0) > 0 && apiHash.isNotBlank(), modifier = Modifier.fillMaxWidth().height(56.dp)) {
                        Text(if (busy) "Checking connection…" else "Connect and enable")
                    }
                    Spacer(Modifier.height(24.dp))
                }
                BottomFadeScrim(modifier = Modifier.rotate(180f))
                FrostedTopBar(title = "Connection details", scrolled = connectionScroll.value > 0, onBack = { if (!busy) onDismiss() }, transparentBackdrop = true, artworkPageChrome = true)
            }
        }
    }
}

@Composable
private fun CloudStreamTerminal(modifier: Modifier = Modifier) {
    var output by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { while (true) { output = CloudStream.logs(); delay(1500) } }
    Column(modifier) {
        val lines = output.lines().filter { it.isNotBlank() }.asReversed()
        SettingsGroup(header = "Recent activity", footer = "Updates automatically. Credentials are excluded from activity logs.") {
            if (lines.isEmpty()) SettingsRow(icon = Icons.Rounded.Cloud, title = "Ready when you are", subtitle = "Connection and streaming activity will appear here")
            lines.forEachIndexed { index, line ->
                if (index > 0) RowDivider()
                val parts = line.split("  ", limit = 2)
                val message = parts.getOrElse(1) { line }
                SettingsRow(icon = if (message.contains("ERROR")) Icons.Rounded.Refresh else Icons.Rounded.CheckCircle,
                    title = message, subtitle = parts.firstOrNull().takeIf { parts.size > 1 })
            }
        }
    }
}

@Composable
internal fun CloudStreamSettingsDialog(onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            val settingsScroll = rememberScrollState()
            Box {
                Column(Modifier.fillMaxSize().verticalScroll(settingsScroll).navigationBarsPadding()) {
                    Spacer(Modifier.height(topBarContentPadding()))
                    Text("CloudStream", style = MaterialTheme.typography.displayLarge, modifier = Modifier.padding(20.dp))
                    CloudStreamSettings()
                }
                BottomFadeScrim(modifier = Modifier.rotate(180f))
                FrostedTopBar(title = "CloudStream", scrolled = settingsScroll.value > 0, onBack = onDismiss, transparentBackdrop = true, artworkPageChrome = true)
            }
        }
    }
}

@Composable
private fun CloudField(label: String,value: String,onChange:(String)->Unit,secret:Boolean=false,numeric:Boolean=false,enabled:Boolean=true) {
    Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text(label,style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.onSurfaceVariant)
        PillTextField(value=value,onValueChange=onChange,placeholder=label,isPassword=secret,enabled=enabled,
            keyboardOptions=KeyboardOptions(keyboardType=if(numeric) KeyboardType.Number else if(secret) KeyboardType.Password else KeyboardType.Text))
    }
}

@Composable
private fun CloudAccountDialog(initial:CloudStreamConfig,onDismiss:()->Unit,onSaved:(CloudStreamConfig)->Unit) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope=rememberCoroutineScope()
    Dialog(onDismissRequest={if(!busy)onDismiss()},properties=DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=false)) {
        Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.background) {
            val scroll=rememberScrollState()
            Box {
                Column(Modifier.fillMaxSize().verticalScroll(scroll).imePadding().navigationBarsPadding().padding(horizontal=20.dp),verticalArrangement=Arrangement.spacedBy(20.dp)) {
                    Spacer(Modifier.height(topBarContentPadding()))
                    Text("Sign in",style=MaterialTheme.typography.displayLarge)
                    Text("Use your existing StreamX account username and password.",color=MaterialTheme.colorScheme.onSurfaceVariant)
                    CloudField("Username",username,{username=it},enabled=!busy)
                    CloudField("Password",password,{password=it},secret=true,enabled=!busy)
                    error?.let {Text(it,color=MaterialTheme.colorScheme.error)}
                    Button(onClick={
                        busy=true;error=null
                        scope.launch {
                            try {
                                val account=CloudStream.engine("login",initial,org.json.JSONObject().put("username",username).put("password",password))
                                password=""
                                onSaved(initial.copy(profileId=account.getString("profile_id"),accountName=account.optString("name"),accountPhoto=account.optString("photo")))
                            }catch(cancelled:kotlinx.coroutines.CancellationException){throw cancelled}
                            catch(failure:Exception){error=failure.message ?: "Could not sign in"}
                            finally{busy=false}
                        }
                    },enabled=!busy&&username.isNotBlank()&&password.isNotBlank(),modifier=Modifier.fillMaxWidth().height(56.dp)) {Text(if(busy) "Signing in…" else "Sign in")}
                }
                BottomFadeScrim(modifier = Modifier.rotate(180f))
                FrostedTopBar(title="Account",scrolled=scroll.value>0,onBack={if(!busy)onDismiss()},transparentBackdrop=true,artworkPageChrome=true)
            }
        }
    }
}
