package com.squads.app.data

import com.squads.app.data.ContentBlock
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HtmlParserTest {
    @Test
    fun `parseMessage with blank html returns empty`() {
        val result = HtmlParser.parseMessage("")
        assertEquals("", result.text)
        assertTrue(result.imageUrls.isEmpty())
        assertNull(result.replyToName)
    }

    @Test
    fun `parseMessage extracts plain text from html`() {
        val result = HtmlParser.parseMessage("<p>Hello world</p>")
        assertEquals("Hello world", result.text)
    }

    @Test
    fun `parseMessage extracts reply info from blockquote`() {
        val html =
            """
            <blockquote itemtype="http://schema.skype.com/Reply">
                <strong>Alice</strong>
                <div itemprop="preview">Original message</div>
            </blockquote>
            <p>My reply</p>
            """.trimIndent()
        val result = HtmlParser.parseMessage(html)
        assertEquals("Alice", result.replyToName)
        assertEquals("Original message", result.replyToPreview)
        assertEquals("My reply", result.text)
    }

    @Test
    fun `parseMessage extracts image urls`() {
        val html = """<p>Check this</p><img src="https://example.com/photo.jpg">"""
        val result = HtmlParser.parseMessage(html)
        assertEquals(1, result.imageUrls.size)
        assertEquals("https://example.com/photo.jpg", result.imageUrls[0])
    }

    @Test
    fun `parseMessage filters out non-https images`() {
        val html = """<img src="http://insecure.com/img.jpg"><img src="data:image/png;base64,abc">"""
        val result = HtmlParser.parseMessage(html)
        assertTrue(result.imageUrls.isEmpty())
    }

    @Test
    fun `parseMessage resolves emoji elements to alt text`() {
        val html = """<emoji alt="😊"></emoji> hi"""
        val result = HtmlParser.parseMessage(html)
        assertTrue(result.text.contains("😊"))
        assertTrue(result.text.contains("hi"))
    }

    @Test
    fun `parseMessage resolves emoji img from known domains`() {
        val html = """<img src="https://statics.teams.cdn.office.net/emoji/1f600.png" alt="😀"> hello"""
        val result = HtmlParser.parseMessage(html)
        assertTrue(result.text.contains("😀"))
        assertTrue(result.text.contains("hello"))
    }

    @Test
    fun `parseContentBlocks separates text and images`() {
        val html =
            """<blockquote>reply</blockquote><p>Text</p>""" +
                """<p><img src="https://x.com/i.jpg" itemtype="http://schema.skype.com/AMSImage"></p>"""
        val blocks = HtmlParser.parseContentBlocks(html)
        assertTrue(blocks.any { it is ContentBlock.Text && "Text" in it.html })
        assertTrue(blocks.any { it is ContentBlock.Image && it.url == "https://x.com/i.jpg" })
        assertTrue(blocks.none { it is ContentBlock.Text && "blockquote" in it.html })
    }

    @Test
    fun `parseContentBlocks with blank html returns empty`() {
        assertEquals(emptyList<ContentBlock>(), HtmlParser.parseContentBlocks(""))
    }

    @Test
    fun `parseContentBlocks preserves interleaved text and images`() {
        val html =
            """
            <p>First paragraph</p>
            <p><img src="https://example.com/img1.png" itemtype="http://schema.skype.com/AMSImage"></p>
            <p>Second paragraph</p>
            <p><img src="https://example.com/img2.png" itemtype="http://schema.skype.com/AMSImage"></p>
            """.trimIndent()
        val blocks = HtmlParser.parseContentBlocks(html)
        assertEquals(4, blocks.size)
        assertTrue(blocks[0] is ContentBlock.Text && "First" in (blocks[0] as ContentBlock.Text).html)
        assertTrue(blocks[1] is ContentBlock.Image && (blocks[1] as ContentBlock.Image).url.contains("img1"))
        assertTrue(blocks[2] is ContentBlock.Text && "Second" in (blocks[2] as ContentBlock.Text).html)
        assertTrue(blocks[3] is ContentBlock.Image && (blocks[3] as ContentBlock.Image).url.contains("img2"))
    }

    @Test
    fun `parseContentBlocks appends extra image urls not in html`() {
        val html = """<p>Some text</p>"""
        val extra = listOf("https://example.com/attachment.png")
        val blocks = HtmlParser.parseContentBlocks(html, extra)
        assertEquals(2, blocks.size)
        assertTrue(blocks[0] is ContentBlock.Text)
        assertEquals("https://example.com/attachment.png", (blocks[1] as ContentBlock.Image).url)
    }

    @Test
    fun `parseContentBlocks does not duplicate extra images already in html`() {
        val url = "https://example.com/photo.jpg"
        val html = """<p><img src="$url" itemtype="http://schema.skype.com/AMSImage"></p>"""
        val blocks = HtmlParser.parseContentBlocks(html, listOf(url))
        assertEquals(1, blocks.size)
        assertTrue(blocks[0] is ContentBlock.Image)
    }

    @Test
    fun `parseContentBlocks keeps emoji as text and extracts real images`() {
        val html =
            """
            <p>Hello <img src="https://statics.teams.cdn.office.net/emoticons/smile.png" alt="😊" itemtype="http://schema.skype.com/Emoji"></p>
            <p><img src="https://example.com/photo.jpg" itemtype="http://schema.skype.com/AMSImage"></p>
            """.trimIndent()
        val blocks = HtmlParser.parseContentBlocks(html)
        assertEquals(2, blocks.size)
        val textBlock = blocks[0] as ContentBlock.Text
        assertTrue("😊" in textBlock.html || "Hello" in textBlock.html)
        assertEquals("https://example.com/photo.jpg", (blocks[1] as ContentBlock.Image).url)
    }

    @Test
    fun `escapeForTeamsHtml escapes special characters`() {
        assertEquals("&amp;", "&".escapeForTeamsHtml())
        assertEquals("&lt;b&gt;", "<b>".escapeForTeamsHtml())
        assertEquals("line1<br>line2", "line1\nline2".escapeForTeamsHtml())
    }

    @Test
    fun `escapeForTeamsHtml handles multiple special chars`() {
        val input = "Tom & Jerry <3 fun\nnew line"
        val expected = "Tom &amp; Jerry &lt;3 fun<br>new line"
        assertEquals(expected, input.escapeForTeamsHtml())
    }
}
