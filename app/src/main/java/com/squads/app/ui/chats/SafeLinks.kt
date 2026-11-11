package com.squads.app.ui.chats

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.URLSpan
import android.view.View
import com.squads.app.ui.mail.isAllowedExternalLinkScheme

/**
 * Replaces every [URLSpan] with one that opens only http, https and mailto links.
 *
 * Any chat member controls the href, and a plain [URLSpan] opens any scheme: deep links into
 * other apps, or `file://`, which crashes the app.
 */
internal fun Spanned.withSafeLinks(): Spanned {
    val out = SpannableStringBuilder(this)
    for (span in out.getSpans(0, out.length, URLSpan::class.java)) {
        val start = out.getSpanStart(span)
        val end = out.getSpanEnd(span)
        val flags = out.getSpanFlags(span)
        out.removeSpan(span)
        out.setSpan(SafeUrlSpan(span.url), start, end, flags)
    }
    return out
}

private class SafeUrlSpan(
    url: String,
) : URLSpan(url) {
    override fun onClick(widget: View) {
        val uri = Uri.parse(url)
        if (!isAllowedExternalLinkScheme(uri.scheme)) return
        try {
            widget.context.startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (_: ActivityNotFoundException) {
            // No app can handle this link; ignore.
        }
    }
}
