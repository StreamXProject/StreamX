package com.music.streamx.data.extensions

import com.music.streamx.data.canvas.ShelfHero
import com.music.streamx.data.canvas.ShelfHeroArt
import com.music.streamx.data.canvas.ShelfMotion
import com.music.streamx.data.canvas.PageExtras
import com.music.streamx.data.canvas.PageExtrasStore
import android.os.Bundle
import com.music.streamx.data.model.*
import com.music.streamx.data.sources.ApkExtensionSource
import com.music.streamx.data.sources.SourceConfig
import com.music.streamx.data.sources.SourceRegistry
import org.json.JSONArray
import org.json.JSONObject

/** Optional native metadata operations, rendered by the same feed/detail UI as built-in catalogues. */
class ApkCatalogue(private val config: SourceConfig, private val metadataOnly: Boolean = false, private val playlistTransfer: Boolean = false) {
    suspend fun track(id: String): Song = source.parseSong(request("track", Bundle().apply { putString("id", id) }))
        ?: error("This Apple Music track is unavailable")
    private val source get() = if (metadataOnly || playlistTransfer) ApkExtensionSource(config) else SourceRegistry.instance(config.id) as? ApkExtensionSource ?: error("Extension is disabled or unavailable")
    private suspend fun request(method: String, extra: Bundle = Bundle()): JSONObject {
        if(playlistTransfer) {
            check(method in setOf("account","detail","playlists","create_playlist","add_to_playlist")) { "Operation is not part of a playlist transfer" }
            check(ApkExtensions.installed.value.any {it.packageName==config.extensionPackage && it.sourceAuthority==config.extensionAuthority && it.certificate==config.extensionCertificate}) { "The installed plugin connection changed" }
        } else if (metadataOnly) check(method in setOf("account", "detail", "saved")) { "This connection is read-only" }
        // In the playback walk, or the Home feed: a feed may come from a plugin
        // the user does not play through. Playback itself stays gated on enabled.
        else check(SourceRegistry.config(config.id)?.let { it.enabled || SourceRegistry.servesFeed(it) } == true) { "Extension is disabled" }
        val response = JSONObject(ApkExtensions.call(config, method, extra))
        check(!response.has("error") || response.isNull("error")) { response.optString("error", "Extension request failed") }
        return response
    }
    private suspend fun libraryState(key: String,kind: String,id: String): LibraryState {
        val response=request("saved",Bundle().apply {putString("kind",kind);putString("id",id)})
        return LibraryState(key,response.getBoolean("saved"),response.optBoolean("can_remove",true))
    }
    suspend fun saved(kind: String, id: String): Boolean = libraryState("",kind,id).saved
    suspend fun save(kind: String, id: String, saved: Boolean) {
        request("save", Bundle().apply { putString("kind", kind); putString("id", id); putBoolean("saved", saved) })
    }
    data class LibraryRemovalInfo(val label: String,val confirmation: String)
    suspend fun libraryRemovalInfo(key: String): LibraryRemovalInfo? {
        val target=parseBrowseId(key) ?: return null
        if(config.extensionPackage!="com.music.streamx.extensions.applemusic" ||
            !(target.id.startsWith("library-") || target.kind in setOf("album","playlist"))) return null
        val data=request("library_item_actions",Bundle().apply {putString("kind",target.kind);putString("id",target.id)})
        return LibraryRemovalInfo(data.getString("remove_label"),data.getString("remove_confirmation"))
    }
    suspend fun removeLibraryItem(key: String) {
        val target=parseBrowseId(key) ?: error("Invalid library item")
        request("remove_library_item",Bundle().apply {putString("kind",target.kind);putString("id",target.id)})
    }
    suspend fun playlists(song: Song? = null): List<UserPlaylist> {
        val track = song?.let { SourceRegistry.parseTrackKey(it.videoId) }
        check(song == null || track?.first == config.id) { "Choose a track from this extension" }
        val result = mutableListOf<UserPlaylist>()
        val folders = ArrayDeque<String?>().apply { add(null) }
        val visited = mutableSetOf<String?>()
        var pages = 0
        while (folders.isNotEmpty()) {
            val folder = folders.removeFirst()
            if (!visited.add(folder)) continue
            var offset = 0
            while (true) {
                check(++pages <= 200) { "Too many playlist pages. Narrow the selection in Spotify first." }
                val page = request("playlists", Bundle().apply {
                    putInt("offset", offset)
                    folder?.let { putString("folder", it) }
                    track?.second?.let { putString("track", it) }
                })
                val rows = page.optJSONArray("items") ?: error("Extension did not return playlists")
                for (index in 0 until rows.length()) {
                    val item = rows.optJSONObject(index) ?: continue
                    val id = item.optString("id").takeIf { it.isNotBlank() && it.length <= 256 && !it.contains('\u0000') } ?: continue
                    result += UserPlaylist(browseId(config.id, "playlist", id), item.optString("title"), item.optString("subtitle"), artwork(item))
                }
                page.optJSONArray("folders")?.let { children ->
                    for (index in 0 until children.length()) {
                        val child = children.optString(index)
                        if (child.isNotBlank() && child.length <= 256 && child !in visited) folders.add(child)
                    }
                }
                val next = page.optInt("next_offset", -1)
                if (next <= offset) break
                offset = next
            }
        }
        return result.distinctBy { it.playlistId }
    }
    suspend fun accountIdentity(): String = request("account").getString("id").also {
        check(it.isNotBlank()) { "Sign in to ${config.displayName} first" }
    }

