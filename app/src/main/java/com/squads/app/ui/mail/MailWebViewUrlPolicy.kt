package com.squads.app.ui.mail

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
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
internal fun isAllowedExternalLinkScheme(scheme: String?): Boolean = scheme?.lowercase() in EXTERNAL_LINK_SCHEMES

/** Opens [uri] in an external app when its scheme is allowed. Does nothing otherwise. */
internal fun openExternalLink(
    context: Context,
    uri: Uri,
) {
    if (!isAllowedExternalLinkScheme(uri.scheme)) return
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
    } catch (_: ActivityNotFoundException) {
        // No app can handle this link; ignore.
    }
}
