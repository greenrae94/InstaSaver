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
}
