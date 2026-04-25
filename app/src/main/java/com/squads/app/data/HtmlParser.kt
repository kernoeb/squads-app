package com.squads.app.data

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode

private val EMOJI_IMG_DOMAINS =
    setOf(
        "statics.teams.cdn.office.net",
        "statics.teams.microsoft.com",
        "emojipedia-us.s3.amazonaws.com",
    )

private val HORIZONTAL_WS = Regex("[ \\t]+")
private val WS_AROUND_NEWLINE = Regex(" *\n *")
private val EXCESS_NEWLINES = Regex("\n{3,}")

data class ParsedMessage(
    val text: String,
    val imageUrls: List<String>,
    val replyToName: String? = null,
    val replyToPreview: String? = null,
)

sealed interface ContentBlock {
    data class Text(
        val html: String,
    ) : ContentBlock

    data class Image(
        val url: String,
    ) : ContentBlock
}

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

        // Use wholeText (preserves whitespace) and collapse manually — doc.text()
        // normalises \n into spaces, which would defeat the <br>/<p> markers above
        // and silently break optimistic-send dedup for multi-line messages.
        val text =
            doc
                .body()
                .wholeText()
                .replace(HORIZONTAL_WS, " ")
                .replace(WS_AROUND_NEWLINE, "\n")
                .replace(EXCESS_NEWLINES, "\n\n")
                .trim()

        return ParsedMessage(text, imageUrls, replyToName, replyToPreview)
    }

    fun parseContentBlocks(
        html: String,
        extraImageUrls: List<String> = emptyList(),
    ): List<ContentBlock> {
        if (html.isBlank()) return emptyList()
        val doc = Jsoup.parse(html)
        resolveEmojis(doc)
        doc.select("blockquote").remove()

        val blocks = mutableListOf<ContentBlock>()
        val htmlImages = mutableSetOf<String>()
        val body = doc.body()
        val currentHtml = StringBuilder()

        fun flushText() {
            val text = currentHtml.toString().trim()
            if (text.isNotEmpty()) {
                blocks.add(ContentBlock.Text(text))
            }
            currentHtml.clear()
        }

        // Iterate childNodes (not children) so direct text nodes of body — present when
        // the message HTML is unwrapped, e.g. "hello<br>world" — are not dropped.
        for (node in body.childNodes()) {
            val imgs =
                (node as? Element)
                    ?.select("img[src]")
                    ?.filter { !isEmojiImage(it.attr("src"), it) }
                    .orEmpty()

            if (node is Element && imgs.isNotEmpty()) {
                imgs.forEach { it.remove() }
                val textPart = node.html().trim()
                if (textPart.isNotEmpty()) {
                    currentHtml.append(textPart)
                }
                flushText()
                for (img in imgs) {
                    val url = img.attr("src")
                    blocks.add(ContentBlock.Image(url))
                    htmlImages.add(url)
                }
            } else {
                currentHtml.append(node.outerHtml())
            }
        }
        flushText()

        for (url in extraImageUrls) {
            if (url !in htmlImages) {
                blocks.add(ContentBlock.Image(url))
            }
        }

        return blocks
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

/** Escape plain text for safe embedding in Teams HTML messages. */
fun String.escapeForTeamsHtml(): String =
    replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\n", "<br>")
