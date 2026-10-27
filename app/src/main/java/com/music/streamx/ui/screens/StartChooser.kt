package com.music.streamx.ui.screens

import com.music.streamx.ui.skins.SkinIcon as Icon
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.streamx.R
import com.music.streamx.data.cloudstream.CloudStream
import com.music.streamx.data.extensions.ApkExtension
import com.music.streamx.data.extensions.ApkExtensions
import com.music.streamx.data.extensions.ExtensionRepository
import com.music.streamx.data.extensions.ExtensionRepositoryLinks
import com.music.streamx.data.extensions.RepositoryExtension
import com.music.streamx.data.skins.SkinRegistry
import com.music.streamx.ui.skins.skinAndroidPainterResource as painterResource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** The start page's source key for the built-in YouTube Music profile. */
internal const val StartYouTubeSource = "youtube"

private val TileShape = RoundedCornerShape(22.dp)

/** One download at a time, shared by the plugin tiles and the skin strip. */
private class StartDownloads {
    var packageName by mutableStateOf<String?>(null)
    var progress by mutableIntStateOf(0)
    var error by mutableStateOf<String?>(null)
}

/**
 * The start page: pick where to listen (selected, then confirmed by the pinned
 * Continue button), pick a look (applied on tap, so the choice is visible
 * straight away), or take another way in.
 *
 * Besides what is installed, both lists offer what the StreamX repositories
 * publish: those carry a small download badge, and installed ones a tick.
 * Offline, the repository lists are simply absent.
 */
