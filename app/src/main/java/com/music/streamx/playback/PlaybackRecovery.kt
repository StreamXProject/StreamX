package com.music.streamx.playback

import java.net.URI
import java.util.UUID

/** A retry must reopen the extractor and must not reuse bytes held by an old reader. */
internal object PlaybackRecovery {
    const val CACHE_PARAMETER = "recovery_cache"
    const val AUDIO_PARAMETER = "recovery_audio"

    fun retryUri(uri: String, preferAac: Boolean, token: String = UUID.randomUUID().toString()): String? {
        val parsed = runCatching { URI(uri) }.getOrNull() ?: return null
        if (parsed.scheme != "streamx" || parsed.rawQuery == null) return null
        val parameters = parsed.rawQuery.split('&')
        if (parameters.none { it.startsWith("v=") }) return null
        val useAac = preferAac || parameters.any { it == "$AUDIO_PARAMETER=aac" }
        val kept = parameters.filter {
            it.substringBefore('=') != CACHE_PARAMETER && it.substringBefore('=') != AUDIO_PARAMETER
        }
        val query = kept + "$CACHE_PARAMETER=$token" +
            if (useAac) listOf("$AUDIO_PARAMETER=aac") else emptyList()
        return uri.substringBefore('?') + "?" + query.joinToString("&") +
            (parsed.rawFragment?.let { "#$it" } ?: "")
    }

    fun cacheKey(videoId: String, token: String): String = "$videoId#recovery-$token"
}
