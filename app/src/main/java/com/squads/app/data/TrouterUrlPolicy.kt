package com.squads.app.data

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Allowlist for URLs that the Trouter service hands us in its `trouter.connected` frame.
 *
 * The frame arrives over TLS from Microsoft, but the values decide where the IC3 bearer token
 * is sent next (reconnect socket, presence subscription, registrar path), so they are checked
 * against the Trouter domain before use.
 */
object TrouterUrlPolicy {
    private const val TROUTER_DOMAIN = "trouter.teams.microsoft.com"
    private const val WSS_PREFIX = "wss://"
    private const val WS_PREFIX = "ws://"
    private const val HTTPS_PREFIX = "https://"

    /**
     * A reconnect URL must be `wss://` on the default port, with a host equal to or under
     * [TROUTER_DOMAIN], and without userinfo.
     */
    fun isAllowedReconnectUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        if (!url.startsWith(WSS_PREFIX, ignoreCase = true)) return false
        // HttpUrl only understands http(s); wss maps onto https for parsing.
        val parsed =
            (HTTPS_PREFIX + url.substring(WSS_PREFIX.length)).toHttpUrlOrNull() ?: return false
        return parsed.port == HttpUrl.defaultPort("https") && isTrouterHost(parsed)
    }

    /**
     * A surl must be `https://` (any port, Trouter uses 3443), with a host equal to or under
     * [TROUTER_DOMAIN], and without userinfo.
     */
    fun isAllowedSurl(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        if (!url.startsWith(HTTPS_PREFIX, ignoreCase = true)) return false
        val parsed = url.toHttpUrlOrNull() ?: return false
        return isTrouterHost(parsed)
    }

    /** Host of [url] for log messages, so full URLs (with ids/paths) never reach logcat. */
    fun hostForLog(url: String?): String {
        if (url.isNullOrBlank()) return "<empty>"
        val httpUrl =
            when {
                url.startsWith(WSS_PREFIX, ignoreCase = true) -> {
                    HTTPS_PREFIX + url.substring(WSS_PREFIX.length)
                }
                url.startsWith(WS_PREFIX, ignoreCase = true) -> {
                    "http://" + url.substring(WS_PREFIX.length)
                }
                else -> url
            }
        return httpUrl.toHttpUrlOrNull()?.host ?: "<unparseable>"
    }

    private fun isTrouterHost(url: HttpUrl): Boolean {
        if (url.username.isNotEmpty() || url.password.isNotEmpty()) return false
        // HttpUrl canonicalizes the host to lowercase.
        val host = url.host
        return host == TROUTER_DOMAIN || host.endsWith(".$TROUTER_DOMAIN")
    }
}