@Composable
internal fun StartChooser(
    installed: List<ApkExtension>,
    selectedSource: String,
    onSelectSource: (String) -> Unit,
    onInstallPlugin: () -> Unit,
    onInstallApk: (Uri) -> Unit,
    installStatus: String?,
    serverSummary: String,
    onServer: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val downloads = remember { StartDownloads() }
    val plugins = rememberRepositoryCatalogue("plugin")
    val skinCatalogue = rememberRepositoryCatalogue("skin")
    // Android's package list, not only StreamX's extension scan: CloudStream, for
    // one, is tracked apart from it. Rechecked whenever a scan runs, which the
    // start page triggers on resume, so returning from the installer updates it.
    val packageRevision by ApkExtensions.packageRevision.collectAsStateWithLifecycle()
    val installedPackages = remember(installed, plugins, skinCatalogue, packageRevision) {
        installed.map { it.packageName }.toSet() + (plugins + skinCatalogue).map { it.packageName }.filter { name ->
            runCatching { context.packageManager.getPackageInfo(name, 0) }.isSuccess
        }
    }
    val cloudVersion by ApkExtensions.cloudStreamVersion.collectAsStateWithLifecycle()
    val sourcePackages = installed.filter { it.sourceAuthority != null }.map { it.packageName }.toSet()

    fun download(entry: RepositoryExtension) {
        if (downloads.packageName != null) return
        downloads.packageName = entry.packageName
        downloads.progress = 0
        downloads.error = null
        scope.launch {
            try {
                val apk = ExtensionRepository.download(context, entry) { downloads.progress = it }
                onInstallApk(FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk))
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { downloads.error = failure.message ?: "Could not download ${entry.name}" }
            finally { downloads.packageName = null }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        StartSectionLabel(stringResource(R.string.start_mode_listen_section))
        val tiles = buildList<@Composable (Modifier) -> Unit> {
            add { modifier ->
                SourceTile(
                    title = stringResource(R.string.start_mode_youtube_music),
                    subtitle = stringResource(R.string.start_mode_youtube_music_subtitle),
                    selected = selectedSource == StartYouTubeSource,
                    badge = TileBadge.NONE,
                    onClick = { onSelectSource(StartYouTubeSource) },
                    modifier = modifier,
                ) {
                    Box(Modifier.size(40.dp).background(Color(0xFF222224), CircleShape), contentAlignment = Alignment.Center) {
                        Icon(painterResource(R.drawable.ic_youtube), null, Modifier.size(22.dp), tint = Color.White)
                    }
                }
            }
            installed.filter { it.sourceAuthority != null }.forEach { extension ->
                add { modifier ->
                    SourceTile(
                        title = extensionDisplayName(extension.name, false),
                        subtitle = stringResource(R.string.start_mode_installed),
                        selected = selectedSource == extension.packageName,
                        badge = TileBadge.INSTALLED,
                        onClick = { onSelectSource(extension.packageName) },
                        modifier = modifier,
                    ) { ExtensionLogo(extension.packageName, extension.versionCode, Modifier.size(40.dp)) }
                }
            }
            // CloudStream uses its signed engine protocol and is discovered separately.
            if (cloudVersion != null) add { modifier ->
                SourceTile(
                    title = "CloudStream",
                    subtitle = stringResource(R.string.start_mode_installed),
                    selected = selectedSource == CloudStream.PACKAGE,
                    badge = TileBadge.INSTALLED,
                    onClick = { onSelectSource(CloudStream.PACKAGE) },
                    modifier = modifier,
                ) { ExtensionLogo(CloudStream.PACKAGE, cloudVersion ?: 0L, Modifier.size(40.dp)) }
            }
            plugins.filter { it.packageName != CloudStream.PACKAGE && it.packageName in installedPackages && it.packageName !in sourcePackages }.forEach { entry ->
                add { modifier ->
                    SourceTile(
                        title = extensionDisplayName(entry.name, false),
                        subtitle = stringResource(R.string.start_mode_installed_elsewhere),
                        selected = false,
                        badge = TileBadge.INSTALLED,
                        onClick = {},
                        modifier = modifier,
                    ) { ExtensionLogo(entry.packageName, entry.versionCode, Modifier.size(40.dp)) }
                }
            }
            plugins.filter { it.packageName !in installedPackages && (it.packageName != CloudStream.PACKAGE || cloudVersion == null) }.forEach { entry ->
                add { modifier ->
                    val busy = downloads.packageName == entry.packageName
                    SourceTile(
                        title = extensionDisplayName(entry.name, false),
                        subtitle = if (busy) stringResource(R.string.start_mode_downloading, downloads.progress)
                            else stringResource(R.string.start_mode_tap_to_download, android.text.format.Formatter.formatShortFileSize(context, entry.size)),
                        selected = false,
                        badge = if (busy) TileBadge.DOWNLOADING else TileBadge.DOWNLOAD,
                        progress = downloads.progress,
                        onClick = { download(entry) },
                        modifier = modifier,
                    ) { ExtensionLogo(entry.packageName, 0, Modifier.size(40.dp)) }
                }
            }
            add { modifier ->
                SourceTile(
                    title = stringResource(R.string.start_mode_add_plugin),
                    subtitle = stringResource(R.string.start_mode_add_plugin_subtitle),
                    selected = false,
                    badge = TileBadge.NONE,
                    dashed = true,
                    onClick = onInstallPlugin,
                    modifier = modifier,
                ) {
                    Box(Modifier.size(40.dp).background(Color(0xFF222224), CircleShape), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Add, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onBackground)
                    }
                }
            }
        }
        tiles.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                pair.forEach { tile -> tile(Modifier.weight(1f)) }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        Text(
            stringResource(R.string.start_mode_listen_footer),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        (downloads.error ?: installStatus)?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 4.dp))
        }

        val installedSkins = installed.filter { it.skinAsset != null }
        val availableSkins = skinCatalogue.filter { it.packageName !in installedPackages }
        Spacer(Modifier.height(8.dp))
        StartSkinStrip(installedSkins, availableSkins, downloads, ::download)

        Spacer(Modifier.height(8.dp))
        StartSectionLabel(stringResource(R.string.start_mode_more_section))
        GroupedCard {
            StartOptionRow(
                title = stringResource(R.string.start_mode_server_title),
                subtitle = serverSummary,
                icon = {
                    Icon(painterResource(R.drawable.ic_logo), null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onBackground)
                },
                onClick = onServer,
            )
        }
    }
}

