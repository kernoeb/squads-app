package com.squads.app.ui.mail

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MailWebViewUrlPolicyTest {
    @Test
    fun `isGraphImageUrl accepts https graph host`() {
        val url = "https://graph.microsoft.com/v1.0/me/messages/abc/attachments/def/\$value"
        assertTrue(isGraphImageUrl(url))
    }

    @Test
    fun `isGraphImageUrl accepts uppercase graph host`() {
        assertTrue(isGraphImageUrl("https://GRAPH.Microsoft.com/v1.0/me/photo/\$value"))
    }

    @Test
    fun `isGraphImageUrl rejects graph host in path`() {
        assertFalse(isGraphImageUrl("https://attacker.example/graph.microsoft.com.png"))
    }

    @Test
    fun `isGraphImageUrl rejects graph host as subdomain prefix`() {
        assertFalse(isGraphImageUrl("https://graph.microsoft.com.attacker.example/"))
    }

    @Test
    fun `isGraphImageUrl rejects graph host in query`() {
        assertFalse(isGraphImageUrl("https://evil.com/?x=graph.microsoft.com"))
    }

    @Test
    fun `isGraphImageUrl rejects plain http`() {
        assertFalse(isGraphImageUrl("http://graph.microsoft.com/"))
    }

    @Test
    fun `isGraphImageUrl rejects graph host as userinfo`() {
        assertFalse(isGraphImageUrl("https://graph.microsoft.com@attacker.example/"))
    }

    @Test
    fun `isGraphImageUrl rejects teams host`() {
        assertFalse(isGraphImageUrl("https://teams.microsoft.com/"))
    }

    @Test
    fun `isGraphImageUrl rejects unparseable and non-http urls`() {
        assertFalse(isGraphImageUrl(""))
        assertFalse(isGraphImageUrl("cid:image001.png"))
        assertFalse(isGraphImageUrl("data:image/png;base64,AAAA"))
    }

    @Test
    fun `isAllowedExternalLinkScheme allows http https and mailto`() {
        assertTrue(isAllowedExternalLinkScheme("http"))
        assertTrue(isAllowedExternalLinkScheme("HTTPS"))
        assertTrue(isAllowedExternalLinkScheme("mailto"))
    }

    @Test
    fun `isAllowedExternalLinkScheme rejects other schemes`() {
        assertFalse(isAllowedExternalLinkScheme("intent"))
        assertFalse(isAllowedExternalLinkScheme("javascript"))
        assertFalse(isAllowedExternalLinkScheme("file"))
        assertFalse(isAllowedExternalLinkScheme("content"))
        assertFalse(isAllowedExternalLinkScheme(null))
    }
}
