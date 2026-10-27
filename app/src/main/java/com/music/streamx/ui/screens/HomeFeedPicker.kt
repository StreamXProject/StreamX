package com.music.streamx.ui.screens

import com.music.streamx.ui.skins.SkinIcon as Icon
import androidx.compose.foundation.layout.size
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
 * What fills Home: one list, tick one or more. The first ticked leads Home,
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
                installedExtensions.any { it.packageName == config.extensionPackage && it.sourceAuthority != null }
            else -> false
        }
    }.distinctBy { if (it.kind == SourceKind.APK_PLUGIN) it.id else it.kind.name }

    SettingsGroup(
        header = "Home",
        footer = "Tick one or more. The first leads Home; each other one gets its own section below it. What plays is set by the playback order.",
    ) {
        rows.forEachIndexed { index, config ->
            if (index > 0) RowDivider()
            val onHome = SourceRegistry.isOnHome(config)
            val lead = onHome && SourceRegistry.isMainFeed(config)
            val needsSetup = when (config.kind) {
                SourceKind.STREAMX -> session.token.isBlank()
                SourceKind.CLOUDSTREAM -> !config.isComplete
                else -> false
            }
            SettingsRow(
                title = when (config.kind) {
                    SourceKind.YOUTUBE -> "YouTube Music"
                    SourceKind.STREAMX -> "StreamX"
                    else -> config.displayName
                },
                subtitle = when {
                    needsSetup -> "Set up to use"
                    lead -> "Leads Home"
                    onHome -> "Own section"
                    else -> null
                },
                onClick = {
                    when {
                        needsSetup && config.kind == SourceKind.STREAMX -> onOpenStreamXSetup()
                        needsSetup -> onOpenCloudStream()
                        else -> SourceRegistry.toggleHomeFeed(config.id)
                    }
                },
                trailing = {
                    if (onHome) Icon(Icons.Rounded.Check, contentDescription = "On Home", Modifier.size(22.dp),
                        tint = MaterialTheme.colorScheme.primary)
                },
            )
        }
    }
}
