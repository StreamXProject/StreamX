package com.music.streamx.ui.screens

import com.music.streamx.ui.skins.SkinIcon as Icon
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.music.streamx.playback.audio.LosslessOutput
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.streamx.R
import com.music.streamx.data.extensions.ApkExtensions
import com.music.streamx.data.settings.AppSettings
import com.music.streamx.data.settings.permits
import com.music.streamx.data.smb.SmbRepository
import com.music.streamx.data.webdav.WebDavRepository
import com.music.streamx.data.settings.AudioQuality
import com.music.streamx.data.sources.AddonSource
import com.music.streamx.data.sources.SourceConfig
import com.music.streamx.data.sources.SourceHealth
import com.music.streamx.data.sources.SourceKind
import com.music.streamx.data.sources.addon.SourceFormats
import com.music.streamx.data.sources.SourceRegistry
import com.music.streamx.ui.components.AddonEditorAlert
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** Music source connections, installed plugins, and verified repository downloads. */
@Composable
fun SourcesScreen(
    contentPadding: PaddingValues,
    onEditSource: (SourceConfig) -> Unit,
    onOpenStreamXSetup: () -> Unit,
    onEditWebDav: () -> Unit,
    onEditSmb: () -> Unit,
    onConfirmJioSaavn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val header: @Composable () -> Unit = {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
            Text(stringResource(R.string.source),
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.onBackground)
            Text(when (tab) {
                    0 -> "Choose what fills Home and what plays it."
                    1 -> "What your registries offer, and which of it StreamX plays from."
                    else -> "Make StreamX yours. Apply a skin or customize each section."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp, bottom = 20.dp))
            SegmentedControl(
                options = listOf(stringResource(R.string.source), "Addons", stringResource(R.string.skins_title)),
                selectedIndex = tab, onSelect = { tab = it },
            )
            Spacer(Modifier.height(12.dp))
        }
    }
    val tabs = rememberSaveableStateHolder()
    Box(modifier.fillMaxSize()) {
        tabs.SaveableStateProvider(tab) {
            when (tab) {
                0 -> SourcesContent(contentPadding, onEditSource, onOpenStreamXSetup,
                    onEditWebDav, onEditSmb, onConfirmJioSaavn, header = header)
                1 -> AddonsContent(contentPadding, header = header)
                else -> SkinsPluginsScreen(onSources = { tab = 0 }, onDismiss = { tab = 0 },
                    embeddedSkins = true, header = header, embeddedPadding = contentPadding)
            }
        }
    }
}

