package com.squads.app.data

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class TokenScopeResolverTest {
    // ─── Legitimate hosts ────────────────────────────────────────

    @Test
    fun `graph photo URL gets graph scope`() {
        assertEquals(
            TokenScope.GRAPH,
            TokenScopeResolver.resolve("https://graph.microsoft.com/v1.0/users/abc/photo/\$value"),
        )
    }

    @Test
    fun `bare graph base URL used by MailApi gets graph scope`() {
        assertEquals(TokenScope.GRAPH, TokenScopeResolver.resolve("https://graph.microsoft.com"))
    }

    @Test
    fun `host match is case insensitive`() {
        assertEquals(TokenScope.GRAPH, TokenScopeResolver.resolve("https://GRAPH.Microsoft.COM/v1.0/me"))
    }

    @Test
    fun `teams api URL gets ic3 scope`() {
        assertEquals(
            TokenScope.IC3,
            TokenScopeResolver.resolve("https://teams.microsoft.com/api/chatsvc/emea/v1/users/ME"),
        )
    }

    @Test
    fun `asyncgw custom emoji URL gets ic3 scope`() {
        assertEquals(
            TokenScope.IC3,
            TokenScopeResolver.resolve("https://eu-prod.asyncgw.teams.microsoft.com/v1/t/objects/o/views/imgt2_anim"),
        )
    }

    @Test
    fun `asm skype inline image URL gets ic3 scope`() {
        assertEquals(
            TokenScope.IC3,
            TokenScopeResolver.resolve("https://eu-api.asm.skype.com/v1/objects/0-weu-d1-abc/views/imgo"),
        )
    }

    // ─── Attacker-controlled URLs ────────────────────────────────

    @Test
    fun `trusted host in path is rejected`() {
        assertNull(TokenScopeResolver.resolve("https://attacker.example/graph.microsoft.com.png"))
    }

    @Test
    fun `trusted host as subdomain prefix of attacker domain is rejected`() {
        assertNull(TokenScopeResolver.resolve("https://graph.microsoft.com.attacker.example/"))
        assertNull(TokenScopeResolver.resolve("https://teams.microsoft.com.attacker.example/x.png"))
    }

    @Test
    fun `trusted host in query is rejected`() {
        assertNull(TokenScopeResolver.resolve("https://evil.com/?x=teams.microsoft.com"))
    }

    @Test
    fun `lookalike suffix without dot boundary is rejected`() {
        assertNull(TokenScopeResolver.resolve("https://evilteams.microsoft.com.example/"))
        assertNull(TokenScopeResolver.resolve("https://notasm.skype.com.evil/"))
        assertNull(TokenScopeResolver.resolve("https://eviltasm.skype.com/"))
    }

    @Test
    fun `userinfo trick is rejected`() {
        assertNull(TokenScopeResolver.resolve("https://graph.microsoft.com@attacker.example/"))
        assertNull(TokenScopeResolver.resolve("https://teams.microsoft.com:443@attacker.example/x.png"))
    }

    @Test
    fun `plain http is rejected`() {
        assertNull(TokenScopeResolver.resolve("http://graph.microsoft.com/"))
        assertNull(TokenScopeResolver.resolve("http://teams.microsoft.com/api/x"))
    }

    @Test
    fun `non http schemes are rejected`() {
        assertNull(TokenScopeResolver.resolve("file:///sdcard/graph.microsoft.com"))
        assertNull(TokenScopeResolver.resolve("content://graph.microsoft.com/x"))
        assertNull(TokenScopeResolver.resolve("data:image/png;base64,graph.microsoft.com"))
    }

    @Test
    fun `non default port is rejected`() {
        assertNull(TokenScopeResolver.resolve("https://graph.microsoft.com:8443/v1.0/me"))
    }

    @Test
    fun `unparseable input is rejected`() {
        assertNull(TokenScopeResolver.resolve(""))
        assertNull(TokenScopeResolver.resolve("graph.microsoft.com"))
    }
}
