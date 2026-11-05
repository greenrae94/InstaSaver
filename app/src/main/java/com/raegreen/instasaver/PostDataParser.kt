package com.raegreen.instasaver

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

enum class MediaType { VIDEO, IMAGE }

data class MediaItem(val url: String, val type: MediaType, val index: Int)

data class ResolvedPost(
    val username: String,
    val shortcode: String,
    val items: List<MediaItem>,
    // The page itself never says whether this is a Reel or a photo post, so the parser cannot
    // know. MediaResolver fills it in from the link, which is the one place it is known.
    val kind: PostKind = PostKind.POST,
)

class PostDataParseException(message: String) : Exception(message)

object PostDataParser {

    private val SCRIPT_PATTERN = Regex(
        """<script type="application/json"[^>]*>(.*?)</script>""",
        RegexOption.DOT_MATCHES_ALL,
    )
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(html: String): ResolvedPost {
        val node = findMediaNode(html) ?: throw PostDataParseException("no post data in page")
        val username = node["user"]?.jsonObjectOrNull()?.get("username")?.jsonPrimitive?.contentOrNull
            ?: throw PostDataParseException("no username in post data")

        // The page states its own shortcode, which is what a share link's redirect would otherwise hide
        val shortcode = node["code"]?.jsonPrimitive?.contentOrNull
            ?: throw PostDataParseException("no shortcode in post data")

        // A multi-item post carries its children in an array; a single post is its own item
        val children = node["carousel_media"] as? JsonArray

        val items = children
            ?.mapIndexedNotNull { position, child -> child.jsonObjectOrNull()?.let { mediaItem(it, position + 1) } }
            ?: listOf(mediaItem(node, index = 1))

        if (items.isEmpty()) throw PostDataParseException("no media in post data")

        return ResolvedPost(username, shortcode, items)
    }

    // The page embeds its data in JSON script blocks; only some of them carry the post.
    // Prefer a candidate that actually carries media, falling back to the first structural match.
    private fun findMediaNode(html: String): JsonObject? {
        val candidates = SCRIPT_PATTERN.findAll(html)
            .map { it.groupValues[1] }
            .filter { it.contains("xig_polaris_media") }
            .mapNotNull { runCatching { json.parseToJsonElement(it) }.getOrNull() }
            .mapNotNull { findUngatedMedia(it) }
            .toList()

        return candidates.firstOrNull { hasMedia(it) } ?: candidates.firstOrNull()
    }

    // Whether a node actually carries something the parser can turn into a MediaItem
    private fun hasMedia(node: JsonObject): Boolean =
        node["video_versions"] != null || node["image_versions2"] != null || node["carousel_media"] != null

    // The post sits at an unpredictable depth, so walk the tree looking for the wrapper
    private fun findUngatedMedia(element: JsonElement): JsonObject? {
        when (element) {
            is JsonObject -> {
                val media = element["xig_polaris_media"]?.jsonObjectOrNull()
                val ungated = media?.get("if_not_gated_logged_out")?.jsonObjectOrNull()

                if (ungated != null) return ungated

                element.values.forEach { child -> findUngatedMedia(child)?.let { return it } }
            }

            is JsonArray -> element.forEach { child -> findUngatedMedia(child)?.let { return it } }

            else -> return null
        }

        return null
    }

    // Videos list ready-made files; images list candidates with the full-size original first
    private fun mediaItem(node: JsonObject, index: Int): MediaItem {
        val video = (node["video_versions"] as? JsonArray)?.firstOrNull()
            ?.jsonObjectOrNull()?.get("url")?.jsonPrimitive?.contentOrNull

        if (video != null) return MediaItem(video, MediaType.VIDEO, index)

        val image = node["image_versions2"]?.jsonObjectOrNull()
            ?.get("candidates")?.let { it as? JsonArray }?.firstOrNull()
            ?.jsonObjectOrNull()?.get("url")?.jsonPrimitive?.contentOrNull
            ?: throw PostDataParseException("no video or image in post data")

        return MediaItem(image, MediaType.IMAGE, index)
    }

    private fun JsonElement.jsonObjectOrNull(): JsonObject? = this as? JsonObject
}
