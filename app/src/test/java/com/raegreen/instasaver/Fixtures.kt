package com.raegreen.instasaver

import java.util.Properties
import org.junit.Assume.assumeTrue

// Real Instagram pages are not committed (they are other people's posts). Developers fetch their
// own with tools/fetch-fixtures.sh; without them, fixture-backed tests skip rather than fail.
object Fixtures {

    private const val SKIP_HINT =
        "Fixture missing. Copy fixtures.properties.example to fixtures.properties, " +
            "fill in your own public post URLs, then run tools/fetch-fixtures.sh"

    private val expectations: Properties? by lazy {
        val stream = Fixtures::class.java.classLoader?.getResourceAsStream("fixtures/expectations.properties")

        stream?.use { Properties().apply { load(it) } }
    }

    // Returns the saved page, or skips the calling test when it has not been fetched
    fun page(name: String): String {
        val stream = Fixtures::class.java.classLoader?.getResourceAsStream("fixtures/$name")

        assumeTrue("$SKIP_HINT ($name not found)", stream != null)

        return stream!!.bufferedReader().use { it.readText() }
    }

    // Returns an expected value written by tools/fetch-fixtures.sh, or skips when absent
    fun expected(key: String): String {
        val value = expectations?.getProperty(key)

        assumeTrue("$SKIP_HINT (expectation $key not found)", value != null)

        return value!!
    }
}
