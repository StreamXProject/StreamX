package com.music.streamx.playback

import android.net.Uri
import android.os.Bundle
import android.util.Base64
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.drm.DefaultDrmSessionManager
import androidx.media3.exoplayer.drm.DefaultDrmSessionManagerProvider
import androidx.media3.exoplayer.drm.DrmSessionManager
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider
import androidx.media3.exoplayer.drm.ExoMediaDrm
import androidx.media3.exoplayer.drm.FrameworkMediaDrm
import androidx.media3.exoplayer.drm.HttpMediaDrmCallback
import androidx.media3.exoplayer.drm.MediaDrmCallback
import com.music.streamx.data.Http
import com.music.streamx.data.extensions.ApkExtensions
import com.music.streamx.data.sources.SourceRegistry
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Which stream URLs are encrypted, and which extension holds the key to them.
 *
 * The decryption itself never happens in this app: it happens inside the
 * device's Widevine CDM, which hands Media3 a challenge to be signed by the
 * service's licence server and accepts the licence that comes back. The only
 * part this app plays is the courier. The courier is the extension rather
 * than an HTTP POST because a licence server wants the user's session with
 * the challenge — Amazon wraps the challenge in JSON alongside its CSRF
 * tokens — and the session belongs to the extension
 * ([ApkExtensionSource][com.music.streamx.data.sources.ApkExtensionSource]
 * never sees it). So the challenge goes out over `streamx.license.v1` and the
 * extension does the wrapping.
 *
 * Declared per URL, the same way [StreamContainer.declare] records a
 * transport, because that is what every place building a [MediaItem] holds.
 */
object StreamDrm {

    /** The scheme Media3 should use, and the source config that answers for its licences. */
    data class Spec(val scheme: UUID, val configId: String)

    private val declared = ConcurrentHashMap<String, Spec>()

    /**
     * Records that [url] is protected by [scheme] — only `widevine` is
     * accepted, the one CDM every Android device carries — with licences
     * issued through [configId]'s extension.
     *
     * @return false for a scheme this app cannot play, so the caller can
     *   refuse the stream rather than hand the player bytes it cannot decrypt.
     */
    fun declare(url: String, scheme: String, configId: String): Boolean {
        val uuid = when (scheme.lowercase()) {
            "widevine" -> C.WIDEVINE_UUID
            else -> return false
        }
        if (declared.size >= MAX_REMEMBERED) declared.clear()
        declared[url] = Spec(uuid, configId)
        return true
    }

    fun of(url: String): Spec? = declared[url]

    /**
     * The [MediaItem.DrmConfiguration] for [url], or null when it is clear.
     *
     * The licence URI is a marker naming the extension, not a server: the
     * [Provider] reads it back to route the challenge, and nothing ever
     * connects to it.
     */
    fun configurationFor(url: String): MediaItem.DrmConfiguration? {
        val spec = of(url) ?: return null
        return configurationForSource(spec.configId, spec.scheme)
    }

    /** The DRM configuration for an item from [configId], whose licences that extension issues. */
    fun configurationForSource(configId: String, scheme: UUID = C.WIDEVINE_UUID): MediaItem.DrmConfiguration =
        MediaItem.DrmConfiguration.Builder(scheme)
            .setLicenseUri(Uri.Builder().scheme(LICENSE_SCHEME).authority(configId).build())
            .build()

    /**
     * Marks a queued track from an encrypted source as DASH with its licence
     * route, before any player chooses how to open it.
     *
     * Without this every such item reached Media3 as an untyped
     * `streamx://source` URI, so a player first read the manifest as audio,
     * failed, and relied on the session player's manifest reopen to recover —
     * a recovery only the session player has. Automix's standby player, which
     * opens the *next* track for the blend, never recovered: it waited out its
     * arming timeout and the transition fell back to a cut.
     */
    fun decorate(item: MediaItem): MediaItem {
        val local = item.localConfiguration ?: return item
        if (local.mimeType != null || local.drmConfiguration != null) return item
        val uri = local.uri
        if (uri.scheme != "streamx" || uri.authority != "source" || uri.getQueryParameter("extension_native") == "1") return item
        val configId = SourceRegistry.parseTrackKey(item.mediaId)?.first ?: uri.getQueryParameter("s") ?: return item
        if (!com.music.streamx.playback.smart.ProtectedAnalysis.isProtectedSource(configId)) return item
        return item.buildUpon()
            .setMimeType(androidx.media3.common.MimeTypes.APPLICATION_MPD)
            .setDrmConfiguration(configurationForSource(configId))
            .build()
    }

