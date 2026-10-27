package com.music.streamx

import com.music.streamx.data.model.Song
import com.music.streamx.data.sources.SourceRegistry
import com.music.streamx.playback.foreignPluginAutoplay
import com.music.streamx.playback.pluginAutoplayTracks
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class PluginAutoplayTest {
    private fun song(id: String,title: String="Night Drive",artist: String="Example Artist")=Song(id,title,artist,null,durationText="3:20")
    private fun apple(id: String,title: String="Night Drive",artist: String="Example Artist")=song(SourceRegistry.trackKey("apple",id),title,artist)
    @Test fun suggestionsResolveToPluginIdsBeforeEnteringTheQueue()=runBlocking {
        val result=pluginAutoplayTracks(emptyList(),listOf(song("youtube-id")),"apple",10){listOf(apple("one"))}
        assertEquals(listOf(SourceRegistry.trackKey("apple","one")),result.map{it.videoId})
    }
    @Test fun missingOrWrongProviderMatchesNeverBecomeYouTubeFallbacks()=runBlocking {
        for(matches in listOf(emptyList(),listOf(song("youtube-id")),listOf(song(SourceRegistry.trackKey("spotify","one"))))) {
            assertTrue(pluginAutoplayTracks(emptyList(),listOf(song("recommendation")),"apple",10){matches}.isEmpty())
        }
    }
    @Test fun wrongRecordingsAndAlreadyQueuedTracksAreSkipped()=runBlocking {
        val existing=apple("one")
        assertTrue(pluginAutoplayTracks(listOf(existing),listOf(song("yt")),"apple",10){listOf(apple("one"))}.isEmpty())
        assertTrue(pluginAutoplayTracks(emptyList(),listOf(song("yt")),"apple",10){listOf(apple("cover",artist="Cover Singer"))}.isEmpty())
    }
    @Test fun pluginSearchFailureDoesNotLeakRecommendationIds()=runBlocking {
        assertTrue(pluginAutoplayTracks(emptyList(),listOf(song("yt")),"apple",10){error("Disconnected")}.isEmpty())
    }
    @Test fun cancellationIsNotHiddenByMatching()=runBlocking {
        try {pluginAutoplayTracks(emptyList(),listOf(song("yt")),"apple",10){throw CancellationException("Cancelled")};fail()}
        catch(expected: CancellationException){}
    }
    @Test fun restoredQueueRepairKeepsUserChoicesAndMatchingPluginTracks() {
        assertTrue(foreignPluginAutoplay("apple","youtube-id",true))
        assertFalse(foreignPluginAutoplay("apple","youtube-id",false))
        assertFalse(foreignPluginAutoplay("apple",SourceRegistry.trackKey("apple","one"),true))
        assertTrue(foreignPluginAutoplay("apple",SourceRegistry.trackKey("spotify","one"),true))
    }
}
