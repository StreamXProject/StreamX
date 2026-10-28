package com.music.streamx.data.sources.module

import android.content.Context
import android.content.SharedPreferences
import com.music.streamx.data.Http
import com.music.streamx.data.sources.ModuleSource
import com.music.streamx.data.sources.SourceKind
import com.music.streamx.data.sources.SourceRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Request

/** One extension a registry offers, whether or not StreamX can use it. */
data class AddonEntry(
    /** The id [ModuleSource] knows it by; what [AddonCatalogue.setEnabled] takes. */
    val key: String,
    val name: String,
    val version: String,
    val description: String,
    /** The registry's own category: download, metadata, lyrics, utility or integration. */
    val category: String,
    val tags: List<String>,
    val icon: String?,
    val author: String,
    /** The source it came from, as the Sources list names it. */
    val registry: String,
    /** Whether StreamX can search and stream with it. */
    val plays: Boolean,
    /** The `.sflx` package, for adding a catalogue extension; blank for plain modules. */
    val packageUrl: String = "",
    val sha256: String = "",
) {
    /** The registry's own id, without the `sflx-` prefix modules use. */
    val extensionId: String get() = key.removePrefix("sflx-")

    /** What StreamX can use it for, from the tags registries publish (as Mornye reads them). */
    val capabilities: List<String> get() = buildList {
        if ("homefeed" in tags) add("Feed")
        if ("search" in tags || "metadata" in tags) add("Search")
        if ("lyrics" in tags || category == "lyrics") add("Lyrics")
        if (plays) add("Plays")
    }

    /** Whether it can be added as a catalogue: Home feed, search and pages. */
    val catalogue: Boolean get() = packageUrl.isNotBlank() && ("homefeed" in tags || "search" in tags || "metadata" in tags)
}

/**
 * Everything the module indexes and SpotiFLAC registries the listener added
 * have to offer, for the Addons page.
 *
 * [ModuleSource] only ever sees the extensions it can stream from, so without
 * this a registry's metadata, lyrics and utility extensions were invisible, and
 * so was which of its providers were actually in use. Switching one off here
 * keeps it out of searches without removing the registry.
 */
object AddonCatalogue {
    private lateinit var prefs: SharedPreferences
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val _disabled = MutableStateFlow<Set<String>>(emptySet())
    val disabled: StateFlow<Set<String>> = _disabled.asStateFlow()

    fun init(context: Context) {
        prefs = context.getSharedPreferences("streamx_addons", Context.MODE_PRIVATE)
        _disabled.value = prefs.getStringSet(KEY_DISABLED, emptySet()).orEmpty().toSet()
    }

    /** Whether [moduleId] may take part in searches. */
    fun allows(moduleId: String): Boolean = moduleId !in _disabled.value

    fun setEnabled(key: String, enabled: Boolean) {
        val next = if (enabled) _disabled.value - key else _disabled.value + key
        _disabled.value = next
        if (::prefs.isInitialized) prefs.edit().putStringSet(KEY_DISABLED, next).apply()
    }

    /** The catalogue source added for [entry], if it has been added. */
    fun added(entry: AddonEntry): com.music.streamx.data.sources.SourceConfig? =
        SourceRegistry.configs.value.firstOrNull { it.extensionPackage == com.music.streamx.data.extensions.SflxBridge.PREFIX + entry.extensionId }

    /** Adds [entry] as a catalogue (Home feed, search, pages), or removes it. */
    fun setCatalogue(entry: AddonEntry, on: Boolean) {
        val existing = added(entry)
        if (on && existing == null) SourceRegistry.add(com.music.streamx.data.extensions.SflxBridge.config(entry.extensionId, entry.name, entry.packageUrl, entry.sha256))
        else if (!on && existing != null) SourceRegistry.remove(existing.id)
    }

    /** Every registry the listener added, read again. Failures leave that registry out. */
    suspend fun load(): List<AddonEntry> = withContext(Dispatchers.IO) {
        SourceRegistry.configs.value
            .filter { (it.kind == SourceKind.CUSTOM_MODULE || it.kind == SourceKind.MODULE) && it.baseUrl.isNotBlank() }
            .flatMap { config -> runCatching { entries(config.id, config.baseUrl, config.displayName) }.getOrDefault(emptyList()) }
    }

    private suspend fun entries(configId: String, url: String, registry: String): List<AddonEntry> {
        val body = Http.client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            check(response.isSuccessful) { "HTTP ${response.code}" }
            response.body?.string().orEmpty()
        }
        val playable = (SourceRegistry.instance(configId) as? ModuleSource)?.playableModuleIds().orEmpty()
        val obj = json.parseToJsonElement(body).jsonObject
        if (!ModuleIndex.isSflxRegistry(obj)) {
            return ModuleIndex.parseModules(json, body).map {
                AddonEntry(it.id, it.name, it.version, it.description, "download", it.tags,
                    it.icon ?: it.logo, it.author, registry, it.id in playable)
            }
        }
        return (obj["extensions"] as JsonArray).mapNotNull { element ->
            val entry = element as? JsonObject ?: return@mapNotNull null
            fun text(key: String) = entry[key]?.jsonPrimitive?.contentOrNull.orEmpty()
            if (text("id").isBlank()) return@mapNotNull null
            val key = "sflx-" + text("id")
            AddonEntry(
                key = key,
                name = text("display_name").ifBlank { text("name") }.ifBlank { text("id") },
                version = text("version"),
                description = text("description"),
                category = text("category").ifBlank { "utility" },
                tags = (entry["tags"] as? JsonArray)?.mapNotNull { it.jsonPrimitive.contentOrNull }.orEmpty(),
                icon = text("icon_url").ifBlank { null },
                author = text("author"),
                registry = registry,
                plays = key in playable,
                packageUrl = text("download_url").takeIf { it.startsWith("https://") }.orEmpty(),
                sha256 = text("sha256").lowercase(),
            )
        }.distinctBy { it.key }
    }

    private const val KEY_DISABLED = "disabled"
}