/** The repositories' entries of [type] for this device, or empty until loaded (or offline). */
@Composable
private fun rememberRepositoryCatalogue(type: String): List<RepositoryExtension> {
    val context = LocalContext.current
    val links by remember(context) { ExtensionRepositoryLinks.observe(context) }.collectAsStateWithLifecycle()
    var entries by remember(type) { mutableStateOf<List<RepositoryExtension>>(emptyList()) }
    LaunchedEffect(type, links) {
        entries = try { ExtensionRepository.loadCatalogue(context, type, links).entries }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { emptyList() }
    }
    return entries
}

private enum class TileBadge { NONE, INSTALLED, DOWNLOAD, DOWNLOADING }

@Composable
private fun StartSectionLabel(text: String, trailing: String? = null) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        trailing?.let {
            Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Selected outranks installed: a filled check, where installed is a quiet one. */
@Composable
private fun Badge(badge: TileBadge, selected: Boolean, progress: Int, size: Int = 22) {
    val ink = MaterialTheme.colorScheme.onBackground
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    when {
        selected -> Box(Modifier.size(size.dp).background(ink, CircleShape), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Check, null, Modifier.size((size * 0.64f).dp), tint = MaterialTheme.colorScheme.background)
        }
        badge == TileBadge.INSTALLED -> Box(
            Modifier.size(size.dp).background(MaterialTheme.colorScheme.background, CircleShape).border(1.dp, muted.copy(alpha = 0.5f), CircleShape),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Rounded.Check, null, Modifier.size((size * 0.6f).dp), tint = ink) }
        badge == TileBadge.DOWNLOAD -> Box(
            Modifier.size(size.dp).background(MaterialTheme.colorScheme.background, CircleShape).border(1.dp, muted.copy(alpha = 0.5f), CircleShape),
            contentAlignment = Alignment.Center,
        ) { Icon(painterResource(R.drawable.ic_cloud_download), null, Modifier.size((size * 0.6f).dp), tint = ink) }
        badge == TileBadge.DOWNLOADING -> CircularProgressIndicator(
            progress = { progress / 100f }, modifier = Modifier.size(size.dp), strokeWidth = 2.dp, color = ink,
        )
        else -> Unit
    }
}

@Composable
private fun SourceTile(
    title: String,
    subtitle: String,
    selected: Boolean,
    badge: TileBadge,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    progress: Int = 0,
    dashed: Boolean = false,
    icon: @Composable () -> Unit,
) {
    val outline = MaterialTheme.colorScheme.outline
    val available = badge == TileBadge.DOWNLOAD || badge == TileBadge.DOWNLOADING
    Column(
        modifier = modifier
            .height(124.dp)
            .clip(TileShape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (dashed || available) 0.14f else 0.35f))
            .border(
                when {
                    selected -> BorderStroke(2.dp, MaterialTheme.colorScheme.onBackground)
                    dashed -> BorderStroke(1.dp, outline.copy(alpha = 0.4f))
                    else -> BorderStroke(1.dp, Color.Transparent)
                },
                TileShape,
            )
            .clickable(role = if (available || dashed) Role.Button else Role.RadioButton, onClick = onClick)
            .semantics { this.selected = selected }
            .padding(16.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) { icon() }
            Spacer(Modifier.weight(1f))
            Badge(badge, selected, progress)
        }
        Spacer(Modifier.weight(1f))
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * Skins as a strip of icons: installed ones apply on tap, repository ones
 * download on tap (and can be applied once Android has installed them).
 */
