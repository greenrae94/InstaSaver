package com.raegreen.instasaver

import android.content.ContentUris
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch

class GalleryRepository(private val context: Context) {

    // Newest first, both folders merged — the gallery is the app's only record of what it saved
    fun load(): List<SavedItem> =
        (query(videoCollection, MediaType.VIDEO) + query(imageCollection, MediaType.IMAGE))
            .sortedByDescending { it.dateAddedSeconds }

    // Emits once immediately, then again every time MediaStore changes, so saves and deletions
    // made anywhere on the phone show up without the user refreshing
    fun items(): Flow<List<SavedItem>> = callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                launch { send(load()) }
            }
        }

        context.contentResolver.registerContentObserver(videoCollection, true, observer)
        context.contentResolver.registerContentObserver(imageCollection, true, observer)

        send(load())

        awaitClose { context.contentResolver.unregisterContentObserver(observer) }
    }.flowOn(Dispatchers.IO).conflate()

    private fun query(collection: Uri, type: MediaType): List<SavedItem> {
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.DATE_ADDED,
            MediaStore.MediaColumns.SIZE,
        )
        val selection = "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?"
        val args = arrayOf("${type.relativePath}/%")
        val items = mutableListOf<SavedItem>()

        context.contentResolver.query(collection, projection, selection, args, null)?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)

            while (cursor.moveToNext()) {
                items += SavedItem(
                    uri = ContentUris.withAppendedId(collection, cursor.getLong(idColumn)),
                    displayName = cursor.getString(nameColumn),
                    type = type,
                    dateAddedSeconds = cursor.getLong(dateColumn),
                    sizeBytes = cursor.getLong(sizeColumn),
                )
            }
        }

        return items
    }

    private val videoCollection: Uri
        get() = MediaType.VIDEO.collection

    private val imageCollection: Uri
        get() = MediaType.IMAGE.collection
}
