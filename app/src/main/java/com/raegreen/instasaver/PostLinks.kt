package com.raegreen.instasaver

import android.content.Context

// Which Instagram post each saved file came from, so the app can offer a way back to it.
// Keyed by display name, because that is already what the gallery and the duplicate check
// treat as a saved item's identity, and it survives a file being deleted and downloaded again.
//
// This gets its own preferences file rather than the shared "instasaver" one. That file holds
// the clipboard's last-handled link and the dismissed failures; keeping a few hundred per-item
// entries out of it means nothing here can ever disturb them.
class PostLinks(context: Context) : LinkRecord {

    private val preferences = context.getSharedPreferences(PREFERENCES_FILE, Context.MODE_PRIVATE)

    override fun record(fileName: String, postUrl: String) {
        preferences.edit().putString(fileName, postUrl).apply()
    }

    // Null for anything saved before this existed, which the item screen reads as "no post to
    // open" rather than as an error
    fun urlFor(fileName: String): String? = preferences.getString(fileName, null)

    fun forget(fileName: String) {
        preferences.edit().remove(fileName).apply()
    }

    private companion object {
        const val PREFERENCES_FILE = "instasaver_post_links"
    }
}
