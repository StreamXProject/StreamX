package com.music.streamx.download

import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadProgressFormatTest {
    @Test fun pipeWithUnknownLengthStaysIndeterminate() {
        assertEquals(0f, downloadProgressFraction(32768, -1), 0f)
        assertEquals(0f, downloadProgressFraction(32768, 0), 0f)
    }
    @Test fun knownLengthIsBounded() {
        assertEquals(.5f, downloadProgressFraction(50, 100), 0f)
        assertEquals(1f, downloadProgressFraction(110, 100), 0f)
    }
    @Test fun byteUpdatesAreVisibleEvenWithoutAPercentage() {
        DownloadSession.clear()
        try {
            DownloadSession.queued(com.music.streamx.data.model.Song(
                videoId = "plugin-track", title = "Track", artist = "Artist", thumbnailUrl = null))
            DownloadSession.running("plugin-track", 0f, 32768)
            val first = DownloadSession.state.value
            DownloadSession.running("plugin-track", 0f, 65536)
            val next = DownloadSession.state.value
            org.junit.Assert.assertNotEquals(first, next)
            org.junit.Assert.assertTrue(next.indeterminate)
            DownloadSession.done("plugin-track")
            org.junit.Assert.assertFalse(DownloadSession.state.value.indeterminate)
        } finally {
            DownloadSession.clear()
        }
    }
}
