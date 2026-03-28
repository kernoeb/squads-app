package com.squads.app.data

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PresenceAvailabilityTest {
    @Test
    fun `fromString parses known availability values`() {
        assertEquals(PresenceAvailability.Available, PresenceAvailability.fromString("Available"))
        assertEquals(PresenceAvailability.Busy, PresenceAvailability.fromString("Busy"))
        assertEquals(PresenceAvailability.DoNotDisturb, PresenceAvailability.fromString("DoNotDisturb"))
        assertEquals(PresenceAvailability.Away, PresenceAvailability.fromString("Away"))
        assertEquals(PresenceAvailability.BeRightBack, PresenceAvailability.fromString("BeRightBack"))
        assertEquals(PresenceAvailability.Offline, PresenceAvailability.fromString("Offline"))
        assertEquals(PresenceAvailability.AvailableIdle, PresenceAvailability.fromString("AvailableIdle"))
        assertEquals(PresenceAvailability.BusyIdle, PresenceAvailability.fromString("BusyIdle"))
    }

    @Test
    fun `fromString is case insensitive`() {
        assertEquals(PresenceAvailability.Available, PresenceAvailability.fromString("available"))
        assertEquals(PresenceAvailability.Busy, PresenceAvailability.fromString("BUSY"))
        assertEquals(PresenceAvailability.DoNotDisturb, PresenceAvailability.fromString("donotdisturb"))
    }

    @Test
    fun `fromString returns Unknown for unrecognized values`() {
        assertEquals(PresenceAvailability.Unknown, PresenceAvailability.fromString(""))
        assertEquals(PresenceAvailability.Unknown, PresenceAvailability.fromString("SomethingElse"))
        assertEquals(PresenceAvailability.Unknown, PresenceAvailability.fromString("online"))
    }

    @Test
    fun `displayName maps idle variants correctly`() {
        assertEquals("Available", PresenceAvailability.Available.displayName)
        assertEquals("Available", PresenceAvailability.AvailableIdle.displayName)
        assertEquals("Busy", PresenceAvailability.Busy.displayName)
        assertEquals("Busy", PresenceAvailability.BusyIdle.displayName)
        assertEquals("Do not disturb", PresenceAvailability.DoNotDisturb.displayName)
        assertEquals("Away", PresenceAvailability.Away.displayName)
        assertEquals("Away", PresenceAvailability.BeRightBack.displayName)
    }

    @Test
    fun `isOnline returns correct values`() {
        assertTrue(PresenceAvailability.Available.isOnline)
        assertTrue(PresenceAvailability.Busy.isOnline)
        assertTrue(PresenceAvailability.Away.isOnline)
        assertTrue(PresenceAvailability.DoNotDisturb.isOnline)
        assertFalse(PresenceAvailability.Offline.isOnline)
        assertFalse(PresenceAvailability.Unknown.isOnline)
    }
}
