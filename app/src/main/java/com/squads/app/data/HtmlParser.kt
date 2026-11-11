package com.squads.app.data

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import org.jsoup.parser.Parser

private val EMOJI_IMG_DOMAINS =
    setOf(
        "statics.teams.cdn.office.net",
        "statics.teams.microsoft.com",
        "emojipedia-us.s3.amazonaws.com",
    )

private val ANY_WS = Regex("[\\s\u00A0]+")
private const val LINE_BREAK = '\uE001'
private val WS_AROUND_NEWLINE = Regex(" *\n *")
private val EXCESS_NEWLINES = Regex("\n{3,}")

// Leading spaces and runs of spaces, which HTML would otherwise collapse.
private val KEPT_SPACE = Regex("(?<= ) |^ ")
private val LINE_END = Regex("\r?\n")
private val HTML_TAG = Regex("<[a-zA-Z/!]")

// Only entities closed by ';'. Without it, "?a=1&times=2" in a URL would become "?a=1×=2".
private val CLOSED_ENTITY = Regex("&(#[0-9]+|#[xX][0-9a-fA-F]+|[a-zA-Z][a-zA-Z0-9]*);")

// Teams marks reply quotes with this itemtype. A plain <blockquote> is a quote the
// sender typed, and is part of the message.
private const val REPLY_SELECTOR = "blockquote[itemtype=http://schema.skype.com/Reply]"

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
    /**
     * Returns the message content as HTML. Messages of type "Text", and content with no tags
     * at all, are plain text: "a <b> c" is not a tag and line breaks are kept. Entities are
     * decoded first in case the server escaped them.
     */
    fun messageHtml(
        content: String,
        messageType: String,
    ): String =
        if (messageType == "Text" || !HTML_TAG.containsMatchIn(content)) {
            content
                .replace(CLOSED_ENTITY) { Parser.unescapeEntities(it.value, false) }
                .escapeForTeamsHtml()
        } else {
            content
        }

    fun parseMessage(html: String): ParsedMessage {
        if (html.isBlank()) return ParsedMessage("", emptyList())
        val doc = Jsoup.parse(html.replace(LINE_BREAK.toString(), ""))
        resolveEmojis(doc)

        val reply = doc.selectFirst(REPLY_SELECTOR)
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
        for (pre in doc.select("pre, codeblock")) {
            pre.codeTextNodes().forEach { it.text(it.wholeText.replace('\n', LINE_BREAK)) }
        }
        doc.select("br").forEach { it.replaceWith(TextNode(LINE_BREAK.toString())) }
        doc.select("div, p, li, tr, pre, blockquote, h1, h2, h3, h4, h5, h6").forEach { it.prepend(LINE_BREAK.toString()) }
        doc.select("td, th").forEach { it.append(" ") }

        // Line breaks typed in the HTML source (Teams sends "\r\n" between tags) are only
        // spaces. Real breaks come from the markers above. doc.text() would lose those too,
        // and the text must keep them for optimistic-send dedup of multi-line messages.
        val text =
            doc
                .body()
                .wholeText()
                .replace(ANY_WS, " ")
                .replace(LINE_BREAK, '\n')
                .replace(WS_AROUND_NEWLINE, "\n")
                .replace(EXCESS_NEWLINES, "\n\n")
                .trim()

        return ParsedMessage(text, imageUrls, replyToName, replyToPreview)
    }

    /**
     * Builds the blocks shown in a message bubble. Every [ContentBlock.Text] holds HTML,
     * so [plainText] is escaped when there is no [contentHtml].
     */
    fun messageBlocks(
        contentHtml: String,
        plainText: String,
        imageUrls: List<String>,
    ): List<ContentBlock> =
        parseContentBlocks(
            if (contentHtml.isEmpty()) plainText.escapeForTeamsHtml() else messageHtml(contentHtml, ""),
            imageUrls,
        )

    fun parseContentBlocks(
        html: String,
        extraImageUrls: List<String> = emptyList(),
    ): List<ContentBlock> {
        if (html.isBlank() && extraImageUrls.isEmpty()) return emptyList()
        val doc = Jsoup.parse(html)
        doc.outputSettings().prettyPrint(false)
        resolveEmojis(doc)
        doc.select(REPLY_SELECTOR).remove()
        // Only https images may be rendered; drop data:, file:, content:, http:, etc.
        doc.select("img").filterNot { isSafeImageUrl(it.attr("src")) }.forEach { it.remove() }
        keepCodeLayout(doc)
        flattenTables(doc)

        val blocks = mutableListOf<ContentBlock>()
        val images = mutableListOf<String>()

        fun addText(body: Element) {
            if (body.text().isNotBlank()) blocks.add(ContentBlock.Text(body.html().trim()))
        }

        // Cut the tree at each image. Both sides keep the image's ancestors, so a link or
        // quote around an image still wraps the text before and after it.
        val rest = doc
        while (true) {
            val img = rest.body().selectFirst("img[src]") ?: break
            val before = rest.clone()
            cutAround(before.body(), nodeAt(before, pathTo(img)), keepBefore = true)
            addText(before.body())
            images.add(img.attr("src"))
            blocks.add(ContentBlock.Image(img.attr("src")))
            cutAround(rest.body(), img, keepBefore = false)
        }
        addText(rest.body())

        for (url in extraImageUrls) {
            if (url !in images && isSafeImageUrl(url)) {
                blocks.add(ContentBlock.Image(url))
            }
        }

        return blocks
    }

    private fun pathTo(node: Node): List<Int> =
        generateSequence(node) { it.parentNode() }
            .takeWhile { it.parentNode() != null }
            .map { it.siblingIndex() }
            .toList()
            .reversed()

    private fun nodeAt(
        root: Node,
        path: List<Int>,
    ): Node = path.fold(root) { node, i -> node.childNode(i) }

    // Removes [node] and, at every level up to [root], the siblings after it (keepBefore)
    // or before it.
    private fun cutAround(
        root: Element,
        node: Node,
        keepBefore: Boolean,
    ) {
        var current = node
        while (current !== root) {
            val parent = current.parentNode() ?: break
            val siblings = parent.childNodes().toList()
            val i = current.siblingIndex()
            (if (keepBefore) siblings.drop(i + 1) else siblings.take(i)).forEach { it.remove() }
            current = parent
        }
        node.remove()
    }

    private fun Element.codeTextNodes(): List<TextNode> = allElements.flatMap { it.textNodes() }

    // Html.fromHtml does not know <pre>: it would join all lines of a code block and
    // drop the indentation.
    private fun keepCodeLayout(doc: Document) {
        for (pre in doc.select("pre, codeblock")) {
            for (textNode in pre.codeTextNodes()) {
                textNode.wholeText.split(LINE_END).forEachIndexed { i, line ->
                    if (i > 0) textNode.before(Element("br"))
                    textNode.before(TextNode(line.replace("\t", "    ").replace(KEPT_SPACE, "\u00A0")))
                }
                textNode.remove()
            }
            val tt = Element("tt")
            pre.childNodes().toList().forEach { tt.appendChild(it) }
            pre.appendChild(tt)
            pre.tagName("div")
        }
        doc.select("code").tagName("tt")
    }

    // Html.fromHtml does not know tables either: all cells would run together on one line.
    private fun flattenTables(doc: Document) {
        // Inner tables come later in document order, so reversing handles them first.
        for (table in doc.select("table").reversed()) {
            val rows =
                table.select("tr").joinToString("") { row ->
                    "<div>" + row.children().joinToString(" | ") { it.html() } + "</div>"
                }
            table.after("<div>$rows</div>")
            table.remove()
        }
    }

    private fun isSafeImageUrl(url: String): Boolean = url.startsWith("https://")

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

/**
 * Trims whitespace at both ends, but keeps the non-breaking spaces just before the first
 * character: they are the indentation of a code block.
 */
fun CharSequence.trimKeepingIndent(): CharSequence {
    val first = indexOfFirst { !it.isWhitespace() }
    if (first < 0) return subSequence(0, 0)
    var start = first
    while (start > 0 && this[start - 1] == '\u00A0') start--
    return subSequence(start, indexOfLast { !it.isWhitespace() } + 1)
}

/** Escape plain text for safe embedding in Teams HTML messages. */
fun String.escapeForTeamsHtml(): String =
    replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("\n", "<br>")
