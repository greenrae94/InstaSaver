package com.raegreen.instasaver

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PostLinksTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val postLinks = PostLinks(context)

    // Removes only the names this test file records, leaving the owner's real entries alone
    @After
    fun cleanUp() {
        for (name in listOf(FILE_NAME, "${FILE_NAME}_2", "never-recorded.mp4")) {
            postLinks.forget(name)
        }
    }

    @Test
    fun `a recorded link persists so a newly constructed instance sees it`() {
        postLinks.record(FILE_NAME, POST_URL)

        assertEquals(POST_URL, PostLinks(context).urlFor(FILE_NAME))
    }

    // Legacy files, and any file saved while this store was empty, must read back as absent
    // rather than blowing up
    @Test
    fun `a file that was never recorded reads back as null`() {
        assertNull(postLinks.urlFor("never-recorded.mp4"))
    }

    @Test
    fun `recording a second file does not clobber the first`() {
        postLinks.record(FILE_NAME, POST_URL)
        postLinks.record("${FILE_NAME}_2", POST_URL)

        assertEquals(POST_URL, postLinks.urlFor(FILE_NAME))
        assertEquals(POST_URL, postLinks.urlFor("${FILE_NAME}_2"))
    }

    @Test
    fun `forgetting one file leaves the others alone`() {
        postLinks.record(FILE_NAME, POST_URL)
        postLinks.record("${FILE_NAME}_2", POST_URL)

        postLinks.forget(FILE_NAME)

        assertNull(postLinks.urlFor(FILE_NAME))
        assertEquals(POST_URL, postLinks.urlFor("${FILE_NAME}_2"))
    }

    private companion object {
        const val FILE_NAME = "post_links_test_account_DdDxpiPOAzs.mp4"
        const val POST_URL = "https://www.instagram.com/reel/DdDxpiPOAzs/"
    }
}
