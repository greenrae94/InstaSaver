package com.raegreen.instasaver

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream

private class FakeSource(
    private val post: ResolvedPost? = null,
    private val error: Exception? = null,
) : PostSource {
    var downloaded = mutableListOf<String>()

    override fun resolve(target: LinkTarget): ResolvedPost = post ?: throw error!!

    override fun download(url: String): InputStream {
        downloaded += url

        return ByteArrayInputStream(byteArrayOf(1, 2, 3))
    }
}

private class FakeSink(private val existing: Set<String> = emptySet()) : MediaSink {
    val saved = mutableListOf<String>()

    override fun alreadySaved(fileName: String, type: MediaType) = fileName in existing

    override fun save(fileName: String, type: MediaType, source: InputStream) {
        source.readBytes()
        saved += fileName
    }
}

private class FakeLinks : LinkRecord {
    val recorded = mutableListOf<Pair<String, String>>()

    override fun record(fileName: String, postUrl: String) {
        recorded += fileName to postUrl
    }
}

class DownloadCoordinatorTest {

    private val reelLink = "https://www.instagram.com/reel/DdDxpiPOAzs/?stkn=abc"
    private val onePost = ResolvedPost(
        "connorirwin_98",
        "DdDxpiPOAzs",
        listOf(MediaItem("https://cdn/v.mp4", MediaType.VIDEO, 1)),
        kind = PostKind.REEL,
    )
    private val carousel = ResolvedPost(
        "fleexyz",
        "DdHMr9eMQQR",
        listOf(
            MediaItem("https://cdn/1.jpg", MediaType.IMAGE, 1),
            MediaItem("https://cdn/2.mp4", MediaType.VIDEO, 2),
        ),
    )

    @Test
    fun `saves a single video and names it after the account and post`() = runBlocking {
        val sink = FakeSink()

        val outcome = DownloadCoordinator(FakeSource(onePost), sink).run(reelLink)

        assertEquals(Outcome.Saved(1), outcome)
        assertEquals(listOf("connorirwin_98_DdDxpiPOAzs.mp4"), sink.saved)
    }

    @Test
    fun `saves every item of a multi item post in order`() = runBlocking {
        val sink = FakeSink()
        val source = FakeSource(carousel)

        val outcome = DownloadCoordinator(source, sink).run(reelLink)

        assertEquals(Outcome.Saved(2), outcome)
        assertEquals(listOf("fleexyz_DdHMr9eMQQR_1.jpg", "fleexyz_DdHMr9eMQQR_2.mp4"), sink.saved)
        assertEquals(listOf("https://cdn/1.jpg", "https://cdn/2.mp4"), source.downloaded)
    }

    @Test
    fun `reports the whole post as saved when a retry only had the remainder to fetch`() = runBlocking {
        val sink = FakeSink(existing = setOf("fleexyz_DdHMr9eMQQR_1.jpg"))
        val source = FakeSource(carousel)

        val outcome = DownloadCoordinator(source, sink).run(reelLink)

        assertEquals(Outcome.Saved(2), outcome)
        assertEquals(listOf("fleexyz_DdHMr9eMQQR_2.mp4"), sink.saved)
        assertEquals(listOf("https://cdn/2.mp4"), source.downloaded)
    }

    @Test
    fun `skips a post that is already saved`() = runBlocking {
        val sink = FakeSink(existing = setOf("connorirwin_98_DdDxpiPOAzs.mp4"))

        val outcome = DownloadCoordinator(FakeSource(onePost), sink).run(reelLink)

        assertEquals(Outcome.AlreadySaved, outcome)
        assertTrue(sink.saved.isEmpty())
    }

    @Test
    fun `rejects text that is not a post link`() = runBlocking {
        val outcome = DownloadCoordinator(FakeSource(onePost), FakeSink()).run("https://www.instagram.com/fleexyz/")

        assertEquals(Outcome.NotAPostLink, outcome)
    }

    @Test
    fun `reports a page it cannot read`() = runBlocking {
        val source = FakeSource(error = PostDataParseException("no post data in page"))

        val outcome = DownloadCoordinator(source, FakeSink()).run(reelLink)

        assertEquals(Outcome.CouldNotRead("no post data in page"), outcome)
    }

    @Test
    fun `reports a network failure separately so it can be retried`() = runBlocking {
        val source = FakeSource(error = IOException("timeout"))

        val outcome = DownloadCoordinator(source, FakeSink()).run(reelLink)

        assertEquals(Outcome.NetworkFailed("timeout"), outcome)
    }

    @Test
    fun `records the post link against every file a multi item post saves`() = runBlocking {
        val links = FakeLinks()

        DownloadCoordinator(FakeSource(carousel), FakeSink(), links).run(reelLink)

        assertEquals(
            listOf(
                "fleexyz_DdHMr9eMQQR_1.jpg" to "https://www.instagram.com/p/DdHMr9eMQQR/",
                "fleexyz_DdHMr9eMQQR_2.mp4" to "https://www.instagram.com/p/DdHMr9eMQQR/",
            ),
            links.recorded,
        )
    }

    // The recorded path follows the post's kind, not the file's media type
    @Test
    fun `records a reel link for a reel`() = runBlocking {
        val links = FakeLinks()

        DownloadCoordinator(FakeSource(onePost), FakeSink(), links).run(reelLink)

        assertEquals(
            listOf("connorirwin_98_DdDxpiPOAzs.mp4" to "https://www.instagram.com/reel/DdDxpiPOAzs/"),
            links.recorded,
        )
    }

    @Test
    fun `records nothing for a post that is already saved`() = runBlocking {
        val links = FakeLinks()
        val sink = FakeSink(existing = setOf("connorirwin_98_DdDxpiPOAzs.mp4"))

        DownloadCoordinator(FakeSource(onePost), sink, links).run(reelLink)

        assertTrue(links.recorded.isEmpty())
    }

    @Test
    fun `records nothing when the page cannot be read`() = runBlocking {
        val links = FakeLinks()
        val source = FakeSource(error = PostDataParseException("no post data in page"))

        DownloadCoordinator(source, FakeSink(), links).run(reelLink)

        assertTrue(links.recorded.isEmpty())
    }

    @Test
    fun `reports what it is working on as it learns it`() = runBlocking {
        val labels = mutableListOf<String>()

        DownloadCoordinator(FakeSource(onePost), FakeSink()).run(reelLink) { labels += it }

        assertEquals(listOf("DdDxpiPOAzs", "@connorirwin_98"), labels)
    }
}
