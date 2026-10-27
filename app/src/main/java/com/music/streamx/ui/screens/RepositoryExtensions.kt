package com.music.streamx.ui.screens

import android.net.Uri
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.compose.foundation.clickable
import androidx.compose.ui.res.painterResource
import com.music.streamx.R
import android.text.format.Formatter
import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.music.streamx.data.extensions.ApkExtensions
import com.music.streamx.data.extensions.ExtensionRepository
import com.music.streamx.data.extensions.ExtensionRepositoryLinks
import com.music.streamx.data.extensions.RepositoryExtension
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private data class RepositoryChoice(val packageName: String, val name: String, val entry: RepositoryExtension?)

@Composable internal fun RepositoryExtensions(
    type: String,
    onInstall: ((Uri)->Unit)? = null,
    excludedPackages: Set<String> = emptySet(),
    onInstalledClick: ((String)->Unit)? = null,
    embedded: Boolean = false,
    /**
     * Only what can be downloaded or updated, with no header, refresh or
     * loading bar: the rows simply join the installed list once they arrive.
     */
    availableOnly: Boolean = false,
) {
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    val repositories by remember(context) { ExtensionRepositoryLinks.observe(context) }.collectAsStateWithLifecycle()
    val installed by ApkExtensions.installed.collectAsStateWithLifecycle()
    val packageRevision by ApkExtensions.packageRevision.collectAsStateWithLifecycle()
    var rows by remember(type){mutableStateOf<List<RepositoryExtension>>(emptyList())}
    var error by remember(type){mutableStateOf<String?>(null)}
    var loading by remember(type){mutableStateOf(true)}
    var refresh by remember(type){mutableIntStateOf(0)}
    var downloading by remember{mutableStateOf<String?>(null)}
    var progress by remember{mutableIntStateOf(0)}
    val cloudVersion by ApkExtensions.cloudStreamVersion.collectAsStateWithLifecycle()
    var pendingApk by remember { mutableStateOf<Uri?>(null) }
    fun openInstaller(uri: Uri) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri,"application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
        } catch(failure: Exception) { error=failure.message ?: "Could not open APK installer" }
    }
    val allowInstallation=rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        pendingApk?.let { if(context.packageManager.canRequestPackageInstalls()) openInstaller(it)
            else error="Allow StreamX to install APKs in Android Settings." }
        pendingApk=null
    }
    fun requestInstallation(uri: Uri) {
        if(onInstall!=null) onInstall(uri)
        else if(context.packageManager.canRequestPackageInstalls()) openInstaller(uri)
        else {
            pendingApk=uri
            try {allowInstallation.launch(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:${context.packageName}")))}
            catch(failure: Exception) {pendingApk=null;error=failure.message ?: "Could not open install permission settings"}
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { scope.launch { ApkExtensions.refresh() } }
    LaunchedEffect(type,refresh,repositories) {
        loading=true;error=null
        try {
            val catalogue=ExtensionRepository.loadCatalogue(context,type,repositories)
            rows=catalogue.entries
            error=catalogue.errors.takeIf { it.isNotEmpty() }?.joinToString("\n")
        }
        catch(cancelled: CancellationException){throw cancelled}
        catch(failure: Exception){error=failure.message ?: "Could not load extension repository"}
        finally{loading=false}
    }
    val choices=buildList {
        rows.forEach { add(RepositoryChoice(it.packageName,it.name,it)) }
        if(type=="plugin") listOf(
            "com.music.streamx.extensions.applemusic" to "Apple Music",
            "com.music.streamx.extensions.spotify" to "Spotify",
            "com.music.streamx.cloudstream" to "CloudStream",
        ).forEach { (packageName,name) ->
            if(rows.none {it.packageName==packageName}) add(RepositoryChoice(packageName,name,null))
        }
    }.filter {it.packageName !in excludedPackages}
    @Suppress("DEPRECATION")
    fun localVersionOf(packageName: String)=runCatching{context.packageManager.getPackageInfo(packageName,0).let{if(Build.VERSION.SDK_INT>=28)it.longVersionCode else it.versionCode.toLong()}}.getOrDefault(0)
    val shownChoices=if(!availableOnly) choices else remember(choices,installed,packageRevision,cloudVersion) {
        choices.filter {choice-> val local=localVersionOf(choice.packageName); local==0L || choice.entry!=null && local<choice.entry.versionCode }
    }
    if(availableOnly && shownChoices.isEmpty()) return
    if(choices.isEmpty() && onInstalledClick!=null && error==null) return
    // Row-style layout: inside another screen, or joining its installed list.
    val rowStyle=embedded || availableOnly
    @Composable fun Content() {
        Column(Modifier.fillMaxWidth().then(if(rowStyle) Modifier else Modifier.padding(18.dp)),
            verticalArrangement=Arrangement.spacedBy(if(rowStyle) 0.dp else 12.dp)) {
            if(!embedded && !availableOnly) Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Text(if(type=="skin")"StreamX Skins" else "StreamX Plugins",Modifier.weight(1f),style=MaterialTheme.typography.titleMedium)
                TextButton(onClick={refresh++},enabled=!loading && downloading==null){Text("Refresh")}
            }
            if(loading && !embedded && !availableOnly) LinearProgressIndicator(Modifier.fillMaxWidth())
            error?.let {message->
                Column(if(rowStyle) Modifier.padding(horizontal=ROW_INSET,vertical=8.dp) else Modifier) {
                    Text(message,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodyMedium)
                }
            }
            if(type=="skin" && !availableOnly && !loading && error==null && rows.isEmpty())Text("No compatible extensions for this device yet.")
            shownChoices.forEachIndexed {index,choice->
                val entry=choice.entry
                @Suppress("DEPRECATION")
                val localVersion=remember(installed,cloudVersion,refresh,packageRevision,choice.packageName){
                    runCatching{context.packageManager.getPackageInfo(choice.packageName,0).let{if(Build.VERSION.SDK_INT>=28)it.longVersionCode else it.versionCode.toLong()}}.getOrDefault(0)
                }
                if(embedded || availableOnly) {if(index>0 || embedded) RowDivider()} else HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
                Row(Modifier.fillMaxWidth().heightIn(min=60.dp)
                    .clickable(enabled=localVersion>0 && onInstalledClick!=null) {onInstalledClick?.invoke(choice.packageName)}
                    .then(if(rowStyle) Modifier.padding(horizontal=ROW_INSET,vertical=10.dp) else Modifier),
                    verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(if(rowStyle) ICON_GAP else 12.dp)) {
                    if(rowStyle) Spacer(Modifier.width(10.dp))
                    ExtensionLogo(choice.packageName,localVersion,modifier=if(rowStyle) Modifier.size(36.dp) else Modifier)
                    Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)) {
                        Text(extensionDisplayName(choice.name,type=="skin"),style=MaterialTheme.typography.bodyLarge)
                        Text(if(embedded) {if(localVersion>0) "Installed · tap to configure" else "Not installed"}
                            else if(availableOnly && entry!=null && localVersion>0) "Update to ${entry.versionName} · ${Formatter.formatShortFileSize(context, entry.size)}"
                            else if(entry!=null) "${entry.versionName} · ${Formatter.formatShortFileSize(context, entry.size)}" else if(localVersion>0) "Installed" else "Download music plugin",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        entry?.repositoryName?.let { Text(it, style=MaterialTheme.typography.labelSmall,
                            color=MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                    val current=downloading==choice.packageName
                    if (!availableOnly && localVersion > 0 && entry!=null && localVersion < entry.versionCode) {
                        Icon(Icons.Rounded.Check, contentDescription="Installed", tint=MaterialTheme.colorScheme.primary,
                            modifier=Modifier.size(20.dp))
                    }
                    if (current) {
                        Box(Modifier.size(48.dp).semantics { contentDescription="Downloading $progress percent" },contentAlignment=Alignment.Center) {
                            CircularProgressIndicator(progress={progress / 100f},modifier=Modifier.size(32.dp),strokeWidth=2.dp)
                            Text("$progress",style=MaterialTheme.typography.labelSmall)
                        }
                    } else if (localVersion > 0 && (entry==null || localVersion >= entry.versionCode)) {
                        Box(Modifier.size(48.dp),contentAlignment=Alignment.Center) {
                            Icon(Icons.Rounded.Check,contentDescription="Installed",tint=MaterialTheme.colorScheme.primary)
                        }
                    } else IconButton(enabled=downloading==null,onClick={
                        scope.launch {
                            downloading=choice.packageName;progress=0;error=null
                            try {
                                val metadata=entry ?: ExtensionRepository.loadCatalogue(context,type,repositories).entries.also {rows=it}
                                    .firstOrNull {it.packageName==choice.packageName}
                                    ?: error("No compatible release is available for this device yet.")
                                val apk=ExtensionRepository.download(context,metadata){value->progress=value}
                                requestInstallation(FileProvider.getUriForFile(context,"${context.packageName}.fileprovider",apk))
                            } catch(cancelled: CancellationException){throw cancelled}
                            catch(failure: Exception){error=failure.message ?: "Could not install extension"}
                            finally{downloading=null}
                        }
                    }) {
                        Icon(painterResource(R.drawable.ic_cloud_download),
                            contentDescription=if(localVersion>0) "Update ${choice.name}" else "Download ${choice.name}")
                    }
                }
            }
        }
    }
    if(embedded) Content()
    else if(availableOnly) SettingsGroup(topSpacing=16.dp,footer="Android confirms each installation. Checksums and signing certificates are verified first.") { Content() }
    else SettingsGroup(header="Available to download",footer="Android confirms installation. Download checksums and repository signing certificates are verified before opening the installer.") { Content() }
}
