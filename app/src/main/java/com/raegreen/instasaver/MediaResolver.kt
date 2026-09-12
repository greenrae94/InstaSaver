package com.raegreen.instasaver

class MediaResolver(private val fetcher: InstagramPageFetcher) {

    fun resolve(target: LinkTarget): ResolvedPost {
        val ref = when (target) {
            is LinkTarget.Post -> target.ref
            is LinkTarget.Redirect -> refFromRedirect(target.url)
        }

        return PostDataParser.parse(fetcher.fetch(ref))
    }

    // Short share links hide the post id, so follow the redirect and read the real URL
    private fun refFromRedirect(url: String): PostRef {
        val finalUrl = fetcher.resolveRedirect(url)
        val target = LinkParser.parse(finalUrl)

        return (target as? LinkTarget.Post)?.ref
            ?: throw PostDataParseException("share link did not lead to a post: $finalUrl")
    }
}
