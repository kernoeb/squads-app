package com.squads.app.data

import org.json.JSONArray
import org.json.JSONObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class JsonExtensionsTest {
    @Test
    fun `str returns value for existing key`() {
        val json = JSONObject().put("name", "Alice")
        assertEquals("Alice", json.str("name"))
    }

    @Test
    fun `str returns fallback for missing key`() {
        val json = JSONObject()
        assertEquals("", json.str("missing"))
        assertEquals("default", json.str("missing", "default"))
    }

    @Test
    fun `str returns fallback for null value`() {
        val json = JSONObject().put("name", JSONObject.NULL)
        assertEquals("", json.str("name"))
        assertEquals("fallback", json.str("name", "fallback"))
    }

    @Test
    fun `objects returns list of JSONObjects`() {
        val arr = JSONArray()
        arr.put(JSONObject().put("id", 1))
        arr.put(JSONObject().put("id", 2))
        arr.put(JSONObject().put("id", 3))

        val result = arr.objects()
        assertEquals(3, result.size)
        assertEquals(1, result[0].getInt("id"))
        assertEquals(3, result[2].getInt("id"))
    }

    @Test
    fun `objects returns empty list for empty array`() {
        val arr = JSONArray()
        assertTrue(arr.objects().isEmpty())
    }
}
