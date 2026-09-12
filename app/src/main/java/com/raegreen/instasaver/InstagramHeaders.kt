package com.raegreen.instasaver

// Instagram only embeds post data for requests that look like a real mobile browser.
// Verified 2026-09-11: dropping these returns a page with no post data at all.
object InstagramHeaders {

    const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 15; SM-S938B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/152.0.0.0 Mobile Safari/537.36"

    val values: Map<String, String> = mapOf(
        "User-Agent" to USER_AGENT,
        "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8",
        "Accept-Language" to "en-GB,en;q=0.9",
        "sec-ch-ua" to "\"Chromium\";v=\"152\", \"Google Chrome\";v=\"152\", \"Not)A;Brand\";v=\"99\"",
        "sec-ch-ua-mobile" to "?1",
        "sec-ch-ua-platform" to "\"Android\"",
        "sec-fetch-dest" to "document",
        "sec-fetch-mode" to "navigate",
        "sec-fetch-site" to "none",
        "sec-fetch-user" to "?1",
        "upgrade-insecure-requests" to "1",
    )
}
