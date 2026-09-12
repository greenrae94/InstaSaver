package com.raegreen.instasaver

import android.net.Uri

data class SavedItem(
    val uri: Uri,
    val displayName: String,
    val type: MediaType,
    val dateAddedSeconds: Long,
    val sizeBytes: Long,
)
