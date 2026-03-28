package com.squads.app.data

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
    fun `cleanForRendering removes images and blockquotes`() {
        val html = """<blockquote>reply</blockquote><p>Text</p><img src="https://x.com/i.jpg">"""
        val result = HtmlParser.cleanForRendering(html)
        assertTrue("Text" in result)
        assertTrue("blockquote" !in result)
        assertTrue("img" !in result)
    }

    @Test
    fun `cleanForRendering with blank html returns empty`() {
        assertEquals("", HtmlParser.cleanForRendering(""))
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