    /** All saved playlists, including followed playlists; mutations use playlists() instead. */
    suspend fun libraryPlaylists(): List<UserPlaylist> {
        val result = mutableListOf<UserPlaylist>()
        var offset = 0
        repeat(200) {
            val page = request("detail", Bundle().apply {
                putString("kind", "collection"); putString("id", "library"); putInt("offset", offset)
            })
            val rows = page.optJSONArray("items") ?: error("Extension did not return its library")
            for (index in 0 until rows.length()) {
                val item = rows.optJSONObject(index) ?: continue
                if (item.optString("type") != "playlist") continue
                val id = item.optString("id").takeIf { it.isNotBlank() && it.length <= 256 } ?: continue
                result += UserPlaylist(browseId(config.id, "playlist", id), item.optString("title"), item.optString("subtitle"), artwork(item))
            }
            val next = page.optInt("next_offset", -1)
            if (next < 0) return result.distinctBy { it.playlistId }
            check(next > offset) { "Extension returned a repeated library page" }
            offset = next
        }
        error("Too many library pages")
    }

    suspend fun playlistTracks(key: String): List<Song> {
        val target = parseBrowseId(key.removePrefix("VL")) ?: error("Invalid extension playlist")
        check(target.configId == config.id && target.kind == "playlist") { "Choose a playlist from this extension" }
        val result = mutableListOf<Song>()
        var offset = 0
        repeat(2000) {
            val page = request("detail", Bundle().apply {
                putString("kind", "playlist"); putString("id", target.id); putInt("offset", offset)
            })
            val rows = page.optJSONArray("items") ?: error("Extension did not return playlist tracks")
            result += songs(rows)
            val next = page.optInt("next_offset", -1)
            if (next < 0) return result
            check(next > offset) { "Extension returned a repeated playlist page" }
            offset = next
        }
        error("Too many playlist pages")
    }

