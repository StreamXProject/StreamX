package com.music.streamx.playback

import org.json.JSONObject

/** Android JSON may stringify JSONObject.NULL as "null"; inspect the value first. */
internal object WebPlaybackResponse {
    /** Catalogue runtime is the selected recording's identity, not an ad/preview's media duration. */
    fun duration(catalogueMs: Long, status: JSONObject): Long =
        if (catalogueMs > 0) catalogueMs else status.optLong("duration_ms").takeIf { it > 0 } ?: androidx.media3.common.C.TIME_UNSET

    fun error(status: JSONObject): String? {
        val value = status.opt("error")
        if (value == null || value === JSONObject.NULL) return null
        return value.toString().takeIf { it.isNotBlank() }
    }
}
