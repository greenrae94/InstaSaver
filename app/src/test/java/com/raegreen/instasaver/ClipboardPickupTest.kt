package com.raegreen.instasaver

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ClipboardPickupTest {

    private val link = "https://www.instagram.com/reel/DdDxpiPOAzs/?stkn=abc"

    @Test
    fun `a newly copied post link is picked up`() {
        assertEquals(link, ClipboardPickup.linkToDownload(link, lastHandled = null))
    }

    @Test
    fun `the same link is not picked up twice`() {
        assertNull(ClipboardPickup.linkToDownload(link, lastHandled = link))
    }

    @Test
    fun `a different post is picked up after an earlier one`() {
        val newer = "https://www.instagram.com/p/DdHMr9eMQQR/"

        assertEquals(newer, ClipboardPickup.linkToDownload(newer, lastHandled = link))
    }

    @Test
    fun `surrounding whitespace does not make a link look new`() {
        assertNull(ClipboardPickup.linkToDownload("  $link  ", lastHandled = link))
    }

    @Test
    fun `non post text is ignored`() {
        assertNull(ClipboardPickup.linkToDownload("https://www.instagram.com/fleexyz/", lastHandled = null))
        assertNull(ClipboardPickup.linkToDownload("just some text", lastHandled = null))
        assertNull(ClipboardPickup.linkToDownload("", lastHandled = null))
        assertNull(ClipboardPickup.linkToDownload(null, lastHandled = null))
    }
}
