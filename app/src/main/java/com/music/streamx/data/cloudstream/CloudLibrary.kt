package com.music.streamx.data.cloudstream

import android.content.Context
import com.music.streamx.data.sources.SourceConfig
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** Local-first app-owned library. Pending entities survive process death; tracks stay read-only. */
object CloudLibrary {
    private lateinit var root: File
    private val lock = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val revision = MutableStateFlow(0)
    val status = MutableStateFlow("Changes are saved on this device")
    private var lastSync = 0L
    private var lastSyncProfile = ""
    private val syncing = Mutex()
    fun init(context: Context) {
        root = File(context.noBackupFilesDir,"cloud_library").apply { mkdirs() }
        scope.launch { while (true) { delay(60_000); if (CloudStream.read(CloudStream.PROFILE).enabled) sync() } }
    }
    fun identity(): String = profile()
    private fun profile(): String {
        val c = CloudStream.read(CloudStream.PROFILE)
        return java.security.MessageDigest.getInstance("SHA-256").digest("${c.mongoUri.substringAfter("@")}|${c.database}|${c.collection}|${c.profileId}".toByteArray()).joinToString("") { "%02x".format(it) }
    }
    private fun file(name: String) = File(root, profile().filter { it.isLetterOrDigit() || it == '-' }.take(80) + "-$name.json")
    private fun load(name: String) = runCatching { JSONObject(file(name).readText()) }.getOrDefault(JSONObject())
    private fun store(name: String, data: JSONObject) {
        val target = android.util.AtomicFile(file(name))
        val output = target.startWrite()
        try { output.write(data.toString().toByteArray()); target.finishWrite(output) }
        catch (failure: Exception) { target.failWrite(output); throw failure }
    }
    private fun entities() = LibraryMerge.apply(load("account"), org.json.JSONArray(load("library").let { local -> local.keys().asSequence().map { local.getJSONObject(it) }.toList() }))
    private fun records(data: JSONObject, kind: String) = data.keys().asSequence().map { data.getJSONObject(it) }
        .filter { it.optString("kind") == kind && !it.optBoolean("deleted") }.toList()
    private suspend fun mutate(key: String, value: JSONObject) = lock.withLock {
        val now = LibraryMerge.nextTimestamp(System.currentTimeMillis(), entities().optJSONObject(key)?.optLong("updated") ?: 0L)
        value.put("key", key).put("updated", now)
        store("pending", load("pending").put(key,value))
        store("library",load("library").put(key,value))
        revision.value += 1
        status.value = "Saved on this device · waiting to sync"
        scope.launch { sync() }
    }
    suspend fun sync() {
        if (!syncing.tryLock()) return
        try {
            val expected = profile()
            val connection = CloudStream.read(CloudStream.PROFILE)
            val pending = lock.withLock { load("pending") }
            val sent = pending.keys().asSequence().map { pending.getJSONObject(it) }.toList()
            val remote = JSONArray()
            var account: JSONObject? = null
            var response = JSONObject()
            try {
                for (batch in sent.chunked(40).ifEmpty { listOf(emptyList()) }) {
                    response = CloudStream.engine("library", config = connection, argument = JSONObject().put("changes",JSONArray(batch)))
                }
                var cursor = ""
                do {
                    if (cursor.isNotBlank()) response = CloudStream.engine("library", config = connection, argument = JSONObject().put("after",cursor))
                    val items = response.optJSONArray("items") ?: JSONArray()
                    for (i in 0 until items.length()) remote.put(items.getJSONObject(i))
                    val next = response.optString("next")
                    check(next.isBlank() || next != cursor) { "Library sync repeated a page" }
                    cursor = next
                } while (cursor.isNotBlank())
                if (connection.profileId.startsWith("user:")) account = accountSnapshot(connection)
                lock.withLock {
                    if (profile() != expected) return@withLock
                    val before = entities()
                    val merged = LibraryMerge.apply(load("library"),remote)
                    val stillPending = LibraryMerge.acknowledge(load("pending"),sent)
                    store("library",merged); store("pending",stillPending)
                    account?.let { store("account",it) }
                    if (before.toString() != entities().toString()) revision.value += 1
                    lastSync = System.currentTimeMillis(); lastSyncProfile = expected; status.value = if (stillPending.length()==0) "Library synced" else "Saved locally · changes waiting to sync"
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { status.value = if (pending.length()>0) "Saved locally · cloud sync unavailable; check library write access" else "Offline library · cloud sync unavailable" }
        } finally { syncing.unlock() }
    }
    private suspend fun accountSnapshot(connection: CloudStreamConfig): JSONObject {
        val result = JSONObject()
        suspend fun pages(kind: String, playlist: String? = null): List<Any> {
            val rows = mutableListOf<Any>()
            var alias = 0
            var count = 1
            while (alias < count) {
                var page = 1
                do {
                    val query = JSONObject().put("kind",kind).put("alias",alias).put("page",page)
                    playlist?.let { query.put("playlist_id",it) }
                    val response = CloudStream.engine("accountLibrary",connection,query)
                    count=response.getInt("aliases")
                    val items=response.getJSONArray("items")
                    for(i in 0 until items.length())rows.add(items.get(i))
                    page++
                } while(response.getBoolean("more"))
                alias++
            }
            return rows
        }
        for(raw in pages("favourites")) {
            val item=raw as JSONObject
            result.put(item.getString("key"),item)
        }
        for(raw in pages("playlists")) {
            val item=raw as JSONObject
            val ids=pages("tracks",item.getString("playlist_id")).map { it.toString() }.distinct()
            item.put("track_ids",JSONArray(ids))
            result.put(item.getString("key"),item)
        }
        return result
    }
    private suspend fun browse(path: List<String>, query: Map<String,String>): JSONObject {
        val argument = JSONObject(query).put("path", JSONArray(path))
        val key = "browse-" + java.security.MessageDigest.getInstance("SHA-256").digest(argument.toString().toByteArray()).joinToString("") { "%02x".format(it) }
        val expected = profile()
        val connection = CloudStream.read(CloudStream.PROFILE)
        return try {
            CloudStream.engine("browse", connection, argument).also { response ->
                check(profile() == expected) { "CloudStream library changed" }
                store(key,response)
                val items = response.optJSONArray("items")
                if (items != null) lock.withLock {
                    val metadata = load("tracks")
                    for (i in 0 until items.length()) {
                        val item = items.getJSONObject(i)
                        if (item.has("audio")) metadata.put(item.getString("_id"),item)
                    }
                    val retained = records(entities(),"favourite").map { it.optString("track_id") }.toMutableSet()
                    records(entities(),"playlist").forEach { playlist ->
                        val ids = playlist.optJSONArray("track_ids") ?: JSONArray()
                        for (i in 0 until ids.length()) retained.add(ids.getString(i))
                    }
                    metadata.keys().asSequence().toList().filterNot { it in retained }.drop(800).forEach(metadata::remove)
                    store("tracks",metadata)
                }
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { if (profile() != expected) throw failure; load(key).takeIf { it.has("items") || it.has("audio") } ?: throw failure }
    }
    private suspend fun tracks(ids: List<String>): JSONArray {
        if (ids.isEmpty()) return JSONArray()
        val result = JSONArray()
        for (batch in ids.chunked(80)) {
            val rows = try { browse(listOf("tracks"), mapOf("ids" to JSONArray(batch).toString(), "limit" to "100")).getJSONArray("items") }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { val metadata=load("tracks"); JSONArray(batch.mapNotNull { metadata.optJSONObject(it) }) }
            val byId = (0 until rows.length()).map { rows.getJSONObject(it) }.associateBy { it.optString("_id") }
            batch.forEach { byId[it]?.let(result::put) }
        }
        return result
    }
    suspend fun request(config: SourceConfig, path: List<String>, query: Map<String,String>, method: String, body: JsonObject?): JsonObject {
        check(CloudStream.read(CloudStream.PROFILE).enabled) { "Configure CloudStream in Sources" }
        val b = JSONObject(body?.toString() ?: "{}")
        if ((lastSyncProfile != profile() || System.currentTimeMillis() - lastSync > 30_000) && method == "GET" && path.firstOrNull() in listOf("me", "auth")) {
            lastSync = System.currentTimeMillis(); lastSyncProfile = profile(); scope.launch { sync() }
        }
        val result: JSONObject
        if (path.firstOrNull() == "auth") result = JSONObject().put("user", JSONObject().put("first_name", "CloudStream"))
        else if (path.firstOrNull() != "me") result = browse(path, query)
        else {
            val resource = path.getOrNull(1)
            result = when (resource) {
                "favourites" -> {
                    val id = path.getOrNull(2) ?: b.optString("track_id")
                    if (method != "GET") mutate("favourite:$id", JSONObject().put("kind", "favourite").put("track_id", id).put("deleted", method == "DELETE"))
                    if (method == "GET") JSONObject().put("items", tracks(records(entities(), "favourite").map { it.getString("track_id") })) else JSONObject()
                }
                "playlists" -> {
                    val id = path.getOrNull(2)
                    val all = entities()
                    if (method == "POST" && id == null) {
                        val next = UUID.randomUUID().toString()
                        mutate("playlist:$next", JSONObject().put("kind", "playlist").put("playlist_id",next).put("name",b.optString("name")).put("cover_url",b.optString("cover_url")).put("track_ids",JSONArray()))
                        JSONObject().put("playlist_id",next)
                    } else if (id != null) {
                        val item = all.optJSONObject("playlist:$id") ?: error("Playlist not found in this CloudStream library")
                        if (method == "DELETE" && path.size == 3) { mutate("playlist:$id", item.put("deleted",true)); JSONObject() }
                        else if (method == "PATCH") { mutate("playlist:$id",item.put("name",b.optString("name"))); JSONObject() }
                        else if (path.getOrNull(3) == "tracks") {
                            var ids = item.optJSONArray("track_ids") ?: JSONArray()
                            if (method == "POST") {
                                val incoming=b.optJSONArray("track_ids") ?: JSONArray()
                                val unique=(0 until ids.length()).map(ids::getString).toMutableList()
                                for(i in 0 until incoming.length()) if(incoming.getString(i) !in unique) unique.add(incoming.getString(i))
                                ids=JSONArray(unique);mutate("playlist:$id",item.put("track_ids",ids))
                            } else if(method == "DELETE") {
                                ids=JSONArray((0 until ids.length()).map(ids::getString).filterNot { it == path.getOrNull(4) })
                                mutate("playlist:$id",item.put("track_ids",ids))
                            }
                            if (method == "GET") JSONObject().put("items",tracks((0 until ids.length()).map(ids::getString))) else JSONObject()
                        } else JSONObject()
                    } else JSONObject().put("items",JSONArray(records(all,"playlist")))
                }
                else -> JSONObject().put("items",JSONArray()).put("ids",JSONArray())
            }
        }
        return Json.parseToJsonElement(result.toString()).jsonObject
    }
}
