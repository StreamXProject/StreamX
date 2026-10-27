package com.music.streamx.playback

import com.music.streamx.data.model.Song
import com.music.streamx.data.sources.SourceRegistry
import com.music.streamx.data.sources.TrackMatcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/** Recommendation metadata may come from radio; every queued audio ID stays in the seed plugin. */
internal suspend fun pluginAutoplayTracks(
    existing: List<Song>,suggestions: List<Song>,sourceId: String,limit: Int,
    search: suspend (String)->List<Song>,
): List<Song> = coroutineScope {
    if(limit<=0)return@coroutineScope emptyList()
    val permits=Semaphore(3)
    val candidates=QueueBuilder.extend(existing,suggestions,(limit*2).coerceAtMost(30))
    val matched=candidates.map { suggestion -> async {
        permits.withPermit {
            val target=TrackMatcher.targetOf(suggestion)
            val query=TrackMatcher.queries(target).firstOrNull() ?: return@withPermit null
            val results=try {search(query)} catch(cancelled: CancellationException){throw cancelled} catch(_: Exception){return@withPermit null}
            TrackMatcher.best(results.filter {candidate->
                SourceRegistry.parseTrackKey(candidate.videoId)?.first==sourceId &&
                    TrackMatcher.sharesArtist(suggestion.artist,candidate.artist)
            },target)
        }
    }}.awaitAll().filterNotNull()
    QueueBuilder.extend(existing,matched,limit)
}

internal fun foreignPluginAutoplay(sourceId: String,id: String,autoplay: Boolean): Boolean =
    autoplay && SourceRegistry.parseTrackKey(id)?.first!=sourceId
