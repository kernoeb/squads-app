package com.squads.app.data

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class RegionConfigTest {
    @Test
    fun `extractRegionFromUrl parses emea from CSA redirect`() {
        val url = "https://teams.microsoft.com/api/csa/emea/api/v2/teams/users/me"
        assertEquals("emea", RegionConfig.extractRegionFromUrl(url))
    }

    @Test
    fun `extractRegionFromUrl parses amer from CSA redirect`() {
        val url = "https://teams.microsoft.com/api/csa/amer/api/v2/teams/users/me"
        assertEquals("amer", RegionConfig.extractRegionFromUrl(url))
    }

    @Test
    fun `extractRegionFromUrl parses apac from CSA redirect`() {
        val url = "https://teams.microsoft.com/api/csa/apac/api/v2/teams/users/me"
        assertEquals("apac", RegionConfig.extractRegionFromUrl(url))
    }

    @Test
    fun `extractRegionFromUrl parses emea from chatsvc URL`() {
        val url = "https://teams.microsoft.com/api/chatsvc/emea/v1/users/ME/conversations"
        assertEquals("emea", RegionConfig.extractRegionFromUrl(url))
    }

    @Test
    fun `extractRegionFromUrl parses amer from chatsvc URL`() {
        val url = "https://teams.microsoft.com/api/chatsvc/amer/v1/users/ME/conversations"
        assertEquals("amer", RegionConfig.extractRegionFromUrl(url))
    }

    @Test
    fun `extractRegionFromUrl returns null for unknown region`() {
        val url = "https://teams.microsoft.com/api/csa/unknown/api/v2/teams/users/me"
        assertNull(RegionConfig.extractRegionFromUrl(url))
    }

    @Test
    fun `extractRegionFromUrl returns null for no region in URL`() {
        val url = "https://graph.microsoft.com/v1.0/me"
        assertNull(RegionConfig.extractRegionFromUrl(url))
    }
}
