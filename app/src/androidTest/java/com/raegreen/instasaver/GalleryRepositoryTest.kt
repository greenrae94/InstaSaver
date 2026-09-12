package com.raegreen.instasaver

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GalleryRepositoryTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val saver = GallerySaver(context)
    private val repository = GalleryRepository(context)

    @Test
    fun `lists a saved image and puts the newest first`() {
        val name = "instasaver_repotest_${System.currentTimeMillis()}.jpg"
        val bytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xD9.toByte())
        val uri = saver.save(name, MediaType.IMAGE, bytes.inputStream())

        try {
            val items = repository.load()
            val mine = items.first { it.displayName == name }

            assertEquals(MediaType.IMAGE, mine.type)
            assertEquals(bytes.size.toLong(), mine.sizeBytes)
            assertEquals(name, items.first().displayName)
            assertTrue(items.zipWithNext().all { (a, b) -> a.dateAddedSeconds >= b.dateAddedSeconds })
        } finally {
            context.contentResolver.delete(uri, null, null)
        }
    }

    @Test
    fun `reads both video and image folders`() {
        val stamp = System.currentTimeMillis()
        val imageName = "instasaver_repotest_${stamp}.jpg"
        val videoName = "instasaver_repotest_${stamp}.mp4"
        val bytes = byteArrayOf(1, 2, 3, 4)
        val imageUri = saver.save(imageName, MediaType.IMAGE, bytes.inputStream())
        val videoUri = saver.save(videoName, MediaType.VIDEO, bytes.inputStream())

        try {
            val names = repository.load().map { it.displayName }

            assertTrue(names.contains(imageName))
            assertTrue(names.contains(videoName))
        } finally {
            context.contentResolver.delete(imageUri, null, null)
            context.contentResolver.delete(videoUri, null, null)
        }
    }
}
