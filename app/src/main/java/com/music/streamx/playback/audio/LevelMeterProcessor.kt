package com.music.streamx.playback.audio

import android.os.SystemClock
import com.music.streamx.playback.AudioLevels
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Reads the signal on its way out and publishes [AudioLevels]; writes nothing.
 *
 * Sits last in [DspChain] so meters show what the listener hears after the
 * equaliser and loudness stages. Per block it takes RMS and peak per channel
 * and a fourteen-band spectrum by Goertzel — one resonator per band over the
 * mono mix, which at a thousand frames is a few thousand multiplies, far below
 * anything an FFT would cost to set up. Readings are smoothed with a fast
 * attack and a slower release so bars rise on a transient and fall like a VU
 * needle, then published at most every [PUBLISH_MS].
 *
 * Runs on the audio thread: no allocation per block beyond the published
 * frame, and that only at the publish rate.
 */
class LevelMeterProcessor(
    private val elapsedRealtime: () -> Long = { SystemClock.elapsedRealtime() },
) : FloatAudioProcessor {

    private var sampleRate = 48_000
    private var channels = 2
    private val bandCount = AudioLevels.BAND_HZ.size
    private val coeff = FloatArray(bandCount)
    private val smoothBands = FloatArray(bandCount)
    private var smoothL = 0f
    private var smoothR = 0f
    private var peakL = 0f
    private var peakR = 0f
    private var lastPublish = 0L
    // Goertzel state, reused across blocks so the audio thread allocates nothing per call.
    private val q1 = FloatArray(bandCount)
    private val q2 = FloatArray(bandCount)

    override fun configure(sampleRate: Int, channelCount: Int) {
        require(sampleRate > 0 && channelCount > 0)
        flush()
        this.sampleRate = sampleRate
        this.channels = channelCount
        for (i in 0 until bandCount) {
            val k = AudioLevels.BAND_HZ[i].coerceAtMost(sampleRate / 2.2f)
            coeff[i] = 2f * cos(2.0 * Math.PI * k / sampleRate).toFloat()
        }
        AudioLevels.markAvailable(true)
    }

    override fun process(block: AudioBlock) {
        val n = block.frameCount
        if (n == 0) return
        val s = block.samples
        val ch = channels
        var sumL = 0f; var sumR = 0f; var pL = 0f; var pR = 0f
        q1.fill(0f); q2.fill(0f)
        var i = 0
        while (i < n) {
            val l = s[i * ch]
            val r = if (ch > 1) s[i * ch + 1] else l
            sumL += l * l; sumR += r * r
            pL = max(pL, if (l < 0) -l else l); pR = max(pR, if (r < 0) -r else r)
            val m = (l + r) * .5f
            for (b in 0 until bandCount) {
                val q0 = coeff[b] * q1[b] - q2[b] + m
                q2[b] = q1[b]; q1[b] = q0
            }
            i++
        }
        val rmsL = sqrt(sumL / n); val rmsR = sqrt(sumR / n)
        smoothL = follow(smoothL, toMeter(rmsL)); smoothR = follow(smoothR, toMeter(rmsR))
        peakL = follow(peakL, pL.coerceIn(0f, 1f)); peakR = follow(peakR, pR.coerceIn(0f, 1f))
        for (b in 0 until bandCount) {
            val power = q1[b] * q1[b] + q2[b] * q2[b] - coeff[b] * q1[b] * q2[b]
            // Magnitude normalised by the window, then lifted so quiet highs still show.
            val mag = sqrt(max(power, 0f)) / n * 2f * (1f + b * .35f)
            smoothBands[b] = follow(smoothBands[b], toMeter(mag))
        }
        val now = elapsedRealtime()
        if (now - lastPublish >= PUBLISH_MS) {
            lastPublish = now
            AudioLevels.markAvailable(true)
            AudioLevels.publish(AudioLevels.Frame(smoothL, smoothR, peakL, peakR, smoothBands.copyOf(), now))
        }
    }

    /** Linear amplitude to a 0..1 meter position on a 60 dB scale. */
    private fun toMeter(amplitude: Float): Float {
        if (amplitude <= 1e-5f) return 0f
        val db = 20f * (ln(amplitude.toDouble()) / ln(10.0)).toFloat()
        return ((db + 60f) / 60f).coerceIn(0f, 1f)
    }

    private fun follow(current: Float, target: Float): Float =
        if (target > current) current + (target - current) * ATTACK else current + (target - current) * RELEASE

    override fun flush() {
        smoothBands.fill(0f); smoothL = 0f; smoothR = 0f; peakL = 0f; peakR = 0f
        lastPublish = 0L
        AudioLevels.publish(AudioLevels.SILENT)
    }

    override fun reset() {
        flush()
        AudioLevels.markAvailable(false)
    }

    private companion object {
        const val PUBLISH_MS = 25L
        const val ATTACK = .6f
        const val RELEASE = .18f
    }
}
