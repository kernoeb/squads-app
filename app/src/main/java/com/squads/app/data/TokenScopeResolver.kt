package com.squads.app.data

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Which access token (if any) may be attached to a request for a given URL. */
enum class TokenScope { GRAPH, IC3 }

/**
 * Decides which bearer token may be sent to a URL, based on the parsed host.
 *
 * URLs reaching this come from untrusted sources (e.g. `<img src>` in chat message
 * HTML), so the check must never be a substring match: it parses the URL, requires
 * `https` on the default port, and compares the exact host against an allowlist.
 * Pure Kotlin/OkHttp so it can be unit-tested without Android.
 */
object TokenScopeResolver {
    private const val GRAPH_HOST = "graph.microsoft.com"

    /** Exact hosts that receive the IC3 token. */
    private val IC3_HOSTS = setOf("teams.microsoft.com", "asm.skype.com")

    /**
     * Parent domains whose subdomains receive the IC3 token, e.g.
     * `eu-prod.asyncgw.teams.microsoft.com` (custom emoji / AMS images) and
     * `eu-api.asm.skype.com` (inline chat images).
     */
    private val IC3_SUFFIXES = listOf(".teams.microsoft.com", ".asm.skype.com")

    fun resolve(url: String): TokenScope? {
        val parsed = url.toHttpUrlOrNull() ?: return null
        if (!parsed.isHttps || parsed.port != 443) return null
        // HttpUrl lowercases and IDN-normalises the host; userinfo is not part of it.
        val host = parsed.host.lowercase()
        return when {
            host == GRAPH_HOST -> TokenScope.GRAPH
            host in IC3_HOSTS || IC3_SUFFIXES.any { host.endsWith(it) } -> TokenScope.IC3
            else -> null
        }
    }
}
