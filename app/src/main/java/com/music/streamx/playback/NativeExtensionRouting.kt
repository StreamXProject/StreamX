package com.music.streamx.playback

import androidx.media3.common.MediaItem
import com.music.streamx.data.sources.SourceRegistry
import java.util.concurrent.ConcurrentHashMap

/** Native routing must reach read-ahead items, before ExoPlayer chooses their extractor. */
internal object NativeExtensionRouting {
    const val APPLE = "com.music.streamx.extensions.applemusic"
    const val SPOTIFY = "com.music.streamx.extensions.spotify"
    private val enabled = ConcurrentHashMap.newKeySet<String>()
    fun update(sourceId: String, native: Boolean) { if (native) enabled.add(sourceId) else enabled.remove(sourceId) }
    fun enabled(sourceId: String, trackId: String): Boolean = sourceId in enabled && !trackId.startsWith("stations:")
    /**
     * What the plugin's native stream is: Apple Music serves HLS; Spotify Premium
     * serves librespot's Ogg Vorbis through its download transfer (see
     * [com.music.streamx.data.sources.SourceResolver.resolve]).
     */
    fun mimeTypeFor(sourceId: String): String =
        if (SourceRegistry.config(sourceId)?.extensionPackage == SPOTIFY) "audio/ogg" else "application/x-mpegURL"

    fun decorate(item: MediaItem): MediaItem {
        val key = SourceRegistry.parseTrackKey(item.mediaId) ?: return item
        val uri = item.localConfiguration?.uri ?: return item
        if (uri.getQueryParameter("ordered_stream") == "1") return item
        if (SourceRegistry.activeForPlayback().firstOrNull()?.configId != key.first) {
            if (uri.getQueryParameter("extension_native") != "1") return item
            val ordered = uri.buildUpon().clearQuery()
            uri.queryParameterNames.filterNot { it == "extension_native" }.forEach { name ->
                uri.getQueryParameters(name).forEach { ordered.appendQueryParameter(name, it) }
            }
            return item.buildUpon().setUri(ordered.build()).setMimeType(null).build()
        }
        if (!enabled(key.first, key.second) || uri.scheme != "streamx" || uri.authority != "source") return item
        if (uri.getQueryParameter("extension_native") == "1") return item
        return item.buildUpon().setUri(uri.buildUpon().appendQueryParameter("extension_native", "1").build())
            .setMimeType(mimeTypeFor(key.first)).setCustomCacheKey(null).build()
    }
}
