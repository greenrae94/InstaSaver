package com.raegreen.instasaver

import android.net.Uri
import android.provider.MediaStore

// The video/image duality gets encoded once here rather than separately in every file that
// needs a filename, a MIME type, a save location, or a share intent for a MediaItem

val MediaType.extension: String
    get() = if (this == MediaType.VIDEO) "mp4" else "jpg"

val MediaType.mimeType: String
    get() = if (this == MediaType.VIDEO) "video/mp4" else "image/jpeg"

val MediaType.relativePath: String
    get() = if (this == MediaType.VIDEO) "Movies/InstaSaver" else "Pictures/InstaSaver"

val MediaType.collection: Uri
    get() = if (this == MediaType.VIDEO) {
        MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
    } else {
        MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
    }
