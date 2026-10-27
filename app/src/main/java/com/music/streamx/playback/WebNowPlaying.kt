package com.music.streamx.playback

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import com.music.streamx.data.sources.SourceRegistry
import org.json.JSONObject

/** A renderer may change recordings inside a single station queue entry. */
internal data class WebNowPlaying(val id: String, val title: String, val artist: String, val album: String, val artwork: String?) {
    fun applyTo(item: MediaItem, durationMs: Long): MediaItem {
        val source = SourceRegistry.parseTrackKey(item.mediaId)?.first ?: return item
        val extras = Bundle(item.mediaMetadata.extras ?: Bundle()).apply {
            putString("streamx.nowPlayingId", SourceRegistry.trackKey(source, "songs:$id"))
            putString("streamx.durationText", if (durationMs > 0) "${durationMs / 60000}:${(durationMs / 1000 % 60).toString().padStart(2, '0')}" else null)
            remove("streamx.artistId"); remove("streamx.albumId"); remove("artistNames"); remove("artistIds")
        }
        return item.buildUpon().setMediaMetadata(item.mediaMetadata.buildUpon()
            .setTitle(title).setArtist(artist).setAlbumTitle(album)
            .setArtworkUri(artwork?.let(Uri::parse)).setExtras(extras).build()).build()
    }
    companion object {
        fun from(status: JSONObject): WebNowPlaying? {
            val row = status.optJSONObject("now_playing") ?: return null
            val id = row.optString("id").takeIf { it.matches(Regex("[A-Za-z0-9._-]{1,200}")) } ?: return null
            val title = row.optString("title").take(500).takeIf { it.isNotBlank() } ?: return null
            val art = (row.opt("thumbnail_url") as? String)?.take(4096)?.takeIf { it.startsWith("https://") }
            return WebNowPlaying(id, title, row.optString("artist").take(500), row.optString("album").take(500), art)
        }
    }
}
