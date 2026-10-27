package com.music.streamx.playback

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Live levels off the audio that is actually leaving the player, for meters
 * and spectrum displays.
 *
 * Published by the host's DSP chain (`LevelMeterProcessor`) about forty times
 * a second while sound is flowing, and read by the original player and by
 * skins. Values are normalised 0..1 and already smoothed enough to draw
 * directly. [available] is false on a build or a path that has no tap — a
 * passthrough bitstream, say — so a meter can fall back to something decorative
 * instead of sitting at zero.
 */
object AudioLevels {

    /**
     * One reading. [bands] runs low to high; see [BAND_HZ] for the centres.
     * [left]/[right] are RMS, [peakLeft]/[peakRight] the block peak.
     */
    class Frame(
        val left: Float,
        val right: Float,
        val peakLeft: Float,
        val peakRight: Float,
        val bands: FloatArray,
        /** Monotonic time of the reading in ms, so a reader can tell a stale frame from a quiet one. */
        val atMs: Long,
    ) {
        val mono: Float get() = (left + right) / 2f
    }

    /** Centre frequencies the host measures, in Hz: roughly a third of an octave apart through the audible range. */
    val BAND_HZ = floatArrayOf(50f, 80f, 125f, 200f, 315f, 500f, 800f, 1250f, 2000f, 3150f, 5000f, 8000f, 12500f, 16000f)

    val SILENT = Frame(0f, 0f, 0f, 0f, FloatArray(BAND_HZ.size), 0L)

    private val _current = MutableStateFlow(SILENT)
    val current: StateFlow<Frame> get() = _current

    private val _available = MutableStateFlow(false)
    val available: StateFlow<Boolean> get() = _available

    /** Host only: the meter tap is in the chain and will publish. */
    fun markAvailable(value: Boolean) { _available.value = value }

    /** Host only. */
    fun publish(frame: Frame) { _current.value = frame }
}
