package com.music.streamx.playback

import java.net.URI
import java.net.URLDecoder

/** Public Apple Music catalogue links; never accepts credentials or arbitrary API paths. */
internal data class AppleMusicLink(val kind: String, val id: String) {
    companion object {
        fun parse(url: String): AppleMusicLink? = runCatching {
            val uri = URI(url)
            if (uri.scheme !in listOf("https", "http") || uri.host?.lowercase() != "music.apple.com" || uri.userInfo != null) return null
            val parts = uri.path.trim('/').split('/')
            val at = if (parts.firstOrNull()?.matches(Regex("[A-Za-z]{2}")) == true) 1 else 0
            val kind = parts.getOrNull(at) ?: return null
            val raw = parts.lastOrNull() ?: return null
            if (kind == "album") {
                val song = uri.rawQuery.orEmpty().split('&').firstOrNull { it.substringBefore('=') == "i" }
                    ?.substringAfter('=', "")?.let { URLDecoder.decode(it, "UTF-8") }
                if (song != null) return song.takeIf { it.matches(Regex("[0-9]+")) }?.let { AppleMusicLink("track", "songs:$it") }
            }
            val type = when (kind) { "song" -> "songs"; "album" -> "albums"; "playlist" -> "playlists"; "artist" -> "artists"; else -> return null }
            if (!raw.matches(if (kind == "playlist") Regex("pl\\.[A-Za-z0-9.-]+") else Regex("[0-9]+"))) return null
            AppleMusicLink(if (kind == "song") "track" else kind, "$type:$raw")
        }.getOrNull()
    }
}
