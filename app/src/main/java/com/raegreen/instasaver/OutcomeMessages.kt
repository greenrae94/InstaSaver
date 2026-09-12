package com.raegreen.instasaver

// One place for what the user is told, used by both the notification and the failed row in the
// list, so the two can never drift apart.
object OutcomeMessages {

    fun isFailure(outcome: Outcome): Boolean = when (outcome) {
        is Outcome.Saved, Outcome.AlreadySaved -> false
        Outcome.NotAPostLink -> true
        is Outcome.CouldNotRead -> true
        is Outcome.NetworkFailed -> true
    }

    // Whether the owner needs to be told anything at all. This is deliberately separate from
    // isFailure: AlreadySaved must alert (silence used to mean "nothing happened", which read as
    // the app ignoring a share) without being recorded as failed work — a failed row saying
    // "already saved" would be wrong and would need dismissing. Do not collapse these two
    // predicates back into one.
    fun needsAttention(outcome: Outcome): Boolean = when (outcome) {
        is Outcome.Saved -> false
        Outcome.AlreadySaved -> true
        Outcome.NotAPostLink -> true
        is Outcome.CouldNotRead -> true
        is Outcome.NetworkFailed -> true
    }

    fun title(outcome: Outcome): String = when (outcome) {
        is Outcome.Saved -> "Saved"
        Outcome.AlreadySaved -> "Already saved"
        Outcome.NotAPostLink -> "Not a post link"
        is Outcome.CouldNotRead -> "Couldn't read this post"
        is Outcome.NetworkFailed -> "Download failed"
    }

    fun text(outcome: Outcome): String = when (outcome) {
        is Outcome.Saved ->
            if (outcome.count == 1) "1 item saved to your gallery" else "${outcome.count} items saved to your gallery"

        Outcome.AlreadySaved -> "This post is already in your gallery"

        Outcome.NotAPostLink -> "That's not a post or Reel link"

        // Instagram does not say which restriction applies, so name the likely causes rather
        // than guessing one
        is Outcome.CouldNotRead ->
            "Instagram didn't send this post's data. It may be age-restricted, private, or removed. Open it in Instagram to check."

        is Outcome.NetworkFailed -> "Couldn't reach Instagram: ${outcome.reason}"
    }
}
