package com.music.streamx.data.sources.module

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Parses a raw module-index JSON body into a flat list of [SpineModule]s.
 *
 * Index servers organise modules under arbitrary "category:<key>" top-level
 * keys (e.g. "category:music", "category:debrid_modules",
 * "category:ricky_modules"). A fixed-field data class would silently drop
 * every module under an unrecognised key; scanning for any key that starts
 * with "category:" picks them all up — the same approach Convx takes.
 */
object ModuleIndex {

    private val excludedCategories = setOf("category:artworks", "category:testing")
    private val listSerializer = ListSerializer(SpineModule.serializer())

    fun parseModules(json: Json, body: String): List<SpineModule> {
        val obj = json.decodeFromString(JsonObject.serializer(), body)
        if (isSflxRegistry(obj)) return parseSflxRegistry(obj)
        return obj.entries
            .filter { it.key.startsWith("category:") && it.key !in excludedCategories }
            .flatMap { (_, value) ->
                runCatching { json.decodeFromJsonElement(listSerializer, value) }
                    .getOrElse { emptyList() }
            }
            .distinctBy { it.id }
    }

    /**
     * A SpotiFLAC extension registry: `{"extensions":[{"id", "download_url": "….sflx", …}]}`.
     * Recognised by the `.sflx` packages it points at, which no other format uses.
     */
    fun isSflxRegistry(obj: JsonObject): Boolean =
        (obj["extensions"] as? JsonArray)?.any { entry ->
            (entry as? JsonObject)?.get("download_url")?.jsonPrimitive?.contentOrNull
                ?.substringBefore('?')?.endsWith(".sflx", ignoreCase = true) == true
        } == true

    /**
     * The registry's download providers as modules. Metadata-only extensions are
     * left out here because they have no audio to offer; whether a download
     * provider can actually stream is settled from its manifest once fetched.
     */
    private fun parseSflxRegistry(obj: JsonObject): List<SpineModule> =
        (obj["extensions"] as JsonArray).mapNotNull { element ->
            val entry = element as? JsonObject ?: return@mapNotNull null
            fun text(key: String) = entry[key]?.jsonPrimitive?.contentOrNull.orEmpty()
            val tags = (entry["tags"] as? JsonArray)?.mapNotNull { it.jsonPrimitive.contentOrNull }.orEmpty()
            val url = text("download_url")
            if (text("id").isBlank() || !url.startsWith("https://")) return@mapNotNull null
            if (text("category") != "download" && "download" !in tags) return@mapNotNull null
            SpineModule(
                id = "sflx-" + text("id"),
                name = text("display_name").ifBlank { text("name") },
                version = text("version"),
                type = SFLX_TYPE,
                description = text("description"),
                declaredTags = tags,
                download = url,
                icon = text("icon_url").ifBlank { null },
                lastUpdated = text("updated_at"),
                sha256 = text("sha256").lowercase(),
            )
        }.distinctBy { it.id }
}
