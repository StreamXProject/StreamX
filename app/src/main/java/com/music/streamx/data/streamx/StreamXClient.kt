package com.music.streamx.data.streamx

import com.music.streamx.data.model.*
import com.music.streamx.data.sources.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** One request context: responses and credentials belong to the captured server and listener. */
class StreamXClient(
    val config: SourceConfig,
    val session: StreamXAccountSession,
    private val http: OkHttpClient = sharedHttp,
) {
    companion object {
        private val json = Json { ignoreUnknownKeys = true }
        private val sharedHttp = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS)
            .callTimeout(75, TimeUnit.SECONDS).retryOnConnectionFailure(false).build()
        const val BROWSE_PREFIX = "streamx:"

        fun current(): StreamXClient {
            // The Home catalogue when it is a server; otherwise StreamX itself,
            // for a StreamX search tab beside another Home feed. Being on Home
            // is enough: whether it is also in the playback order is separate.
            val kind = SourceRegistry.catalogue.value.takeIf { it == SourceKind.STREAMX || it == SourceKind.CLOUDSTREAM } ?: SourceKind.STREAMX
            val config = SourceRegistry.configs.value.firstOrNull { it.kind == kind }
                ?: error("Add your server in Sources")
            check(config.isComplete && (config.enabled || SourceRegistry.catalogue.value == kind)) { "Add or enable your StreamX server in Sources" }
            return StreamXClient(config, StreamXSession.state.value)
        }

        suspend fun validateEndpoint(endpoint: String) {
            val client = StreamXClient(SourceConfig(kind = SourceKind.STREAMX, baseUrl = endpoint), StreamXAccountSession())
            val health = client.request(listOf("health"))
            check(health.text("status") == "ok") { "This address is not a healthy StreamGO server" }
            val tracks = client.request(listOf("tracks"), mapOf("limit" to "1"))
            check(tracks["items"] is JsonArray || tracks["items"] == JsonNull) { "This server did not return a StreamGO catalogue" }
        }

        suspend fun <T> result(block: suspend () -> T): Result<T> = try {
            Result.success(block())
        } catch (e: CancellationException) { throw e
        } catch (e: Exception) { Result.failure(e) }
    }

    val cloud: Boolean get() = config.kind == SourceKind.CLOUDSTREAM
    val personalAccount: Boolean get() = cloud || session.personalAccount

    private fun url(path: List<String>, query: Map<String, String> = emptyMap()): HttpUrl {
        val builder = (config.baseUrl.trimEnd('/') + "/").toHttpUrl().newBuilder()
        path.forEach(builder::addPathSegment)
        query.forEach { (key, value) -> builder.addQueryParameter(key, value) }
        return builder.build()
    }

    fun headers(): Map<String, String> =
        if (session.endpoint == config.baseUrl && session.token.isNotBlank()) mapOf("Authorization" to "Bearer ${session.token}")
        else emptyMap()

    internal suspend fun request(
        path: List<String>, query: Map<String, String> = emptyMap(),
        method: String = "GET", body: JsonObject? = null,
    ): JsonObject = withContext(Dispatchers.IO) {
        if (cloud) return@withContext com.music.streamx.data.cloudstream.CloudLibrary.request(config, path, query, method, body)
        val builder = Request.Builder().url(url(path, query)).header("Accept", "application/json")
        headers().forEach { (key, value) -> builder.header(key, value) }
        if (method != "GET") builder.method(method, if (body != null || method != "DELETE")
            (body?.toString() ?: "{}").toRequestBody("application/json".toMediaType()) else null)
        val response = suspendCancellableCoroutine<Response> { continuation ->
            val call = http.newCall(builder.build())
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (!continuation.isCancelled) continuation.resumeWithException(e)
                }
                override fun onResponse(call: Call, response: Response) {
                    continuation.resume(response) { _, value, _ -> value.close() }
                }
            })
        }
        response.use {
            val content = response.body?.string().orEmpty()
            val value = runCatching { json.parseToJsonElement(content) as? JsonObject }.getOrNull()
            if (!response.isSuccessful) {
                if (response.code == 401) StreamXSession.expire(session)
                error(value?.text("error")?.ifBlank { null } ?: value?.text("detail")?.ifBlank { null }
                    ?: "Server request failed (${response.code})")
            }
            value ?: error("Server returned an invalid response")
        }
    }

    suspend fun authenticate(method: String, username: String, password: String, inviteCode: String = "") {
        val path = when (method) { "server" -> "password"; "signup" -> "create"; else -> "login" }
        val response = request(listOf("auth", path), method = "POST", body = buildJsonObject {
            put("password", password)
            if (method != "server") put("username", username.trim())
            if (inviteCode.isNotBlank()) put("invite_code", inviteCode.trim())
        })
        acceptSession(response, username)
    }

    private fun acceptSession(response: JsonObject, username: String = "") {
        val token = response.text("token").also { check(it.isNotBlank()) { "The server did not issue a session. Please complete registration on the server." } }
        val user = response.obj("user")
        StreamXSession.authenticated(session, token,
            (response["user_id"] as? JsonPrimitive)?.longOrNull ?: (user["id"] as? JsonPrimitive)?.longOrNull,
            response.text("first_name").ifBlank { response.text("username") }.ifBlank { username },
            response.text("photo_url").ifBlank { user.text("photo_url") }.ifBlank { null })
    }

    suspend fun telegramClientId(): String {
        val config = request(listOf("auth", "telegram", "config"))
        check((config["enabled"] as? JsonPrimitive)?.booleanOrNull != false) { "Telegram login is not enabled on this server" }
        return config.text("client_id").ifBlank { config.text("bot_id") }.also {
            check(it.isNotBlank()) { "This server has no Telegram login client configured" }
        }
    }

    suspend fun authenticateTelegram(idToken: String, inviteCode: String) {
        val response = request(listOf("auth", "telegram", "validate-token"), mapOf("set_cookie" to "false"),
            method = "POST", body = buildJsonObject {
                put("id_token", idToken)
                if (inviteCode.isNotBlank()) put("invite_code", inviteCode.trim())
            })
        acceptSession(response)
    }

    private fun artwork(value: JsonObject): String? {
        val base = config.baseUrl.toHttpUrl()
        val candidates = listOf(value.text("cloudflare_cover_url"), value.text("cover_url"),
            value.text("normal_thumbnail"), value.text("thumbnail_url")) +
            (value["thumbnails"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.orEmpty() +
            listOf(value.obj("spotify").text("cover_url"), value.obj("audio").text("cover_url"))
        return candidates.asSequence().filter { it.isNotBlank() }.mapNotNull { base.resolve(it) }
            // Legacy playlist records advertise PNGs for a route StreamGO does not serve.
            // Use their real track thumbnails instead; do not probe the network for each card.
            .firstOrNull { !(it.host == base.host && it.port == base.port &&
                it.encodedPath.contains("/covers/user-playlist/")) }?.toString()
    }

    internal fun song(value: JsonObject): Song {
        // Favourites wrap a BrowseItem; catalogue and playlist responses return it directly.
        val track = value.obj("track").takeIf { it.isNotEmpty() } ?: value
        val audio = track.obj("audio").takeIf { it.isNotEmpty() } ?: track
        val id = track.text("id").ifBlank { track.text("_id") }.ifBlank { track.text("track_id") }
        check(id.isNotBlank()) { "Server returned a track without an id" }
        val seconds = audio.int("duration_sec") ?: 0
        return Song(
            videoId = SourceRegistry.trackKey(config.id, id), title = audio.text("title"),
            artist = audio.text("artist").ifBlank { audio.text("performer") }, thumbnailUrl = artwork(track),
            durationText = String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60),
            albumName = audio.text("album").ifBlank { null },
            albumId = audio.text("album_id").takeIf { it.isNotBlank() }?.let { browseId("album", it) },
            sourceQuality = if (format(audio).isLossless == true) "LOSSLESS" else "HIGH",
        )
    }

    private fun format(audio: JsonObject): StreamFormat {
        val stated = audio.text("codec").ifBlank { audio.text("codec_name") }.ifBlank { audio.text("type") }.lowercase(Locale.ROOT)
        val codec = when {
            stated.contains("alac") || stated.contains("apple lossless") -> "alac"
            stated == "audio/flac" || stated == "audio/x-flac" -> "flac"
            else -> stated.ifBlank { null }
        }
        // An M4A container and a bit depth alone cannot distinguish AAC from ALAC.
        return StreamFormat(codec, audio.int("bitrate_kbps"), audio.int("sampling_rate_hz"), audio.int("bit_depth"))
    }

    suspend fun stream(id: String, streamRequest: StreamRequest): SourceStream? {
        if (cloud) com.music.streamx.data.cloudstream.CloudStream.stream(if (cloud) com.music.streamx.data.cloudstream.CloudStream.PROFILE else config.baseUrl, id, streamRequest)?.let {
            return it.copy(sourceConfigId = config.id)
        }
        val track = request(listOf("tracks", id))
        val audio = track.obj("audio")
        val format = format(audio)
        val kbps = format.kbps
        // StreamGO currently serves originals; it has no bitrate-cap transcoding endpoint.
        if (streamRequest is StreamRequest.Capped && (format.isLossless == true || kbps == null || kbps > streamRequest.maxKbps)) return null
        return SourceStream(url = url(listOf("tracks", id, "stream")).toString(), format = format,
            headers = headers(), durationSec = audio.int("duration_sec"), sourceConfigId = config.id)
    }

    suspend fun songs(path: List<String>, query: Map<String, String> = emptyMap()): List<Song> =
        request(path, query).objects("items").filterNot { it["track"] == JsonNull }.map(::song)

    private suspend fun allItems(path: List<String>): List<JsonObject> {
        val songs = mutableListOf<JsonObject>()
        var page = 1
        while (true) {
            val response = request(path, mapOf("page" to page.toString(), "limit" to "100"))
            val items = response.objects("items")
            check(items.isEmpty() || items != songs.takeLast(items.size)) { "Server repeated a catalogue page" }
            songs += items
            val total = response.int("total") ?: songs.size
            if (items.isEmpty() || songs.size >= total) break
            page++
        }
        return songs
    }

    suspend fun allSongs(path: List<String>): List<Song> = allItems(path).filterNot { it["track"] == JsonNull }.map(::song).distinctBy { it.videoId }

    suspend fun search(query: String, filter: SearchFilter = SearchFilter.SONGS, limit: Int = 50): List<SearchResult> = coroutineScope {
        when (filter) {
            SearchFilter.ALL, SearchFilter.SONGS -> songs(listOf("search"), mapOf("q" to query, "limit" to limit.toString())).map { SearchResult.Track(it) }
            SearchFilter.ARTISTS, SearchFilter.ALBUMS -> {
                val kind = if (filter == SearchFilter.ARTISTS) "artist" else "album"
                allItems(listOf("${kind}s"))
                    .filter { it.text("name").contains(query, true) || it.text("title").contains(query, true) || it.text("artist").contains(query, true) }
                    .map { SearchResult.Browse(browse(it, kind)) }
            }
            SearchFilter.PLAYLISTS -> (if (personalAccount) playlists() else emptyList()).filter { it.title.contains(query, true) }.map {
                SearchResult.Browse(BrowseItem(it.browseId, it.title, it.subtitle, it.thumbnailUrl, BrowseType.PLAYLIST))
            }
            SearchFilter.VIDEOS -> emptyList()
        }
    }

    private fun browseId(kind: String, id: String) = "$BROWSE_PREFIX${config.id}:$kind:$id"
    private fun browse(item: JsonObject, kind: String): BrowseItem = BrowseItem(
        browseId(kind, item.text("id").ifBlank { item.text("_id") }),
        item.text("name").ifBlank { item.text("title") }, item.text("artist"), artwork(item),
        if (kind == "artist") BrowseType.ARTIST else BrowseType.ALBUM,
    )

    suspend fun playlists(): List<UserPlaylist> = request(listOf("me", "playlists")).objects("items").map {
        // UserPlaylist adds VL for browsing, so keep a namespaced id behind it.
        UserPlaylist(browseId("playlist", it.text("playlist_id")), it.text("name"), "", artwork(it))
    }

    suspend fun library(): LibraryPage = coroutineScope {
        check(personalAccount) { "Sign in to your StreamX account to open your library" }
        val favourites = async { allSongs(listOf("me", "favourites")) }
        val playlists = async { playlists() }
        val albums = async { allItems(listOf("me", "albums")) }
        val followed = async { favouriteArtistIds() }
        val liked = favourites.await()
        val shelf = playlists.await().map { ShelfItem(it.title, it.subtitle, it.thumbnailUrl, null, it.browseId) }
        LibraryPage(liked, emptyList(), buildList {
            add(HomeShelf("Your music", listOf(ShelfItem("Favourites", "${liked.size} songs", liked.firstOrNull()?.thumbnailUrl, null, browseId("favourites", "all")))))
            if (shelf.isNotEmpty()) add(HomeShelf("Playlists", shelf))
            val saved = albums.await().map { item ->
                val album = item.obj("album").takeIf { it.isNotEmpty() } ?: item
                val id = item.text("album_id").ifBlank { album.text("id") }.ifBlank { album.text("_id") }
                ShelfItem(album.text("title"), album.text("artist"), artwork(album), null, browseId("album", id))
            }
            if (saved.isNotEmpty()) add(HomeShelf("Albums", saved))
            val ids = followed.await()
            if (ids.isNotEmpty()) {
                val artists = ids.toList().chunked(4).flatMap { batch ->
                    batch.map { artistId -> async { result {
                        val response = request(listOf("artists", artistId))
                        response.obj("artist").takeIf { it.isNotEmpty() } ?: response
                    } } }.mapNotNull { it.await().getOrNull() }
                }
                add(HomeShelf("Following", artists.map {
                    val artist = browse(it, "artist")
                    ShelfItem(artist.title, artist.subtitle, artist.thumbnailUrl, null, artist.browseId)
                }))
            }
        })
    }

    suspend fun account(): Account {
        val user = request(listOf("auth", "me")).obj("user")
        check(user.isNotEmpty()) { "Sign in to your personal account" }
        return Account(user.text("first_name").ifBlank { user.text("username") }.ifBlank { session.name }, "",
            user.text("photo_url").ifBlank { session.photo })
    }

    suspend fun history(): List<Song> = songs(listOf("me", "history"), mapOf("limit" to "100"))
    private fun shelf(title: String, songs: List<Song>) = HomeShelf(title, songs.map { ShelfItem(it.title, it.artist, it.thumbnailUrl, it.videoId, null) })
    suspend fun home(): List<HomeShelf> = coroutineScope {
        val latest = async { songs(listOf("tracks"), mapOf("limit" to "24")) }
        val recent = if (personalAccount) async { result { history() } } else null
        buildList {
            recent?.await()?.getOrNull()?.takeIf { it.isNotEmpty() }?.let { add(shelf("Recently played", it.take(12))) }
            add(shelf("Latest songs", latest.await()).copy(moreBrowseId = browseId("catalogue", "tracks")))
        }
    }

    /** Bounded secondary shelves, loaded after the first playable tracks are visible. */
    suspend fun homeSections(fallbackArtwork: List<String> = emptyList()): List<HomeShelf> = coroutineScope {
        val albums = async { result { request(listOf("albums"), mapOf("limit" to "12")).objects("items") } }
        val artists = async { result { request(listOf("artists"), mapOf("limit" to "12")).objects("items") } }
        val shuffled = async { result { songs(listOf("tracks", "random"), mapOf("limit" to "12")) } }
        val daily = async { result { dailyMixes(fallbackArtwork) } }
        buildList {
            daily.await().getOrNull()?.takeIf { it.isNotEmpty() }?.let { add(HomeShelf("Daily mixes", it)) }
            albums.await().getOrNull()?.takeIf { it.isNotEmpty() }?.let { items ->
                add(HomeShelf("Albums", items.map { item ->
                    val album = browse(item, "album")
                    ShelfItem(album.title, album.subtitle, album.thumbnailUrl, null, album.browseId)
                }, moreBrowseId = browseId("catalogue", "albums")))
            }
            artists.await().getOrNull()?.takeIf { it.isNotEmpty() }?.let { items ->
                add(HomeShelf("Artists", items.map { item ->
                    val artist = browse(item, "artist")
                    ShelfItem(artist.title, "", artist.thumbnailUrl, null, artist.browseId)
                }, moreBrowseId = browseId("catalogue", "artists")))
            }
            shuffled.await().getOrNull()?.takeIf { it.isNotEmpty() }?.let { add(shelf("On shuffle", it).copy(moreBrowseId = browseId("catalogue", "shuffle"))) }
        }
    }

    suspend fun explore(): List<MoodGenreSection> = coroutineScope {
        val artists = async { request(listOf("artists"), mapOf("limit" to "24")).objects("items") }
        val topics = async { request(listOf("topics"), mapOf("limit" to "50")).objects("items") }
        buildList {
            topics.await().takeIf { it.isNotEmpty() }?.let { items ->
                add(MoodGenreSection("Topics", items.map { item ->
                    val name = item.text("topic_name").ifBlank { item.text("name") }
                    MoodGenre(name, browseId("catalogue", "topic/$name"), params = null, thumbnailUrl = artwork(item))
                }))
            }
            artists.await().takeIf { it.isNotEmpty() }?.let { items ->
                add(MoodGenreSection("Artists", listOf(MoodGenre("All artists", browseId("catalogue", "artists"), params = null)) + items.map { item ->
                    val artist = browse(item, "artist")
                    MoodGenre(artist.title, artist.browseId, params = null, thumbnailUrl = artist.thumbnailUrl)
                }))
            }
        }
    }

    private fun identity(browseId: String): Pair<String, String> {
        val prefix = "$BROWSE_PREFIX${config.id}:"
        val key = browseId.removePrefix("VL")
        check(key.startsWith(prefix)) { "This item belongs to another server" }
        val remaining = key.removePrefix(prefix)
        return remaining.substringBefore(':') to remaining.substringAfter(':')
    }

    suspend fun dailyMixes(fallbackArtwork: List<String> = emptyList()): List<ShelfItem> =
        request(listOf("playlists", "available")).objects("items")
            .filter { it.text("kind") == "daily" && (personalAccount || (it["requires_auth"] as? JsonPrimitive)?.booleanOrNull != true) }
            .mapIndexed { index, item -> ShelfItem(item.text("name"), "", artwork(item) ?: fallbackArtwork.getOrNull(index),
                null, browseId("daily", item.text("id"))) }

    suspend fun favouriteArtistIds(): Set<String> =
        (request(listOf("me", "artists", "favourites", "ids"))["ids"] as? JsonArray)
            ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }?.toSet().orEmpty()

    suspend fun followArtist(id: String, follow: Boolean) {
        val (kind, key) = identity(id)
        check(kind == "artist" && personalAccount) { "Sign in to follow artists" }
        if (follow) request(listOf("me", "artists", "favourites"), method = "POST", body = buildJsonObject { put("artist_id", key) })
        else request(listOf("me", "artists", "favourites", key), method = "DELETE")
    }

    suspend fun detail(id: String, title: String, subtitle: String, thumbnail: String?): DetailPage {
        val (kind, key) = identity(id)
        if (kind == "catalogue") return cataloguePage(id, title, 1)
        val rows = when (kind) {
            "daily" -> songs(listOf("daily-playlist", key), mapOf("limit" to "75"))
            "favourites" -> allSongs(listOf("me", "favourites"))
            "playlist" -> allSongs(listOf("me", "playlists", key, "tracks"))
            "artist" -> allSongs(listOf("artists", key, "tracks"))
            else -> allSongs(listOf("albums", key, "tracks"))
        }
        return DetailPage(id, title, subtitle, thumbnail?.takeIf { it.isNotBlank() } ?: rows.firstNotNullOfOrNull { it.thumbnailUrl }, UiState.Success(if (kind == "playlist") rows.map { it.copy(setVideoId = it.videoId) } else rows),
            type = when (kind) { "playlist", "favourites", "daily" -> BrowseType.PLAYLIST; "artist" -> BrowseType.ARTIST; else -> BrowseType.ALBUM },
            subscription = if (kind == "artist" && personalAccount) SubscriptionState(id, key in favouriteArtistIds()) else null)
    }

    suspend fun cataloguePage(id: String, title: String, page: Int): DetailPage {
        val (kind, key) = identity(id)
        check(kind == "catalogue")
        val limit = 24
        val path = when {
            key.startsWith("topic/") -> listOf("topics", key.removePrefix("topic/"), "tracks")
            key == "albums" || key == "artists" -> listOf(key)
            key == "tracks" || key == "shuffle" -> listOf("tracks")
            else -> error("Unknown catalogue")
        }
        val response = request(path, mapOf("page" to page.toString(), "limit" to limit.toString()))
        val items = response.objects("items")
        val total = response.int("total")
        val next = if (items.isEmpty() || (total != null && page * limit >= total) ||
            (total == null && items.size < limit)) null else page + 1
        val browsable = key == "albums" || key == "artists"
        val rows = if (browsable) emptyList() else items.filterNot { it["track"] == JsonNull }.map(::song)
            .let { if (key == "shuffle") it.shuffled() else it }
        val sections = if (!browsable) emptyList() else listOf(HomeShelf(title, items.map {
            val entry = browse(it, if (key == "artists") "artist" else "album")
            ShelfItem(entry.title, entry.subtitle, entry.thumbnailUrl, null, entry.browseId)
        }))
        return DetailPage(id, title, "", null, UiState.Success(rows), sections = sections, nextCataloguePage = next)
    }

    suspend fun rate(videoId: String, status: LikeStatus) {
        val track = SourceRegistry.parseTrackKey(videoId)
        check(track?.first == config.id) { "This track belongs to another source" }
        if (status == LikeStatus.LIKE) request(listOf("me", "favourites"), method = "POST", body = buildJsonObject { put("track_id", track!!.second) })
        else request(listOf("me", "favourites", track!!.second), method = "DELETE")
    }

    suspend fun createPlaylist(name: String, songs: List<String>, coverUrl: String? = null): String {
        val response = request(listOf("me", "playlists"), method = "POST", body = buildJsonObject {
            put("name", name)
            put("title", name)
            coverUrl?.trim()?.takeIf { it.isNotEmpty() }?.let { put("cover_url", it) }
        })
        val id = response.text("playlist_id").ifBlank { response.obj("playlist").text("playlist_id") }
        check(id.isNotBlank()) { "Server did not return a playlist id" }
        val namespaced = browseId("playlist", id)
        if (songs.isNotEmpty()) addToPlaylist(namespaced, songs)
        return namespaced
    }

    suspend fun addToPlaylist(id: String, songs: List<String>) {
        val (_, key) = identity(id)
        val ids = songs.map { SourceRegistry.parseTrackKey(it)?.takeIf { track -> track.first == config.id }?.second
            ?: error("Only tracks from this server can be added to this playlist") }
        request(listOf("me", "playlists", key, "tracks"), method = "POST", body = buildJsonObject {
            put("track_ids", JsonArray(ids.map(::JsonPrimitive)))
        })
    }

    suspend fun removeFromPlaylist(id: String, videoId: String) {
        val track = SourceRegistry.parseTrackKey(videoId)?.takeIf { it.first == config.id }
            ?: error("This track belongs to another server")
        request(listOf("me", "playlists", identity(id).second, "tracks", track.second), method = "DELETE")
    }

    suspend fun renamePlaylist(id: String, name: String) {
        request(listOf("me", "playlists", identity(id).second), method = "PATCH", body = buildJsonObject { put("name", name) })
    }
    suspend fun deletePlaylist(id: String) { request(listOf("me", "playlists", identity(id).second), method = "DELETE") }
}

private fun JsonObject.text(key: String): String = (get(key) as? JsonPrimitive)?.contentOrNull.orEmpty()
private fun JsonObject.int(key: String): Int? = (get(key) as? JsonPrimitive)?.intOrNull
private fun JsonObject.obj(key: String): JsonObject = get(key) as? JsonObject ?: JsonObject(emptyMap())
private fun JsonObject.objects(key: String): List<JsonObject> = (get(key) as? JsonArray)?.filterIsInstance<JsonObject>().orEmpty()
