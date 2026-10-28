package com.movie2freehd

import okhttp3.*
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.util.concurrent.TimeUnit

class MeeRoutingTest {
    private val id = "1234567890abcdef1234567890abcdef12"
    private val routing = MeeRouting.create(id, listOf("https://a.example", "https://b.example"))!!
    private class Chain(val input: Request, val statuses: List<Int>) : Interceptor.Chain {
        private val testCall = OkHttpClient().newCall(input)
        val requests = mutableListOf<Request>()
        override fun request() = input
        override fun proceed(request: Request): Response {
            requests += request
            val status = statuses[requests.lastIndex]
            if (status == 0) throw IOException("Synthetic unavailable mirror")
            return Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(status).message("Test").body("media".toResponseBody()).build()
        }
        override fun connection(): Connection? = null
        override fun call(): Call = testCall
        override fun connectTimeoutMillis() = 1000
        override fun readTimeoutMillis() = 1000
        override fun writeTimeoutMillis() = 1000
        override fun withConnectTimeout(timeout: Int, unit: TimeUnit) = this
        override fun withReadTimeout(timeout: Int, unit: TimeUnit) = this
        override fun withWriteTimeout(timeout: Int, unit: TimeUnit) = this
    }
    @Test fun `CDN failover preserves Range and signature query`() {
        for (status in listOf(0, 503)) {
            val chain = Chain(Request.Builder().url("https://a.example/files/$id/1080/1.ts?sig=synthetic")
                .header("Range", "bytes=0-1023").build(), listOf(status, 206))
            MeeRoutingInterceptor(routing).intercept(chain).use { assertEquals(206, it.code) }
            assertEquals(listOf("a.example", "b.example"), chain.requests.map { it.url.host })
            assertEquals("synthetic", chain.requests.last().url.queryParameter("sig"))
            assertEquals("bytes=0-1023", chain.requests.last().header("Range"))
        }
    }
    @Test fun `routing leaves playlists and unrelated media untouched`() {
        for (url in listOf("https://meeplayer.com/hlsr2/$id/master", "https://unrelated.example/files/$id/1080/1.ts", "https://a.example/files/other/1080/1.ts")) {
            assertTrue(routing.urls(url).isEmpty())
        }
    }
    @Test fun `malformed persisted routing is ignored`() {
        assertNull(MeeRouting.decode("{broken"))
        assertNull(MeeRouting.create(id, listOf("http://insecure.example", "https://user@host/", "https://host/path")))
        assertNull(MeeRouting.create("../other", listOf("https://a.example")))
        assertEquals(routing.urls("https://a.example/files/$id/720/2.ts"), MeeRouting.decode(routing.encode())!!.urls("https://a.example/files/$id/720/2.ts"))
    }

    @Test fun `advertised CDN origins also route original meeplayer fragments and preserve fallback`() {
        val url = "https://cdn.meeplayer.com/files/$id/1080/index0.jpg"
        assertEquals(listOf("https://a.example/files/$id/1080/index0.jpg", "https://b.example/files/$id/1080/index0.jpg", url), routing.urls(url))
        assertTrue(routing.urls("https://a.example/files/$id/1080/index.m3u8?sig=keep").isEmpty())
    }
}
