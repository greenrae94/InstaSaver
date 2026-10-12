package com.raegreen.instasaver

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DismissedFailuresTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    // Removes only the ids this test file adds, leaving any other stored dismissal untouched
    @After
    fun cleanUp() {
        val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        val remaining = preferences.getStringSet(KEY, emptySet()).orEmpty() - setOf("x", "y", "mutated-locally")

        preferences.edit().putStringSet(KEY, remaining).apply()
    }

    @Test
    fun `a dismissal persists so a newly constructed instance sees it`() {
        DismissedFailures(context).add("x")

        val reloaded = DismissedFailures(context).get()

        assertTrue(reloaded.contains("x"))
    }

    @Test
    fun `adding a second id does not clobber the first`() {
        val dismissedFailures = DismissedFailures(context)

        dismissedFailures.add("x")
        dismissedFailures.add("y")

        val stored = dismissedFailures.get()

        assertTrue(stored.contains("x"))
        assertTrue(stored.contains("y"))
    }

    @Test
    fun `mutating a previously returned set does not affect a later read`() {
        DismissedFailures(context).add("x")

        val dismissedFailures = DismissedFailures(context)
        val first = dismissedFailures.get()

        // The Android trap: getStringSet can hand back the live internal set, so mutating a
        // returned set in place must never leak into a later read. The defensive copy in get()
        // means the attempt either throws or is silently discarded depending on the copy's
        // concrete type — both are correct, and neither may reach the store.
        runCatching { (first as? MutableSet<String>)?.add("mutated-locally") }

        val second = dismissedFailures.get()

        assertFalse(second.contains("mutated-locally"))
    }

    private companion object {
        const val KEY = "dismissed_failures"
    }
}
