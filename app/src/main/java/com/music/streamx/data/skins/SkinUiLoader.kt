package com.music.streamx.data.skins

import android.content.Context
import android.content.pm.PackageManager
import com.music.streamx.data.extensions.ApkExtensions
import com.music.streamx.ui.skins.SKIN_UI_API_VERSION
import com.music.streamx.ui.skins.SkinUiRenderer
import dalvik.system.PathClassLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/** Loads only an installed, certificate-verified skin; shared SDK/framework classes come from the host. */
internal object SkinUiLoader {
    private val loaded = ConcurrentHashMap<String, SkinUiRenderer>()

    suspend fun load(context: Context, skin: SkinSpec): SkinUiRenderer? = withContext(Dispatchers.IO) {
        try {
        val descriptor = skin.renderer ?: return@withContext null
        descriptor.validate()
        val packageName = requireNotNull(skin.extensionPackage) { "Renderer must belong to an installed APK" }
        require(descriptor.entryPoint.startsWith("$packageName.")) { "Renderer class must belong to the skin package" }
        check(ApkExtensions.certificate(packageName) == skin.extensionCertificate) { "Skin signing certificate changed" }
        val info = context.packageManager.getPackageInfo(packageName, 0)
        check(info.longVersionCode == skin.extensionVersion) { "Skin was updated; refresh and apply it again" }
        val cacheKey = "$packageName:${info.longVersionCode}:${info.lastUpdateTime}:${skin.extensionCertificate}:${descriptor.entryPoint}"
        loaded[cacheKey]?.let { return@withContext it }
        val application = requireNotNull(info.applicationInfo)
        // Installed APK paths are read-only. Never execute a downloaded or writable cache file.
        val paths = (listOf(application.sourceDir) + application.splitSourceDirs.orEmpty()).joinToString(java.io.File.pathSeparator)
        val classLoader = PathClassLoader(paths, context.classLoader)
        val type = Class.forName(descriptor.entryPoint, true, classLoader)
        require(type.classLoader === classLoader && SkinUiRenderer::class.java.isAssignableFrom(type)) { "Invalid skin UI entry point" }
        com.music.streamx.ui.skins.validateSkinThemeImplementation(type)
        val renderer = type.getDeclaredConstructor().newInstance() as SkinUiRenderer
        require(renderer.apiVersion == SKIN_UI_API_VERSION) { "Skin UI API does not match this host" }
        check(ApkExtensions.certificate(packageName) == skin.extensionCertificate) { "Skin changed while loading" }
        renderer.verifyCompatibility()
        loaded[cacheKey] = renderer
        renderer
        } catch (error: LinkageError) {
            throw IllegalArgumentException("Skin is incompatible with the host UI runtime", error)
        }
    }
}
