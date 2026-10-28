package com.music.streamx.data.extensions

import android.os.Bundle
import com.music.streamx.data.sources.SourceConfig
import com.music.streamx.data.sources.SourceKind
import com.music.streamx.data.sources.module.ModuleManager
import com.music.streamx.data.sources.module.QuickJsExecutor
import com.music.streamx.data.sources.module.SFLX_TYPE
import com.music.streamx.data.sources.module.SpineModule
import org.json.JSONArray
import org.json.JSONObject

/**
 * Answers the plugin protocol ([ApkExtensions.call]) for a SpotiFLAC extension.
 *
 * A metadata extension added from the Addons page is stored as a plugin source
 * whose package is `sflx:<id>`, its authority the package URL and its
 * certificate the package digest. Routing its calls here is what lets the Home
 * feed, search and album, playlist and artist pages treat it exactly like an
 * installed plugin: `home` runs `getHomeFeed`, `search_page` runs
 * `customSearch`, and `detail` runs `getAlbum`, `getPlaylist` or `getArtist`.
 *
 * Nothing here plays audio. A track from an extension is matched and played
 * through the listener's playback order, as feed tracks from any plugin are.
 */
object SflxBridge {
    const val PREFIX = "sflx:"

    private val manager by lazy { ModuleManager() }

    /** Whether [config] is an extension rather than an installed APK. */
    fun handles(config: SourceConfig): Boolean =
        config.kind == SourceKind.APK_PLUGIN && config.extensionPackage.startsWith(PREFIX)

    /** The plugin source for registry extension [id], as the Addons page adds it. */
    fun config(id: String, name: String, packageUrl: String, sha256: String): SourceConfig = SourceConfig(
        kind = SourceKind.APK_PLUGIN,
        label = name,
        // A catalogue, not a playback source: the walk would only ever find no stream here.
        enabled = false,
        extensionPackage = PREFIX + id,
        extensionAuthority = packageUrl,
        extensionCertificate = sha256.ifBlank { "unverified" },
    )

    suspend fun call(config: SourceConfig, method: String, extras: Bundle): String = runCatching {
        when (method) {
            "health" -> { load(config); JSONObject().put("ok", true) }
            "home" -> home(config)
            "search" -> JSONObject().put("items", searchPage(config, extras.getString("query").orEmpty(), "SONGS").optJSONArray("items"))
            "search_page" -> searchPage(config, extras.getString("query").orEmpty(), extras.getString("filter").orEmpty())
            "detail" -> detail(config, extras.getString("kind").orEmpty(), extras.getString("id").orEmpty())
            "saved" -> JSONObject().put("saved", false).put("can_remove", false)
            "stream" -> JSONObject()
            else -> JSONObject().put("error", "${config.displayName} does not support $method")
        }
    }.getOrElse { JSONObject().put("error", it.message ?: "${config.displayName} failed") }.toString()

    private suspend fun load(config: SourceConfig): SpineModule {
        val module = SpineModule(
            id = "sflx-" + config.extensionPackage.removePrefix(PREFIX),
            name = config.displayName,
            type = SFLX_TYPE,
            download = config.extensionAuthority,
            sha256 = config.extensionCertificate.takeIf { it != "unverified" }.orEmpty(),
        )
        manager.loadModule(module).getOrThrow()
        return module
    }

    /** Runs extension function [name] with JSON-encodable [args]; null when it has no such function. */
    private suspend fun invoke(config: SourceConfig, name: String, vararg args: Any?): Any? {
        val module = load(config)
        val raw = QuickJsExecutor.callExport(module.id, "call",
            listOf(JSONObject.quote(name), JSONObject.quote(JSONArray(args.toList()).toString()))).getOrThrow()
        val reply = JSONObject(raw)
        if (reply.has("error")) error(reply.optString("error"))
        if (reply.optBoolean("missing")) return null
        val value = reply.opt("value")
        (value as? JSONObject)?.let { if (it.has("success") && !it.optBoolean("success", true)) error(it.optString("error", "Request failed")) }
        return value
    }

    private suspend fun home(config: SourceConfig): JSONObject {
        val feed = invoke(config, "getHomeFeed") as? JSONObject ?: error("${config.displayName} has no home feed")
        val sections = JSONArray()
        feed.optJSONArray("sections")?.let { rows ->
            for (i in 0 until rows.length()) {
                val row = rows.optJSONObject(i) ?: continue
                val items = items(row.optJSONArray("items"))
                if (items.length() == 0) continue
                sections.put(JSONObject().put("title", row.optString("title")).put("subtitle", row.optString("subtitle")).put("items", items))
            }
        }
        return JSONObject().put("sections", sections)
    }

