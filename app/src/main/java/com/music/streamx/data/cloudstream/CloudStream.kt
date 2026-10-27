package com.music.streamx.data.cloudstream

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.music.streamx.data.sources.SourceStream
import com.music.streamx.data.sources.StreamFormat
import com.music.streamx.data.sources.StreamRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.security.MessageDigest

/** An explicit database wins; URI paths are database names, while authSource is only for login. */
internal fun cloudStreamDatabase(uri: String, database: String): String {
    if (database.isNotBlank()) return database.trim()
    val path = runCatching { java.net.URI(uri.trim()).path.orEmpty().trim('/') }.getOrDefault("")
    return path.ifBlank { "Stream" }
}

/** Device-only settings; a failure of Keystore never falls back to plaintext. */
data class CloudStreamConfig(
    val botToken: String = "", val apiId: String = "", val apiHash: String = "",
    val mongoUri: String = "", val database: String = "", val collection: String = "", val enabled: Boolean = false,
    val profileId: String = "", val libraryCollection: String = "streamx_app_library", val accountName: String = "", val accountPhoto: String = "",
) {
    fun validate() {
        require(botToken.matches(Regex("[0-9]+:[A-Za-z0-9_-]+"))) { "Enter a valid Telegram bot token" }
        require((apiId.toIntOrNull() ?: 0) > 0) { "Enter your Telegram API ID" }
        require(apiHash.matches(Regex("[a-fA-F0-9]{32}"))) { "Enter your Telegram API hash" }
        require(mongoUri.startsWith("mongodb+srv://") || mongoUri.startsWith("mongodb://")) { "Enter a MongoDB connection URI" }
        require(database.isNotBlank() && collection.isNotBlank()) { "Enter the database and collection names" }
    }
    fun bundle() = Bundle().apply {
        putString("bot_token", botToken); putInt("api_id", apiId.toIntOrNull() ?: 0); putString("api_hash", apiHash)
        putString("mongo_uri", mongoUri); putString("database", database); putString("collection", collection); putString("profile_id", profileId); putString("library_collection", libraryCollection)
    }
    override fun toString() = "CloudStreamConfig(enabled=$enabled, credentials=redacted)"
}

