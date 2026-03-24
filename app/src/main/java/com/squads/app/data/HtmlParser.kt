package com.squads.app.data

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.TextNode

private val EMOJI_IMG_DOMAINS =
    setOf(
        "statics.teams.cdn.office.net",
        "statics.teams.microsoft.com",
        "emojipedia-us.s3.amazonaws.com",
    )

data class ParsedMessage(
    val text: String,
    val imageUrls: List<String>,
    val replyToName: String? = null,
    val replyToPreview: String? = null,
)

object HtmlParser {
    fun parseMessage(html: String): ParsedMessage {
        if (html.isBlank()) return ParsedMessage("", emptyList())
        val doc = Jsoup.parse(html)
        resolveEmojis(doc)

        // Extract reply info from <blockquote itemtype="http://schema.skype.com/Reply">
        val reply = doc.selectFirst("blockquote")
        var replyToName: String? = null
        var replyToPreview: String? = null
        if (reply != null) {
            replyToName = reply.selectFirst("strong")?.text()
            replyToPreview = reply.selectFirst("[itemprop=preview]")?.text()
            reply.remove()
        }

        val imageUrls =
            doc
                .select("img[src]")
                .map { it.attr("src") }
                .filter { it.startsWith("https://") }

        doc.select("img").remove()
        doc.select("br").forEach { it.replaceWith(TextNode("\n")) }
        doc.select("div, p, li").forEach { it.prepend("\n") }

        val text =
            doc
                .text()
                .replace(Regex("\n{3,}"), "\n\n")
                .trim()

        return ParsedMessage(text, imageUrls, replyToName, replyToPreview)
    }

    fun cleanForRendering(html: String): String {
        if (html.isBlank()) return ""
        val doc = Jsoup.parse(html)
        resolveEmojis(doc)
        doc.select("img").remove()
        // Remove blockquotes — rendered separately by Compose
        doc.select("blockquote").remove()
        return doc.body().html()
    }

    private fun resolveEmojis(doc: Document) {
        doc.select("emoji").forEach { el ->
            val alt = el.attr("alt")
            if (alt.isNotEmpty()) el.replaceWith(TextNode(alt)) else el.remove()
        }
        doc.select("img[src]").forEach { img ->
            if (isEmojiImage(img.attr("src"), img)) {
                val alt = img.attr("alt")
                if (alt.isNotEmpty()) img.replaceWith(TextNode(alt)) else img.remove()
            }
        }
    }

    private fun isEmojiImage(
        url: String,
        img: org.jsoup.nodes.Element,
    ): Boolean {
        if (img.attr("itemtype") == "http://schema.skype.com/Emoji") return true
        if (EMOJI_IMG_DOMAINS.any { url.contains(it) }) return true
        if (url.contains("/emoticons/") || url.contains("/emoji/")) return true
        return false
    }
}
