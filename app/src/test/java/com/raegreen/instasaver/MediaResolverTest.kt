package com.raegreen.instasaver

import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

class MediaResolverTest {

    private lateinit var server: MockWebServer
    private lateinit var resolver: MediaResolver

    private fun fixture(name: String): String =
        checkNotNull(javaClass.classLoader?.getResourceAsStream("fixtures/$name")).bufferedReader().use { it.readText() }

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        resolver = MediaResolver(InstagramPageFetcher(OkHttpClient(), server.url("/").toString().trimEnd('/')))
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `resolves a reel to one video item`() {
        server.enqueue(MockResponse().setBody(fixture("reel.html")))

        val post = resolver.resolve(LinkTarget.Post(PostRef("DdDxpiPOAzs", PostKind.REEL)))

        assertEquals(1, post.items.size)
        assertEquals(MediaType.VIDEO, post.items[0].type)
    }

    @Test
    fun `follows a share link before resolving`() {
        val stub = StubFetcher(finalUrl = "https://www.instagram.com/reel/DdDxpiPOAzs/", page = fixture("reel.html"))
        val stubResolver = MediaResolver(stub)

        val post = stubResolver.resolve(LinkTarget.Redirect("https://www.instagram.com/share/reel/xyz/"))

        assertEquals(MediaType.VIDEO, post.items[0].type)
        assertEquals(PostRef("DdDxpiPOAzs", PostKind.REEL), stub.fetchedRef)
    }

    @Test
    fun `reports a share link that does not lead to a post`() {
        val stub = StubFetcher(finalUrl = "https://www.instagram.com/accounts/login/")
        val stubResolver = MediaResolver(stub)

        assertThrows(PostDataParseException::class.java) {
            stubResolver.resolve(LinkTarget.Redirect("https://www.instagram.com/share/reel/xyz/"))
        }
    }

    // Stands in for real HTTP so the redirect tests exercise MediaResolver's logic, not OkHttp
    private class StubFetcher(
        private val finalUrl: String,
        private val page: String = "",
    ) : InstagramPageFetcher(OkHttpClient()) {
        var fetchedRef: PostRef? = null

        override fun resolveRedirect(url: String): String = finalUrl

        override fun fetch(ref: PostRef): String {
            fetchedRef = ref

            return page
        }
    }
}
