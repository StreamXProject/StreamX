package com.music.streamx

import com.music.streamx.data.innertube.StreamResolver
import kotlinx.coroutines.runBlocking
import com.music.streamx.data.sources.SourceConfig
import com.music.streamx.data.sources.SourceKind
import com.music.streamx.data.sources.SourceRegistry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class YouTubeFallbackPolicyTest {
    @Test fun `explicit audio fallback can reopen a URL with the catalogue disabled`() = runBlocking {
        val priorPolicy = StreamResolver.sourceEnabled
        val priorPlaybackPolicy = StreamResolver.playbackEnabled
        try {
            StreamResolver.sourceEnabled = { false }
            StreamResolver.playbackEnabled = { StreamResolver.sourceEnabled() }
            val id = "fallback-policy-fixture"
            val url = "https://example.invalid/audio.webm"
            StreamResolver::class.java.getDeclaredMethod("remember", String::class.java, String::class.java).apply {
                isAccessible = true
                invoke(StreamResolver, id, url)
            }
            val ordinary = runCatching { StreamResolver.resolve(id) }.exceptionOrNull()
            assertTrue(ordinary is IllegalStateException)
            assertEquals("YouTube Music is disabled in Sources", ordinary?.message)
            assertEquals(url, StreamResolver.resolve(id, explicitFallback = true))
            assertEquals(url, StreamResolver.resolve(id, explicitFallback = true))
            assertTrue(runCatching { StreamResolver.resolveForDownload(id, 128) }.exceptionOrNull() is IllegalStateException)
        } finally {
            StreamResolver.sourceEnabled = priorPolicy
            StreamResolver.playbackEnabled = priorPlaybackPolicy
        }
    }
    @Test fun `Spotify conversion and read ahead resolve without enabling the YouTube catalogue`() = runBlocking {
        val priorSource = StreamResolver.sourceEnabled
        val priorPlayback = StreamResolver.playbackEnabled
        var sources = listOf(SourceConfig(
            id = "spotify", kind = SourceKind.APK_PLUGIN, enabled = true,
            extensionPackage = "com.music.streamx.extensions.spotify",
            extensionAuthority = "spotify.source", extensionCertificate = "fixture",
        ))
        try {
            StreamResolver.sourceEnabled = { false }
            StreamResolver.playbackEnabled = { SourceRegistry.permitsYouTubePlayback(sources) }
            val id = "conversion-policy-fixture"
            val url = "https://example.invalid/converted.webm"
            StreamResolver::class.java.getDeclaredMethod("remember", String::class.java, String::class.java).apply {
                isAccessible = true
                invoke(StreamResolver, id, url)
            }
            // Converted MediaItems and analysis reopen by the YouTube id with no fallback flag.
            assertEquals(url, StreamResolver.resolve(id))
            assertEquals(url, StreamResolver.resolve(id))
            assertFalse(StreamResolver.sourceEnabled())
            assertTrue(runCatching { StreamResolver.resolveForDownload(id, 128) }.exceptionOrNull() is IllegalStateException)
            sources = sources.map { it.copy(enabled = false) }
            assertTrue(runCatching { StreamResolver.resolve(id) }.exceptionOrNull() is IllegalStateException)
        } finally {
            StreamResolver.sourceEnabled = priorSource
            StreamResolver.playbackEnabled = priorPlayback
        }
    }

}
