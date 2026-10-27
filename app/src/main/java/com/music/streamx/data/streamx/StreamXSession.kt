package com.music.streamx.data.streamx

import android.content.Context
import android.content.SharedPreferences
import com.music.streamx.auth.EncryptedPrefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.util.UUID

@Serializable
data class StreamXAccountSession(
    val endpoint: String = "",
    val token: String = "",
    val userId: Long? = null,
    val name: String = "",
    val photo: String? = null,
    val scope: String = UUID.randomUUID().toString(),
    val expired: Boolean = false,
) {
    val personalAccount: Boolean get() = token.isNotBlank() && userId != null
}

@Serializable
data class StreamXServer(val id: String, val endpoint: String, val name: String)

/** Uses the existing encrypted source store, already excluded from Android backups. */
object StreamXSession {
    private lateinit var prefs: SharedPreferences
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    val state = MutableStateFlow(StreamXAccountSession())
    val servers = MutableStateFlow<List<StreamXServer>>(emptyList())

    fun init(context: Context) {
        prefs = EncryptedPrefs.open(context, "streamx_sources", "streamx_sources_plain")
        state.value = runCatching {
            json.decodeFromString<StreamXAccountSession>(prefs.getString("streamx_session", null) ?: "{}")
        }.getOrDefault(StreamXAccountSession())
        servers.value = runCatching {
            json.decodeFromString(ListSerializer(StreamXServer.serializer()), prefs.getString("streamx_servers", null) ?: "[]")
        }.getOrDefault(emptyList()).distinctBy { it.endpoint }
    }

    @Synchronized
    fun rememberServer(id: String, endpoint: String, name: String) {
        val existing = servers.value.firstOrNull { it.endpoint == endpoint }
        val server = StreamXServer(existing?.id ?: id, endpoint, name.trim().ifBlank {
            existing?.name ?: android.net.Uri.parse(endpoint).host ?: endpoint
        })
        val retained = servers.value.filterNot { it.id == id && it.endpoint != endpoint }
        val next = if (existing == null) retained + server else retained.map { if (it.endpoint == endpoint) server else it }
        if (next != servers.value) saveServers(next)
    }

    @Synchronized
    fun removeServer(id: String) {
        val server = servers.value.firstOrNull { it.id == id } ?: return
        check(server.endpoint != state.value.endpoint) { "Switch to another server before removing the active one." }
        saveServers(servers.value.filterNot { it.id == id })
    }

    private fun saveServers(next: List<StreamXServer>) {
        prefs.edit().putString("streamx_servers", json.encodeToString(ListSerializer(StreamXServer.serializer()), next)).apply()
        servers.value = next
    }

    @Synchronized
    fun connect(endpoint: String) {
        if (state.value.endpoint != endpoint) save(StreamXAccountSession(endpoint = endpoint))
    }

    @Synchronized
    fun authenticated(expected: StreamXAccountSession, token: String, userId: Long?, name: String, photo: String?) {
        check(state.value.scope == expected.scope) { "Server changed. Please sign in again." }
        save(expected.copy(token = token, userId = userId, name = name, photo = photo,
            scope = UUID.randomUUID().toString(), expired = false))
    }

    @Synchronized
    fun expire(expected: StreamXAccountSession) {
        if (expected.token.isNotBlank() && state.value.scope == expected.scope) {
            save(StreamXAccountSession(endpoint = expected.endpoint, expired = true))
        }
    }

    @Synchronized
    fun signOut() = save(StreamXAccountSession(endpoint = state.value.endpoint))

    private fun save(next: StreamXAccountSession) {
        prefs.edit().putString("streamx_session", json.encodeToString(StreamXAccountSession.serializer(), next)).apply()
        state.value = next
    }
}
