package com.raegreen.instasaver

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PostDataParserTest {

    // Instagram serves media from either CDN host depending on when and where the page was fetched
    private fun isInstagramCdn(url: String): Boolean = url.contains("fbcdn") || url.contains("cdninstagram")

    @Test
    fun `reads a single video from a reel page`() {
        val post = PostDataParser.parse(Fixtures.page("reel.html"))

        assertEquals(1, post.items.size)
        assertEquals(MediaType.VIDEO, post.items[0].type)
        assertEquals(1, post.items[0].index)
        assertTrue(post.items[0].url.startsWith("https://"))
        assertTrue(isInstagramCdn(post.items[0].url))
        assertEquals(Fixtures.expected("reel.username"), post.username)
        assertEquals(Fixtures.expected("reel.shortcode"), post.shortcode)
    }

    @Test
    fun `reads a single image from a photo post page`() {
        val post = PostDataParser.parse(Fixtures.page("image-post.html"))

        assertEquals(1, post.items.size)
        assertEquals(MediaType.IMAGE, post.items[0].type)
        assertTrue(isInstagramCdn(post.items[0].url))
        assertEquals(Fixtures.expected("image.username"), post.username)
        assertEquals(Fixtures.expected("image.shortcode"), post.shortcode)
    }

    @Test
    fun `rejects a page with no post data`() {
        val error = assertThrows(PostDataParseException::class.java) {
            PostDataParser.parse("<html><body>nothing here</body></html>")
        }

        assertTrue(error.message!!.contains("no post data"))
    }

    @Test
    fun `reads every item of a carousel in order`() {
        val post = PostDataParser.parse(Fixtures.page("carousel.html"))

        val expectedCount = Fixtures.expected("carousel.itemCount").toInt()

        assertEquals(expectedCount, post.items.size)
        assertEquals((1..expectedCount).toList(), post.items.map { it.index })
        // Distinct URLs guard against reading the parent's cover image for every child
        assertEquals(expectedCount, post.items.map { it.url }.distinct().size)
        assertTrue(post.items.all { isInstagramCdn(it.url) })
        assertEquals(Fixtures.expected("carousel.username"), post.username)
        assertEquals(Fixtures.expected("carousel.shortcode"), post.shortcode)
    }

    @Test
    fun `rejects post data with no username`() {
        val html = """<script type="application/json">{"xig_polaris_media":{"if_not_gated_logged_out":{}}}</script>"""

        val error = assertThrows(PostDataParseException::class.java) {
            PostDataParser.parse(html)
        }

        assertEquals("no username in post data", error.message)
    }

    @Test
    fun `rejects post data with no shortcode`() {
        val html =
            """<script type="application/json">{"xig_polaris_media":{"if_not_gated_logged_out":{"user":{"username":"someone"}}}}</script>"""

        val error = assertThrows(PostDataParseException::class.java) {
            PostDataParser.parse(html)
        }

        assertEquals("no shortcode in post data", error.message)
    }

    @Test
    fun `rejects post data with no video or image`() {
        val html =
            """<script type="application/json">{"xig_polaris_media":{"if_not_gated_logged_out":{"user":{"username":"someone"},"code":"ABC123"}}}</script>"""

        val error = assertThrows(PostDataParseException::class.java) {
            PostDataParser.parse(html)
        }

        assertEquals("no video or image in post data", error.message)
    }
}
