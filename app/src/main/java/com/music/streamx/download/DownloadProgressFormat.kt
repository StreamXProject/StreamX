package com.music.streamx.download

/** Unknown lengths stay indeterminate; provider pipes do not advertise Content-Length. */
internal fun downloadProgressFraction(written: Long, total: Long): Float =
    if (total > 0) (written.toDouble() / total).toFloat().coerceIn(0f, 1f) else 0f
