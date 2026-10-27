package com.music.streamx

import com.music.streamx.data.innertube.StreamResolver
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.*
import org.junit.Test

class StreamResolverPlaybackRecoveryTest {
    @Test fun `failed track invalidates both containers and cancels stale resolutions only for that track`() {
        val resolver = StreamResolver
        val recentField = resolver.javaClass.getDeclaredField("recent").apply { isAccessible = true }
        val flightsField = resolver.javaClass.getDeclaredField("inFlight").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        val recent = recentField.get(resolver) as MutableMap<String, Any>
        @Suppress("UNCHECKED_CAST")
        val flights = flightsField.get(resolver) as MutableMap<String, Any>
        val resolvedClass = resolver.javaClass.declaredClasses.single { it.simpleName == "Resolved" }
        val constructor = resolvedClass.declaredConstructors.single().apply { isAccessible = true }
        val stale = constructor.newInstance("https://example.invalid/stale", 0L)
        val opus = CompletableDeferred<Any>()
        val aac = CompletableDeferred<Any>()
        val unrelated = CompletableDeferred<Any>()
        val id = "recovery-test"
        try {
            recent[id] = stale
            recent["$id#aac"] = stale
            recent["unrelated-test"] = stale
            flights[id] = opus
            flights["$id#aac"] = aac
            flights["unrelated-test"] = unrelated
            resolver.invalidatePlayback(id)
            assertFalse(recent.containsKey(id))
            assertFalse(recent.containsKey("$id#aac"))
            assertFalse(flights.containsKey(id))
            assertFalse(flights.containsKey("$id#aac"))
            assertTrue(opus.isCancelled)
            assertTrue(aac.isCancelled)
            assertSame(stale, recent["unrelated-test"])
            assertFalse(unrelated.isCancelled)
        } finally {
            listOf(id, "$id#aac", "unrelated-test").forEach {
                recent.remove(it)
                flights.remove(it)
            }
            unrelated.cancel()
        }
    }
}
