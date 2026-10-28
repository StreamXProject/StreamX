package com.music.streamx.data.extensions

import android.content.BroadcastReceiver
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.net.Uri
import com.music.streamx.data.skins.SkinRegistry
import com.music.streamx.data.skins.SkinSpec
import com.music.streamx.data.sources.SourceConfig
import kotlinx.coroutines.CancellationException
import com.music.streamx.data.sources.SourceRegistry
import com.music.streamx.data.sources.SourceKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.security.MessageDigest

/** Public, versioned IPC/asset contract. Extension code stays inside its APK. */
data class ApkExtension(
    val packageName: String,
    val name: String,
    val component: ComponentName,
    val certificate: String,
    val versionCode: Long,
    val skinAsset: String?,
    val sourceAuthority: String?,
    val playbackMode: String? = null,
    val playbackService: ComponentName? = null,
) {
    val skinId: String get() = "apk-" + digest(packageName.toByteArray()).take(24)
    companion object {
        internal fun digest(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    }
}
object ApkExtensions {
    const val ACTION = "com.music.streamx.action.EXTENSION"
    private lateinit var context: Context
    val settingsRequested = MutableStateFlow<ApkExtension?>(null)
    fun showSettings(extension: ApkExtension) {
        if(extension.playbackMode=="remote_web_player")settingsRequested.value=extension
        else context.startActivity(configurationIntent(extension).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
    val installed = MutableStateFlow<List<ApkExtension>>(emptyList())
    // CloudStream uses its original signed engine protocol, not the generic source API.
    val cloudStreamVersion = MutableStateFlow<Long?>(null)
    val scanError = MutableStateFlow<String?>(null)
    /** Changes even when the discovery list is equal, so package-manager UI caches can refresh. */
    val packageRevision = MutableStateFlow(0L)
    private val refreshMutex = Mutex()
    private val packageScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var packageReceiverRegistered = false
    @Synchronized fun init(context: Context) {
        this.context = context.applicationContext
        if (!packageReceiverRegistered) {
            val filter=IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_ADDED)
                addAction(Intent.ACTION_PACKAGE_REMOVED)
                addAction(Intent.ACTION_PACKAGE_REPLACED)
                addDataScheme("package")
            }
            ContextCompat.registerReceiver(this.context,object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    // A replacement emits REMOVED before ADDED; do not disable the source in between.
                    if(intent.action==Intent.ACTION_PACKAGE_REMOVED && intent.getBooleanExtra(Intent.EXTRA_REPLACING,false)) return
                    val pending=goAsync()
                    packageScope.launch { try { kotlinx.coroutines.withTimeout(8_000) { refresh() } } finally { pending.finish() } }
                }
            },filter,ContextCompat.RECEIVER_NOT_EXPORTED)
            packageReceiverRegistered=true
        }
    }

    @Suppress("DEPRECATION")
    private fun packageInfo(packageName: String) = context.packageManager.getPackageInfo(packageName,
        if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES)

    @Suppress("DEPRECATION")
    fun certificate(packageName: String): String {
        val info = packageInfo(packageName)
        val signers = if (Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners.orEmpty() else info.signatures.orEmpty()
        check(signers.isNotEmpty()) { "Could not verify extension signing certificate" }
        return signers.map { ApkExtension.digest(it.toByteArray()) }.sorted().joinToString(":")
    }
    private fun verifyProvider(packageName: String, authority: String) {
        val info = context.packageManager.resolveContentProvider(authority, 0) ?: error("Extension provider is unavailable")
        check(info.packageName == packageName && info.exported && info.enabled) { "Extension provider does not belong to this installed APK" }
    }
    @Suppress("DEPRECATION")
    suspend fun refresh() = withContext(Dispatchers.IO) {
        refreshMutex.withLock {
            try {
                cloudStreamVersion.value = if (com.music.streamx.data.cloudstream.CloudStream.installed()) {
                    val info = packageInfo(com.music.streamx.data.cloudstream.CloudStream.PACKAGE)
                    if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
                } else null
                val entries = context.packageManager.queryIntentActivities(Intent(ACTION).addCategory(Intent.CATEGORY_DEFAULT), PackageManager.GET_META_DATA)
                val discovered = entries.mapNotNull { entry ->
                    runCatching {
                        val activity = entry.activityInfo
                        if (!activity.exported || !activity.enabled) return@runCatching null
                        val metadata = activity.metaData ?: return@runCatching null
                        if (metadata.getInt("streamx.extension.api", 0) != 1) return@runCatching null
                        val type = metadata.getString("streamx.extension.type") ?: return@runCatching null
                        if (type !in setOf("skin", "source", "both")) return@runCatching null
                        val asset = if (type != "source") metadata.getString("streamx.extension.skinAsset")?.also {
                            require(it.length <= 128 && !it.startsWith('/') && ".." !in it && Regex("[A-Za-z0-9_./-]+\\.json").matches(it))
                        } ?: error("Missing skin asset") else null
                        val authority = if (type != "skin") metadata.getString("streamx.extension.authority")?.also {
                            require(Regex("[A-Za-z0-9_.-]+").matches(it))
                            verifyProvider(activity.packageName, it)
                        } ?: error("Missing source provider") else null
                        val info = packageInfo(activity.packageName)
                        ApkExtension(activity.packageName, entry.loadLabel(context.packageManager).toString().take(80),
                            ComponentName(activity.packageName, activity.name), certificate(activity.packageName),
                            if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong(), asset, authority,
                            activity.metaData?.getString("streamx.extension.playback")?.takeIf { it in setOf("web_player", "remote_web_player") },
                            activity.metaData?.getString("streamx.extension.playback_service")?.let { ComponentName(activity.packageName, it) })
                    }.getOrNull()
                }.distinctBy { it.packageName }
                installed.value = discovered
                scanError.value = null
                SourceRegistry.configs.value.filter { it.kind == SourceKind.APK_PLUGIN && !SflxBridge.handles(it) }.forEach { config ->
                    val current = discovered.firstOrNull { it.packageName == config.extensionPackage }
                    if (current == null || current.certificate != config.extensionCertificate || current.sourceAuthority != config.extensionAuthority) {
                        if (config.enabled) SourceRegistry.setEnabled(config.id, false)
                    } else if (current.name != config.label) {
                        SourceRegistry.update(config.copy(label = current.name))
                    }
                }
                // Refresh every referenced override without changing the main selection.
                val overrideIds = SkinRegistry.sectionOverrides.value.values.toSet()
                SkinRegistry.skins.value.filter { it.id in overrideIds && it.extensionPackage != null }.forEach { spec ->
                    val current = discovered.firstOrNull { it.packageName == spec.extensionPackage && it.skinAsset != null }
                    if(current == null || current.certificate != spec.extensionCertificate) SkinRegistry.remove(spec.id)
                    else if(current.versionCode != spec.extensionVersion) runCatching { activateSkin(current, activate=false) }.onFailure { failure ->
                        if(failure is CancellationException) throw failure
                        SkinRegistry.sectionOverrides.value.filterValues { it == spec.id }.keys.forEach { SkinRegistry.selectSection(it,null) }
                        scanError.value = "A section skin could not be updated: ${failure.message}"
                    }
                }
                val skin = SkinRegistry.active.value
                skin.extensionPackage?.let { packageName ->
                    val current = discovered.firstOrNull { it.packageName == packageName && it.skinAsset != null }
                    if (current == null || current.certificate != skin.extensionCertificate) SkinRegistry.select("default")
                    else if (current.versionCode != skin.extensionVersion) runCatching { activateSkin(current) }.onFailure {
                        if (it is CancellationException) throw it
                        SkinRegistry.select("default")
                        scanError.value = "The updated skin could not be loaded: ${it.message}"
                    }
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { scanError.value = e.message ?: "Could not find installed extensions" }
            finally { packageRevision.value++ }
        }
    }
    suspend fun activateSkin(extension: ApkExtension, activate: Boolean = true) = withContext(Dispatchers.IO) {
        check(certificate(extension.packageName) == extension.certificate) { "Extension changed. Refresh the list first." }
        val packageContext = context.createPackageContext(extension.packageName, 0)
        val content = packageContext.assets.open(extension.skinAsset ?: error("This APK has no skin")).use { input ->
            val buffer = ByteArray(65_537); var count = 0
            while (count < buffer.size) {
                val read = input.read(buffer, count, buffer.size - count)
                if (read < 0) break
                count += read
            }
            require(count <= 65_536) { "Skin asset exceeds 64 KiB" }
            String(buffer, 0, count, Charsets.UTF_8)
        }
        val spec = Json { ignoreUnknownKeys = false }.decodeFromString(SkinSpec.serializer(), content)
            .copy(id = extension.skinId, extensionPackage = extension.packageName,
                extensionCertificate = extension.certificate, extensionVersion = extension.versionCode)
        SkinRegistry.validate(spec)
        com.music.streamx.data.skins.SkinFontsLoader.load(context, spec)
        com.music.streamx.data.skins.SkinUiLoader.load(context, spec)
        SkinRegistry.installSpec(spec, activate)
    }
    fun configurationIntent(extension: ApkExtension): Intent {
        val mode = com.music.streamx.data.settings.AppSettings.themeMode.value.name
        val dark = when (mode) {
            "LIGHT" -> false
            "SYSTEM" -> context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK == android.content.res.Configuration.UI_MODE_NIGHT_YES
            else -> true
        }
        val skin = com.music.streamx.data.skins.resolveSectionSkin(SkinRegistry.active.value, SkinRegistry.skins.value,
            SkinRegistry.sectionOverrides.value, com.music.streamx.data.skins.SkinSection.SETTINGS)
        val palette = SkinRegistry.runtimePalettes[skin.id to dark] ?: (if (dark) skin.dark else skin.light) ?: com.music.streamx.data.skins.SkinPalette(
            "#FFFFFF", "#F7F7F9", "#F2F2F7", "#000000", "#6E6E73", "#000000")
        return Intent().setComponent(extension.component)
            .putExtra("streamx.ui.dark", dark)
            .putExtra("streamx.ui.palette", Json.encodeToString(com.music.streamx.data.skins.SkinPalette.serializer(), palette))
            .putExtra("streamx.ui.cornerRadiusDp", skin.cornerRadiusDp ?: 22)
            .putExtra("streamx.ui.settingsLayout", skin.layout.settings.name)
            .putExtra("streamx.ui.material", skin.renderer?.entryPoint?.let { it.startsWith("com.music.streamx.skins.material.") || it.startsWith("com.music.streamx.skins.expressive.") } == true)
    }

    fun usesRemoteWebPlayer(config: SourceConfig): Boolean {
        if(!config.enabled)return false
        fun matches(extension: ApkExtension)=extension.packageName==config.extensionPackage && extension.certificate==config.extensionCertificate &&
            extension.sourceAuthority==config.extensionAuthority && extension.playbackMode=="remote_web_player"
        if(installed.value.any(::matches))return true
        // Queue restoration can happen before the screen's asynchronous extension scan.
        // Resolve this installed package now so protected tracks do not accidentally enter URL fallback.
        val found=runCatching {
            @Suppress("DEPRECATION")
            val entry=context.packageManager.queryIntentActivities(Intent(ACTION).addCategory(Intent.CATEGORY_DEFAULT)
                .setPackage(config.extensionPackage),PackageManager.GET_META_DATA).firstOrNull { result ->
                val activity=result.activityInfo;val metadata=activity.metaData
                activity.enabled && activity.exported && metadata?.getInt("streamx.extension.api")==1 &&
                    metadata.getString("streamx.extension.authority")==config.extensionAuthority &&
                    metadata.getString("streamx.extension.playback")=="remote_web_player"
            } ?: return@runCatching null
            val activity=entry.activityInfo
            val cert=certificate(config.extensionPackage)
            check(cert==config.extensionCertificate){"Extension signing certificate changed"}
            verifyProvider(config.extensionPackage,config.extensionAuthority)
            ApkExtension(activity.packageName,entry.loadLabel(context.packageManager).toString().take(80),
                ComponentName(activity.packageName,activity.name),cert,0,null,config.extensionAuthority,"remote_web_player",
                activity.metaData.getString("streamx.extension.playback_service")?.let {ComponentName(activity.packageName,it)})
        }.getOrNull() ?: return false
        installed.value=(installed.value+found).distinctBy {it.packageName}
        return true
    }
    suspend fun playbackClient(config: SourceConfig) = withContext(Dispatchers.IO) {
        check(usesRemoteWebPlayer(config)) { "Remote extension is disabled or unavailable" }
        check(certificate(config.extensionPackage) == config.extensionCertificate) { "Extension signing certificate changed" }
        verifyProvider(config.extensionPackage, config.extensionAuthority)
        val extension = installed.value.first { it.packageName == config.extensionPackage }
        val component = extension.playbackService ?: error("Extension is missing its playback service")
        val service = context.packageManager.getServiceInfo(component, 0)
        check(service.packageName == config.extensionPackage && service.exported && service.enabled &&
            service.permission == "com.music.streamx.permission.WEB_PLAYBACK") { "Invalid extension playback service" }
        context.startForegroundService(Intent().setComponent(component))
        try {
            context.contentResolver.acquireContentProviderClient(Uri.parse("content://${config.extensionAuthority}"))
                ?: error("Extension playback provider is unavailable")
        } catch (failure: Exception) {
            if(component.className.endsWith(".SpotifyPlaybackService"))
                context.startService(Intent("streamx.playback.STOP").setComponent(component))
            else context.stopService(Intent().setComponent(component))
            throw failure
        }
    }

    /** Optional activity playback; no media URL or credentials cross the boundary. */
    fun webPlaybackIntent(config: SourceConfig, trackId: String): Intent? {
        val extension = installed.value.firstOrNull {
            it.packageName == config.extensionPackage && it.sourceAuthority == config.extensionAuthority
        } ?: return null
        if (extension.playbackMode != "web_player") return null
        check(config.enabled && config.kind == SourceKind.APK_PLUGIN) { "Extension is disabled" }
        check(extension.certificate == config.extensionCertificate && certificate(extension.packageName) == config.extensionCertificate) {
            "Extension signing certificate changed. Refresh Sources."
        }
        require(trackId.isNotBlank() && trackId.length <= 256) { "Invalid extension track" }
        return configurationIntent(extension).putExtra("streamx.playback.track_id", trackId)
    }

    /** Optional short-lived binding keeps a provider awake on Android's cached-process freezer. */
    private suspend fun bindSource(config: SourceConfig): android.content.ServiceConnection? {
        @Suppress("DEPRECATION")
        val name=context.packageManager.queryIntentActivities(Intent(ACTION).addCategory(Intent.CATEGORY_DEFAULT)
            .setPackage(config.extensionPackage),PackageManager.GET_META_DATA).firstOrNull()?.activityInfo?.metaData
            ?.getString("streamx.extension.connection_service") ?: return null
        val component=ComponentName(config.extensionPackage,name)
        val service=context.packageManager.getServiceInfo(component,0)
        check(service.exported && service.enabled && service.packageName==config.extensionPackage &&
            service.permission=="com.music.streamx.permission.WEB_PLAYBACK"){"Invalid source connection service"}
        val ready=kotlinx.coroutines.CompletableDeferred<Unit>()
        val connection=object : android.content.ServiceConnection {
            override fun onServiceConnected(name: ComponentName,binder: android.os.IBinder){ready.complete(Unit)}
            override fun onServiceDisconnected(name: ComponentName){}
            override fun onNullBinding(name: ComponentName){ready.completeExceptionally(IllegalStateException("Source connection unavailable"))}
        }
        check(context.bindService(Intent().setComponent(component),connection,Context.BIND_AUTO_CREATE or Context.BIND_IMPORTANT)){"Could not connect to source"}
        try {kotlinx.coroutines.withTimeout(5000){ready.await()}}
        catch(failure: Throwable){context.unbindService(connection);throw failure}
        return connection
    }

    suspend fun call(config: SourceConfig, method: String, extras: Bundle = Bundle()): String = withContext(Dispatchers.IO) {
        // A SpotiFLAC extension answers the same protocol without an APK behind it.
        if (SflxBridge.handles(config)) return@withContext SflxBridge.call(config, method, extras)
        check(config.extensionPackage.isNotBlank() && config.extensionAuthority.isNotBlank()) { "Extension has no provider" }
        check(certificate(config.extensionPackage) == config.extensionCertificate) { "Extension signing certificate changed. Re-enable the installed APK." }
        verifyProvider(config.extensionPackage, config.extensionAuthority)
        val binding=bindSource(config)
        try {
        val readOnly = method in setOf("home", "browse", "radio", "search_landing", "search_page", "lyrics", "library", "history", "account", "health", "search", "detail", "playlists", "preferences", "plan", "playback_route", "saved", "library_item_actions")
        var payload: String? = null
        repeat(if (readOnly) 2 else 1) { attempt ->
            if (payload == null) {
                try {
                    context.contentResolver.acquireContentProviderClient(Uri.parse("content://${config.extensionAuthority}"))?.use { client ->
                        payload = client.call("streamx.$method.v1", null, extras)?.getString("json")
                    }
                } catch (dead: android.os.DeadObjectException) {
                    if (!readOnly || attempt == 1) throw dead
                }
                if (payload == null && readOnly && attempt == 0) kotlinx.coroutines.delay(250)
            }
        }
        val response = payload ?: error("${config.displayName} provider disconnected during $method. Try again.")
        require(response.length <= 262_144) { "Extension response exceeds 256 KiB" }
        if (config.extensionPackage == com.music.streamx.playback.NativeExtensionRouting.APPLE && method in setOf("preferences", "set_preferences", "playback_route")) {
            val value = org.json.JSONObject(response)
            if (!value.has("error")) {
                if (value.has("alac_enabled")) com.music.streamx.playback.NativeExtensionRouting.update(config.id, value.optBoolean("alac_enabled"))
                else if (value.optString("backend") == "stream") com.music.streamx.playback.NativeExtensionRouting.update(config.id, true)
            }
        }
        response
        } finally {binding?.let {context.unbindService(it)}}
    }
}
