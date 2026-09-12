package com.raegreen.instasaver

import android.content.Context

object ClipboardPickup {

    // Returns the text to download, or null when there is nothing new to act on. Trimmed before
    // comparing so re-copying the same link with stray whitespace does not look like a new one.
    fun linkToDownload(clipboardText: String?, lastHandled: String?): String? {
        val text = clipboardText?.trim().orEmpty()

        if (text.isEmpty() || text == lastHandled) return null

        return if (LinkParser.parse(text) != null) text else null
    }
}

// The app's only owned persistent state: the last link it acted on
class LastHandledLink(context: Context) {

    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun get(): String? = preferences.getString(KEY, null)

    fun set(link: String) {
        preferences.edit().putString(KEY, link).apply()
    }

    private companion object {
        const val KEY = "last_handled_link"
    }
}
