package com.raegreen.instasaver

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import java.io.IOException
import java.io.InputStream

object FileNaming {

    fun fileName(username: String, shortcode: String, item: MediaItem, totalItems: Int): String {
        val suffix = if (totalItems > 1) "_${item.index}" else ""

        return "${username}_${shortcode}$suffix.${item.type.extension}"
    }
}

class GallerySaver(private val context: Context) {

    fun alreadySaved(fileName: String, type: MediaType): Boolean {
        val projection = arrayOf(MediaStore.MediaColumns._ID)
        val selection = "${MediaStore.MediaColumns.DISPLAY_NAME} = ? AND ${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?"
        val args = arrayOf(fileName, "${type.relativePath}/%")

        context.contentResolver.query(type.collection, projection, selection, args, null)?.use { cursor ->
            return cursor.count > 0
        }

        return false
    }

    // IS_PENDING hides the file from the gallery until it is written in full
    fun save(fileName: String, type: MediaType, source: InputStream): Uri {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, type.mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, type.relativePath)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }

        return source.use { input ->
            val uri = context.contentResolver.insert(type.collection, values)
                ?: throw IOException("could not create gallery entry for $fileName")

            try {
                context.contentResolver.openOutputStream(uri)?.use { output ->
                    input.copyTo(output)
                } ?: throw IOException("could not open $fileName for writing")

                context.contentResolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            } catch (error: Exception) {
                context.contentResolver.delete(uri, null, null)

                throw error
            }

            uri
        }
    }
}