@Composable
private fun StartSkinStrip(
    installed: List<ApkExtension>,
    available: List<RepositoryExtension>,
    downloads: StartDownloads,
    onDownload: (RepositoryExtension) -> Unit,
) {
    val active by SkinRegistry.active.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var applying by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val sorted = remember(installed) { installed.sortedBy { it.name.lowercase() } }
    val defaultId = SkinRegistry.builtIns.first().id
    val activeName = if (active.id == defaultId) stringResource(R.string.start_mode_default_skin)
        else sorted.firstOrNull { it.skinId == active.id }?.let { extensionDisplayName(it.name, true) } ?: active.name

    fun apply(extension: ApkExtension) {
        if (applying != null || active.id == extension.skinId) return
        applying = extension.packageName
        error = null
        scope.launch {
            try { ApkExtensions.activateSkin(extension) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { error = failure.message ?: "Could not apply this skin" }
            finally { applying = null }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        StartSectionLabel(stringResource(R.string.start_mode_skins_section), trailing = activeName)
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            // Sized so a fifth icon is cut by the edge: the strip reads as scrollable.
            contentPadding = PaddingValues(horizontal = 4.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            item(key = defaultId) {
                SkinChip(
                    name = stringResource(R.string.start_mode_default_skin),
                    applied = active.id == defaultId,
                    badge = TileBadge.NONE,
                    onClick = { if (applying == null) { error = null; SkinRegistry.select(defaultId) } },
                ) {
                    Box(Modifier.size(60.dp).background(Color(0xFF222224), CircleShape), contentAlignment = Alignment.Center) {
                        Icon(painterResource(R.drawable.ic_logo), null, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.onBackground)
                    }
                }
            }
            items(sorted, key = { it.packageName }) { extension ->
                SkinChip(
                    name = extensionDisplayName(extension.name, true),
                    applied = active.id == extension.skinId,
                    badge = TileBadge.INSTALLED,
                    busy = applying == extension.packageName,
                    onClick = { apply(extension) },
                ) { ExtensionLogo(extension.packageName, extension.versionCode, Modifier.size(60.dp).clip(CircleShape)) }
            }
            items(available, key = { "repo-" + it.packageName }) { entry ->
                val downloading = downloads.packageName == entry.packageName
                SkinChip(
                    name = extensionDisplayName(entry.name, true),
                    applied = false,
                    badge = if (downloading) TileBadge.DOWNLOADING else TileBadge.DOWNLOAD,
                    progress = downloads.progress,
                    dimmed = true,
                    onClick = { onDownload(entry) },
                ) { ExtensionLogo(entry.packageName, 0, Modifier.size(60.dp).clip(CircleShape)) }
            }
        }
        error?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 4.dp))
        }
    }
}

@Composable
private fun SkinChip(
    name: String,
    applied: Boolean,
    badge: TileBadge,
    onClick: () -> Unit,
    busy: Boolean = false,
    progress: Int = 0,
    dimmed: Boolean = false,
    icon: @Composable () -> Unit,
) {
    val appliedLabel = stringResource(R.string.extensions_skin_applied)
    Column(
        modifier = Modifier
            .width(74.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(role = if (dimmed) Role.Button else Role.RadioButton, onClick = onClick)
            .semantics { selected = applied; if (applied) contentDescription = "$name, $appliedLabel" }
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(68.dp)) {
            Box(
                Modifier
                    .size(68.dp)
                    .border(BorderStroke(2.5.dp, if (applied) MaterialTheme.colorScheme.onBackground else Color.Transparent), CircleShape)
                    .padding(4.dp)
                    .clip(CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                icon()
                // Not installed yet: shown, but quieter than what can be applied now.
                if (dimmed) Box(Modifier.size(60.dp).background(Color.Black.copy(alpha = 0.35f), CircleShape))
                if (busy) Box(Modifier.size(60.dp).background(Color.Black.copy(alpha = 0.45f), CircleShape), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = Color.White)
                }
            }
            // Corner badge, overlapping the ring like a status dot.
            Box(Modifier.align(Alignment.BottomEnd).offset(x = 2.dp, y = 2.dp)) {
                Badge(badge, selected = applied, progress = progress, size = 22)
            }
        }
        Text(
            name, style = MaterialTheme.typography.bodySmall,
            color = if (applied) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (applied) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
        )
    }
}
