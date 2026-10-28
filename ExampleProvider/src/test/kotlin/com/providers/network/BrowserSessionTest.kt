package com.providers.network

import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.util.concurrent.TimeUnit

class BrowserSessionTest {
    private class Chain(private val original: Request, val respond: (Request) -> Response) : Interceptor.Chain {
        val seen = mutableListOf<Request>()
        override fun request() = original
        override fun proceed(request: Request): Response { seen += request; return respond(request) }
        override fun connection(): Connection? = null
        override fun call(): Call = error("No network calls in this fixture")
        override fun connectTimeoutMillis() = 1000
        override fun readTimeoutMillis() = 1000
        override fun writeTimeoutMillis() = 1000
        override fun withConnectTimeout(timeout: Int, unit: TimeUnit) = this
        override fun withReadTimeout(timeout: Int, unit: TimeUnit) = this
        override fun withWriteTimeout(timeout: Int, unit: TimeUnit) = this
    }
    private fun request() = Request.Builder().url("https://catalog.example/ep-2")
        .header("Referer", "https://parent.example/").header("Cookie", "session=caller; token=a=b").build()
    private fun response(request: Request, code: Int = 200) = Response.Builder()
        .request(request).protocol(Protocol.HTTP_1_1).code(code).message("Fixture")
        .header("Server", "cloudflare").header("cf-mitigated", "challenge")
        .body("<script src='/cdn-cgi/challenge-platform/test'></script>".toResponseBody("text/html".toMediaType())).build()

    @Test fun `shares website cookies without discarding caller session or referer`() {
        var cookieUrl: String? = null
        val session = BrowserSession(cookies = { cookieUrl = it; "session=browser; login=keep" }, userAgent = { "WebView UA" })
        val chain = Chain(request()) { response(it) }
        session.intercept(chain).close()
        assertEquals(1, chain.seen.size)
        assertEquals("https://catalog.example/ep-2", cookieUrl)
        assertEquals("session=browser; token=a=b; login=keep", chain.seen.single().header("Cookie"))
        assertEquals("https://parent.example/", chain.seen.single().header("Referer"))
        assertEquals("WebView UA", chain.seen.single().header("User-Agent"))
    }

    @Test fun `explicit player agent is preserved with existing browser cookies`() {
        val session = BrowserSession(cookies = { "session=browser" }, userAgent = { "WebView UA" })
        val chain = Chain(request().newBuilder().header("User-Agent", "Required player UA").build()) { response(it) }
        session.intercept(chain).close()
        assertEquals("Required player UA", chain.seen.single().header("User-Agent"))
    }

    @Test fun `no browser cookies leaves caller headers intact`() {
        val session = BrowserSession(cookies = { null }, userAgent = { "Unrelated WebView UA" })
        val original = request()
        val chain = Chain(original) { response(it) }
        session.intercept(chain).close()
        assertEquals(original.headers, chain.seen.single().headers)
    }

    @Test fun `access denial and genuine challenge return immediately without retry`() {
        listOf(403, 503).forEach { code ->
            val session = BrowserSession(cookies = { "session=keep" }, userAgent = { null })
            val chain = Chain(request()) { response(it, code) }
            val denied = session.intercept(chain)
            denied.use { assertEquals(code, it.code); assertTrue(it.body.string().contains("challenge-platform")) }
            assertEquals(1, chain.seen.size)
        }
    }

    @Test fun `transport failures propagate without another request`() {
        val session = BrowserSession(cookies = { null }, userAgent = { null })
        val error = IOException("Network lost")
        val chain = Chain(request()) { throw error }
        assertSame(error, assertThrows(IOException::class.java) { session.intercept(chain) })
        assertEquals(1, chain.seen.size)
    }
}
