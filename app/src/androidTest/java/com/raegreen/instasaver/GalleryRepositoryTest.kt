package com.raegreen.instasaver

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
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

        try {
            val videoUri = saver.save(videoName, MediaType.VIDEO, bytes.inputStream())

            try {
                val names = repository.load().map { it.displayName }

                assertTrue(names.contains(imageName))
                assertTrue(names.contains(videoName))
            } finally {
                context.contentResolver.delete(videoUri, null, null)
            }
        } finally {
            context.contentResolver.delete(imageUri, null, null)
        }
    }

    @Test
    fun `items emits an update when a file is saved while collecting`() = runBlocking {
        val name = "instasaver_repotest_${System.currentTimeMillis()}.jpg"
        val bytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xD9.toByte())
        var uri: Uri? = null
        var saved = false

        try {
            withTimeout(10_000) {
                // Save only once collection is underway (inside the predicate, on the first
                // emission) so this proves the ContentObserver re-read, not just the initial load
                async {
                    repository.items().first { list ->
                        if (!saved) {
                            saved = true
                            uri = saver.save(name, MediaType.IMAGE, bytes.inputStream())
                        }

                        list.any { it.displayName == name }
                    }
                }.await()
            }
        } finally {
            uri?.let { context.contentResolver.delete(it, null, null) }
        }

        Unit
    }
}
