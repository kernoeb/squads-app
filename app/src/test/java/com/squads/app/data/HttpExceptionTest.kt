package com.squads.app.data

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HttpExceptionTest {
    @Test
    fun `parses OAuth style error string`() {
        val body =
            """{"error":"invalid_grant","error_description":"AADSTS70043: expired",""" +
                """"correlation_id":"abc-123"}"""
        assertEquals("invalid_grant", HttpException.parseErrorCode(body))
    }

    @Test
    fun `parses Graph style nested error code`() {
        val body =
            """{"error":{"code":"InvalidAuthenticationToken","message":"Access token is empty."}}"""
        assertEquals("InvalidAuthenticationToken", HttpException.parseErrorCode(body))
    }

    @Test
    fun `returns null for HTML body`() {
        val body = "<html><head><title>502 Bad Gateway</title></head><body>oops</body></html>"
        assertNull(HttpException.parseErrorCode(body))
    }

    @Test
    fun `returns null for empty or blank body`() {
        assertNull(HttpException.parseErrorCode(""))
        assertNull(HttpException.parseErrorCode("   "))
        assertNull(HttpException.parseErrorCode(null))
    }

    @Test
    fun `returns null for malformed JSON or missing error field`() {
        assertNull(HttpException.parseErrorCode("{not json"))
        assertNull(HttpException.parseErrorCode("""{"message":"nope"}"""))
        assertNull(HttpException.parseErrorCode("""{"error":{"message":"no code"}}"""))
        assertNull(HttpException.parseErrorCode("""{"error":null}"""))
    }

    @Test
    fun `fromResponse sets code errorCode and short message`() {
        val e = HttpException.fromResponse(400, """{"error":"invalid_grant"}""")
        assertEquals(400, e.code)
        assertEquals("invalid_grant", e.errorCode)
        assertEquals("""HTTP 400 invalid_grant: {"error":"invalid_grant"}""", e.message)
        assertEquals("HTTP 400: invalid_grant", e.summary)
    }

    @Test
    fun `message keeps invalid_grant detectable for fallback check`() {
        val e = HttpException.fromResponse(400, """{"error":"invalid_grant"}""")
        assertTrue(e.message?.contains("invalid_grant") == true)
    }

    @Test
    fun `message without body or errorCode is just the status`() {
        val e = HttpException.fromResponse(503, "")
        assertNull(e.errorCode)
        assertEquals("HTTP 503", e.message)
        assertEquals("HTTP 503", e.summary)
    }

    @Test
    fun `message truncates long body and collapses newlines`() {
        val body = "<html>\n<body>\r\n" + "x".repeat(1000) + "\n</body></html>"
        val e = HttpException.fromResponse(500, body)
        val message = e.message.orEmpty()

        assertFalse(message.contains('\n'))
        assertFalse(message.contains('\r'))
        assertTrue(message.startsWith("HTTP 500: <html> <body> xxx"))
        assertTrue(message.endsWith("…"))
        val prefix = "HTTP 500: "
        assertEquals(prefix.length + HttpException.MAX_BODY_CHARS + 1, message.length)
    }

    @Test
    fun `message does not truncate body at the limit`() {
        val body = "y".repeat(HttpException.MAX_BODY_CHARS)
        val e = HttpException.fromResponse(404, body)
        assertEquals("HTTP 404: $body", e.message)
    }
}
