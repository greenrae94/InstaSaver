package com.raegreen.instasaver

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LinkParserTest {

    private fun post(text: String) = LinkParser.parse(text) as LinkTarget.Post

    @Test
    fun `parses a reel link with tracking parameter`() {
        val target = post("https://www.instagram.com/reel/DdDxpiPOAzs/?stkn=OTQwa3YzaWNzcWZo")

        assertEquals(PostRef("DdDxpiPOAzs", PostKind.REEL), target.ref)
    }

    @Test
    fun `parses a post link with tracking parameter`() {
        val target = post("https://www.instagram.com/p/DdHMr9eMQQR/?stkn=bmRucHNmY2Y2aDd5")

        assertEquals(PostRef("DdHMr9eMQQR", PostKind.POST), target.ref)
    }

    @Test
    fun `parses reels and tv paths as reels`() {
        assertEquals(PostKind.REEL, post("https://www.instagram.com/reels/AbC123_-x/").ref.kind)
        assertEquals(PostKind.REEL, post("https://instagram.com/tv/AbC123_-x/").ref.kind)
    }

    @Test
    fun `finds the link inside surrounding share text`() {
        val target = post("Check this out https://www.instagram.com/reel/DdDxpiPOAzs/?igsh=abc \n- sent from Instagram")

        assertEquals("DdDxpiPOAzs", target.ref.shortcode)
    }

    @Test
    fun `treats a share link as needing a redirect`() {
        val target = LinkParser.parse("https://www.instagram.com/share/reel/AbCdEf123/") as LinkTarget.Redirect

        assertEquals("https://www.instagram.com/share/reel/AbCdEf123/", target.url)
    }

    @Test
    fun `rejects links that are not posts`() {
        assertNull(LinkParser.parse("https://www.instagram.com/fleexyz/"))
        assertNull(LinkParser.parse("https://www.instagram.com/stories/fleexyz/3982244854065794284/"))
        assertNull(LinkParser.parse("https://youtube.com/watch?v=abc"))
        assertNull(LinkParser.parse(""))
        assertNull(LinkParser.parse(null))
    }
}
