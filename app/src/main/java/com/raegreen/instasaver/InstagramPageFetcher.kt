package com.raegreen.instasaver

import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

// Open so tests can substitute a stub instead of driving real HTTP through MockWebServer
open class InstagramPageFetcher(
    private val client: OkHttpClient,
    private val baseUrl: String = "https://www.instagram.com",
) {

    open fun fetch(ref: PostRef): String {
        val segment = if (ref.kind == PostKind.POST) "p" else "reel"

        // ?l=1 skips the "watch this reel in the app" interstitial
        return get("$baseUrl/$segment/${ref.shortcode}/?l=1")
    }

    open fun resolveRedirect(url: String): String {
        val request = Request.Builder().url(url).headers(headers()).build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code} for $url")

            return response.request.url.toString()
        }
    }

    private fun get(url: String): String {
        val request = Request.Builder().url(url).headers(headers()).build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code} for $url")

            return response.body?.string() ?: throw IOException("empty body for $url")
        }
    }

    private fun headers() = okhttp3.Headers.Builder().apply {
        InstagramHeaders.values.forEach { (name, value) -> add(name, value) }
    }.build()
}
