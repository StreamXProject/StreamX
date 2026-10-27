package com.music.streamx.data.lyrics

import android.os.Bundle
import com.music.streamx.data.extensions.ApkExtensions
import com.music.streamx.data.sources.SourceRegistry
import kotlinx.coroutines.CancellationException
import org.json.JSONObject

/** Exact Apple catalogue lyrics, using the plugin's own account and preference. */
internal object PluginLyrics {
    fun supports(key: String): Boolean {
        val (source,_)=SourceRegistry.parseTrackKey(key) ?: return false
        return SourceRegistry.configs.value.any {it.id==source && it.extensionPackage=="com.music.streamx.extensions.applemusic"}
    }
    internal fun parseResponse(value: JSONObject): List<LyricLine>? =
        if(!value.optBoolean("enabled") || value.has("error"))null
        else value.optString("ttml").takeIf{it.length in 1..120000}?.let {TtmlLyrics.parse(it).takeIf{lines->lines.isNotEmpty()}}

    suspend fun forTrack(key: String): List<LyricLine>? {
        val (source,id)=SourceRegistry.parseTrackKey(key) ?: return null
        val config=SourceRegistry.configs.value.firstOrNull {
            it.id==source && it.extensionPackage=="com.music.streamx.extensions.applemusic"
        } ?: return null
        return try {
            val value=JSONObject(ApkExtensions.call(config,"lyrics",Bundle().apply {putString("track_id",id)}))
            parseResponse(value)
        } catch(cancel: CancellationException){throw cancel}
        catch(_: Exception){null}
    }
}
