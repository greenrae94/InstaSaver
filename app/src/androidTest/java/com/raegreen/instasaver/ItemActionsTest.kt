package com.raegreen.instasaver

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ItemActionsTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun `share intent carries the file and its type`() {
        val saver = GallerySaver(context)
        val name = "instasaver_sharetest_${System.currentTimeMillis()}.mp4"
        val uri = saver.save(name, MediaType.VIDEO, byteArrayOf(1, 2, 3).inputStream())

        try {
            val item = SavedItem(uri, name, MediaType.VIDEO, 0, 3)
            val intent = ItemActions.shareIntent(item)

            assertEquals(Intent.ACTION_SEND, intent.action)
            assertEquals("video/mp4", intent.type)
            assertEquals(uri, intent.getParcelableExtra(Intent.EXTRA_STREAM, android.net.Uri::class.java))
            assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        } finally {
            context.contentResolver.delete(uri, null, null)
        }
    }

    @Test
    fun `deleting a file this app saved needs no confirmation`() {
        val saver = GallerySaver(context)
        val name = "instasaver_deletetest_${System.currentTimeMillis()}.jpg"
        val uri = saver.save(name, MediaType.IMAGE, byteArrayOf(9, 9, 9).inputStream())

        val result = ItemActions.deleteOrRequest(context, uri)

        assertEquals(ItemActions.DeleteResult.Deleted, result)
        assertEquals(0, GalleryRepository(context).load().count { it.displayName == name })
    }
}
