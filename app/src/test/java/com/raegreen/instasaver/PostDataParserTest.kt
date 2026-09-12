package com.raegreen.instasaver

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PostDataParserTest {

    private fun fixture(name: String): String =
        checkNotNull(javaClass.classLoader?.getResourceAsStream("fixtures/$name")) { "missing fixture $name" }
            .bufferedReader()
            .use { it.readText() }

    @Test
    fun `reads a single video from a reel page`() {
        val post = PostDataParser.parse(fixture("reel.html"))

        assertEquals(1, post.items.size)
        assertEquals(MediaType.VIDEO, post.items[0].type)
        assertEquals(1, post.items[0].index)
        assertTrue(post.items[0].url.startsWith("https://"))
        assertTrue(post.items[0].url.contains("fbcdn"))
        assertEquals("connorirwin_98", post.username)
        assertEquals("DdDxpiPOAzs", post.shortcode)
    }

    @Test
    fun `reads a single image from a photo post page`() {
        val post = PostDataParser.parse(fixture("image-post.html"))

        assertEquals(1, post.items.size)
        assertEquals(MediaType.IMAGE, post.items[0].type)
        assertTrue(post.items[0].url.contains("fbcdn"))
        assertEquals("fleexyz", post.username)
        assertEquals("DdHMr9eMQQR", post.shortcode)
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
        val post = PostDataParser.parse(fixture("carousel.html"))

        assertEquals(3, post.items.size)
        assertEquals(listOf(1, 2, 3), post.items.map { it.index })
        assertEquals(3, post.items.map { it.url }.distinct().size)
        assertTrue(post.items.all { it.url.contains("fbcdn") })
        assertEquals("fleexyz", post.username)
        assertEquals("DdKEYq0DlhH", post.shortcode)
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
