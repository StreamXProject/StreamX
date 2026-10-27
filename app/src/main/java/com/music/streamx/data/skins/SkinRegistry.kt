package com.music.streamx.data.skins

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlin.math.pow
import com.music.streamx.ui.skins.SkinLayout

@Serializable
data class SkinPalette(
    val background: String = "#000000",
    val surface: String = "#0D0D0F",
    val surfaceVariant: String = "#1C1C1E",
    val text: String = "#FFFFFF",
    val secondary: String = "#8E8E93",
    val accent: String = "#FFFFFF",
)
@Serializable
data class SkinFonts(val regular: String, val bold: String) {
    fun validate() {
        listOf(regular, bold).forEach {
            require(Regex("streamx/fonts/[a-zA-Z0-9_-]+\\.(otf|ttf)").matches(it)) { "Fonts must be OTF/TTF assets under streamx/fonts/" }
        }
    }
}

@Serializable
data class SkinRendererSpec(val entryPoint: String, val api: Int = 1) {
    fun validate() {
        require(api == com.music.streamx.ui.skins.SKIN_UI_API_VERSION) { "Skin UI API is not supported" }
        require(Regex("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)+").matches(entryPoint)) { "Invalid renderer class" }
    }
}

@Serializable
data class SkinSpec(
    val version: Int = 1,
    val id: String,
    val name: String,
    val author: String = "",
    val dark: SkinPalette = SkinPalette(),
    val light: SkinPalette? = null,
    val cornerRadiusDp: Int? = null,
    val layout: SkinLayout = SkinLayout(),
    val fonts: SkinFonts? = null,
    val renderer: SkinRendererSpec? = null,
    val extensionPackage: String? = null,
    val extensionCertificate: String? = null,
    val extensionVersion: Long? = null,
)

enum class SkinSection(val title: String) {
    HOME("Home screen"), PLAYER("Full player and lyrics"), MINI_PLAYER("Bottom player"), SETTINGS("Settings")
}

/** Resolves missing/uninstalled overrides to the main skin. */
internal fun resolveSectionSkin(main: SkinSpec, installed: List<SkinSpec>, overrides: Map<SkinSection, String>, section: SkinSection): SkinSpec =
    installed.firstOrNull { it.id == overrides[section] } ?: main

/** Appearance registry. Version 3 can select an APK-owned renderer through the versioned UI ABI. */
object SkinRegistry {
    private lateinit var prefs: SharedPreferences
    private val json = Json { ignoreUnknownKeys = false; encodeDefaults = true }
    // The app's base appearance is the only built-in; custom skins are supplied by APKs.
    val builtIns = listOf(SkinSpec(id = "default", name = "StreamX"))
    val skins = MutableStateFlow(builtIns)
    val active = MutableStateFlow(builtIns.first())
    val sectionOverrides = MutableStateFlow<Map<SkinSection, String>>(emptyMap())
    @Volatile var runtimeAppearance: Triple<String, Boolean, SkinPalette>? = null
    val runtimePalettes = java.util.concurrent.ConcurrentHashMap<Pair<String, Boolean>, SkinPalette>()

    fun init(context: Context) {
        prefs = context.getSharedPreferences("streamx_skins", Context.MODE_PRIVATE)
        val custom = runCatching {
            json.decodeFromString(ListSerializer(SkinSpec.serializer()), prefs.getString("installed", null) ?: "[]")
        }.getOrDefault(emptyList()).filter { spec ->
            spec.extensionPackage != null && builtIns.none { it.id == spec.id } && runCatching { validate(spec) }.isSuccess
        }.distinctBy { it.id }
        skins.value = builtIns + custom
        sectionOverrides.value = SkinSection.entries.mapNotNull { section ->
            prefs.getString("section.${section.name}", null)?.takeIf { id -> skins.value.any { it.id == id } }?.let { section to it }
        }.toMap()
        active.value = skins.value.firstOrNull { it.id == prefs.getString("active", "default") } ?: builtIns.first()
    }

