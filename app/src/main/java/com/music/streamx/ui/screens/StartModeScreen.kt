package com.music.streamx.ui.screens

import com.music.streamx.ui.skins.SkinIcon as Icon
import androidx.activity.compose.BackHandler
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import com.music.streamx.data.streamx.StreamXTelegramAuth
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawWithContent
import com.music.streamx.data.cloudstream.CloudStream
import com.music.streamx.data.cloudstream.CloudStreamConfig
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import com.music.streamx.ui.skins.skinAndroidPainterResource as painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import com.music.streamx.R
import com.music.streamx.ui.icons.StreamXIcons
import com.music.streamx.ui.components.ConfirmationAlert
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.music.streamx.data.extensions.ApkExtensions
import com.music.streamx.data.settings.AppSettings
import com.music.streamx.data.sources.SourceConfig
import com.music.streamx.data.sources.SourceKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import com.music.streamx.ui.components.lightweightLiquidGlass
import com.music.streamx.data.streamx.StreamXClient
import com.music.streamx.data.streamx.StreamXSession
import com.music.streamx.data.sources.SourceRegistry
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

internal enum class StreamXSetupPage { START, ENDPOINT, AUTH, SERVER_PASSWORD, USER_LOGIN, SIGN_UP }

/** StreamGO setup. Passwords are sent only to the chosen server and never persisted. */
@Composable
internal fun StartModeScreen(
    initialEndpoint: String,
    onEndpointAdded: (String) -> Unit,
    onAuthenticated: () -> Unit,
    reduceAnimation: Boolean,
    modifier: Modifier = Modifier,
    onDismiss: (() -> Unit)? = null,
    initialPage: StreamXSetupPage = StreamXSetupPage.START,
    onServersRequested: (() -> Unit)? = null,
    initialServerId: String? = null,
    initialServerName: String = "",
) {
    var page by rememberSaveable { mutableStateOf(initialPage) }
    var endpoint by rememberSaveable { mutableStateOf(initialEndpoint) }
    val servers by StreamXSession.servers.collectAsStateWithLifecycle()
    val session by StreamXSession.state.collectAsStateWithLifecycle()
    val installedApks by ApkExtensions.installed.collectAsStateWithLifecycle()
    val cloudVersion by ApkExtensions.cloudStreamVersion.collectAsStateWithLifecycle()
    val installedExtensions = installedApks.filter { it.sourceAuthority != null }
    var serverName by rememberSaveable { mutableStateOf(initialServerName) }
    var editingServerId by rememberSaveable { mutableStateOf(initialServerId) }
    var pendingEndpoint by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingName by rememberSaveable { mutableStateOf("") }
    var endpointError by rememberSaveable { mutableStateOf(false) }
    var installStatus by remember { mutableStateOf<String?>(null) }
    var pendingApk by remember { mutableStateOf<Uri?>(null) }
    // Passwords deliberately use remember, never rememberSaveable or preferences.
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var requestError by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var inviteCode by remember { mutableStateOf("") }
    var requestJob by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val alertHazeState = remember { HazeState() }
    val telegramState by StreamXTelegramAuth.state.collectAsStateWithLifecycle()
    var startSource by rememberSaveable { mutableStateOf(StartYouTubeSource) }
    var cloudSetup by remember { mutableStateOf<CloudStreamConfig?>(null) }
    val cloudSelected = startSource == CloudStream.PACKAGE && cloudVersion != null
    // A plugin that was selected and then uninstalled falls back to YouTube Music.
    val startExtension = installedExtensions.firstOrNull { it.packageName == startSource }
    fun continueWithSource() {
        // The pick becomes the Home feed and joins playback, so it works at once;
        // Sources can later split the two (another feed, a different play order).
        if (cloudSelected) {
            val config = try { CloudStream.read(CloudStream.PROFILE) }
                catch (_: Exception) {
                    installStatus = "Could not read encrypted CloudStream settings"
                    return
                }
            if (!config.enabled) {
                cloudSetup = config
                return
            }
            SourceRegistry.configureCloudStream()
            SourceRegistry.useFeedCatalogue(SourceKind.CLOUDSTREAM)
        } else if (startExtension != null) {
            SourceRegistry.enableApk(startExtension)
            SourceRegistry.configs.value.firstOrNull { it.extensionPackage == startExtension.packageName }
                ?.let { SourceRegistry.useFeedExtension(it.id) }
        } else {
            SourceRegistry.configs.value.firstOrNull { it.kind == SourceKind.YOUTUBE }?.let { SourceRegistry.setEnabled(it.id, true) }
            SourceRegistry.useFeedCatalogue(SourceKind.YOUTUBE)
        }
        AppSettings.finishStreamXSetupPreview()
        onAuthenticated()
    }

    fun install(uri: Uri) {
        try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, "application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            )
        } catch (e: Exception) {
            installStatus = e.message ?: "Could not open the APK installer"
        }
    }
    val allowInstallation = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        pendingApk?.let { uri ->
            if (context.packageManager.canRequestPackageInstalls()) install(uri)
            else installStatus = "Allow StreamX to install APKs, or install the extension using your file manager."
        }
        pendingApk = null
    }
    fun requestInstall(uri: Uri) {
        if (context.packageManager.canRequestPackageInstalls()) install(uri)
        else {
            pendingApk = uri
            try {
                allowInstallation.launch(
                    Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:${context.packageName}")
                    )
                )
            } catch (e: Exception) {
                pendingApk = null
                installStatus = e.message ?: "Install the APK using your file manager."
            }
        }
    }
    val chooseApk = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) requestInstall(uri)
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        scope.launch { ApkExtensions.refresh() }
    }

    LaunchedEffect(telegramState.completedScope) {
        if (telegramState.completedScope != null && telegramState.completedScope == StreamXSession.state.value.scope) {
            StreamXTelegramAuth.cancel()
            onAuthenticated()
        }
    }
    val keyboard = LocalSoftwareKeyboardController.current
    val keyboardVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0

    fun navigate(next: StreamXSetupPage) {
        StreamXTelegramAuth.cancel()
        keyboard?.hide()
        password = ""
        confirmation = ""
        requestJob?.cancel()
        requestJob = null
        busy = false
        requestError = null
        page = next
    }

    fun back() {
        if (pendingEndpoint != null) {
            pendingEndpoint = null
            return
        }
        when (page) {
            StreamXSetupPage.START -> onDismiss?.invoke() ?: (context as? Activity)?.moveTaskToBack(true)
            StreamXSetupPage.ENDPOINT -> if (initialPage == StreamXSetupPage.START) navigate(StreamXSetupPage.START) else if (onDismiss != null) onDismiss.invoke() else navigate(StreamXSetupPage.START)
            StreamXSetupPage.AUTH -> navigate(StreamXSetupPage.ENDPOINT)
            else -> navigate(StreamXSetupPage.AUTH)
        }
    }

    fun connectEndpoint(normalized: String, name: String) {
        keyboard?.hide()
        requestError = null
        busy = true
        requestJob = scope.launch {
            StreamXClient.result { StreamXClient.validateEndpoint(normalized) }.fold(
                onSuccess = {
                    endpoint = normalized
                    serverName = name
                    username = ""
                    inviteCode = ""
                    StreamXTelegramAuth.cancel()
                    // Rotate scope even for the same server: account switching must log out.
                    if (StreamXSession.state.value.token.isNotBlank()) StreamXSession.signOut()
                    StreamXSession.connect(normalized)
                    SourceRegistry.configureStreamX(normalized, name)
                    editingServerId?.let { id ->
                        StreamXSession.servers.value.firstOrNull { it.id == id && it.endpoint != normalized }?.let {
                            StreamXSession.removeServer(it.id)
                        }
                    }
                    editingServerId = null
                    onEndpointAdded(normalized)
                    requestJob = null
                    navigate(StreamXSetupPage.AUTH)
                },
                onFailure = { requestError = it.message ?: "Could not reach this server"; busy = false },
            )
        }
    }

    fun requestConnection(normalized: String, name: String) {
        if (busy || pendingEndpoint != null) return
        if (session.token.isNotBlank()) {
            pendingName = name
            pendingEndpoint = normalized
            keyboard?.hide()
        } else connectEndpoint(normalized, name)
    }

    fun addEndpoint() {
        if (busy || pendingEndpoint != null) return
        val normalized = normalizeStreamXEndpoint(endpoint)
        endpointError = normalized == null
        val editing = servers.firstOrNull { it.id == editingServerId }
        if (normalized != null && editing != null && editing.endpoint == normalized) {
            StreamXSession.rememberServer(editing.id, normalized, serverName)
            editingServerId = null
            onServersRequested?.invoke() ?: navigate(StreamXSetupPage.AUTH)
            return
        }
        if (normalized != null) requestConnection(normalized, serverName)
    }

    fun authenticate() {
        if (busy || telegramState.loading || password.isBlank() ||
            (page != StreamXSetupPage.SERVER_PASSWORD && username.isBlank()) ||
            (page == StreamXSetupPage.SIGN_UP && password != confirmation)
        ) return
        keyboard?.hide()
        busy = true
        requestError = null
        val method = when (page) {
            StreamXSetupPage.SERVER_PASSWORD -> "server"
            StreamXSetupPage.SIGN_UP -> "signup"
            else -> "login"
        }
        requestJob = scope.launch {
            StreamXClient.result {
                StreamXClient.current().authenticate(method, username, password, inviteCode)
            }.fold(
                onSuccess = { password = ""; confirmation = ""; busy = false; onAuthenticated() },
                onFailure = { requestError = it.message ?: "Could not sign in"; busy = false },
            )
        }
    }

    cloudSetup?.let { config ->
        CloudStreamConnectionDialog(
            initial = config,
            onDismiss = { cloudSetup = null },
            onSaved = { connected ->
                CloudStream.save(CloudStream.PROFILE, connected)
                SourceRegistry.configureCloudStream()
                SourceRegistry.useFeedCatalogue(SourceKind.CLOUDSTREAM)
                cloudSetup = null
                AppSettings.finishStreamXSetupPreview()
                onAuthenticated()
            },
        )
    }

    BackHandler { back() }

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(
            modifier = Modifier.fillMaxSize()
                .hazeSource(alertHazeState)
                .background(MaterialTheme.colorScheme.background)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                .imePadding()
                .navigationBarsPadding(),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier.widthIn(max = 520.dp).fillMaxWidth().fillMaxHeight()
                    .padding(horizontal = 20.dp),
            ) {
                // The start page is the root of setup: no bar holding a back arrow
                // over its content (system back still leaves), so it scrolls free.
                if (page != StreamXSetupPage.START) Row(
                    modifier = Modifier.fillMaxWidth().height(64.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    IconButton(
                        onClick = { back() },
                        modifier = Modifier,
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.streamx_setup_back))
                    }
                    if (page != StreamXSetupPage.ENDPOINT && page != StreamXSetupPage.START) {
                        Icon(
                            painterResource(R.drawable.ic_logo), contentDescription = null,
                            modifier = Modifier.size(32.dp), tint = MaterialTheme.colorScheme.onBackground,
                        )
                    } else {
                        Spacer(Modifier.size(32.dp))
                    }
                    Spacer(Modifier.size(48.dp))
                }

                val background = MaterialTheme.colorScheme.background
                AnimatedContent(
                    targetState = page,
                    modifier = Modifier.weight(1f).fillMaxWidth().drawWithContent {
                        drawContent()
                        if (page == StreamXSetupPage.START) {
                            val fadeTop = (size.height - 40.dp.toPx()).coerceAtLeast(0f)
                            drawRect(
                                brush = Brush.verticalGradient(
                                    colors = listOf(background.copy(alpha = 0f), background),
                                    startY = fadeTop,
                                    endY = size.height,
                                ),
                                topLeft = Offset(0f, fadeTop),
                            )
                        }
                    },
                    transitionSpec = {
                        val duration = if (reduceAnimation) 0 else 180
                        fadeIn(tween(duration)) togetherWith fadeOut(tween(duration))
                    },
                    label = "streamx_setup_page",
                ) { visiblePage ->
                    Column(
                        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                            .padding(top = if (keyboardVisible) 12.dp else if (visiblePage == StreamXSetupPage.START) 40.dp else 28.dp, bottom = if (keyboardVisible) 8.dp else 24.dp),
                        verticalArrangement = Arrangement.spacedBy(if (visiblePage == StreamXSetupPage.ENDPOINT) 24.dp else 18.dp),
                    ) {
                        val title = when (visiblePage) {
                            StreamXSetupPage.START -> R.string.start_mode_title
                            StreamXSetupPage.ENDPOINT -> R.string.streamx_setup_welcome
                            StreamXSetupPage.AUTH -> R.string.streamx_setup_auth_title
                            StreamXSetupPage.SERVER_PASSWORD -> R.string.streamx_setup_unlock_title
                            StreamXSetupPage.USER_LOGIN -> R.string.streamx_setup_login_title
                            StreamXSetupPage.SIGN_UP -> R.string.streamx_setup_signup_title
                        }
                        val subtitle = when (visiblePage) {
                            StreamXSetupPage.START -> R.string.start_mode_subtitle
                            StreamXSetupPage.ENDPOINT -> R.string.streamx_setup_endpoint_description
                            StreamXSetupPage.AUTH -> R.string.streamx_setup_auth_description
                            StreamXSetupPage.SERVER_PASSWORD -> R.string.streamx_setup_server_description
                            StreamXSetupPage.USER_LOGIN -> R.string.streamx_setup_login_description
                            StreamXSetupPage.SIGN_UP -> R.string.streamx_setup_signup_description
                        }
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.Start,
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text(
                                stringResource(title),
                                style = MaterialTheme.typography.headlineLarge.copy(
                                    fontSize = if (visiblePage == StreamXSetupPage.START) 38.sp else 32.sp,
                                    lineHeight = if (visiblePage == StreamXSetupPage.START) 42.sp else 36.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = (-0.8).sp,
                                ),
                                textAlign = TextAlign.Start,
                            )
                            Text(
                                stringResource(subtitle), style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Start,
                            )
                        }

                        if (visiblePage == StreamXSetupPage.START) {
                            val serverSummary = if (servers.isNotEmpty()) {
                                val first = servers.first().name.ifBlank { "StreamX" }
                                if (servers.size > 1) stringResource(R.string.start_mode_servers_summary, first, servers.size)
                                else stringResource(R.string.start_mode_server_summary, first)
                            } else stringResource(R.string.start_mode_server_connect)
                            StartChooser(
                                installed = installedApks,
                                selectedSource = startSource,
                                onSelectSource = { startSource = it },
                                onInstallPlugin = {
                                    chooseApk.launch(arrayOf("application/vnd.android.package-archive", "application/octet-stream"))
                                },
                                onInstallApk = ::requestInstall,
                                installStatus = installStatus,
                                serverSummary = serverSummary,
                                onServer = { navigate(StreamXSetupPage.ENDPOINT) },
                            )
                        } else if (visiblePage == StreamXSetupPage.ENDPOINT) {
                            SetupForm {
                                SetupField(
                                    label = stringResource(R.string.streamx_servers_name),
                                    value = serverName, onValueChange = { serverName = it },
                                    placeholder = stringResource(R.string.streamx_servers_name_hint),
                                    onDone = null,
                                    enabled = !busy && pendingEndpoint == null,
                                )
                                SetupField(
                                    label = stringResource(R.string.streamx_setup_endpoint),
                                    value = endpoint,
                                    onValueChange = { endpoint = it; endpointError = false },
                                    placeholder = "https://music.example.com",
                                    keyboardType = KeyboardType.Uri,
                                    onDone = { addEndpoint() }, enabled = !busy && pendingEndpoint == null,
                                )
                                if (endpointError) Text(
                                    stringResource(R.string.streamx_setup_invalid_endpoint),
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                requestError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                                if (!keyboardVisible) SetupPrimaryButton(
                                    label = stringResource(if (busy) R.string.streamx_setup_connecting else if (editingServerId != null) R.string.streamx_servers_save else R.string.streamx_setup_add_server),
                                    enabled = endpoint.isNotBlank() && !busy && pendingEndpoint == null, onClick = { addEndpoint() },
                                )
                            }
                        } else {
                            ServerAddressCard(
                                endpoint = endpoint,
                                expanded = visiblePage == StreamXSetupPage.AUTH,
                                onEdit = { navigate(StreamXSetupPage.ENDPOINT) },
                            )

                            if (visiblePage == StreamXSetupPage.AUTH) {
                                AuthChoice(
                                    Icons.Rounded.Lock, stringResource(R.string.streamx_setup_server_password),
                                    stringResource(R.string.streamx_setup_server_description),
                                ) { navigate(StreamXSetupPage.SERVER_PASSWORD) }
                                AuthChoice(
                                    Icons.Rounded.Person, stringResource(R.string.streamx_setup_user_login),
                                    stringResource(R.string.streamx_setup_login_description),
                                ) { navigate(StreamXSetupPage.USER_LOGIN) }
                            } else {
                                SetupForm {
                                    if (visiblePage != StreamXSetupPage.SERVER_PASSWORD) {
                                        SegmentedControl(
                                            options = listOf(stringResource(R.string.streamx_setup_user_login), stringResource(R.string.streamx_setup_signup)),
                                            selectedIndex = if (visiblePage == StreamXSetupPage.SIGN_UP) 1 else 0,
                                            onSelect = { navigate(if (it == 0) StreamXSetupPage.USER_LOGIN else StreamXSetupPage.SIGN_UP) },
                                        )
                                        SetupField(
                                            stringResource(R.string.username), username, { username = it },
                                            stringResource(R.string.username), onDone = null, enabled = !busy,
                                        )
                                    }
                                    SetupField(
                                        label = stringResource(if (visiblePage == StreamXSetupPage.SERVER_PASSWORD) R.string.streamx_setup_server_password else R.string.password),
                                        value = password, onValueChange = { password = it },
                                        placeholder = stringResource(R.string.password), isPassword = true, enabled = !busy,
                                        onDone = if (visiblePage == StreamXSetupPage.SIGN_UP) null else ({ authenticate() }),
                                    )
                                    if (visiblePage == StreamXSetupPage.SIGN_UP) SetupField(
                                        stringResource(R.string.streamx_setup_confirm_password), confirmation,
                                        { confirmation = it }, stringResource(R.string.streamx_setup_confirm_password),
                                        isPassword = true, onDone = { authenticate() }, enabled = !busy,
                                    )
                                    if (visiblePage == StreamXSetupPage.SIGN_UP) SetupField(
                                        label = stringResource(R.string.streamx_setup_invite_code),
                                        value = inviteCode, onValueChange = { inviteCode = it },
                                        placeholder = stringResource(R.string.streamx_setup_optional),
                                        onDone = { authenticate() }, enabled = !busy,
                                    )
                                    SetupPrimaryButton(
                                        label = stringResource(if (busy) R.string.streamx_setup_signing_in else if (visiblePage == StreamXSetupPage.SIGN_UP) R.string.streamx_setup_submit_signup else R.string.streamx_setup_submit_login),
                                        enabled = !busy && password.isNotBlank() &&
                                            (visiblePage == StreamXSetupPage.SERVER_PASSWORD || username.isNotBlank()) &&
                                            (visiblePage != StreamXSetupPage.SIGN_UP || password == confirmation),
                                        onClick = { authenticate() },
                                    )
                                }
                                requestError?.let {
                                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                        if (visiblePage != StreamXSetupPage.ENDPOINT) {
                            Spacer(Modifier.height(16.dp))
                            SetupPrimaryButton(
                                label = stringResource(if (telegramState.loading) R.string.streamx_setup_telegram_connecting else R.string.streamx_setup_telegram),
                                enabled = !busy && !telegramState.loading,
                                secondary = true,
                                onClick = { keyboard?.hide(); StreamXTelegramAuth.start(context, inviteCode) },
                            )
                            telegramState.error?.let { message ->
                                Text(message, modifier = Modifier.padding(top = 8.dp),
                                    color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }

                // The start page's one action, pinned so it never scrolls away.
                if (page == StreamXSetupPage.START) {
                    Box(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 16.dp)) {
                        SetupPrimaryButton(
                            label = stringResource(
                                R.string.start_mode_continue_with,
                                if (cloudSelected) "CloudStream" else startExtension?.name ?: stringResource(R.string.start_mode_youtube_music),
                            ),
                            enabled = !busy && !telegramState.loading,
                            onClick = { continueWithSource() },
                        )
                    }
                }
                // Keep the endpoint action visible above the IME, outside the scroll clip.
                if (page == StreamXSetupPage.ENDPOINT && keyboardVisible) {
                    Box(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        SetupPrimaryButton(
                            label = stringResource(if (busy) R.string.streamx_setup_connecting else if (editingServerId != null) R.string.streamx_servers_save else R.string.streamx_setup_add_server),
                            enabled = endpoint.isNotBlank() && !busy && pendingEndpoint == null, onClick = { addEndpoint() },
                        )
                    }
                }
                AnimatedVisibility(
                    visible = !keyboardVisible && page != StreamXSetupPage.START,
                    enter = fadeIn(tween(if (reduceAnimation) 0 else 180)) +
                        expandVertically(tween(if (reduceAnimation) 0 else 180), expandFrom = Alignment.Top),
                    exit = fadeOut(tween(if (reduceAnimation) 0 else 140)) +
                        shrinkVertically(tween(if (reduceAnimation) 0 else 180), shrinkTowards = Alignment.Top),
                ) {
                    Column(Modifier.fillMaxWidth()) {
                        if (page == StreamXSetupPage.AUTH) {
                            TextButton(
                                onClick = { navigate(StreamXSetupPage.SIGN_UP) },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(stringResource(R.string.streamx_setup_create_account), style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                        Text(
                            stringResource(R.string.streamx_setup_footer),
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 20.dp),
                            textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        // Siblings of the page capture: the alert samples the page, never itself.
        pendingEndpoint?.let { target ->
            ConfirmationAlert(
                hazeState = alertHazeState,
                title = stringResource(R.string.streamx_servers_switch_title),
                description = stringResource(R.string.streamx_servers_switch_description),
                confirmLabel = stringResource(R.string.streamx_servers_continue),
                onConfirm = {
                    val name = pendingName
                    pendingEndpoint = null
                    connectEndpoint(target, name)
                },
                onDismiss = { pendingEndpoint = null },
            )
        }

    }
}

@Composable
internal fun GroupedCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
            .clip(RoundedCornerShape(26.dp)),
        content = content,
    )
}

@Composable
internal fun StartOptionRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit = {
        Icon(
            imageVector = StreamXIcons.ChevronRight,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    },
    icon: @Composable () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(Color(0xFF222224), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            icon()
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle.isNotBlank()) Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        trailing()
    }
}

@Composable
internal fun GroupedDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 78.dp, end = 16.dp),
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
    )
}

@Composable
private fun SetupForm(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) { content() }
}

@Composable
private fun SetupIconBadge(icon: ImageVector) {
    Box(
        modifier = Modifier.size(44.dp).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(23.dp))
    }
}

@Composable
private fun ServerAddressCard(endpoint: String, expanded: Boolean, onEdit: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
            .clickable(onClickLabel = stringResource(R.string.streamx_setup_edit), onClick = onEdit)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (expanded) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                SetupIconBadge(Icons.Rounded.Dns)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.streamx_setup_server_url), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.streamx_setup_connect_description),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(StreamXIcons.ChevronRight, contentDescription = null,
                    modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth()
                .background(MaterialTheme.colorScheme.background, RoundedCornerShape(4.dp))
                .heightIn(min = 50.dp).padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(Icons.Rounded.Dns, contentDescription = null, modifier = Modifier.size(20.dp))
            Text(endpoint, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium)
            Box(Modifier.size(width = 1.dp, height = 22.dp)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.16f)))
            Text(stringResource(R.string.streamx_setup_edit), style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun AuthChoice(icon: ImageVector, title: String, description: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick).padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        SetupIconBadge(icon)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(StreamXIcons.ChevronRight, contentDescription = null, modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SetupPrimaryButton(label: String, enabled: Boolean, secondary: Boolean = false, onClick: () -> Unit) {
    Button(
        onClick = onClick, enabled = enabled, shape = CircleShape,
        modifier = Modifier.fillMaxWidth().height(56.dp),
        border = if (secondary) BorderStroke(1.dp, MaterialTheme.colorScheme.outline) else null,
        colors = if (secondary) ButtonDefaults.buttonColors(
            containerColor = Color.Transparent, contentColor = MaterialTheme.colorScheme.onBackground,
        ) else ButtonDefaults.buttonColors(),
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
    }
}

@Composable
internal fun SetupField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    isPassword: Boolean = false,
    keyboardType: KeyboardType = if (isPassword) KeyboardType.Password else KeyboardType.Text,
    onDone: (() -> Unit)?,
    enabled: Boolean = true,
) {
    var passwordVisible by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
        OutlinedTextField(
            value = value, onValueChange = onValueChange,
            placeholder = { Text(placeholder, style = MaterialTheme.typography.bodyLarge) },
            enabled = enabled, singleLine = true,
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = label },
            shape = RoundedCornerShape(4.dp),
            visualTransformation = if (isPassword && !passwordVisible) PasswordVisualTransformation() else VisualTransformation.None,
            trailingIcon = if (isPassword) ({
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(if (passwordVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                        contentDescription = stringResource(if (passwordVisible) R.string.streamx_hide_password else R.string.streamx_show_password))
                }
            }) else null,
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.55f),
                focusedBorderColor = MaterialTheme.colorScheme.onBackground,
                cursorColor = MaterialTheme.colorScheme.primary,
            ),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = if (onDone == null) ImeAction.Next else ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onDone?.invoke() }),
        )
    }
}

/** A base endpoint only; auth belongs in the later login step. No network request is made. */
internal fun normalizeStreamXEndpoint(value: String): String? {
    val input = value.trim()
    if (!input.startsWith("https://", ignoreCase = true) && !input.startsWith("http://", ignoreCase = true)) return null
    val url = input.toHttpUrlOrNull() ?: return null
    if (url.username.isNotEmpty() || url.password.isNotEmpty() || url.query != null || url.fragment != null) return null
    return url.toString().trimEnd('/')
}
