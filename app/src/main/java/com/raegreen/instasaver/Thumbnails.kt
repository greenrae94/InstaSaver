package com.raegreen.instasaver

import android.content.Context
import android.util.LruCache
import android.util.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object Thumbnails {

    private const val PIXELS = 256
    private val cache = LruCache<String, ImageBitmap>(64)

    // MediaStore generates thumbnails for both video and image entries, so no image library is
    // needed. A failure returns null and the row falls back to an icon.
    suspend fun load(context: Context, item: SavedItem): ImageBitmap? {
        cache.get(item.uri.toString())?.let { return it }

        val bitmap = withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.loadThumbnail(item.uri, Size(PIXELS, PIXELS), null).asImageBitmap()
            }.getOrNull()
        }

        bitmap?.let { cache.put(item.uri.toString(), it) }

        return bitmap
    }
}
