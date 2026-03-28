package com.squads.app.data

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.time.ZoneId

class TimeExtensionsTest {
    @Test
    fun `toEpochMillis and toLocalDateTime are inverse operations`() {
        val original = LocalDateTime.of(2025, 6, 15, 14, 30, 0)
        val millis = original.toEpochMillis()
        val roundTripped = millis.toLocalDateTime()
        assertEquals(original, roundTripped)
    }

    @Test
    fun `toEpochMillis produces positive value for recent dates`() {
        val dt = LocalDateTime.of(2024, 1, 1, 0, 0)
        assertTrue(dt.toEpochMillis() > 0)
    }

    @Test
    fun `toLocalDateTime handles zero`() {
        val result = 0L.toLocalDateTime()
        // Epoch 0 = 1970-01-01 in UTC, adjusted to local timezone
        val expected = LocalDateTime.ofInstant(java.time.Instant.EPOCH, ZoneId.systemDefault())
        assertEquals(expected, result)
    }

    @Test
    fun `toRelativeTime returns non-empty for recent time`() {
        val recent = LocalDateTime.now().minusMinutes(5)
        val result = recent.toRelativeTime()
        assertTrue(result.isNotEmpty())
    }

    @Test
    fun `toTimeString returns formatted time`() {
        val dt = LocalDateTime.of(2025, 3, 15, 9, 30)
        val result = dt.toTimeString()
        assertTrue(result.contains("9") || result.contains("09"))
        assertTrue(result.contains("30"))
    }
}