@Composable
private fun SourcesContent(
    contentPadding: PaddingValues,
    /**
     * Asks the activity to open the editor for a source — or for a new one,
     * when handed a config the registry does not have yet.
     *
     * Raised rather than drawn here, and not as a style preference: this screen
     * sits inside the subtree carrying `hazeSource`, so a frosted card drawn
     * from within it is part of the very layer it samples and comes out with no
     * background at all. Hosting it at the activity — where every other alert
     * in this app already lives — is also what lets the scrim cover the tab bar
     * and the mini player.
     */
    onEditSource: (SourceConfig) -> Unit,
    onOpenStreamXSetup: () -> Unit,
    /**
     * Asks the activity to open the WebDAV editor — raised for the same
     * reason as [onEditSource]: the frosted alert cannot be drawn from inside
     * this subtree.
     */
    onEditWebDav: () -> Unit,
    /** As [onEditWebDav], for the SMB share editor. */
    onEditSmb: () -> Unit,
    /** Opens the full-window warning before JioSaavn is opted into. */
    onConfirmJioSaavn: () -> Unit,
    modifier: Modifier = Modifier,
    header: @Composable () -> Unit,
) {
    var cloudSettings by remember { mutableStateOf(false) }
    if (cloudSettings) CloudStreamSettingsDialog(onDismiss = { cloudSettings = false })
    val context = LocalContext.current
    val configs by SourceRegistry.configs.collectAsStateWithLifecycle()
    val installedExtensions by ApkExtensions.installed.collectAsStateWithLifecycle()
    LaunchedEffect(installedExtensions) {
        // Discovering a new plugin adds a disabled, configurable source; it does not opt into playback.
        installedExtensions.filter { it.sourceAuthority!=null }.forEach {extension->
            if(SourceRegistry.configs.value.none {it.kind==SourceKind.APK_PLUGIN && it.extensionPackage==extension.packageName})
                SourceRegistry.showApkSource(extension,true)
        }
    }
    val cloudVersion by ApkExtensions.cloudStreamVersion.collectAsStateWithLifecycle()
    val catalogue by SourceRegistry.catalogue.collectAsStateWithLifecycle()
    val selectedExtensionId by SourceRegistry.selectedExtensionId.collectAsStateWithLifecycle()
    val playbackOrder by SourceRegistry.playbackOrder.collectAsStateWithLifecycle()
    val wifiQuality by AppSettings.audioQualityWifi.collectAsStateWithLifecycle()
    val cellularQuality by AppSettings.audioQualityCellular.collectAsStateWithLifecycle()
    val metered by AppSettings.meteredConnection.collectAsStateWithLifecycle()
    val upgradeLengthSlackSeconds by AppSettings.upgradeLengthSlackSeconds.collectAsStateWithLifecycle()
    val webdavUrl by AppSettings.webdavUrl.collectAsStateWithLifecycle()
    val webdavUsername by AppSettings.webdavUsername.collectAsStateWithLifecycle()
    val webdavPassword by AppSettings.webdavPassword.collectAsStateWithLifecycle()
    val smbHost by AppSettings.smbHost.collectAsStateWithLifecycle()
    val smbShare by AppSettings.smbShare.collectAsStateWithLifecycle()
    val smbUsername by AppSettings.smbUsername.collectAsStateWithLifecycle()
    val smbPassword by AppSettings.smbPassword.collectAsStateWithLifecycle()

    /** Last known reachability per source, filled in as the probes come back. */
    val health = remember { mutableStateMapOf<String, SourceHealth>() }
    var probeRefresh by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { probeRefresh++ }
    val losslessOutput by LosslessOutput.state.collectAsStateWithLifecycle()

    // Probe enabled sources so the compact status reports reachability rather
    // than installation type. Re-probe after enabling or changing a connection.
    val probeKey = configs.joinToString {
        "${it.id}@${it.enabled}@${it.baseUrl}@${it.checkValidLossless}@${it.extensionCertificate}@${it.extensionAuthority}"
    } + "@${losslessOutput.capable}"
    LaunchedEffect(probeKey, probeRefresh) {
        configs.filter { it.enabled && it.isComplete && !it.awaitsLosslessOutput }.forEach { config ->
            val source = SourceRegistry.instance(config.id) ?: return@forEach
            health[config.id] = withContext(Dispatchers.IO) {
                runCatching { source.health() }
                    .getOrElse { SourceHealth.Unreachable(it.message ?: "Failed") }
            }
            // An addon names itself, so the row takes that name rather than a
            // hostname — and rather than a field asking the user to make one up.
            //
            // Written here, off the probe that has just fetched the manifest
            // anyway, because this is the one place that both talks to every
            // configured source and is allowed to change what is stored. It
            // settles after one pass: the next probe finds the label already
            // equal and writes nothing, so there is no loop between this and
            // the [configs] it is reading. A rename on the addon's side is
            // picked up the next time this screen is opened.
            if (source is AddonSource) {
                val named = withContext(Dispatchers.IO) { runCatching { source.manifestName() }.getOrNull() }
                if (named != null && named != config.label) {
                    SourceRegistry.update(config.copy(label = named))
                }
            }
        }
    }

    // The ceiling in force *right now*, which is the only one this screen can
    // speak for: the rows below are switches the user set once, and whether a
    // given source is reached also depends on which connection is up. Reading
    // it here rather than baking it into the switches is deliberate — the
    // preset write that used to do that is what made a mobile-data rung follow
    // the user onto Wi-Fi. See [AudioQuality.permits].
    val ceiling = if (metered == true) cellularQuality else wifiQuality

    /** Whether the ceiling in force right now would cap a lossless stream anyway. */
    val cappedByQuality = ceiling != AudioQuality.LOSSLESS
    // Asked of the kinds themselves rather than of the module specifically, so
    // a source added later answers this on its own terms instead of being
    // invisible to it.
    val anyLosslessSource = configs.any { it.enabled && it.isComplete && it.kind.canServeLossless }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding),
    ) {
        header()

        if (catalogue == SourceKind.STREAMX && selectedExtensionId == null) SettingsGroup(header = "StreamX") {
            Row(
                Modifier.fillMaxWidth().clickable(onClick = onOpenStreamXSetup)
                    .heightIn(min = 60.dp).padding(horizontal = ROW_INSET, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Dns, null, Modifier.size(ICON_SIZE))
                Spacer(Modifier.width(ICON_GAP))
                Text(stringResource(R.string.streamx_servers_settings), style = MaterialTheme.typography.bodyLarge)
            }
        }

        HomeFeedPicker(
            configs = configs,
            installedExtensions = installedExtensions,
            cloudStreamInstalled = cloudVersion != null,
            onOpenStreamXSetup = onOpenStreamXSetup,
            onOpenCloudStream = { cloudSettings = true },
        )

        // Only installed extensions are shown in the playback order.
        val visibleConfigs = configs.filter {
            when(it.kind) {
                SourceKind.APK_PLUGIN -> it.shownInSources && installedExtensions.any { extension->extension.packageName==it.extensionPackage }
                SourceKind.CLOUDSTREAM -> cloudVersion!=null
                else -> true
            }
        }
        val ordered = SourceRegistry.ordered(visibleConfigs, playbackOrder)

        SettingsGroup(
            header = stringResource(R.string.playback_order_header),
            footer = buildString {
                append(stringResource(R.string.playback_order_footer))
                if (cappedByQuality) {
                    append("\n\n")
                    append(stringResource(R.string.sources_quality_capped))
                } else if (!anyLosslessSource) {
                    append("\n\n")
                    append(stringResource(R.string.sources_no_lossless))
                }
            },
        ) {
            // The resolver's order, with rows that can be held and dragged;
            // the drag writes the order the resolver then walks.
            val row: @Composable (Int, SourceConfig, Modifier) -> Unit =
                { position, config, rowDrag ->
                    SourceRow(
                        position = position,
                        config = config,
                        dragModifier = rowDrag,
                        health = health[config.id],
                        // Switched on, but not reached on this connection. Said
                        // on the row rather than by moving the switch, so the
                        // switch keeps meaning "I want this source" and the
                        // connection keeps meaning "…and here is what it costs
                        // today".
                        skippedByQuality = config.enabled && !ceiling.permits(config.kind),
                        awaitingOutput = if (config.enabled && config.awaitsLosslessOutput) losslessOutput else null,
                        onMetered = metered == true,
                        ceiling = ceiling,
                        // Anything the user configured is theirs to edit or
                        // delete. JioSaavn and YouTube have no address to
                        // change, so a tap on them would open an empty editor.
                        onClick = when {
                            config.kind == SourceKind.APK_PLUGIN -> ({
                                com.music.streamx.data.extensions.ApkExtensions.installed.value.firstOrNull { it.packageName == config.extensionPackage }
                                    ?.let { com.music.streamx.data.extensions.ApkExtensions.showSettings(it) }
                                Unit
                            })
                            config.kind == SourceKind.CLOUDSTREAM -> ({ cloudSettings = true })
                            config.kind == SourceKind.STREAMX -> onOpenStreamXSetup
                            config.kind.needsServer -> ({ onEditSource(config) })
                            else -> null
                        },
                        onToggle = { enabled ->
                            if (config.kind == SourceKind.CLOUDSTREAM && enabled && !com.music.streamx.data.cloudstream.CloudStream.read(com.music.streamx.data.cloudstream.CloudStream.PROFILE).enabled) {
                                cloudSettings = true
                            } else if (config.kind == SourceKind.JIOSAAVN && enabled && !config.enabled) {
                                onConfirmJioSaavn()
                            } else if (config.kind == SourceKind.APK_PLUGIN && enabled) {
                                com.music.streamx.data.extensions.ApkExtensions.installed.value
                                    .firstOrNull { it.packageName == config.extensionPackage && it.sourceAuthority != null }
                                    ?.let(SourceRegistry::enableApk)
                            } else {
                                // Playback only; the Home feed above is its own choice.
                                SourceRegistry.setEnabled(config.id, enabled)
                            }
                        },
                    )
                }

            ReorderableSourceList(
                configs = ordered,
                onReorder = { SourceRegistry.reorderSources(it) },
                row = row,
            )

            // The one shut-out an addon can't tell apart from "not lossless":
            // headphones on LDAC, LHDC or SSC UHQ whose codec Android won't name without
            // the Nearby devices permission. Offered only then, and only from
            // here, where the reason for asking is on screen.
            val waiting = configs.any { it.enabled && it.awaitsLosslessOutput }
            if (waiting && losslessOutput.bluetoothCodecUnknown) {
                RowDivider()
                BluetoothCodecPermissionRow()
            }

            RepositoryExtensions(
                type="plugin",
                embedded=true,
                excludedPackages=configs.filter { it.kind==SourceKind.APK_PLUGIN && it.shownInSources &&
                    installedExtensions.any {extension->extension.packageName==it.extensionPackage} }
                    .map {it.extensionPackage}.toSet() +
                    if(cloudVersion!=null && configs.any {it.kind==SourceKind.CLOUDSTREAM}) setOf("com.music.streamx.cloudstream") else emptySet(),
                onInstalledClick={packageName->
                    if(packageName=="com.music.streamx.cloudstream") cloudSettings=true
                    else installedExtensions.firstOrNull {it.packageName==packageName}?.let {extension->
                        SourceRegistry.showApkSource(extension,true)
                        ApkExtensions.showSettings(extension)
                    }
                },
            )

            RowDivider()
            AddSourceRow(
                onClick = { onEditSource(SourceConfig(kind = SourceKind.ADDON)) },
            )
        }

        // A personal library rather than a catalogue in the order above: it
        // holds the listener's own files instead of answering for YouTube's,
        // so it takes no part in the ranking and gets its own group. The
        // editor is raised to the activity like every other alert.
        RemoteLibrarySection(
            icon = Icons.Rounded.Cloud,
            title = stringResource(R.string.webdav),
            description = stringResource(R.string.webdav_description),
            subtitle = if (webdavUrl.isBlank()) {
                stringResource(R.string.webdav_not_configured)
            } else if (webdavUsername.isNotBlank()) {
                "$webdavUrl · $webdavUsername"
            } else {
                webdavUrl
            },
            listSubtitle = stringResource(R.string.webdav_subtitle),
            disconnectTitle = stringResource(R.string.webdav_disconnect),
            configured = webdavUrl.isNotBlank(),
            testFailedRes = R.string.webdav_test_failed,
            onEdit = onEditWebDav,
            onTest = {
                WebDavRepository.testConnection(webdavUrl, webdavUsername, webdavPassword)
            },
            onDisconnect = { AppSettings.clearWebDav() },
        )

        // A personal library on a file share, filed next to the WebDAV one
        // for the same reason: it holds the listener's own files instead of
        // answering for YouTube's, so it takes no part in the ranking above.
        RemoteLibrarySection(
            icon = Icons.Rounded.Storage,
            title = stringResource(R.string.smb),
            description = stringResource(R.string.smb_description),
            subtitle = if (smbHost.isBlank() || smbShare.isBlank()) {
                stringResource(R.string.smb_not_configured)
            } else if (smbUsername.isNotBlank()) {
                "$smbHost · $smbShare · $smbUsername"
            } else {
                "$smbHost · $smbShare"
            },
            listSubtitle = stringResource(R.string.smb_subtitle),
            disconnectTitle = stringResource(R.string.smb_disconnect),
            configured = smbHost.isNotBlank() && smbShare.isNotBlank(),
            testFailedRes = R.string.smb_test_failed,
            onEdit = onEditSmb,
            onTest = {
                SmbRepository.testConnection(
                    smbHost,
                    smbShare,
                    AppSettings.smbBasePath.value,
                    smbUsername,
                    smbPassword,
                )
            },
            onDisconnect = { AppSettings.clearSmb() },
        )

        SettingsGroup(
            header = stringResource(R.string.source_matching),
            footer = stringResource(R.string.upgrade_length_slack_footer),
        ) {
            SliderRow(
                icon = Icons.Rounded.Tune,
                title = stringResource(R.string.upgrade_length_slack),
                subtitle = stringResource(R.string.upgrade_length_slack_subtitle),
                value = stringResource(R.string.seconds_short, upgradeLengthSlackSeconds),
                sliderValue = upgradeLengthSlackSeconds.toFloat(),
                onSliderValue = {
                    AppSettings.setUpgradeLengthSlackSeconds(it.roundToInt())
                },
                valueRange = AppSettings.MIN_UPGRADE_LENGTH_SLACK_SECONDS.toFloat()..
                    AppSettings.MAX_UPGRADE_LENGTH_SLACK_SECONDS.toFloat(),
                steps = AppSettings.MAX_UPGRADE_LENGTH_SLACK_SECONDS -
                    AppSettings.MIN_UPGRADE_LENGTH_SLACK_SECONDS - 1,
            )
        }

        Spacer(Modifier.height(32.dp))
    }

}


