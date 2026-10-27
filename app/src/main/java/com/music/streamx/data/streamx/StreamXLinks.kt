package com.music.streamx.data.streamx

import com.music.streamx.data.sources.SourceKind
import com.music.streamx.data.sources.SourceRegistry
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Public links never contain account credentials or internal src: identifiers. */
object StreamXLinks {
    fun track(mediaId: String): String? {
        val source = SourceRegistry.parseTrackKey(mediaId)
            ?: return "https://music.youtube.com/watch?v=$mediaId"
        val spotify = SourceRegistry.config(source.first)?.takeIf { it.extensionPackage == "com.music.streamx.extensions.spotify" }
        if (spotify != null) return spotifyLink("track", source.second)
        if (SourceRegistry.config(source.first)?.extensionPackage == "com.music.streamx.extensions.applemusic")
            return appleMusicLink("track", source.second)
        val config = SourceRegistry.configs.value.firstOrNull { it.id == source.first && it.kind == SourceKind.STREAMX }
            ?: return null
        return (config.baseUrl.trimEnd('/') + "/").toHttpUrlOrNull()?.newBuilder()
            ?.addPathSegment("share")?.addPathSegment("tracks")?.addPathSegment(source.second)?.build()?.toString()
    }

    private fun spotifyLink(kind: String, id: String): String? =
        if (kind in setOf("track", "playlist", "album", "artist") && id.matches(Regex("[A-Za-z0-9]{22}")))
            "https://open.spotify.com/$kind/$id" else null

    private fun appleMusicLink(kind: String, id: String): String? {
        val type = when(kind) { "track" -> "songs"; "album" -> "albums"; "playlist" -> "playlists"; "artist" -> "artists"; else -> return null }
        if (!id.startsWith("$type:")) return null
        val raw = id.substringAfter(':')
        if (!raw.matches(if(kind == "playlist") Regex("pl\\.[A-Za-z0-9.-]+") else Regex("[0-9]+"))) return null
        return "https://music.apple.com/${if(kind == "track") "song" else kind}/$raw"
    }

    fun browse(browseId: String): String? {
        com.music.streamx.data.extensions.ApkCatalogue.parseBrowseId(browseId)?.let { target ->
            if (SourceRegistry.config(target.configId)?.extensionPackage == "com.music.streamx.extensions.spotify")
                return spotifyLink(target.kind, target.id)
            if (SourceRegistry.config(target.configId)?.extensionPackage == "com.music.streamx.extensions.applemusic")
                return appleMusicLink(target.kind, target.id)
            return null
        }
        val key = browseId.removePrefix("VL")
        if (!key.startsWith(StreamXClient.BROWSE_PREFIX)) return null
        val configId = key.removePrefix(StreamXClient.BROWSE_PREFIX).substringBefore(':')
        val rest = key.removePrefix("${StreamXClient.BROWSE_PREFIX}$configId:")
        val kind = rest.substringBefore(':')
        val id = rest.substringAfter(':')
        val config = SourceRegistry.configs.value.firstOrNull { it.id == configId && it.kind == SourceKind.STREAMX } ?: return null
        val builder = (config.baseUrl.trimEnd('/') + "/").toHttpUrlOrNull()?.newBuilder() ?: return null
        when (kind) {
            "playlist" -> builder.addPathSegment("share").addPathSegment("playlists").addPathSegment(id)
            "artist" -> builder.addPathSegment("artists").addPathSegment(id)
            "album" -> builder.addPathSegment("albums").addPathSegment(id)
            else -> return null // Personal favourites have no public URL.
        }
        return builder.build().toString()
    }
}
