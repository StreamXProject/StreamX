package com.music.streamx.data.streamx

import com.music.streamx.data.NerdStats
import org.junit.Assert.*
import org.junit.Test

class AlacQualityTest {
    @Test fun recognizesAlacFromDecoderWithGenericContainer() {
        val mime = NerdStats.measuredAudioMime("audio/mp4", null, "ffmpeg7.1-alac")
        assertEquals("audio/alac", mime)
        assertTrue(NerdStats.isLosslessMime(mime))
    }

    @Test fun recognizesParsedAlacCodec() {
        assertEquals("audio/alac", NerdStats.measuredAudioMime("audio/x-m4a", "alac", null))
        assertTrue(NerdStats.isLosslessMime("Audio/ALAC; codecs=alac"))
    }

    @Test fun m4aContainerAloneDoesNotClaimLossless() {
        assertFalse(NerdStats.isLosslessMime(NerdStats.measuredAudioMime("audio/mp4", null, null)))
        assertFalse(NerdStats.isLosslessMime(NerdStats.measuredAudioMime("audio/mp4a-latm", "mp4a.40.2", "ffmpeg7.1-aac")))
    }
}