/**
 * A personal library section: the server row, plus test and disconnect rows
 * once something is configured. Shared by WebDAV and SMB, which differ only
 * in what "configured" means and which repository answers the test — the
 * test state lives here rather than in the screen, so each section tracks
 * its own result.
 */
@Composable
private fun RemoteLibrarySection(
    icon: ImageVector,
    title: String,
    description: String,
    subtitle: String,
    listSubtitle: String,
    disconnectTitle: String,
    configured: Boolean,
    testFailedRes: Int,
    onEdit: () -> Unit,
    onTest: suspend () -> Result<Unit>,
    onDisconnect: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var testing by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }
    SettingsGroup(header = title, footer = description) {
        SettingsRow(icon = icon, title = title, subtitle = subtitle, onClick = onEdit)
        if (configured) {
            RowDivider()
            SettingsRow(
                icon = Icons.Rounded.Dns,
                title = if (testing) {
                    stringResource(R.string.testing)
                } else {
                    testResult ?: stringResource(R.string.test)
                },
                subtitle = listSubtitle,
                onClick = {
                    if (testing) return@SettingsRow
                    testing = true
                    testResult = null
                    scope.launch {
                        onTest().fold(
                            onSuccess = {
                                testResult = context.getString(R.string.connected)
                            },
                            onFailure = {
                                testResult = context.getString(
                                    testFailedRes,
                                    it.message ?: context.getString(R.string.failed),
                                )
                            },
                        )
                        testing = false
                    }
                },
            )
            RowDivider()
            SettingsRow(
                icon = Icons.Rounded.DeleteSweep,
                title = disconnectTitle,
                onClick = {
                    onDisconnect()
                    testResult = null
                },
            )
        }
    }
}
/**
 * The one row on this screen that adds something rather than describing
 * something.
 *
 * Deliberately at the bottom of the same group as the sources rather than off
 * in a section of its own: what it adds goes to the *top* of that list, and a
 * row sitting under the numbered ones is the clearest way to say "and you can
 * put another one in here".
 */
