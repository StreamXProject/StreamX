package com.music.streamx.data.playlistsync

import com.music.streamx.data.model.UserPlaylist
import java.security.MessageDigest

/** Hidden choices are local preferences, isolated across providers and signed-in accounts. */
internal fun transferVisibilityKey(provider: String,account: String): String = "hidden." +
    MessageDigest.getInstance("SHA-256").digest("$provider\u0000$account".toByteArray(Charsets.UTF_8)).joinToString(""){"%02x".format(it.toInt() and 255)}

internal fun visibleTransferPlaylists(playlists: List<UserPlaylist>,hidden: Set<String>,includeHidden: Boolean,query: String): List<UserPlaylist> {
    val text=query.trim()
    return playlists.filter { (includeHidden || it.playlistId !in hidden) && (text.isBlank() || it.title.contains(text,ignoreCase=true) || it.subtitle.contains(text,ignoreCase=true)) }
}