    private suspend fun searchPage(config: SourceConfig, query: String, filter: String): JSONObject {
        val wanted = when (filter) { "SONGS" -> "tracks"; "ALBUMS" -> "albums"; "ARTISTS" -> "artists"; "PLAYLISTS" -> "playlists"; else -> null }
        val options = JSONObject().put("limit", 25).apply { if (wanted != null) put("filter", wanted) }
        val found = invoke(config, "customSearch", query, options) ?: invoke(config, "searchTracks", query, 25)
        val rows = when (found) {
            is JSONArray -> found
            is JSONObject -> found.optJSONArray("tracks") ?: found.optJSONArray("items") ?: found.optJSONArray("results") ?: JSONArray()
            else -> JSONArray()
        }
        return JSONObject().put("items", items(rows))
    }

    private suspend fun detail(config: SourceConfig, kind: String, id: String): JSONObject {
        val page = when (kind) {
            "album" -> invoke(config, "getAlbum", id)
            "playlist" -> invoke(config, "getPlaylist", id)
            "artist" -> invoke(config, "getArtist", id)
            else -> null
        } as? JSONObject ?: error("${config.displayName} could not open this page")
        val result = JSONObject()
            .put("title", page.optString("name"))
            .put("subtitle", page.optString("artists").ifBlank { page.optString("owner") })
            .put("description", page.optString("description"))
            .put("thumbnail_url", cover(page))
        if (kind == "artist") {
            result.put("items", tracks(page.optJSONArray("top_tracks")))
            val albums = items(page.optJSONArray("albums"), defaultType = "album")
            if (albums.length() > 0) result.put("sections", JSONArray().put(JSONObject().put("title", "Albums").put("items", albums)))
        } else {
            result.put("items", tracks(page.optJSONArray("tracks")))
        }
        return result
    }

    private fun cover(value: JSONObject): String {
        for (key in listOf("cover_url", "images", "cover", "thumbnail", "image_url")) {
            val field = value.opt(key)
            val url = when (field) {
                is String -> field
                is JSONArray -> field.optJSONObject(0)?.optString("url") ?: field.optString(0)
                is JSONObject -> field.optString("url")
                else -> null
            }
            if (url != null && url.startsWith("https://")) return url
        }
        return ""
    }

    private fun track(value: JSONObject): JSONObject? {
        val id = value.optString("id").ifBlank { value.optString("spotify_id") }
        val title = value.optString("name").ifBlank { value.optString("title") }
        if (id.isBlank() || title.isBlank()) return null
        val artists = value.opt("artists")
        val artist = when (artists) {
            is JSONArray -> (0 until artists.length()).joinToString(", ") { artists.optJSONObject(it)?.optString("name") ?: artists.optString(it) }
            is String -> artists
            else -> value.optString("artist")
        }
        return JSONObject().put("type", "track").put("id", id).put("title", title).put("artist", artist)
            .put("album", value.optString("album_name").ifBlank { value.optString("album") })
            .put("album_id", value.optString("album_id")).put("artist_id", value.optString("artist_id"))
            .put("thumbnail_url", cover(value))
            .put("duration_sec", (value.optLong("duration_ms") / 1000).toInt())
            .put("explicit", value.optBoolean("explicit"))
    }

    private fun tracks(rows: JSONArray?): JSONArray = JSONArray().apply {
        for (i in 0 until (rows?.length() ?: 0)) rows?.optJSONObject(i)?.let(::track)?.let(::put)
    }

    /** Mixed feed or search rows: tracks as tracks, the rest as album, artist or playlist cards. */
    private fun items(rows: JSONArray?, defaultType: String? = null): JSONArray = JSONArray().apply {
        for (i in 0 until (rows?.length() ?: 0)) {
            val row = rows?.optJSONObject(i) ?: continue
            val type = row.optString("item_type").ifBlank { row.optString("type") }.ifBlank { defaultType ?: "track" }
            if (type == "track") { track(row)?.let(::put); continue }
            if (type !in setOf("album", "artist", "playlist")) continue
            val id = row.optString("id"); val title = row.optString("name").ifBlank { row.optString("title") }
            if (id.isBlank() || title.isBlank()) continue
            val subtitle = row.optString("artists").ifBlank { row.optString("owner") }.ifBlank { row.optString("description") }
            put(JSONObject().put("type", type).put("id", id).put("title", title).put("subtitle", subtitle).put("thumbnail_url", cover(row)))
        }
    }
}
