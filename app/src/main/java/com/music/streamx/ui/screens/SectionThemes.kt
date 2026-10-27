package com.music.streamx.ui.screens

import com.music.streamx.ui.skins.SkinIcon as Icon
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.streamx.data.extensions.ApkExtension
import com.music.streamx.data.extensions.ApkExtensions
import com.music.streamx.data.skins.SkinRegistry
import com.music.streamx.data.skins.SkinSection
import com.music.streamx.ui.components.BottomFadeScrim
import com.music.streamx.ui.components.FrostedTopBar
import com.music.streamx.ui.components.topBarContentPadding
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private val SectionCard = RoundedCornerShape(22.dp)

/**
 * Per-section skins on one page: each section shows its options as a strip,
 * tap to choose. No dialog in between, and every choice is visible at once.
 */
@Composable
internal fun SectionThemesScreen(skins: List<ApkExtension>, onBack: () -> Unit) {
    val overrides by SkinRegistry.sectionOverrides.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf<Pair<SkinSection, String>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val sorted = remember(skins) { skins.sortedBy { it.name.lowercase() } }
    val list = rememberLazyListState()

    fun choose(section: SkinSection, extension: ApkExtension) {
        if (busy != null) return
        busy = section to extension.skinId
        error = null
        scope.launch {
            try {
                ApkExtensions.activateSkin(extension, activate = false)
                SkinRegistry.selectSection(section, extension.skinId)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { error = failure.message ?: "Could not load this skin" }
            finally { busy = null }
        }
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize()) {
            LazyColumn(
                state = list,
                modifier = Modifier.fillMaxSize().navigationBarsPadding(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = topBarContentPadding(), bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item {
                    Column(Modifier.padding(horizontal = 4.dp)) {
                        Text("Customize sections", style = MaterialTheme.typography.displayLarge, modifier = Modifier.padding(top = 8.dp, bottom = 8.dp))
                        Text("Give a part of the app its own skin. Everything else follows your main skin.",
                            style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                items(SkinSection.entries, key = { it.name }) { section ->
                    val chosen = overrides[section]
                    val chosenName = when (chosen) {
                        null -> "Main skin"
                        "default" -> "StreamX"
                        else -> sorted.firstOrNull { it.skinId == chosen }?.let { extensionDisplayName(it.name, true) } ?: "Main skin"
                    }
                    Column(
                        Modifier.fillMaxWidth().clip(SectionCard)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                            .padding(vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp), verticalAlignment = Alignment.Bottom) {
                            Column(Modifier.weight(1f)) {
                                Text(section.label, style = MaterialTheme.typography.titleMedium)
                                Text(section.hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(chosenName, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 14.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            item(key = "main") {
                                SectionChip("Main skin", chosen == null, false, { if (busy == null) SkinRegistry.selectSection(section, null) }) {
                                    Icon(Icons.Rounded.Link, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onBackground)
                                }
                            }
                            item(key = "default") {
                                SectionChip("StreamX", chosen == "default", false, { if (busy == null) SkinRegistry.selectSection(section, "default") }) {
                                    ExtensionLogo(LocalContext.current.packageName, 0, Modifier.size(52.dp).clip(CircleShape))
                                }
                            }
                            items(sorted, key = { it.packageName }) { extension ->
                                SectionChip(
                                    extensionDisplayName(extension.name, true),
                                    chosen == extension.skinId,
                                    busy == section to extension.skinId,
                                    { choose(section, extension) },
                                ) { ExtensionLogo(extension.packageName, extension.versionCode, Modifier.size(52.dp).clip(CircleShape)) }
                            }
                        }
                    }
                }
                item {
                    Column(Modifier.padding(horizontal = 4.dp)) {
                        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
                        if (overrides.isNotEmpty()) TextButton(onClick = { SkinRegistry.resetSections() }) { Text("Use main skin everywhere") }
                    }
                }
            }
            val topFadeAlpha by remember { derivedStateOf {
                if (list.firstVisibleItemIndex > 0) 1f else {
                    val titleHeight = list.layoutInfo.visibleItemsInfo.firstOrNull { it.index == 0 }?.size?.toFloat() ?: 1f
                    ((list.firstVisibleItemScrollOffset - titleHeight * .4f) / (titleHeight * .6f)).coerceIn(0f, 1f)
                }
            } }
            BottomFadeScrim(modifier = Modifier.rotate(180f).graphicsLayer { alpha = topFadeAlpha })
            FrostedTopBar(title = "Customize sections", scrolled = false, onBack = onBack, transparentBackdrop = true, artworkPageChrome = true)
        }
    }
}

private val SkinSection.label: String get() = when (this) {
    SkinSection.HOME -> "Home"
    SkinSection.PLAYER -> "Full player"
    SkinSection.MINI_PLAYER -> "Mini player"
    SkinSection.SETTINGS -> "Settings"
}

private val SkinSection.hint: String get() = when (this) {
    SkinSection.HOME -> "Browsing and navigation"
    SkinSection.PLAYER -> "Including lyrics and queue"
    SkinSection.MINI_PLAYER -> "The bar above the tabs"
    SkinSection.SETTINGS -> "Settings and sources"
}

@Composable
private fun SectionChip(name: String, chosen: Boolean, busy: Boolean, onClick: () -> Unit, icon: @Composable () -> Unit) {
    Column(
        Modifier.width(70.dp).clip(RoundedCornerShape(14.dp))
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics { selected = chosen }
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            Modifier.size(60.dp)
                .border(BorderStroke(2.5.dp, if (chosen) MaterialTheme.colorScheme.onBackground else Color.Transparent), CircleShape)
                .padding(4.dp).clip(CircleShape).background(Color(0xFF222224)),
            contentAlignment = Alignment.Center,
        ) {
            icon()
            if (busy) Box(Modifier.size(52.dp).background(Color.Black.copy(alpha = 0.45f), CircleShape), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
            }
        }
        Text(name, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
            fontWeight = if (chosen) FontWeight.Bold else FontWeight.Normal,
            color = if (chosen) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
