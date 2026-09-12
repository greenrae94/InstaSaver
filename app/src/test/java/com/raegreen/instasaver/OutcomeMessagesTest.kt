package com.raegreen.instasaver

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OutcomeMessagesTest {

    @Test
    fun `saving and skipping are not failures`() {
        assertFalse(OutcomeMessages.isFailure(Outcome.Saved(1)))
        assertFalse(OutcomeMessages.isFailure(Outcome.AlreadySaved))
    }

    @Test
    fun `every unsuccessful outcome is a failure`() {
        assertTrue(OutcomeMessages.isFailure(Outcome.NotAPostLink))
        assertTrue(OutcomeMessages.isFailure(Outcome.CouldNotRead("no post data in page")))
        assertTrue(OutcomeMessages.isFailure(Outcome.NetworkFailed("timeout")))
    }

    @Test
    fun `a successful save does not need attention`() {
        assertFalse(OutcomeMessages.needsAttention(Outcome.Saved(1)))
    }

    @Test
    fun `already saved needs attention but is still not a failure`() {
        assertTrue(OutcomeMessages.needsAttention(Outcome.AlreadySaved))
        assertFalse(OutcomeMessages.isFailure(Outcome.AlreadySaved))
    }

    @Test
    fun `every genuinely failing outcome needs attention`() {
        assertTrue(OutcomeMessages.needsAttention(Outcome.NotAPostLink))
        assertTrue(OutcomeMessages.needsAttention(Outcome.CouldNotRead("no post data in page")))
        assertTrue(OutcomeMessages.needsAttention(Outcome.NetworkFailed("timeout")))
    }

    @Test
    fun `saved text counts the items`() {
        assertEquals("1 item saved to your gallery", OutcomeMessages.text(Outcome.Saved(1)))
        assertEquals("3 items saved to your gallery", OutcomeMessages.text(Outcome.Saved(3)))
    }

    @Test
    fun `an unreadable post explains the likely causes without inventing one`() {
        val text = OutcomeMessages.text(Outcome.CouldNotRead("no post data in page"))

        assertTrue(text.contains("age-restricted"))
        assertTrue(text.contains("private"))
        assertTrue(text.contains("removed"))
    }

    @Test
    fun `a network failure carries its reason`() {
        assertTrue(OutcomeMessages.text(Outcome.NetworkFailed("timeout")).contains("timeout"))
    }
}
