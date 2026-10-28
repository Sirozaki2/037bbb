package com.providers.network

import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.util.concurrent.TimeUnit

class CloudflareSessionTest {
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
    private fun response(request: Request, challenge: Boolean = false, code: Int = if (challenge) 403 else 200) = Response.Builder()
        .request(request).protocol(Protocol.HTTP_1_1).code(code).message("Fixture")
        .header("Server", "cloudflare")
        .body((if (challenge) "<script src='/cdn-cgi/challenge-platform/test'></script>" else "Content")
            .toResponseBody("text/html".toMediaType())).build()
    private fun request(path: String = "/") = Request.Builder().url("https://catalog.example$path")
        .header("Referer", "https://parent.example/").header("Cookie", "session=caller").build()

    @Test fun `persisted clearance and matching UA are reused without opening WebView`() {
        val session = CloudflareSession(cookies = { "cf_clearance=persisted; session=browser" }, userAgent = { "WebView UA" }, solve = { _, _ -> error("Not needed") })
        val chain = Chain(request()) { response(it) }
        session.intercept(chain).close()
        assertEquals(1, chain.seen.size)
        assertEquals("session=browser; cf_clearance=persisted", chain.seen.single().header("Cookie"))
        assertEquals("WebView UA", chain.seen.single().header("User-Agent"))
        assertEquals("https://parent.example/", chain.seen.single().header("Referer"))
    }
    @Test fun `rejected stale clearance is refreshed and content retried with new cookie`() {
        var cookie = "cf_clearance=stale"
        var solves = 0
        val session = CloudflareSession(cookies = { cookie }, userAgent = { "WebView UA" }, solve = { _, cleared ->
            solves++
            assertFalse(cleared())
            cookie = "cf_clearance=fresh=="
            assertTrue(cleared())
        })
        val chain = Chain(request()) { response(it, challenge = !it.header("Cookie").orEmpty().contains("fresh==")) }
        session.intercept(chain).use { assertEquals(200, it.code) }
        assertEquals(1, solves)
        assertEquals(2, chain.seen.size)
        assertTrue(chain.seen.last().header("Cookie")!!.contains("session=caller"))
        assertEquals("fresh==", CloudflareSession.clearance(cookie))
    }
    @Test fun `failed challenge is bounded per host then may be retried after cooldown`() {
        var now = 100L
        var solves = 0
        val session = CloudflareSession(cookies = { null }, userAgent = { null }, clock = { now }, solve = { _, _ -> solves++ })
        listOf("/category/sub", "/category/dub").forEach { path ->
            session.intercept(Chain(request(path)) { response(it, challenge = true) }).use { assertEquals(403, it.code) }
        }
        assertEquals(1, solves)
        now += 30_001
        session.intercept(Chain(request()) { response(it, challenge = true) }).close()
        assertEquals(2, solves)
    }
    @Test fun `ordinary access denial does not launch a Cloudflare challenge`() {
        val session = CloudflareSession(cookies = { null }, userAgent = { null }, solve = { _, _ -> error("Not a challenge") })
        session.intercept(Chain(request()) { response(it, code = 403) }).use { assertEquals(403, it.code) }
    }
    @Test fun `player UA is preserved when no clearance is available`() {
        val session = CloudflareSession(cookies = { "session=browser" }, userAgent = { "Unrelated WebView UA" }, solve = { _, _ -> error("Not needed") })
        val chain = Chain(request().newBuilder().header("User-Agent", "Required player UA").build()) { response(it) }
        session.intercept(chain).close()
        assertEquals("Required player UA", chain.seen.single().header("User-Agent"))
    }
    @Test fun `clearance completed by another request is reused without another challenge`() {
        var cookie = "cf_clearance=stale"
        var solves = 0
        val session = CloudflareSession(cookies = { cookie }, userAgent = { null }, solve = { _, _ -> solves++ })
        val chain = Chain(request()) {
            if (it.header("Cookie").orEmpty().contains("stale")) {
                cookie = "cf_clearance=fresh"
                response(it, challenge = true)
            } else response(it)
        }
        session.intercept(chain).use { assertEquals(200, it.code) }
        assertEquals(0, solves)
        assertEquals(2, chain.seen.size)
    }
    @Test fun `missing WebView returns original denial and suppresses repeated attempts`() {
        var solves = 0
        val session = CloudflareSession(cookies = { null }, userAgent = { null }, solve = { _, _ -> solves++; throw IllegalStateException("WebView unavailable") })
        repeat(2) { session.intercept(Chain(request()) { response(it, challenge = true) }).use { assertEquals(403, it.code) } }
        assertEquals(1, solves)
    }
    @Test fun `transport failure during cleared retry propagates instead of returning closed response`() {
        var cookie: String? = null
        val session = CloudflareSession(cookies = { cookie }, userAgent = { null }, solve = { _, _ -> cookie = "cf_clearance=fresh" })
        val chain = Chain(request()) { if (it.header("Cookie").orEmpty().contains("fresh")) throw IOException("Network lost") else response(it, challenge = true) }
        val error = assertThrows(IOException::class.java) { session.intercept(chain) }
        assertEquals("Network lost", error.message)
    }
}
