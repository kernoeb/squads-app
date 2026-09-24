package com.squads.app.data

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TrouterUrlPolicyTest {
    // ─── Reconnect URL ────────────────────────────────────────

    @Test
    fun `reconnect accepts default regional URL`() {
        val url = "wss://go-eu.trouter.teams.microsoft.com/v4/c"
        assertTrue(TrouterUrlPolicy.isAllowedReconnectUrl(url))
    }

    @Test
    fun `reconnect accepts bare trouter domain`() {
        assertTrue(TrouterUrlPolicy.isAllowedReconnectUrl("wss://trouter.teams.microsoft.com/v4/c"))
    }

    @Test
    fun `reconnect accepts uppercase scheme and host`() {
        val url = "WSS://GO-EU.TROUTER.TEAMS.MICROSOFT.COM/v4/c"
        assertTrue(TrouterUrlPolicy.isAllowedReconnectUrl(url))
    }

    @Test
    fun `reconnect rejects ws scheme`() {
        val url = "ws://go-eu.trouter.teams.microsoft.com/v4/c"
        assertFalse(TrouterUrlPolicy.isAllowedReconnectUrl(url))
    }

    @Test
    fun `reconnect rejects https scheme`() {
        val url = "https://go-eu.trouter.teams.microsoft.com/v4/c"
        assertFalse(TrouterUrlPolicy.isAllowedReconnectUrl(url))
    }

    @Test
    fun `reconnect rejects lookalike suffix host`() {
        val url = "wss://trouter.teams.microsoft.com.evil.example/v4/c"
        assertFalse(TrouterUrlPolicy.isAllowedReconnectUrl(url))
    }

    @Test
    fun `reconnect rejects lookalike prefix host`() {
        val url = "wss://eviltrouter.teams.microsoft.com/v4/c"
        assertFalse(TrouterUrlPolicy.isAllowedReconnectUrl(url))
    }

    @Test
    fun `reconnect rejects userinfo`() {
        val url = "wss://user:pass@go-eu.trouter.teams.microsoft.com/v4/c"
        assertFalse(TrouterUrlPolicy.isAllowedReconnectUrl(url))
    }

    @Test
    fun `reconnect rejects userinfo pointing at trouter with evil host`() {
        val url = "wss://go-eu.trouter.teams.microsoft.com@evil.example/v4/c"
        assertFalse(TrouterUrlPolicy.isAllowedReconnectUrl(url))
    }

    @Test
    fun `reconnect rejects non-default port`() {
        val url = "wss://go-eu.trouter.teams.microsoft.com:8443/v4/c"
        assertFalse(TrouterUrlPolicy.isAllowedReconnectUrl(url))
    }

    @Test
    fun `reconnect rejects empty and malformed`() {
        assertFalse(TrouterUrlPolicy.isAllowedReconnectUrl(null))
        assertFalse(TrouterUrlPolicy.isAllowedReconnectUrl(""))
        assertFalse(TrouterUrlPolicy.isAllowedReconnectUrl("   "))
        assertFalse(TrouterUrlPolicy.isAllowedReconnectUrl("wss://"))
        assertFalse(TrouterUrlPolicy.isAllowedReconnectUrl("not a url"))
        assertFalse(TrouterUrlPolicy.isAllowedReconnectUrl("go-eu.trouter.teams.microsoft.com"))
    }

    // ─── surl ─────────────────────────────────────────────────

    @Test
    fun `surl accepts regional URL with port 3443`() {
        val url = "https://pub-ent-euno-07-t.trouter.teams.microsoft.com:3443/v4/f/AbCdEf123/"
        assertTrue(TrouterUrlPolicy.isAllowedSurl(url))
    }

    @Test
    fun `surl accepts default port`() {
        val url = "https://go-eu.trouter.teams.microsoft.com/v4/f/AbCdEf123/"
        assertTrue(TrouterUrlPolicy.isAllowedSurl(url))
    }

    @Test
    fun `surl rejects http scheme`() {
        val url = "http://pub-ent-euno-07-t.trouter.teams.microsoft.com:3443/v4/f/AbCdEf123/"
        assertFalse(TrouterUrlPolicy.isAllowedSurl(url))
    }

    @Test
    fun `surl rejects wss scheme`() {
        val url = "wss://pub-ent-euno-07-t.trouter.teams.microsoft.com:3443/v4/f/AbCdEf123/"
        assertFalse(TrouterUrlPolicy.isAllowedSurl(url))
    }

    @Test
    fun `surl rejects lookalike suffix host`() {
        val url = "https://trouter.teams.microsoft.com.evil.example:3443/v4/f/AbCdEf123/"
        assertFalse(TrouterUrlPolicy.isAllowedSurl(url))
    }

    @Test
    fun `surl rejects lookalike prefix host`() {
        val url = "https://eviltrouter.teams.microsoft.com:3443/v4/f/AbCdEf123/"
        assertFalse(TrouterUrlPolicy.isAllowedSurl(url))
    }

    @Test
    fun `surl rejects userinfo`() {
        val url = "https://user@pub-ent-euno-07-t.trouter.teams.microsoft.com:3443/v4/f/Ab/"
        assertFalse(TrouterUrlPolicy.isAllowedSurl(url))
    }

    @Test
    fun `surl rejects empty and malformed`() {
        assertFalse(TrouterUrlPolicy.isAllowedSurl(null))
        assertFalse(TrouterUrlPolicy.isAllowedSurl(""))
        assertFalse(TrouterUrlPolicy.isAllowedSurl("https://"))
        assertFalse(TrouterUrlPolicy.isAllowedSurl("https://:3443/v4/f/Ab/"))
        assertFalse(TrouterUrlPolicy.isAllowedSurl("not a url"))
    }

    // ─── hostForLog ───────────────────────────────────────────

    @Test
    fun `hostForLog returns only the host`() {
        val url = "wss://evil.example/v4/c?token=secret"
        assertEquals("evil.example", TrouterUrlPolicy.hostForLog(url))
    }

    @Test
    fun `hostForLog handles empty and malformed`() {
        assertEquals("<empty>", TrouterUrlPolicy.hostForLog(null))
        assertEquals("<unparseable>", TrouterUrlPolicy.hostForLog("not a url"))
    }
}
