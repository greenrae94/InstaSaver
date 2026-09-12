package com.raegreen.instasaver

import java.io.IOException
import java.io.InputStream

interface PostSource {
    fun resolve(target: LinkTarget): ResolvedPost
    fun download(url: String): InputStream
}

interface MediaSink {
    fun alreadySaved(fileName: String, type: MediaType): Boolean
    fun save(fileName: String, type: MediaType, source: InputStream)
}

sealed interface Outcome {
    data class Saved(val count: Int) : Outcome
    object AlreadySaved : Outcome
    object NotAPostLink : Outcome
    data class CouldNotRead(val reason: String) : Outcome
    data class NetworkFailed(val reason: String) : Outcome
}

class DownloadCoordinator(private val source: PostSource, private val sink: MediaSink) {

    // onLabel is called as the coordinator learns what this download is: the shortcode as soon
    // as the link parses, then the account once the page is fetched
    suspend fun run(sharedText: String?, onLabel: suspend (String) -> Unit = {}): Outcome {
        val target = LinkParser.parse(sharedText) ?: return Outcome.NotAPostLink

        (target as? LinkTarget.Post)?.ref?.shortcode?.let { onLabel(it) }

        val post = try {
            source.resolve(target)
        } catch (error: PostDataParseException) {
            return Outcome.CouldNotRead(error.message ?: "unreadable post")
        } catch (error: IOException) {
            return Outcome.NetworkFailed(error.message ?: "network error")
        }

        onLabel("@${post.username}")

        var saved = 0
        var alreadyPresent = 0

        for (item in post.items) {
            val fileName = FileNaming.fileName(post.username, post.shortcode, item, post.items.size)

            if (sink.alreadySaved(fileName, item.type)) {
                alreadyPresent++

                continue
            }

            try {
                sink.save(fileName, item.type, source.download(item.url))
            } catch (error: IOException) {
                return Outcome.NetworkFailed(error.message ?: "download failed")
            }

            saved++
        }

        // A retry that only had to save the remainder still saved the whole post, so the count
        // reported to the owner should describe the post, not just this invocation
        return if (saved == 0) Outcome.AlreadySaved else Outcome.Saved(saved + alreadyPresent)
    }
}