@Composable
private fun AddSourceRow(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = 60.dp)
            .padding(horizontal = ROW_INSET, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Aligned with the numbers above it rather than with their icons, so
        // the plus reads as belonging to the same column the list is indexed by.
        Spacer(Modifier.width(24.dp))
        Icon(
            imageVector = Icons.Rounded.Add,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(ICON_SIZE),
        )
        Spacer(Modifier.width(ICON_GAP))
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.add_addon),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = stringResource(R.string.add_addon_detail),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
            )
        }
    }
}

/**
 * Asks for Nearby devices so the Bluetooth codec can be read — the only way an
 * LDAC, LHDC or SSC UHQ headset qualifies for an addon's `checkValidLossless`. Draws
 * nothing where the permission does not exist (before Android 12) or is
 * already granted, since asking again would do nothing.
 */
@Composable
private fun BluetoothCodecPermissionRow() {
    val context = LocalContext.current
    val permission = "android.permission.BLUETOOTH_CONNECT"
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    fun isGranted() =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    var granted by remember { mutableStateOf(isGranted()) }
    var denied by remember { mutableStateOf(false) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok
        denied = !ok
        if (ok) LosslessOutput.refresh()
    }
    // Back from system Settings, where it may have been switched on.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        val now = isGranted()
        if (now && !granted) LosslessOutput.refresh()
        granted = now
        if (now) denied = false
    }
    // Asked as soon as the screen shows the shut-out it would fix, and again
    // on every later visit, since this row is composed afresh each time. After
    // a second refusal Android answers without a dialog, which lands in
    // [denied] and the Settings prompt below.
    LaunchedEffect(Unit) {
        if (!isGranted()) ask.launch(permission)
    }
    if (granted) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                if (denied) {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                            .setData(Uri.fromParts("package", context.packageName, null)),
                    )
                } else {
                    ask.launch(permission)
                }
            }
            .heightIn(min = 60.dp)
            .padding(horizontal = ROW_INSET, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.width(24.dp))
        Icon(
            imageVector = Icons.Rounded.Bluetooth,
            contentDescription = null,
            tint = if (denied) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(ICON_SIZE),
        )
        Spacer(Modifier.width(ICON_GAP))
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(
                    if (denied) R.string.lossless_output_permission_denied else R.string.lossless_output_permission,
                ),
                style = MaterialTheme.typography.bodyLarge,
                color = if (denied) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            )
            Text(
                text = stringResource(
                    if (denied) R.string.lossless_output_permission_denied_detail else R.string.lossless_output_permission_detail,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
            )
        }
    }
}