    data class HistoryPage(val songs: List<Song>, val shelves: List<HomeShelf>)
    suspend fun history(): HistoryPage {
        val page=request("history")
        return HistoryPage(songs(page.optJSONArray("items")),shelves(page.optJSONArray("sections")))
    }
    suspend fun createPlaylist(title: String): Pair<String,String> {
        val result=request("create_playlist",Bundle().apply {putString("title",title)})
        val id=result.getString("id")
        check(id.isNotBlank() && id.length<=256 && !id.contains('\u0000')) {"Extension returned an invalid playlist ID"}
        return browseId(config.id,"playlist",id) to result.optString("warning")
    }
    suspend fun addToPlaylist(key: String, song: Song) {
        val target=parseBrowseId(key) ?: error("Invalid playlist")
        val track=SourceRegistry.parseTrackKey(song.videoId) ?: error("Track belongs to another source")
        check(target.configId==config.id && target.kind=="playlist" && track.first==config.id) {"Choose a playlist and track from the same extension"}
        request("add_to_playlist",Bundle().apply {putString("id",target.id);putString("tracks",JSONArray().put(track.second).toString())})
        com.music.streamx.data.playlistsync.PlaylistTransferEvents.changed("plugin:${config.id}")
    }
    suspend fun removePlaylistTrack(key: String,song: Song) {
        val target=parseBrowseId(key) ?: error("Invalid playlist")
        val track=SourceRegistry.parseTrackKey(song.videoId) ?: error("Invalid track")
        check(target.configId==config.id && target.kind=="playlist" && track.first==config.id) {"Choose a track in this playlist"}
        request("remove_from_playlist",Bundle().apply {putString("id",target.id);putString("track",track.second)})
        com.music.streamx.data.playlistsync.PlaylistTransferEvents.changed("plugin:${config.id}")
    }
    /** Every selected track must belong to the receiving provider. */
    suspend fun addTracksToPlaylist(key: String, trackKeys: List<String>) {
        require(trackKeys.isNotEmpty() && trackKeys.size<=100) { "Choose between 1 and 100 tracks" }
        val target=parseBrowseId(key) ?: error("Invalid playlist")
        check(target.configId==config.id && target.kind=="playlist") { "Choose a playlist from this extension" }
        val tracks=trackKeys.map { key ->
            val track=SourceRegistry.parseTrackKey(key) ?: error("Invalid destination track")
            check(track.first==config.id) { "Track belongs to another extension" }
            track.second
        }
        request("add_to_playlist",Bundle().apply {putString("id",target.id);putString("tracks",JSONArray(tracks).toString())})
        com.music.streamx.data.playlistsync.PlaylistTransferEvents.changed("plugin:${config.id}")
    }
    suspend fun account(): Account {
        val user = request("account")
        return Account(user.optString("name", config.displayName), user.optString("email"), artwork(user))
    }
    data class FeedPage(val shelves: List<HomeShelf>, val title: String, val categoryGrid: Boolean, val fullSearch: Boolean)
    suspend fun feedPage(method: String): FeedPage {
        val page = request(method)
        return FeedPage(shelves(page.optJSONArray("sections")), page.optString("title"),
            page.optString("layout", "grid") != "shelves", page.optBoolean("full_search"))
    }
    suspend fun searchPage(query: String, filter: SearchFilter): List<SearchResult> {
        val page = request("search_page", Bundle().apply {putString("query",query);putString("filter",filter.name);putInt("limit",25)})
        val rows = page.optJSONArray("items") ?: return emptyList()
        return (0 until minOf(rows.length(),100)).mapNotNull { index ->
            val value = rows.optJSONObject(index) ?: return@mapNotNull null
            if(value.optString("type")=="track") source.parseSong(value)?.let(SearchResult::Track)
            else {
                val kind=value.optString("type")
                val type=when(kind){"album"->BrowseType.ALBUM;"artist"->BrowseType.ARTIST;"playlist"->BrowseType.PLAYLIST;else->return@mapNotNull null}
                val id=value.optString("id");val title=value.optString("title")
                if(id.isBlank() || id.length>256 || title.isBlank()) return@mapNotNull null
                SearchResult.Browse(BrowseItem(browseId(config.id,kind,id),title,value.optString("subtitle"),artwork(value),type))
            }
        }
    }
    suspend fun feed(method: String = "home"): List<HomeShelf> = shelves(request(method).optJSONArray("sections"))
    suspend fun library(): LibraryPage {
        val response = request("library")
        val sections = shelves(response.optJSONArray("sections"))
        val liked = response.optJSONArray("sections")?.let { rows ->
            (0 until rows.length()).mapNotNull { rows.optJSONObject(it) }.firstOrNull { it.optString("id") == "liked" }
        }?.optJSONArray("items")?.let(::songs).orEmpty()
        return LibraryPage(liked, liked, sections)
    }
    private fun shelves(rows: JSONArray?): List<HomeShelf> = (0 until minOf(rows?.length() ?: 0, 30)).mapNotNull { index ->
        val section = rows?.optJSONObject(index) ?: return@mapNotNull null
        val title = section.optString("title").takeIf { it.isNotBlank() } ?: return@mapNotNull null
        val items = items(section.optJSONArray("items"), title)
        if (items.isEmpty()) return@mapNotNull null
        HomeShelf(title, items, section.optString("subtitle"),
            section.optString("id").takeIf { it.isNotBlank() }?.let { browseId(config.id, "collection", it) })
    }
    private fun items(rows: JSONArray?, shelf: String? = null): List<ShelfItem> = (0 until minOf(rows?.length() ?: 0, 100)).mapNotNull { index ->
        val value = rows?.optJSONObject(index) ?: return@mapNotNull null
        val kind = value.optString("type")
        if (kind == "track") {
            val song = source.parseSong(value) ?: return@mapNotNull null
            ShelfItem(song.title, song.artist, song.thumbnailUrl, song.videoId, null)
        } else {
            val id = value.optString("id").takeIf { it.isNotBlank() && it.length <= 1024 } ?: return@mapNotNull null
            val title = value.optString("title").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            if (kind !in setOf("album", "artist", "playlist", "collection")) return@mapNotNull null
            val key = browseId(config.id, kind, id)
            ShelfMotion.put(key, value.optString("motion_url"))
            if (shelf != null) ShelfHero.put(shelf, key, value.optJSONObject("hero")?.let(::heroArt))
            ShelfItem(title, value.optString("subtitle"), artwork(value), null, key,
                value.optString("background_color").removePrefix("#").takeIf { it.matches(Regex("[0-9A-Fa-f]{6}")) }
                    ?.toLong(16)?.or(0xff000000L))
        }
    }
    private fun songs(rows: JSONArray?): List<Song> = (0 until minOf(rows?.length() ?: 0, 100)).mapNotNull { index -> rows?.optJSONObject(index)?.let(source::parseSong) }
    suspend fun detail(key: String, fallbackTitle: String = "", offset: Int = 0): DetailPage {
        val target = parseBrowseId(key) ?: error("Invalid extension page")
        check(target.configId == config.id) { "Extension page belongs to another source" }
        val response = request("detail", Bundle().apply { putString("kind", target.kind); putString("id", target.id); putInt("offset", offset) })
        val rows = response.optJSONArray("items")
        val tracks = songs(rows)
        val cards = items(rows).filter { it.browseId != null }
        val title = if (target.kind == "collection" && fallbackTitle.isNotBlank()) fallbackTitle else response.optString("title").ifBlank { fallbackTitle }
        if (offset == 0) PageExtrasStore.put(key, PageExtras(
            response.optString("motion_url").takeIf { it.startsWith("https://") && it.length <= 2048 },
            response.optJSONArray("footer")?.let { lines -> (0 until minOf(lines.length(), 4)).mapNotNull { lines.optString(it).take(200).ifBlank { null } } }.orEmpty(),
        ))
        return DetailPage(key, title, response.optString("subtitle"), artwork(response), UiState.Success(tracks),
            type = when (target.kind) { "artist" -> BrowseType.ARTIST; "album" -> BrowseType.ALBUM; else -> BrowseType.PLAYLIST },
            nextCataloguePage = response.optInt("next_offset", -1).takeIf { it > offset },
            library = if (target.kind in setOf("playlist", "album"))
                try { libraryState(key,target.kind,target.id) }
                catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                catch (_: Exception) { null } else null,
            sections = shelves(response.optJSONArray("sections")).ifEmpty { if (cards.isEmpty()) emptyList() else listOf(HomeShelf(title, cards)) },
            description = response.optString("description").take(2000).ifBlank { null },
            owned = if (target.kind == "playlist" && response.has("owned")) response.optBoolean("owned") else null)
    }
    companion object {
        const val PREFIX = "apk:"
        data class Target(val configId: String, val kind: String, val id: String) {
            val isBrowseCategory: Boolean get() = kind=="collection" && id.startsWith("browse:")
        }
        fun browseId(configId: String, kind: String, id: String) = "$PREFIX$configId:$kind:$id"
        fun parseBrowseId(key: String): Target? {
            if (!key.startsWith(PREFIX)) return null
            val parts = key.removePrefix(PREFIX).split(':', limit = 3)
            if (parts.size != 3 || parts.any { it.isBlank() } || parts[1] !in setOf("artist", "album", "playlist", "collection")) return null
            return Target(parts[0], parts[1], parts[2])
        }
        private fun artwork(value: JSONObject) = value.optString("thumbnail_url").takeIf { it.startsWith("https://") }
        private fun colour(value: JSONObject, key: String) = value.optString(key).removePrefix("#")
            .takeIf { it.matches(Regex("[0-9A-Fa-f]{6}")) }?.toLong(16)?.or(0xff000000L)
        private fun heroArt(value: JSONObject): ShelfHeroArt? = artwork(value)?.let { still ->
            ShelfHeroArt(still, value.optString("motion_url").takeIf { it.startsWith("https://") && it.length <= 2048 },
                value.optString("eyebrow").take(120), value.optString("description").take(300),
                colour(value, "text_color"), colour(value, "secondary_text_color"), colour(value, "background_color"),
                value.optBoolean("square"))
        }
    }
}
