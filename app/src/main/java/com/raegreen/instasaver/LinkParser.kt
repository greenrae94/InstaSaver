package com.raegreen.instasaver

enum class PostKind { POST, REEL }

data class PostRef(val shortcode: String, val kind: PostKind)

sealed interface LinkTarget {
    data class Post(val ref: PostRef) : LinkTarget
    data class Redirect(val url: String) : LinkTarget
}

object LinkParser {

    // Instagram share sheets wrap the link in other text, so both patterns search rather than match whole
    private val POST_PATTERN = Regex("""instagram\.com/(p|reel|reels|tv)/([A-Za-z0-9_-]+)""")
    private val SHARE_PATTERN = Regex("""https?://\S*instagram\.com/share/\S+""")

    fun parse(sharedText: String?): LinkTarget? {
        val text = sharedText?.trim().orEmpty()

        if (text.isEmpty()) return null

        // Short share links hide the real post id behind a redirect, so they are resolved later
        SHARE_PATTERN.find(text)?.let { return LinkTarget.Redirect(it.value.trimEnd('.', ',')) }

        val match = POST_PATTERN.find(text) ?: return null
        val kind = if (match.groupValues[1] == "p") PostKind.POST else PostKind.REEL

        return LinkTarget.Post(PostRef(match.groupValues[2], kind))
    }
}

// The inverse of LinkParser: the canonical link to a post, with the path matching its kind and
// no tracking parameters. This is what gets recorded against a saved file, so it has to be a
// link the app could be handed again and parse — see PostUrlsTest.
object PostUrls {

    fun canonical(shortcode: String, kind: PostKind): String {
        val segment = if (kind == PostKind.REEL) "reel" else "p"

        return "https://www.instagram.com/$segment/$shortcode/"
    }
}
