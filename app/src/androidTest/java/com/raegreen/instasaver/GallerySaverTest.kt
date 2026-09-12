package com.raegreen.instasaver

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GallerySaverTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val saver = GallerySaver(context)

    @Test
    fun `names_single_and_multi_item_files`() {
        val video = MediaItem("https://x/v.mp4", MediaType.VIDEO, 1)
        val image = MediaItem("https://x/i.jpg", MediaType.IMAGE, 2)

        assertEquals("fleexyz_DdHMr9eMQQR.mp4", FileNaming.fileName("fleexyz", "DdHMr9eMQQR", video, totalItems = 1))
        assertEquals("fleexyz_DdHMr9eMQQR_2.jpg", FileNaming.fileName("fleexyz", "DdHMr9eMQQR", image, totalItems = 3))
    }

    @Test
    fun `saves_an_image_into_the_gallery_and_finds_it_again`() {
        val name = "instasaver_test_${System.currentTimeMillis()}.jpg"
        val bytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xD9.toByte())

        assertFalse(saver.alreadySaved(name, MediaType.IMAGE))

        val uri = saver.save(name, MediaType.IMAGE, bytes.inputStream())

        assertTrue(saver.alreadySaved(name, MediaType.IMAGE))
        assertEquals(bytes.size, context.contentResolver.openInputStream(uri)!!.use { it.readBytes().size })

        context.contentResolver.delete(uri, null, null)
    }
}