object CloudStream {
    const val PROFILE = "cloudstream:library"
    const val PACKAGE = "com.music.streamx.cloudstream"
    private const val AUTHORITY = "$PACKAGE.engine"
    private lateinit var context: Context
    val revision = kotlinx.coroutines.flow.MutableStateFlow(0)
    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val uri = Uri.parse("content://$AUTHORITY")
    private val prefs by lazy {
        EncryptedSharedPreferences.create(context, "cloudstream_credentials",
            MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)
    }
    fun init(context: Context) { this.context = context.applicationContext; CloudLibrary.init(context) }
    fun migrate(sources: List<com.music.streamx.data.sources.SourceConfig>) {
        if (read(PROFILE).botToken.isNotBlank()) return
        val previous = sources.firstOrNull { it.kind == com.music.streamx.data.sources.SourceKind.STREAMX && read(it.baseUrl).enabled } ?: return
        save(PROFILE, read(previous.baseUrl).copy(profileId = java.util.UUID.randomUUID().toString()))
    }
    suspend fun engine(method: String, config: CloudStreamConfig = read(PROFILE), argument: JSONObject? = null): JSONObject = withContext(Dispatchers.IO) {
        check(installed()) { "Install the matching CloudStream engine" }
        val result = context.contentResolver.call(uri, method, argument?.toString(), config.bundle()) ?: error("CloudStream engine disconnected")
        check(result.getBoolean("ok")) { result.getString("error") ?: "CloudStream request failed" }
        JSONObject(result.getString("json") ?: "{}")
    }
    suspend fun discover(config: CloudStreamConfig): List<String> {
        val result = engine("discover", config)
        val items = result.getJSONArray("collections")
        return (0 until items.length()).map(items::getString)
    }
    private fun key(endpoint: String) = MessageDigest.getInstance("SHA-256").digest(endpoint.toByteArray()).joinToString("") { "%02x".format(it) }
    fun read(endpoint: String): CloudStreamConfig {
        if (!::context.isInitialized || context.getSharedPreferences("cloudstream_credentials", Context.MODE_PRIVATE).all.isEmpty()) return CloudStreamConfig()
        val j = JSONObject(prefs.getString(key(endpoint), "{}") ?: "{}")
        return CloudStreamConfig(j.optString("bot_token"), j.optString("api_id"), j.optString("api_hash"),
            j.optString("mongo_uri"), j.optString("database"), j.optString("collection"), j.optBoolean("enabled"), j.optString("profile_id"), j.optString("library_collection", "streamx_app_library"), j.optString("account_name"), j.optString("account_photo"))
    }
    fun save(endpoint: String, value: CloudStreamConfig) {
        val j = JSONObject().put("bot_token",value.botToken).put("api_id",value.apiId).put("api_hash",value.apiHash)
            .put("mongo_uri",value.mongoUri).put("database",value.database).put("collection",value.collection).put("enabled",value.enabled).put("profile_id", value.profileId).put("library_collection", value.libraryCollection).put("account_name",value.accountName).put("account_photo",value.accountPhoto)
        check(prefs.edit().putString(key(endpoint),j.toString()).commit()) { "Could not store CloudStream settings" }
        if (endpoint == PROFILE) revision.value += 1
        if (!value.enabled && installed()) io.launch { runCatching { context.contentResolver.call(uri,"close",null,null) } }
    }
    fun installed(): Boolean = runCatching {
        val provider = context.packageManager.resolveContentProvider(AUTHORITY,0) ?: return false
        provider.packageName == PACKAGE && provider.exported && provider.enabled &&
            context.packageManager.checkSignatures(context.packageName,PACKAGE) == PackageManager.SIGNATURE_MATCH
    }.getOrDefault(false)
    suspend fun catalogue(endpoint: String, query: Map<String, String>): String? = withContext(Dispatchers.IO) {
        val config = read(endpoint)
        if (!config.enabled) return@withContext null
        check(installed()) { "Install the matching CloudStream engine" }
        val result = context.contentResolver.call(uri, "catalogue", JSONObject(query).toString(), config.bundle())
            ?: error("CloudStream engine disconnected")
        check(result.getBoolean("ok")) { result.getString("error") ?: "CloudStream catalogue failed" }
        result.getString("json") ?: error("Missing CloudStream catalogue")
    }
    suspend fun logs(clear: Boolean = false): String = withContext(Dispatchers.IO) {
        if (!installed()) return@withContext "Install the CloudStream engine to view connection logs."
        runCatching { context.contentResolver.call(uri, if (clear) "clearLogs" else "logs", null, null)?.getString("logs").orEmpty() }
            .getOrDefault("CloudStream engine unavailable")
    }
    suspend fun verify(config: CloudStreamConfig) = withContext(Dispatchers.IO) {
        config.validate()
        check(installed()) { "Install the matching CloudStream companion APK first" }
        val result = context.contentResolver.call(uri,"verify",null,config.bundle()) ?: error("CloudStream engine disconnected")
        check(result.getBoolean("ok")) { result.getString("error") ?: "CloudStream connection failed" }
    }
    suspend fun stream(endpoint: String, trackId: String, request: StreamRequest): SourceStream? = withContext(Dispatchers.IO) {
        val config = read(endpoint)
        if (!config.enabled) return@withContext null
        check(installed()) { "CloudStream is enabled. Install its matching companion APK or turn it off in StreamX settings." }
        val result = context.contentResolver.call(uri,"prepare",trackId,config.bundle()) ?: error("CloudStream engine disconnected")
        check(result.getBoolean("ok")) { result.getString("error") ?: "CloudStream could not open this track" }
        val j=JSONObject(result.getString("json") ?: error("Missing CloudStream response"))
        val audio=j.getJSONObject("audio")
        val format=StreamFormat(codec=audio.optString("type").ifBlank { null }, kbps=audio.optInt("bitrate_kbps").takeIf { it>0 },
            sampleRateHz=audio.optInt("sampling_rate_hz").takeIf { it>0 }, bitDepth=audio.optInt("bit_depth").takeIf { it>0 })
        if (request is StreamRequest.Capped && (format.isLossless==true || (format.kbps ?: Int.MAX_VALUE)>request.maxKbps))
            error("CloudStream serves original audio; this track exceeds the current quality limit")
        SourceStream(url=j.getString("uri"),format=format,durationSec=audio.optInt("duration_sec").takeIf { it>0 })
    }
}
