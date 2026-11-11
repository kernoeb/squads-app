package com.squads.app.ui.chats

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.text.Layout
import android.text.Spanned
import android.text.style.LeadingMarginSpan
import android.text.style.QuoteSpan

// The default QuoteSpan is a thin, square, dark blue bar, and its colour and width
// can only be set from API 28.
internal fun Spanned.withQuoteBars(
    color: Int,
    barWidthPx: Float,
    gapPx: Int,
): Spanned = replaceSpans(QuoteSpan::class.java) { QuoteBarSpan(color, barWidthPx, gapPx) }

private class QuoteBarSpan(
    private val color: Int,
    private val barWidthPx: Float,
    private val gapPx: Int,
) : LeadingMarginSpan {
    private val rect = RectF()

    override fun getLeadingMargin(first: Boolean): Int = barWidthPx.toInt() + gapPx

    override fun drawLeadingMargin(
        c: Canvas,
        p: Paint,
        x: Int,
        dir: Int,
        top: Int,
        baseline: Int,
        bottom: Int,
        text: CharSequence,
        start: Int,
        end: Int,
        first: Boolean,
        layout: Layout?,
    ) {
        val spanned = text as? Spanned ?: return
        val radius = barWidthPx / 2
        // Round only the two ends of the whole quote, so the bar looks like one piece
        // across its lines.
        val isTop = start <= spanned.getSpanStart(this)
        val isBottom = end >= spanned.getSpanEnd(this)
        val left = if (dir > 0) x.toFloat() else x - barWidthPx
        rect.set(left, top.toFloat(), left + barWidthPx, bottom.toFloat())

        val oldColor = p.color
        val oldStyle = p.style
        p.color = color
        p.style = Paint.Style.FILL
        c.drawRoundRect(rect, radius, radius, p)
        if (!isTop) c.drawRect(rect.left, rect.top, rect.right, rect.top + radius, p)
        if (!isBottom) c.drawRect(rect.left, rect.bottom - radius, rect.right, rect.bottom, p)
        p.color = oldColor
        p.style = oldStyle
    }
}
