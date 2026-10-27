package com.music.streamx.ui.skins

import com.music.streamx.data.NerdStats
import org.junit.Assert.*
import org.junit.Test

class SkinPlaybackTelemetryTest {
    @Test fun alacLabelDescribesTheEncodedStream() {
        val label = NerdStats.Snapshot("audio/alac", null, 96000, 2, 24).trackFormatLabel()
        assertEquals("ALAC · 24-bit · 96.0 kHz", label)
    }
    @Test fun lossyCodecDoesNotAcquireTheOutputPcmBitDepth() {
        val label = NerdStats.Snapshot("audio/vorbis", 320, 44100, 2, 16).trackFormatLabel()
        assertEquals("Vorbis · 44.1 kHz · 320 kbps", label)
        assertFalse(label.contains("16-bit"))
    }
    @Test fun unknownTelemetryDoesNotInventAFormat() {
        assertEquals("", NerdStats.Snapshot(null, null, null, null).trackFormatLabel())
    }
    @Test fun browserPlaybackShowsItsSourceWithoutInventingCodecOrPrecision() {
        val stats = NerdStats.Snapshot(null, null, null, null,
            sourceName = "Apple Music", container = "Web player")
        assertEquals("Apple Music · Web player", stats.playbackStatsLabel())
    }
    @Test fun nativePlaybackShowsEncodedAlacInsteadOfOutputPcm() {
        val stats = NerdStats.Snapshot("audio/alac", null, 96000, 2, 24,
            sourceName = "Apple Music", container = "Native player")
        assertEquals("Apple Music · ALAC · 24-bit · 96.0 kHz · Stereo", stats.playbackStatsLabel())
    }
}
