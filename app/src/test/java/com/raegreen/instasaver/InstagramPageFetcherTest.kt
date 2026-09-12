package com.raegreen.instasaver

import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

class InstagramPageFetcherTest {

    private lateinit var server: MockWebServer
    private lateinit var fetcher: InstagramPageFetcher

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        fetcher = InstagramPageFetcher(OkHttpClient(), server.url("/").toString().trimEnd('/'))
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `requests the reel path with the interstitial skipped`() {
        server.enqueue(MockResponse().setBody("<html>page</html>"))

        val body = fetcher.fetch(PostRef("DdDxpiPOAzs", PostKind.REEL))
        val request = server.takeRequest()

        assertEquals("/reel/DdDxpiPOAzs/?l=1", request.path)
        assertEquals("<html>page</html>", body)
    }

    @Test
    fun `requests the post path for photo posts`() {
        server.enqueue(MockResponse().setBody("<html>page</html>"))

        fetcher.fetch(PostRef("DdHMr9eMQQR", PostKind.POST))

        assertEquals("/p/DdHMr9eMQQR/?l=1", server.takeRequest().path)
    }

    @Test
    fun `sends the browser headers instagram requires`() {
        server.enqueue(MockResponse().setBody("<html>page</html>"))

        fetcher.fetch(PostRef("DdDxpiPOAzs", PostKind.REEL))
        val request = server.takeRequest()

        // Pins the source map to eleven entries so a header silently added or removed from the
        // constant itself is noticed; the loop below is what actually catches a dropped request header
        assertEquals(11, InstagramHeaders.values.size)

        InstagramHeaders.values.forEach { (name, value) ->
            assertEquals(value, request.getHeader(name))
        }

        // Task 6's downloader uses this constant directly, so pin the request to it too
        assertEquals(InstagramHeaders.USER_AGENT, request.getHeader("User-Agent"))
    }

    @Test
    fun `reports a failed response`() {
        server.enqueue(MockResponse().setResponseCode(404).setBody("gone"))

        assertThrows(IOException::class.java) {
            fetcher.fetch(PostRef("DdDxpiPOAzs", PostKind.REEL))
        }
    }

    @Test
    fun `follows a share link to its final url`() {
        server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", "/reel/DdDxpiPOAzs/"))
        server.enqueue(MockResponse().setBody("<html>page</html>"))

        val finalUrl = fetcher.resolveRedirect(server.url("/share/reel/xyz/").toString())

        assertTrue(finalUrl.endsWith("/reel/DdDxpiPOAzs/"))
    }
}