    @Synchronized
    fun selectSection(section: SkinSection, id: String?) {
        require(id == null || skins.value.any { it.id == id }) { "Skin is not installed" }
        prefs.edit().apply { if(id == null) remove("section.${section.name}") else putString("section.${section.name}", id) }.apply()
        sectionOverrides.value = sectionOverrides.value.toMutableMap().apply { if(id == null) remove(section) else put(section,id) }
    }
    @Synchronized
    fun resetSections() { SkinSection.entries.forEach { selectSection(it,null) } }
    @Synchronized
    fun select(id: String) {
        val selected = skins.value.firstOrNull { it.id == id } ?: return
        prefs.edit().putString("active", id).apply()
        active.value = selected
    }
    @Synchronized
    fun installSpec(skin: SkinSpec, activate: Boolean = true): SkinSpec {
        validate(skin)
        require(builtIns.none { it.id == skin.id }) { "Built-in skins cannot be replaced." }
        val next = skins.value.filterNot { it.id == skin.id } + skin
        prefs.edit().putString("installed", json.encodeToString(ListSerializer(SkinSpec.serializer()), next.filter { it.id !in builtIns.map(SkinSpec::id) }))
            .apply { if(activate) putString("active", skin.id) }.apply()
        skins.value = next
        if(activate) active.value = skin
        return skin
    }
    @Synchronized
    fun remove(id: String) {
        require(builtIns.none { it.id == id }) { "Built-in skins cannot be removed." }
        sectionOverrides.value.filterValues { it == id }.keys.forEach { selectSection(it,null) }
        val next = skins.value.filterNot { it.id == id }
        val selected = if (active.value.id == id) builtIns.first() else active.value
        prefs.edit().putString("installed", json.encodeToString(ListSerializer(SkinSpec.serializer()), next.filter { it.id !in builtIns.map(SkinSpec::id) }))
            .putString("active", selected.id).apply()
        skins.value = next
        active.value = selected
    }

    internal fun validate(skin: SkinSpec) {
        require(skin.version in 1..3) { "Unsupported skin format version." }
        require(skin.version >= 2 || (skin.layout == SkinLayout() && skin.fonts == null)) { "Layouts and fonts require skin version 2." }
        skin.renderer?.validate()
        require(skin.renderer == null || skin.version >= 3) { "Executable renderers require skin version 3" }
        require(skin.renderer != null || (skin.layout.icons != com.music.streamx.ui.skins.SkinIconPack.EXTENSION && skin.layout.player != com.music.streamx.ui.skins.PlayerLayout.EXTENSION)) { "Extension layouts require an APK renderer" }
        skin.layout.validate()
        skin.fonts?.validate()
        require(Regex("[a-z][a-z0-9._-]{0,63}").matches(skin.id)) { "Skin id must contain lowercase letters, numbers, dots, underscores, or hyphens." }
        require(skin.name.isNotBlank() && skin.name.length <= 48 && skin.author.length <= 64) { "Invalid skin name or author." }
        require(skin.cornerRadiusDp == null || skin.cornerRadiusDp in 0..32) { "Corner radius must be between 0 and 32 dp." }
        listOfNotNull(skin.dark, skin.light).forEach { palette ->
            val values = listOf(palette.background, palette.surface, palette.surfaceVariant, palette.text, palette.secondary, palette.accent)
            require(values.all { Regex("#[0-9A-Fa-f]{6}").matches(it) }) { "Skin colors must use #RRGGBB." }
            listOf(palette.background, palette.surface, palette.surfaceVariant).forEach { background ->
                require(contrast(palette.text, background) >= 4.5 && contrast(palette.secondary, background) >= 4.5) {
                    "Skin text colors need enough contrast against all surfaces."
                }
            }
        }
        require(contrast(skin.dark.background, "#FFFFFF") >= 4.5) { "Dark skin backgrounds must work with light system icons." }
        skin.light?.let { require(contrast(it.background, "#000000") >= 4.5) { "Light skin backgrounds must work with dark system icons." } }
    }
    internal fun contrast(first: String, second: String): Double {
        fun luminance(value: String): Double {
            val rgb = value.removePrefix("#").toInt(16)
            fun linear(channel: Int): Double {
                val s = channel / 255.0
                return if (s <= 0.04045) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
            }
            return linear((rgb shr 16) and 255) * .2126 + linear((rgb shr 8) and 255) * .7152 + linear(rgb and 255) * .0722
        }
        val a = luminance(first); val b = luminance(second)
        return (maxOf(a, b) + .05) / (minOf(a, b) + .05)
    }
}
