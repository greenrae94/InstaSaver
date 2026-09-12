package com.raegreen.instasaver

import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.io.InputStream

class MediaDownloader(private val client: OkHttpClient) {

    // Instagram's CDN answers the first request with a 302, so redirects must be followed
    fun download(url: String): InputStream {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", InstagramHeaders.USER_AGENT)
            .build()

        val response = client.newCall(request).execute()

        if (!response.isSuccessful) {
            response.close()

            throw IOException("HTTP ${response.code} downloading $url")
        }

        val body = response.body

        if (body == null) {
            response.close()

            throw IOException("empty body for $url")
        }

        return body.byteStream()
    }
}
