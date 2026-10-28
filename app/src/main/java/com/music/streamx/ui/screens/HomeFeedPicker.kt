package com.music.streamx.ui.screens

import com.music.streamx.ui.skins.SkinIcon as Icon
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.streamx.data.extensions.ApkExtension
import com.music.streamx.data.sources.SourceConfig
import com.music.streamx.data.sources.SourceKind
import com.music.streamx.data.sources.SourceRegistry
import com.music.streamx.data.streamx.StreamXSession

/**
 * What fills Home: a row of source icons, tap one or more. The first ticked leads Home,
 * each further one is its own labelled section. Separate from what plays —
 * that is the playback order below.
 */
@Suppress("UNUSED_VARIABLE")
@Composable
internal fun HomeFeedPicker(
    configs: List<SourceConfig>,
    installedExtensions: List<ApkExtension>,
    cloudStreamInstalled: Boolean,
    onOpenStreamXSetup: () -> Unit,
    onOpenCloudStream: () -> Unit,
) {
    // Collected so the ticks recompose when any part of the Home choice changes;
    // the answers themselves come from SourceRegistry.
    val catalogue by SourceRegistry.catalogue.collectAsStateWithLifecycle()
    val selectedExtensionId by SourceRegistry.selectedExtensionId.collectAsStateWithLifecycle()
    val extraFeedIds by SourceRegistry.extraFeedIds.collectAsStateWithLifecycle()
    val multiFeed by SourceRegistry.multiFeed.collectAsStateWithLifecycle()
    val session by StreamXSession.state.collectAsStateWithLifecycle()
    val revision = listOf(catalogue, selectedExtensionId, extraFeedIds, multiFeed)

    val rows = configs.filter { config ->
        when (config.kind) {
            SourceKind.STREAMX, SourceKind.YOUTUBE -> true
            SourceKind.CLOUDSTREAM -> cloudStreamInstalled
            SourceKind.APK_PLUGIN -> SourceRegistry.feedEligible(config) &&
                (com.music.streamx.data.extensions.SflxBridge.handles(config) ||
                    installedExtensions.any { it.packageName == config.extensionPackage && it.sourceAuthority != null })
            else -> false
        }
    }.distinctBy { if (it.kind == SourceKind.APK_PLUGIN) it.id else it.kind.name }

    SettingsGroup(
        header = "Home",
        footer = "Tap to add or remove. The first leads Home; each other one gets its own section below it. What plays is set by the playback order.",
    ) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            rows.forEach { config ->
                val onHome = SourceRegistry.isOnHome(config)
                val lead = onHome && SourceRegistry.isMainFeed(config)
                val needsSetup = when (config.kind) {
                    SourceKind.STREAMX -> session.token.isBlank()
                    SourceKind.CLOUDSTREAM -> !config.isComplete
                    else -> false
                }
                val name = when (config.kind) {
                    SourceKind.YOUTUBE -> "YouTube Music"
                    SourceKind.STREAMX -> "StreamX"
                    else -> config.displayName
                }
                val status = when {
                    needsSetup -> "Set up"
                    lead -> "Leads"
                    onHome -> "Section"
                    else -> ""
                }
                val primary = MaterialTheme.colorScheme.primary
                Column(
                    Modifier.width(84.dp).clip(RoundedCornerShape(16.dp)).clickable(onClickLabel = name) {
                        when {
                            needsSetup && config.kind == SourceKind.STREAMX -> onOpenStreamXSetup()
                            needsSetup -> onOpenCloudStream()
                            else -> SourceRegistry.toggleHomeFeed(config.id)
                        }
                    }.padding(vertical = 6.dp).semantics { stateDescription = status.ifBlank { "Not on Home" } },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(contentAlignment = Alignment.TopEnd) {
                        Box(
                            Modifier.size(64.dp).clip(RoundedCornerShape(18.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .border(if (onHome) 2.dp else 0.dp, if (onHome) primary else Color.Transparent, RoundedCornerShape(18.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            SourceGlyph(config, Modifier.size(36.dp).alpha(if (onHome || needsSetup) 1f else .7f))
                        }
                        if (onHome) Box(
                            Modifier.offset(x = 4.dp, y = (-4).dp).size(22.dp).clip(CircleShape).background(primary),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Rounded.Check, contentDescription = null, Modifier.size(15.dp),
                                tint = MaterialTheme.colorScheme.onPrimary)
                        }
                    }
                    Text(name, Modifier.padding(top = 8.dp), style = MaterialTheme.typography.labelMedium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                        color = if (onHome) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(status.ifBlank { " " }, style = MaterialTheme.typography.labelSmall,
                        color = if (needsSetup) MaterialTheme.colorScheme.onSurfaceVariant else primary, maxLines = 1)
                }
            }
        }
    }
}