@Composable
private fun SourceRow(
    position: Int,
    config: SourceConfig,
    health: SourceHealth?,
    onClick: (() -> Unit)?,
    onToggle: (Boolean) -> Unit,
    /** Long-press-to-lift on the row body, supplied by the list that owns the drag. */
    dragModifier: Modifier = Modifier,
    /** On, but skipped by the ceiling the current connection is set to. */
    skippedByQuality: Boolean = false,
    /** On, but its addon needs a lossless output and this is what is connected instead. Null when not waiting. */
    awaitingOutput: LosslessOutput.State? = null,
    /** Which of the two ceilings [ceiling] is, so the row can name it. */
    onMetered: Boolean = false,
    ceiling: AudioQuality = AudioQuality.LOSSLESS,
) {
    // Dimmed for the same reason an off source is: it is not in the walk. The
    // switch stays where the user left it, so the row reads "on, but not
    // today" rather than "off".
    val dimmed = !config.enabled || skippedByQuality || awaitingOutput != null
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = onClick != null) { onClick?.invoke() }
            .then(dragModifier)
            .heightIn(min = 60.dp)
            .padding(horizontal = ROW_INSET, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "$position",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .width(18.dp)
                .alpha(if (dimmed) 0.4f else 1f),
        )
        Spacer(Modifier.width(6.dp))
        SourceGlyph(config, Modifier.size(36.dp))
        Spacer(Modifier.width(ICON_GAP))
        Column(
            Modifier
                .weight(1f)
                .alpha(if (dimmed) 0.4f else 1f),
        ) {
            Text(
                text = config.displayName,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = config.statusLine(health),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        Spacer(Modifier.width(8.dp))
        com.music.streamx.ui.components.SkinSwitch(
            checked = config.enabled,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                checkedBorderColor = MaterialTheme.colorScheme.primary,
            ),
        )
    }
}

