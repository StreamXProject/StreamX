package com.music.streamx.data.sources

/** Saved priority is authoritative: a quicker lower-ranked source cannot win. */
internal suspend fun <T, R : Any> firstAvailableInOrder(
    sources: List<T>,
    resolve: suspend (T) -> R?,
): R? {
    for (source in sources) resolve(source)?.let { return it }
    return null
}

/** Remote renderers play catalogue matches directly; they need no public audio URL. */
internal suspend fun matchRemotePlaybackTrack(
    source: MusicSource,
    target: TrackMatcher.Target,
    request: StreamRequest,
): com.music.streamx.data.model.Song? {
    for (query in TrackMatcher.queries(target)) {
        val candidates = source.search(query, limit = 15, waitForAll = false, request = request)
        TrackMatcher.ranked(candidates, target).firstOrNull()?.let { return it }
    }
    return null
}

internal fun hasEarlierRemoteSource(originId: String, orderedIds: List<String>, remoteIds: Set<String>): Boolean =
    orderedIds.takeWhile { it != originId }.any { it in remoteIds }
