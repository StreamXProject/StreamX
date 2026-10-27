package com.music.streamx.ui.screens

import com.music.streamx.ui.components.SkinAlertDialog as AlertDialog
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.ViewAgenda
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.graphics.graphicsLayer
import com.music.streamx.ui.components.BottomFadeScrim
import com.music.streamx.ui.components.FrostedTopBar
import com.music.streamx.ui.components.topBarContentPadding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.streamx.R
import com.music.streamx.data.skins.SkinRegistry
import com.music.streamx.data.skins.SkinSection
import com.music.streamx.data.sources.SourceKind
import com.music.streamx.data.sources.SourceRegistry
import kotlinx.coroutines.launch

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.music.streamx.data.extensions.ApkExtensions
import kotlinx.coroutines.CancellationException

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun SkinsPluginsScreen(
    onSources: () -> Unit,
    onDismiss: () -> Unit,
    embeddedSkins: Boolean = false,
    header: (@Composable () -> Unit)? = null,
    embeddedPadding: PaddingValues = PaddingValues(),
) {
    val active by SkinRegistry.active.collectAsStateWithLifecycle()
    val sectionOverrides by SkinRegistry.sectionOverrides.collectAsStateWithLifecycle()
    val registeredSkins by SkinRegistry.skins.collectAsStateWithLifecycle()
    var editingSections by rememberSaveable { mutableStateOf(false) }
    val extensions by ApkExtensions.installed.collectAsStateWithLifecycle()
    val cloudStreamVersion by ApkExtensions.cloudStreamVersion.collectAsStateWithLifecycle()
    var cloudSettings by remember { mutableStateOf(false) }
    if (cloudSettings) CloudStreamSettingsDialog { cloudSettings = false }
    val scanError by ApkExtensions.scanError.collectAsStateWithLifecycle()
    val configs by SourceRegistry.configs.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var pendingApk by remember { mutableStateOf<Uri?>(null) }
    fun install(uri: Uri) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
        } catch (e: Exception) { status = e.message ?: "Could not open the APK installer" }
    }
    val allowInstallation = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        pendingApk?.let { uri ->
            if (context.packageManager.canRequestPackageInstalls()) install(uri)
            else status = "Allow StreamX to install APKs, or install the extension using your file manager."
        }
        pendingApk = null
    }
    val chooseApk = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            if (context.packageManager.canRequestPackageInstalls()) install(uri)
            else {
                pendingApk = uri
                try {
                    allowInstallation.launch(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")))
                } catch (e: Exception) { pendingApk = null; status = e.message ?: "Install the APK using your file manager." }
            }
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { scope.launch { ApkExtensions.refresh() } }
    BackHandler(enabled = !embeddedSkins, onBack = onDismiss)
    var selectedTab by rememberSaveable { mutableIntStateOf(if (embeddedSkins) 1 else 0) }
    var expandedPackage by rememberSaveable { mutableStateOf<String?>(null) }
    val visibleExtensions = extensions.filter {
        if (selectedTab == 0) it.sourceAuthority != null else it.skinAsset != null
    }
    var skinConfiguration by remember { mutableStateOf<com.music.streamx.data.skins.SkinSpec?>(null) }
    val themeProvider = com.music.streamx.ui.skins.LocalSkinRenderer.current as? com.music.streamx.ui.skins.SkinThemeProvider
    skinConfiguration?.let { spec ->
        androidx.compose.ui.window.Dialog(onDismissRequest={skinConfiguration=null}, properties=androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth=false, decorFitsSystemWindows=false)) {
            com.music.streamx.ui.theme.StreamXTheme(overrideSkin=spec, section=SkinSection.SETTINGS) {
                val provider=com.music.streamx.ui.skins.LocalSkinRenderer.current as? com.music.streamx.ui.skins.SkinThemeProvider
                if(provider!=null) provider.Configuration {skinConfiguration=null}
                else Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center) {
                    if(spec.renderer==null || com.music.streamx.ui.skins.LocalSkinRenderer.current!=null) Column(horizontalAlignment=Alignment.CenterHorizontally) {
                        Text("This skin has no additional settings.")
                        TextButton(onClick={skinConfiguration=null}) { Text("Done") }
                    } else CircularProgressIndicator()
                }
            }
        }
    }
    if(editingSections) {
        val sectionEditor: @Composable () -> Unit = {
            SectionThemesScreen(extensions.filter { it.skinAsset != null }, onBack = { editingSections = false })
        }
        if (embeddedSkins) {
            // Host settings chrome must not intercept the section editor's back button.
            androidx.compose.ui.window.Dialog(
                onDismissRequest = { editingSections = false },
                properties = androidx.compose.ui.window.DialogProperties(
                    usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
            ) { sectionEditor() }
        } else {
            BackHandler { editingSections = false }
            sectionEditor()
        }
        return
    }
    val list = rememberLazyListState()
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize()) {
            LazyColumn(
                state = list,
                modifier = if (embeddedSkins) Modifier.fillMaxSize() else Modifier.fillMaxSize().navigationBarsPadding(),
                contentPadding = if (embeddedSkins) embeddedPadding else PaddingValues(top = topBarContentPadding(), bottom = 24.dp),
            ) {
                item(key = "title") {
                    if (header != null) header() else Text(
                        stringResource(R.string.skins_plugins_title),
                        style = MaterialTheme.typography.displayLarge,
                        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 14.dp),
                    )
                }
                if (!embeddedSkins) item(key = "categories") {
                    if(themeProvider!=null) PrimaryTabRow(selectedTabIndex=selectedTab,containerColor=MaterialTheme.colorScheme.surface) {
                        listOf(stringResource(R.string.extensions_tab_plugins),stringResource(R.string.skins_title)).forEachIndexed { index,label ->
                            Tab(selected=selectedTab==index,onClick={selectedTab=index;expandedPackage=null},text={Text(label)},modifier=Modifier.heightIn(min=56.dp))
                        }
                    }
                    else Box(Modifier.fillMaxWidth().padding(horizontal=20.dp,vertical=8.dp)) {
                        SegmentedControl(options=listOf(stringResource(R.string.extensions_tab_plugins),stringResource(R.string.skins_title)),selectedIndex=selectedTab,onSelect={selectedTab=it;expandedPackage=null})
                    }
                }
                item(key = "actions") {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        TextButton(onClick = {
                            chooseApk.launch(arrayOf("application/vnd.android.package-archive", "application/octet-stream"))
                        }) {
                            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.extensions_install_short))
                        }
                        TextButton(onClick = { scope.launch { ApkExtensions.refresh() } }) {
                            Text(stringResource(R.string.extensions_refresh_short))
                        }
                    }
                    (status ?: scanError)?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
                    }
                }
                if (selectedTab == 0) {
                    item(key = "sources") {
                        SettingsGroup(topSpacing = 4.dp) {
                            SettingsRow(
                                title = "Manage sources",
                                subtitle = "Home feeds and playback order",
                                onClick = onSources,
                            )
                        }
                    }
                } else {
                    item(key = "section-themes") {
                        SettingsGroup(topSpacing=4.dp) {
                            SettingsRow(icon=Icons.Rounded.Palette,title="Customize sections",
                                subtitle=if(sectionOverrides.isEmpty()) "Choose a skin for Home, players and Settings" else "${sectionOverrides.size} sections customized",
                                onClick={editingSections=true})
                        }
                    }
                    item(key = "default") {
                        SettingsGroup(topSpacing = 4.dp) {
                            ExtensionIdentityRow(
                                packageName = context.packageName,
                                title = "Default appearance",
                                subtitle = null,
                                onClick = { SkinRegistry.select("default") },
                                trailing = {
                                    if (active.extensionPackage == null) {
                                        Icon(Icons.Rounded.Check, contentDescription = stringResource(R.string.skins_selected), tint = MaterialTheme.colorScheme.primary)
                                    }
                                },
                            )
                        }
                    }
                }
                if (selectedTab == 0 && cloudStreamVersion != null) {
                    item(key = "cloudstream-plugin") {
                        val packageName = com.music.streamx.data.cloudstream.CloudStream.PACKAGE
                        val expanded = expandedPackage == packageName
                        SettingsGroup(topSpacing = 16.dp) {
                            Column(Modifier.animateContentSize()) {
                                ExtensionIdentityRow(
                                    packageName = packageName,
                                    version = cloudStreamVersion ?: 0L,
                                    title = "CloudStream",
                                    subtitle = "Available in Sources",
                                    onClick = { expandedPackage = if (expanded) null else packageName },
                                    trailing = {
                                        val rotation by animateFloatAsState(if (expanded) 180f else 0f, label = "cloudStreamDetailsChevron")
                                        Icon(Icons.Rounded.ExpandMore,
                                            contentDescription = stringResource(if (expanded) R.string.extensions_collapse_details else R.string.extensions_expand_details),
                                            modifier = Modifier.rotate(rotation), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    },
                                )
                                if (expanded) {
                                    RowDivider()
                                    SettingsRow(title = stringResource(R.string.extensions_version, cloudStreamVersion ?: 0L), subtitle = packageName)
                                    RowDivider()
                                    SettingsRow(title = stringResource(R.string.settings), onClick = { cloudSettings = true })
                                    RowDivider()
                                    SettingsRow(title = "Manage in Sources", onClick = onSources)
                                    RowDivider()
                                    SettingsRow(title = stringResource(R.string.extensions_uninstall), onClick = {
                                        runCatching { context.startActivity(Intent(Intent.ACTION_UNINSTALL_PACKAGE, Uri.parse("package:$packageName")).putExtra(Intent.EXTRA_RETURN_RESULT, true)) }
                                            .onFailure { status = it.message ?: "Could not open the uninstaller" }
                                    })
                                }
                            }
                        }
                    }
                }
                if (visibleExtensions.isEmpty() && (selectedTab != 0 || cloudStreamVersion == null)) {
                    item(key = "empty") {
                        SettingsGroup(topSpacing = 16.dp) {
                            SettingsRow(
                                icon = if (selectedTab == 0) Icons.Rounded.Extension else Icons.Rounded.Palette,
                                title = stringResource(if (selectedTab == 0) R.string.extensions_no_plugins else R.string.extensions_no_skins),
                                subtitle = stringResource(if (selectedTab == 0) R.string.extensions_install_plugin_hint else R.string.extensions_install_skin_hint),
                            )
                        }
                    }
                }
                items(visibleExtensions, key = { "extension-${selectedTab}-${it.packageName}" }) { extension ->
                    val source = configs.firstOrNull { it.kind == SourceKind.APK_PLUGIN && it.extensionPackage == extension.packageName }
                    val trusted = source?.extensionCertificate == extension.certificate
                    val shown = source?.shownInSources == true && trusted
                    val applied = active.extensionPackage == extension.packageName
                    val expanded = expandedPackage == extension.packageName
                    // Skins carry a tick when applied rather than a status line.
                    val summary = if (selectedTab == 0) {
                        if (shown) R.string.extensions_source_shown else R.string.extensions_source_hidden
                    } else null
                    SettingsGroup(topSpacing = 16.dp) {
                        Column(Modifier.animateContentSize()) {
                            ExtensionIdentityRow(
                                packageName = extension.packageName,
                                version = extension.versionCode,
                                title = extensionDisplayName(extension.name,selectedTab == 1),
                                subtitle = summary?.let { stringResource(it) },
                                onClick = { expandedPackage = if (expanded) null else extension.packageName },
                                trailing = {
                                    if (selectedTab == 1 && applied) Icon(Icons.Rounded.Check,
                                        contentDescription = stringResource(R.string.extensions_skin_applied),
                                        tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(end = 12.dp))
                                    val rotation by animateFloatAsState(if (expanded) 180f else 0f, label = "extensionDetailsChevron")
                                    Icon(
                                        Icons.Rounded.ExpandMore,
                                        contentDescription = stringResource(if (expanded) R.string.extensions_collapse_details else R.string.extensions_expand_details),
                                        modifier = Modifier.rotate(rotation),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                },
                            )
                            if (expanded) {
                                RowDivider()
                                SettingsRow(title = stringResource(R.string.extensions_version, extension.versionCode), subtitle = extension.packageName)
                                RowDivider()
                                if (selectedTab == 0) {
                                    SettingsRow(
                                        title = "Show in Sources and Home",
                                        subtitle = "Off hides it from Home and from playback",
                                        trailing = {
                                            com.music.streamx.ui.components.SkinSwitch(checked = shown, onCheckedChange = { SourceRegistry.showApkSource(extension, it) })
                                        },
                                    )
                                } else {
                                    SettingsRow(
                                        icon = Icons.Rounded.Palette,
                                        title = stringResource(R.string.extensions_apply_skin),
                                        trailing = if (applied) ({ Icon(Icons.Rounded.Check, contentDescription = stringResource(R.string.extensions_skin_applied), tint = MaterialTheme.colorScheme.primary) }) else null,
                                        enabled = !busy,
                                        onClick = {
                                            if (!applied) scope.launch {
                                                busy = true
                                                status = null
                                                try { ApkExtensions.activateSkin(extension) }
                                                catch (cancelled: CancellationException) { throw cancelled }
                                                catch (failure: Exception) { status = failure.message ?: "Could not apply this skin" }
                                                finally { busy = false }
                                            }
                                        },
                                    )
                                }
                                RowDivider()
                                SettingsRow(title = stringResource(R.string.settings), onClick = {
                                    if(selectedTab==1) scope.launch {
                                        busy=true
                                        try { skinConfiguration=ApkExtensions.activateSkin(extension,activate=false) }
                                        catch(cancelled: CancellationException) { throw cancelled }
                                        catch(failure: Exception) { status=failure.message ?: "Could not open skin settings" }
                                        finally { busy=false }
                                    } else runCatching { ApkExtensions.showSettings(extension) }
                                        .onFailure { status = it.message ?: "Could not open extension settings" }
                                })
                                RowDivider()
                                SettingsRow(title = stringResource(R.string.extensions_uninstall), onClick = {
                                    runCatching { context.startActivity(Intent(Intent.ACTION_UNINSTALL_PACKAGE, Uri.parse("package:${extension.packageName}")).putExtra(Intent.EXTRA_RETURN_RESULT,true)) }
                                        .onFailure { status = it.message ?: "Could not open the uninstaller" }
                                })
                            }
                        }
                    }
                }
                if (selectedTab == 0) {
                    items(
                        configs.filter { it.kind == SourceKind.APK_PLUGIN && extensions.none { apk -> apk.packageName == it.extensionPackage } },
                        key = { "missing-${it.id}" },
                    ) { missing ->
                        SettingsGroup(header = "Unavailable plugin") {
                            SettingsRow(title = missing.displayName, subtitle = "APK is no longer available")
                            RowDivider()
                            SettingsRow(title = "Remove from list", onClick = { SourceRegistry.remove(missing.id) })
                        }
                    }
                }
                item(key = "repository-$selectedTab") {
                    RepositoryExtensions(if(selectedTab==0)"plugin" else "skin",availableOnly=true,onInstall={uri->
                        if(context.packageManager.canRequestPackageInstalls())install(uri)
                        else {
                            pendingApk=uri
                            try {allowInstallation.launch(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:${context.packageName}")))}
                            catch(failure: Exception){pendingApk=null;status=failure.message ?: "Allow StreamX to install APKs in Android Settings"}
                        }
                    })
                }
                item(key = "footer") {
                    Text(
                        stringResource(if (selectedTab == 0) R.string.extensions_plugins_footer else R.string.extensions_skins_footer),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                    )
                }
            }
            val topFadeAlpha by remember { derivedStateOf {
                if(list.firstVisibleItemIndex>0) 1f else {
                    val titleHeight=list.layoutInfo.visibleItemsInfo.firstOrNull{it.index==0}?.size?.toFloat() ?: 1f
                    ((list.firstVisibleItemScrollOffset-titleHeight*.4f)/(titleHeight*.6f)).coerceIn(0f,1f)
                }
            } }
            if (!embeddedSkins) {
                BottomFadeScrim(modifier=Modifier.rotate(180f).graphicsLayer { alpha=topFadeAlpha })
            FrostedTopBar(
                title = stringResource(R.string.skins_plugins_title),
                scrolled = false,
                onBack = onDismiss,
                transparentBackdrop = true,
                artworkPageChrome = true,
            )
            }
        }
    }
}
