package com.music.streamx.data.cloudstream

import org.junit.Assert.*
import org.junit.Test

class CloudStreamConfigTest {
    @Test fun `database follows explicit name then URI then bot default`() {
        assertEquals("Custom", cloudStreamDatabase("mongodb+srv://cluster.example/UriDb", " Custom "))
        assertEquals("UriDb", cloudStreamDatabase("mongodb+srv://cluster.example/UriDb?authSource=admin", ""))
        assertEquals("Stream", cloudStreamDatabase("mongodb+srv://cluster.example/?authSource=admin", ""))
        assertEquals("Stream", cloudStreamDatabase("mongodb://localhost:27017", ""))
    }
    private val valid = CloudStreamConfig("123456:fixture_token", "123", "0123456789abcdef0123456789abcdef",
        "mongodb+srv://fixture:secret@cluster.example/", "music", "tracks")
    @Test fun `requires all six fields before connecting`() {
        valid.validate()
        listOf(valid.copy(botToken = ""), valid.copy(apiId = "0"), valid.copy(apiHash = "bad"),
            valid.copy(mongoUri = "https://example.com"), valid.copy(database = ""), valid.copy(collection = "")).forEach {
            assertTrue(runCatching { it.validate() }.exceptionOrNull() is IllegalArgumentException)
        }
    }
    @Test fun `credentials are absent from diagnostics`() {
        val printed = valid.toString()
        assertFalse(printed.contains(valid.botToken))
        assertFalse(printed.contains(valid.apiHash))
        assertFalse(printed.contains(valid.mongoUri))
        assertFalse(printed.contains("secret"))
    }
}
