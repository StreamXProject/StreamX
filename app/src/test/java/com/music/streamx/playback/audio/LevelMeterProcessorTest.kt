package com.music.streamx.playback.audio

import com.music.streamx.playback.AudioLevels
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class LevelMeterProcessorTest {
    @After fun clearLevels() {
        AudioLevels.publish(AudioLevels.SILENT)
        AudioLevels.markAvailable(false)
    }

    private fun tone(channels: Int = 2): AudioBlock =
        AudioBlock(channels, 1920).apply {
            setFrameCount(1920)
            for (frame in 0 until frameCount) {
                val wave = sin(2.0 * PI * 1250.0 * frame / 48000.0).toFloat()
                samples[frame * channels] = wave * .5f
                if (channels > 1) samples[frame * channels + 1] = wave * .125f
            }
        }

    @Test fun `meter publishes stereo levels and spectrum without changing PCM`() {
        val meter = LevelMeterProcessor { 100L }
        meter.configure(48000, 2)
        val block = tone()
        val bits = block.samples.map { it.toRawBits() }
        meter.process(block)
        val frame = AudioLevels.current.value
        assertEquals(bits, block.samples.map { it.toRawBits() })
        assertTrue(AudioLevels.available.value)
        assertTrue(frame.left > frame.right)
        assertTrue(frame.peakLeft > frame.peakRight)
        assertTrue(frame.bands[7] > frame.bands[0])
        assertTrue(frame.bands.all { it.isFinite() && it in 0f..1f })
        assertEquals(100L, frame.atMs)
    }

    @Test fun `mono is reflected in both channels`() {
        val meter = LevelMeterProcessor { 100L }
        meter.configure(48000, 1)
        meter.process(tone(1))
        val frame = AudioLevels.current.value
        assertEquals(frame.left, frame.right, 0f)
        assertEquals(frame.peakLeft, frame.peakRight, 0f)
    }

    @Test fun `updates are throttled and published band arrays remain immutable`() {
        var now = 100L
        val meter = LevelMeterProcessor { now }
        meter.configure(48000, 2)
        val block = tone()
        meter.process(block)
        val first = AudioLevels.current.value
        val bands = first.bands.copyOf()
        now += 10
        meter.process(block)
        assertSame(first, AudioLevels.current.value)
        now += 15
        meter.process(block)
        assertNotSame(first, AudioLevels.current.value)
        assertArrayEquals(bands, first.bands, 0f)
    }

    @Test fun `flush clears stale readings and reset releases availability`() {
        val meter = LevelMeterProcessor { 100L }
        meter.configure(48000, 2)
        meter.process(tone())
        meter.flush()
        assertSame(AudioLevels.SILENT, AudioLevels.current.value)
        meter.process(tone())
        assertTrue(AudioLevels.current.value.left > 0f)
        meter.reset()
        assertSame(AudioLevels.SILENT, AudioLevels.current.value)
        assertFalse(AudioLevels.available.value)
    }

    @Test fun `an active sink restores availability after another sink resets`() {
        val active = LevelMeterProcessor { 100L }
        val outgoing = LevelMeterProcessor { 100L }
        active.configure(48000, 2)
        outgoing.configure(48000, 2)
        outgoing.reset()
        active.process(tone())
        assertTrue(AudioLevels.available.value)
    }
}
