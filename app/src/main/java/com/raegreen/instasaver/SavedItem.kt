package com.raegreen.instasaver

import android.net.Uri

data class SavedItem(
    val uri: Uri,
    val displayName: String,
    val type: MediaType,
    val dateAddedSeconds: Long,
    val sizeBytes: Long,
    // The post this file came from, or null for anything saved before the app recorded it
    val postUrl: String? = null,
)