@Composable
private fun AudioQuality.localizedLabel(): String = stringResource(
    when (this) {
        AudioQuality.LOW -> R.string.low
        AudioQuality.MEDIUM -> R.string.medium
        AudioQuality.HIGH -> R.string.high
        AudioQuality.LOSSLESS -> R.string.lossless
    },
)

/** Keep source rows focused on their name and current connection state. */
@Composable
private fun SourceConfig.statusLine(health: SourceHealth?): String = stringResource(
    if (enabled && isComplete && health is SourceHealth.Ok) R.string.connected
    else R.string.not_connected,
)

/**
 * Add or edit a source that has an address.
 *
 * Test is offered rather than required: a server that happens to be asleep is
 * still worth saving, and refusing to store it until it answers would make
 * setting one up from a coffee shop impossible. What Test does buy is the
 * difference between "not answering" and "answering, but not with something
 * this app can use" — a manifest missing the `stream` resource is a mistake
 * worth hearing about before the first track rather than after it.
 */
@Composable
internal fun SourceEditorAlert(
    hazeState: HazeState,
    config: SourceConfig,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
    onDelete: () -> Unit,
    scope: kotlinx.coroutines.CoroutineScope,
) {
    val isNew = SourceRegistry.config(config.id) == null
    var baseUrl by remember { mutableStateOf(config.baseUrl) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var statusIsGood by remember { mutableStateOf(false) }

    val connected = stringResource(R.string.connected)
    val unreadable = stringResource(R.string.source_unrecognised)
    val alreadyAdded = stringResource(R.string.source_already_added)

    /**
     * Identify what is at the URL, then say so.
     *
     * Both buttons run this — the only difference is whether a success is then
     * stored. Testing and saving asking the *same* question is the point: a
     * Save that skipped identification could store a config whose kind was
     * guessed, and the guess is exactly what this screen no longer makes.
     */
    fun run(thenSave: Boolean) {
        busy = true
        status = null
        scope.launch {
            val identified = withContext(Dispatchers.IO) {
                runCatching { SourceRegistry.identify(baseUrl.trim(), config.takeUnless { isNew }) }
                    .getOrElse { Result.failure(it) }
            }
            // An index that names itself (a SpotiFLAC registry) is labelled with
            // that name rather than its host, unless the user already chose one.
            val found = identified.getOrNull()?.let { config ->
                SourceFormats.indexName(config.baseUrl)
                    ?.takeIf { config.kind == SourceKind.CUSTOM_MODULE && config.label.isBlank() }
                    ?.let { config.copy(label = it) } ?: config
            }
            if (found == null) {
                statusIsGood = false
                status = identified.exceptionOrNull()?.message ?: unreadable
                busy = false
                return@launch
            }

            // Already here? Checked against the *identified* base rather than
            // the typed text, so an addon's root and its manifest.json are
            // recognised as the one source they are — which is the whole reason
            // this runs after identification and not before it. Reported on
            // Test as well as Save: finding out by pressing Test beats finding
            // out by ending up with the same catalogue searched twice on every
            // track.
            SourceRegistry.duplicateOf(found.baseUrl, exceptId = config.id.takeUnless { isNew })
                ?.let { existing ->
                    statusIsGood = false
                    status = String.format(alreadyAdded, existing.displayName)
                    busy = false
                    return@launch
                }

            // Identified, and now asked whether it actually works. The two are
            // different questions: a manifest can be perfectly well formed and
            // its server still be refusing every search.
            val health = withContext(Dispatchers.IO) {
                runCatching { SourceRegistry.probeCandidate(found) }
                    .getOrElse { SourceHealth.Unreachable(it.message ?: "Failed") }
            }
            statusIsGood = health.isOk
            status = when (health) {
                is SourceHealth.Ok -> listOfNotNull(
                    connected,
                    found.kind.label,
                    health.detail,
                ).joinToString(" · ")
                is SourceHealth.Rejected -> health.reason
                is SourceHealth.Unreachable -> health.reason
            }
            busy = false

            // Saved even when the probe came back unhappy, but only once the
            // format is known: a server that is asleep is still worth storing —
            // that was true before and is why Test was never mandatory — while
            // a URL nothing can be made of is not.
            if (thenSave) {
                if (isNew) SourceRegistry.add(found) else SourceRegistry.update(found)
                onSaved()
            }
        }
    }

    AddonEditorAlert(
        hazeState = hazeState,
        title = if (isNew) {
            stringResource(R.string.add_addon)
        } else {
            config.displayName
        },
        description = stringResource(R.string.addon_url_description),
        urlValue = baseUrl,
        // A result describes the address it was run against, so the moment that
        // address is edited it stops being true and is cleared. Left up, it
        // would report "Connected" over a URL nobody has tried.
        onUrlChange = { baseUrl = it; status = null },
        urlPlaceholder = "https://my-addon.example.com",
        status = status,
        statusIsGood = statusIsGood,
        testing = busy,
        canSubmit = baseUrl.isNotBlank(),
        onTest = { run(thenSave = false) },
        onSave = { run(thenSave = true) },
        onRemove = if (isNew) null else onDelete,
        onDismiss = onDismiss,
    )
}
