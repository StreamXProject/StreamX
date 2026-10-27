package com.music.streamx.data.sources

import com.music.streamx.data.model.SearchFilter
import com.music.streamx.data.model.SearchResult
import com.music.streamx.data.model.Song
import com.music.streamx.data.streamx.StreamXClient
import com.music.streamx.data.streamx.StreamXSession

class StreamXSource(override val config: SourceConfig) : MusicSource, SourceRegistry.ConfigBacked {
    override val configId: String get() = config.id
    override val kind: SourceKind get() = config.kind
    override val displayName: String get() = config.kind.label
    private fun client() = StreamXClient(config, StreamXSession.state.value)

    override suspend fun health(): SourceHealth = StreamXClient.result {
        if (config.kind == SourceKind.CLOUDSTREAM) com.music.streamx.data.cloudstream.CloudStream.verify(com.music.streamx.data.cloudstream.CloudStream.read(com.music.streamx.data.cloudstream.CloudStream.PROFILE))
        else StreamXClient.validateEndpoint(config.baseUrl)
        SourceHealth.Ok()
    }.getOrElse { SourceHealth.Unreachable(it.message ?: "Server unavailable") }

    override suspend fun search(query: String, limit: Int, waitForAll: Boolean, request: StreamRequest?): List<Song> =
        client().search(query, SearchFilter.SONGS, limit).filterIsInstance<SearchResult.Track>().map { it.song }

    override suspend fun stream(trackId: String, request: StreamRequest): SourceStream? = client().stream(trackId, request)
}
