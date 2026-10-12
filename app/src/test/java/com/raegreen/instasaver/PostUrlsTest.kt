package com.raegreen.instasaver

import org.junit.Assert.assertEquals
import org.junit.Test

class PostUrlsTest {

    @Test
    fun `builds a reel link for a reel`() {
        assertEquals(
            "https://www.instagram.com/reel/DdDxpiPOAzs/",
            PostUrls.canonical("DdDxpiPOAzs", PostKind.REEL),
        )
    }

    @Test
    fun `builds a post link for a photo post`() {
        assertEquals(
            "https://www.instagram.com/p/DdHMr9eMQQR/",
            PostUrls.canonical("DdHMr9eMQQR", PostKind.POST),
        )
    }

    // The link recorded against a saved file is the one the app would be handed if it were
    // shared again, so parsing it back must return the post it was built from — that is what
    // makes a future re-download work without any special case
    @Test
    fun `a built link parses back to the same post`() {
        for (kind in PostKind.entries) {
            val parsed = LinkParser.parse(PostUrls.canonical("DdDxpiPOAzs", kind))

            assertEquals(LinkTarget.Post(PostRef("DdDxpiPOAzs", kind)), parsed)
        }
    }

    @Test
    fun `carries no tracking parameters`() {
        val url = PostUrls.canonical("DdDxpiPOAzs", PostKind.REEL)

        assertEquals(false, url.contains("?"))
    }
}
