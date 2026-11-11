package com.squads.app.ui.chats

import android.net.Uri
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.URLSpan
import android.view.View
import com.squads.app.ui.mail.openExternalLink

// Any chat member controls the href, and a plain URLSpan opens any scheme:
// deep links into other apps, or file://, which crashes the app.
internal fun Spanned.withSafeLinks(): Spanned = replaceSpans(URLSpan::class.java) { SafeUrlSpan(it.url) }

internal fun <T : Any> Spanned.replaceSpans(
    type: Class<T>,
    replacement: (T) -> Any,
): Spanned {
    val spans = getSpans(0, length, type)
    if (spans.isEmpty()) return this
    val out = SpannableStringBuilder(this)
    for (span in spans) {
        val start = out.getSpanStart(span)
        val end = out.getSpanEnd(span)
        val flags = out.getSpanFlags(span)
        out.removeSpan(span)
        out.setSpan(replacement(span), start, end, flags)
    }
    return out
}

private class SafeUrlSpan(
    url: String,
) : URLSpan(url) {
    override fun onClick(widget: View) = openExternalLink(widget.context, Uri.parse(url))
}
