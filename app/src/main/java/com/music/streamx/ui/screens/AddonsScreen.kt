package com.music.streamx.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.rememberAsyncImagePainter
import com.music.streamx.data.sources.SourceKind
import com.music.streamx.data.sources.SourceRegistry
import com.music.streamx.data.sources.module.AddonCatalogue
import com.music.streamx.data.sources.module.AddonEntry
import java.util.Locale

/** The order categories are listed in; anything else a registry invents follows. */
private val CATEGORY_ORDER = listOf("download", "metadata", "lyrics", "utility", "integration")

private fun categoryTitle(category: String) = when (category) {
    "download" -> "Download providers"
    "metadata" -> "Metadata"
    "lyrics" -> "Lyrics"
    "utility" -> "Utilities"
    "integration" -> "Integrations"
    else -> category.replaceFirstChar { it.titlecase(Locale.ROOT) }
}

/**
 * Every extension the listener's registries offer — SpotiFLAC registries and
 * module indexes alike — searchable and grouped by what it does, with the ones
 * StreamX can play from switchable on and off.
 */
@Composable
internal fun AddonsContent(contentPadding: PaddingValues, header: @Composable () -> Unit) {
    val configs by SourceRegistry.configs.collectAsStateWithLifecycle() // also redraws catalogue switches
    val disabled by AddonCatalogue.disabled.collectAsStateWithLifecycle()
    val registries = configs.filter { (it.kind == SourceKind.CUSTOM_MODULE || it.kind == SourceKind.MODULE) && it.baseUrl.isNotBlank() }
    var entries by remember { mutableStateOf<List<AddonEntry>?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(registries.map { it.baseUrl }) {
        entries = null
        entries = AddonCatalogue.load()
    }

    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(contentPadding)) {
        header()
        if (registries.isEmpty()) {
            SettingsGroup(header = "Addons", footer = "Add a SpotiFLAC registry or a module index under Sources → Add an addon, and everything it offers is listed here.") {
                SettingsRow(icon = Icons.Rounded.Extension, title = "No registries yet",
                    subtitle = "Extensions appear once a registry is added")
            }
            return@Column
        }
        val all = entries
        if (all == null) {
            LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp))
            return@Column
        }

        OutlinedTextField(
            value = query, onValueChange = { query = it }, singleLine = true,
            placeholder = { Text("Search ${all.size} extensions") },
            leadingIcon = { Icon(Icons.Rounded.Search, null) },
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        )
        val categories = all.map { it.category }.distinct()
            .sortedBy { CATEGORY_ORDER.indexOf(it).takeIf { i -> i >= 0 } ?: CATEGORY_ORDER.size }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(selected = category == null, onClick = { category = null }, label = { Text("All") })
            categories.forEach { c ->
                FilterChip(selected = category == c, onClick = { category = if (category == c) null else c },
                    label = { Text("${categoryTitle(c)} · ${all.count { it.category == c }}") })
            }
        }

        val needle = query.trim().lowercase(Locale.ROOT)
        val shown = all.filter { entry ->
            (category == null || entry.category == category) && (needle.isEmpty() ||
                entry.name.lowercase(Locale.ROOT).contains(needle) ||
                entry.description.lowercase(Locale.ROOT).contains(needle) ||
                entry.tags.any { it.lowercase(Locale.ROOT).contains(needle) })
        }
        if (shown.isEmpty()) {
            Text(if (all.isEmpty()) "These registries answered with no extensions." else "Nothing matches “$query”.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(20.dp))
        }
        shown.groupBy { it.category }.toList()
            .sortedBy { (c, _) -> CATEGORY_ORDER.indexOf(c).takeIf { it >= 0 } ?: CATEGORY_ORDER.size }
            .forEach { (c, group) ->
                SettingsGroup(
                    header = categoryTitle(c),
                    footer = if (c == "download") "Switched-on providers are searched when a source plays a track."
                        else "Switch one on to use it for Home and search. Pick it under Sources → Home; its tracks play through your playback order.",
                ) {
                    group.forEachIndexed { index, entry ->
                        if (index > 0) RowDivider()
                        // A provider StreamX streams from is switched as a source; a feed or
                        // search extension is switched in as a catalogue for Home and search.
                        val catalogueOn = entry.catalogue && AddonCatalogue.added(entry) != null
                        val usable = entry.plays || entry.catalogue
                        val on = if (entry.plays) entry.key !in disabled else catalogueOn
                        val toggle: (Boolean) -> Unit = { value ->
                            if (entry.plays) AddonCatalogue.setEnabled(entry.key, value) else AddonCatalogue.setCatalogue(entry, value)
                        }
                        SettingsRow(
                            icon = if (entry.icon == null) Icons.Rounded.Extension else null,
                            iconPainter = entry.icon?.let { rememberAsyncImagePainter(it) },
                            title = entry.name,
                            subtitle = listOfNotNull(
                                entry.version.takeIf { it.isNotBlank() }?.let { "v$it" },
                                entry.description.takeIf { it.isNotBlank() },
                                entry.registry.takeIf { registries.size > 1 },
                            ).joinToString(" · ").ifBlank { null },
                            badge = if (usable) entry.capabilities.joinToString(" · ").ifBlank { null } else "Not supported yet",
                            enabled = usable,
                            onClick = if (usable) ({ toggle(!on) }) else null,
                            trailing = if (usable) ({
                                com.music.streamx.ui.components.SkinSwitch(checked = on, onCheckedChange = toggle)
                            }) else null,
                        )
                    }
                }
            }
    }
}
