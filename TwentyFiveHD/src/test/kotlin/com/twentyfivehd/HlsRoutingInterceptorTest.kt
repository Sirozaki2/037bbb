package com.twentyfivehd

import okhttp3.*
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.util.concurrent.TimeUnit

class HlsRoutingInterceptorTest {
    private val path = "/hls/1234567890abcdef12345678/1080p/1.m4s"
    private val routing = HlsRouting.fromManifest("https://gateway.example/hls/1234567890abcdef12345678/_master", """{
        "PATHWAY-PRIORITY":["a","b"], "PATHWAY-CLONES":[
          {"ID":"a","URI-REPLACEMENT":{"HOST":"a.example"}},
          {"ID":"b","URI-REPLACEMENT":{"HOST":"b.example"}}
        ]}""")
    private class FakeChain(val input: Request, val responses: List<Int>) : Interceptor.Chain {
        val requests = mutableListOf<Request>()
        override fun request() = input
        override fun proceed(request: Request): Response {
            requests.add(request)
            val status = responses[requests.lastIndex]
            if (status == 0) throw IOException("Synthetic unavailable mirror")
            return Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(status)
                .message("Synthetic").body("media".toResponseBody()).build()
        }
        override fun connection(): Connection? = null
        override fun call(): Call = error("Unused")
        override fun connectTimeoutMillis() = 1000
        override fun readTimeoutMillis() = 1000
        override fun writeTimeoutMillis() = 1000
        override fun withConnectTimeout(timeout: Int, unit: TimeUnit) = this
        override fun withReadTimeout(timeout: Int, unit: TimeUnit) = this
        override fun withWriteTimeout(timeout: Int, unit: TimeUnit) = this
    }

    @Test fun `tries the next advertised mirror on HTTP or network failure and keeps player headers`() {
        for (firstStatus in listOf(403, 0)) {
            val chain = FakeChain(Request.Builder().url("https://gateway.example$path")
                .header("Range", "bytes=0-2047").header("Referer", "https://zmdb.net/").build(), listOf(firstStatus, 206))
            HlsRoutingInterceptor(routing).intercept(chain).use { assertEquals(206, it.code) }
            assertEquals(listOf("a.example", "b.example"), chain.requests.map { it.url.host })
            assertEquals("bytes=0-2047", chain.requests.last().header("Range"))
            assertEquals("https://zmdb.net/", chain.requests.last().header("Referer"))
        }
    }

    @Test fun `passes signed playlists through unchanged`() {
        val url = "https://gateway.example/hls/1234567890abcdef12345678/_a_1/_index?sig=keep"
        val chain = FakeChain(Request.Builder().url(url).build(), listOf(200))
        HlsRoutingInterceptor(routing).intercept(chain).close()
        assertEquals(url, chain.requests.single().url.toString())
    }
}
