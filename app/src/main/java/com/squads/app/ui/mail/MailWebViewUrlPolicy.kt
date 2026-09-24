package com.squads.app.ui.mail

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

private const val GRAPH_HOST = "graph.microsoft.com"

private val EXTERNAL_LINK_SCHEMES = setOf("http", "https", "mailto")

/**
 * Returns true only when [url] is an https URL whose host is exactly [GRAPH_HOST].
 *
 * Email HTML is attacker-controlled, so the Graph bearer token must never be attached based on a
 * substring match. HttpUrl parsing also handles userinfo tricks such as
 * `https://graph.microsoft.com@attacker.example/`, whose real host is `attacker.example`.
 */
internal fun isGraphImageUrl(url: String): Boolean {
    val parsed = url.toHttpUrlOrNull() ?: return false
    return parsed.isHttps && parsed.host.lowercase() == GRAPH_HOST
}

/** Returns true when a link tapped in an email body may be handed off to an external app. */
internal fun isAllowedExternalLinkScheme(scheme: String?): Boolean =
    scheme?.lowercase() in EXTERNAL_LINK_SCHEMES