    /** Every player's media source factory, with [decorate] applied first. */
    class Factory(private val inner: androidx.media3.exoplayer.source.MediaSource.Factory) :
        androidx.media3.exoplayer.source.MediaSource.Factory by inner {
        override fun createMediaSource(mediaItem: MediaItem): androidx.media3.exoplayer.source.MediaSource =
            inner.createMediaSource(decorate(mediaItem))
    }

    /**
     * Builds the session manager for an item: ours for an extension-licensed
     * item, Media3's stock one for anything else (which today is nothing, but
     * an addon that ever sends a plain licence URL needs no change here).
     */
    class Provider : DrmSessionManagerProvider {
        override fun get(mediaItem: MediaItem): DrmSessionManager {
            val drm = mediaItem.localConfiguration?.drmConfiguration ?: return DrmSessionManager.DRM_UNSUPPORTED
            val licence = drm.licenseUri
            // Media3's own provider caches one manager per configuration, which
            // is wrong here for the same reason as below.
            if (licence?.scheme != LICENSE_SCHEME) return DefaultDrmSessionManagerProvider().get(mediaItem)
            val configId = licence.authority ?: return DrmSessionManager.DRM_UNSUPPORTED
            // A fresh manager per item, never a shared one: a manager binds to
            // the first player that prepares with it, and the factory behind it
            // is shared by the session player, Automix's standby player and the
            // quality audition. Shared, the standby's prepare of the next track
            // threw on the wrong thread and every blend into an encrypted track
            // fell back to a cut. Each track has its own keys anyway.
            return DefaultDrmSessionManager.Builder()
                .setUuidAndExoMediaDrmProvider(drm.scheme, FrameworkMediaDrm.DEFAULT_PROVIDER)
                .build(ExtensionLicence(configId))
        }
    }

    /**
     * Carries a CDM challenge to the extension and its licence back.
     *
     * Provisioning — the one-time certificate a fresh CDM fetches from Google
     * — is not the service's business and goes straight to Google, exactly as
     * it would for any other app.
     */
    private class ExtensionLicence(private val configId: String) : MediaDrmCallback {
        private val provisioning = HttpMediaDrmCallback(null, OkHttpDataSource.Factory(Http.client))

        override fun executeProvisionRequest(uuid: UUID, request: ExoMediaDrm.ProvisionRequest): MediaDrmCallback.Response =
            provisioning.executeProvisionRequest(uuid, request)

        override fun executeKeyRequest(uuid: UUID, request: ExoMediaDrm.KeyRequest): MediaDrmCallback.Response =
            // Media3 calls this on its own DRM thread and expects it to block.
            MediaDrmCallback.Response(licence(configId, request.data))
    }

    /**
     * Has [configId]'s extension sign a Widevine [challenge], blocking.
     *
     * Shared by playback and by Automix's analysis decode
     * ([ProtectedAnalysis][com.music.streamx.playback.smart.ProtectedAnalysis]),
     * which asks the same CDM for the same keys and must reach the licence
     * server the same way.
     */
    fun licence(configId: String, challenge: ByteArray): ByteArray {
        val config = SourceRegistry.config(configId) ?: error("Licence source $configId is no longer configured")
        val response = runBlocking {
            withTimeout(LICENCE_TIMEOUT_MS) {
                ApkExtensions.call(config, "license", Bundle().apply {
                    putString("scheme", "widevine")
                    putString("challenge", Base64.encodeToString(challenge, Base64.NO_WRAP))
                })
            }
        }
        val value = JSONObject(response)
        check(!value.has("error")) { value.optString("error", "Licence request failed") }
        val licence = value.optString("license").ifBlank { error("Extension returned no licence") }
        return Base64.decode(licence, Base64.DEFAULT)
    }

    private const val LICENSE_SCHEME = "streamx-license"
    private const val LICENCE_TIMEOUT_MS = 20_000L
    private const val MAX_REMEMBERED = 64
}
