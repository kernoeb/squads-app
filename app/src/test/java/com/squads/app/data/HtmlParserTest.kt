package com.squads.app.data

import com.squads.app.data.ContentBlock
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
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
            """<blockquote itemtype="http://schema.skype.com/Reply">reply</blockquote><p>Text</p>""" +
                """<p><img src="https://x.com/i.jpg" itemtype="http://schema.skype.com/AMSImage"></p>"""
        val blocks = HtmlParser.parseContentBlocks(html)
        assertTrue(blocks.any { it is ContentBlock.Text && "Text" in it.html })
        assertTrue(blocks.any { it is ContentBlock.Image && it.url == "https://x.com/i.jpg" })
        assertTrue(blocks.none { it is ContentBlock.Text && "blockquote" in it.html })
    }

    @Test
    fun `parseContentBlocks drops non-https images`() {
        val html =
            """<p>Hi</p>""" +
                """<p><img src="http://graph.microsoft.com/a.png"></p>""" +
                """<p><img src="data:image/png;base64,AAAA"></p>""" +
                """<p><img src="file:///sdcard/secret.png"></p>""" +
                """<p><img src="content://media/external/images/1"></p>""" +
                """<p><img src="https://eu-api.asm.skype.com/v1/objects/x/views/imgo"></p>"""
        val images = HtmlParser.parseContentBlocks(html).filterIsInstance<ContentBlock.Image>()
        assertEquals(listOf("https://eu-api.asm.skype.com/v1/objects/x/views/imgo"), images.map { it.url })
    }

    @Test
    fun `parseContentBlocks drops non-https extra image urls`() {
        val blocks =
            HtmlParser.parseContentBlocks(
                "<p>Hi</p>",
                extraImageUrls = listOf("http://x.com/a.png", "file:///a.png", "https://x.com/b.png"),
            )
        val images = blocks.filterIsInstance<ContentBlock.Image>()
        assertEquals(listOf("https://x.com/b.png"), images.map { it.url })
    }

    @Test
    fun `parseContentBlocks with blank html returns empty`() {
        assertEquals(emptyList<ContentBlock>(), HtmlParser.parseContentBlocks(""))
    }

    @Test
    fun `parseMessage round-trips newline content for optimistic dedup`() {
        // mergeWithOptimistic compares local raw content vs server parsed text by equality.
        // Local "hello\nworld" sends as html "hello<br>world", server returns "<p>hello<br>world</p>".
        // Both must yield the same parsed text.
        val parsedServer = HtmlParser.parseMessage("<p>hello<br>world</p>").text
        assertEquals("hello\nworld", parsedServer)
    }

    @Test
    fun `parseContentBlocks keeps text when not wrapped in a block element`() {
        // Local optimistic non-reply messages with newlines look like "hello<br>world"
        // (no surrounding <p>). Body's text nodes must not be lost when iterating.
        val blocks = HtmlParser.parseContentBlocks("hello<br>world")
        assertEquals(1, blocks.size)
        val text = (blocks[0] as ContentBlock.Text).html
        assertTrue("hello" in text) { "expected 'hello' in $text" }
        assertTrue("world" in text) { "expected 'world' in $text" }
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

    // Shape of a card sent by squads-cli, copied from a real Teams message with the text
    // replaced. Teams puts "\r\n" between tags.
    private val squadsCliCard =
        "<blockquote>\n\r\n" +
            "<p><span style=\"color:#D97757; font-size:20px\">❋</span> <b style=\"font-size:18px\">Card title</b></p>\r\n\n\r\n" +
            "<p>Intro line.</p>\r\n\n\r\n" +
            "<p>&nbsp;</p>\r\n\n\r\n" +
            "<p><b>1. Point one</b><br>\r\n\nBody one.</p>\r\n\n\r\n" +
            "<p>&nbsp;</p>\r\n\n\r\n" +
            "<p><b>2. Point two</b><br>\r\n\nBody two.</p>\r\n\n\r\n" +
            "<p><span style=\"color:#8b8b8b; font-size:12px\">footer text</span></p>\r\n\n" +
            "</blockquote>"

    @Test
    fun `parseMessage reads a squads-cli card as content, not as a reply`() {
        val result = HtmlParser.parseMessage(squadsCliCard)
        assertNull(result.replyToName)
        assertNull(result.replyToPreview)
        assertEquals(
            "❋ Card title\nIntro line.\n\n1. Point one\nBody one.\n\n2. Point two\nBody two.\nfooter text",
            result.text,
        )
    }

    @Test
    fun `messageBlocks shows a squads-cli card as one quoted text block`() {
        val blocks = HtmlParser.messageBlocks(squadsCliCard, "", emptyList())
        val html = (blocks.single() as ContentBlock.Text).html
        assertTrue(html.startsWith("<blockquote>") && html.endsWith("</blockquote>")) { html }
        listOf("Card title", "Intro line.", "<b>1. Point one</b><br>", "Body two.", "footer text").forEach {
            assertTrue(it in html) { "expected '$it' in $html" }
        }
    }

    @Test
    fun `parseMessage ignores line breaks in the html source`() {
        assertEquals("a b\nc", HtmlParser.parseMessage("<p>a\r\nb</p>\r\n\n\r\n<p>c</p>").text)
    }

    @Test
    fun `parseMessage keeps a quote the sender typed`() {
        val result = HtmlParser.parseMessage("<blockquote><p>To be or not to be</p></blockquote>")
        assertEquals("To be or not to be", result.text)
        assertNull(result.replyToName)
    }

    @Test
    fun `parseContentBlocks keeps a quote the sender typed`() {
        val html =
            """<blockquote itemtype="http://schema.skype.com/Reply"><strong>Bob</strong><p itemprop="preview">Hi</p></blockquote>""" +
                """<blockquote><p>Quoted</p></blockquote><p>Answer</p>"""
        val blocks = HtmlParser.parseContentBlocks(html)
        assertEquals(listOf(ContentBlock.Text("<blockquote><p>Quoted</p></blockquote><p>Answer</p>")), blocks)
    }

    @Test
    fun `parseContentBlocks keeps text on both sides of an inline image in order`() {
        val html = """<div><p>before <img src="https://x.com/a.png" itemtype="http://schema.skype.com/AMSImage"> after</p></div>"""
        val blocks = HtmlParser.parseContentBlocks(html)
        assertEquals(3, blocks.size) { "$blocks" }
        assertTrue("before" in (blocks[0] as ContentBlock.Text).html && "after" !in (blocks[0] as ContentBlock.Text).html)
        assertEquals(ContentBlock.Image("https://x.com/a.png"), blocks[1])
        assertTrue("after" in (blocks[2] as ContentBlock.Text).html)
    }

    @Test
    fun `parseContentBlocks drops text blocks that are only whitespace`() {
        val html = """<p>&nbsp;</p><p><img src="https://x.com/a.png"></p><p>&nbsp;<br></p>"""
        assertEquals(listOf(ContentBlock.Image("https://x.com/a.png")), HtmlParser.parseContentBlocks(html))
    }

    @Test
    fun `parseContentBlocks keeps line breaks and indentation of code blocks`() {
        val html = "<pre><code>fun a() {\n    return 1\n}</code></pre>"
        val text = (HtmlParser.parseContentBlocks(html).single() as ContentBlock.Text).html
        assertEquals(2, Regex("<br>").findAll(text).count()) { text }
        assertTrue("&nbsp;&nbsp;&nbsp;&nbsp;return 1" in text) { text }
        assertTrue("<tt>" in text && "<pre>" !in text) { text }
    }

    @Test
    fun `parseContentBlocks puts each table row on its own line`() {
        val html = "<table><tr><th>Name</th><th>Age</th></tr><tr><td>Ann</td><td>30</td></tr></table>"
        val text = (HtmlParser.parseContentBlocks(html).single() as ContentBlock.Text).html
        assertTrue("<div>Name | Age</div><div>Ann | 30</div>" in text) { text }
        assertTrue("<table" !in text) { text }
    }

    @Test
    fun `parseMessage separates table cells and rows`() {
        val html = "<table><tr><td>Ann</td><td>30</td></tr><tr><td>Bob</td><td>25</td></tr></table>"
        assertEquals("Ann 30\nBob 25", HtmlParser.parseMessage(html).text)
    }

    @Test
    fun `messageBlocks escapes plain text so angle brackets are not read as tags`() {
        val blocks = HtmlParser.messageBlocks(contentHtml = "", plainText = "if x<y>z", imageUrls = emptyList())
        assertEquals(listOf(ContentBlock.Text("if x&lt;y&gt;z")), blocks)
    }

    @Test
    fun `messageBlocks keeps image urls when there is no text`() {
        val blocks = HtmlParser.messageBlocks("", "", listOf("https://x.com/a.png"))
        assertEquals(listOf(ContentBlock.Image("https://x.com/a.png")), blocks)
    }

    @Test
    fun `messageHtml treats Text messages as plain text`() {
        val html = HtmlParser.messageHtml("if a<b and c>d", "Text")
        assertEquals("if a<b and c>d", HtmlParser.parseMessage(html).text)
        assertEquals("Tom & Jerry", HtmlParser.parseMessage(HtmlParser.messageHtml("Tom &amp; Jerry", "Text")).text)
        assertEquals("<p>Hi</p>", HtmlParser.messageHtml("<p>Hi</p>", "RichText/Html"))
    }

    @Test
    fun `parseContentBlocks keeps mentions links and emoji in one text block`() {
        val html =
            """<p><span itemscope="" itemtype="http://schema.skype.com/Mention" itemid="0">Alice</span> see """ +
                """<a href="https://example.com">this</a> <img src="https://statics.teams.cdn.office.net/x.png" alt="👍" itemtype="http://schema.skype.com/Emoji"></p>"""
        val text = (HtmlParser.parseContentBlocks(html).single() as ContentBlock.Text).html
        assertTrue("Alice" in text && "<a href=\"https://example.com\">this</a>" in text && "👍" in text) { text }
    }

    @Test
    fun `parseContentBlocks keeps the tags around an image on both sides`() {
        val html = """<blockquote><a href="https://e.com">see <img src="https://x.com/a.png"> docs</a></blockquote>"""
        assertEquals(
            listOf(
                ContentBlock.Text("""<blockquote><a href="https://e.com">see </a></blockquote>"""),
                ContentBlock.Image("https://x.com/a.png"),
                ContentBlock.Text("""<blockquote><a href="https://e.com"> docs</a></blockquote>"""),
            ),
            HtmlParser.parseContentBlocks(html),
        )
    }

    @Test
    fun `content with no tags keeps its line breaks`() {
        val html = HtmlParser.messageHtml("line1\nline2", "RichText/Html")
        assertEquals("line1\nline2", HtmlParser.parseMessage(html).text)
        val blocks = HtmlParser.messageBlocks("line1\nline2", "line1\nline2", emptyList())
        assertEquals(listOf(ContentBlock.Text("line1<br>line2")), blocks)
    }

    @Test
    fun `messageHtml keeps entity names that have no semicolon`() {
        val url = "https://x.com/?a=1&times=2&copy=3"
        assertEquals(url, HtmlParser.parseMessage(HtmlParser.messageHtml(url, "Text")).text)
    }

    @Test
    fun `parseContentBlocks drops carriage returns in code blocks`() {
        val text = (HtmlParser.parseContentBlocks("<pre>a\r\nb</pre>").single() as ContentBlock.Text).html
        assertFalse('\r' in text) { text }
        assertTrue("a<br>b" in text) { text }
    }

    @Test
    fun `parseContentBlocks keeps tab indentation in code blocks`() {
        val text = (HtmlParser.parseContentBlocks("<pre>x\n\ty</pre>").single() as ContentBlock.Text).html
        assertTrue("<br>&nbsp;&nbsp;&nbsp;&nbsp;y" in text) { text }
    }

    @Test
    fun `trimKeepingIndent keeps the indentation of the first line`() {
        assertEquals("  x\ny", "\n \n  x\ny  \n".trimKeepingIndent().toString())
        assertEquals("", " \n ".trimKeepingIndent().toString())
    }

    @Test
    fun `parseMessage ignores private line break characters in the input`() {
        assertEquals("ab", HtmlParser.parseMessage("<p>ab</p>").text)
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

    @Test
    fun `escapeForTeamsHtml escapes double quotes`() {
        assertEquals("say &quot;hi&quot;", "say \"hi\"".escapeForTeamsHtml())
    }

    @Test
    fun `escapeForTeamsHtml neutralises markup in display names`() {
        val name = "Eve <img src=x onerror=alert(1)> & Co"
        val expected = "Eve &lt;img src=x onerror=alert(1)&gt; &amp; Co"
        assertEquals(expected, name.escapeForTeamsHtml())
    }
}
