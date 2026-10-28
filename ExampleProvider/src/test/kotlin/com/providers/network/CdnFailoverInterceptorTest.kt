package com.providers.network

import okhttp3.*
import okio.Buffer
import okio.BufferedSource
import okio.ForwardingSource
import okio.buffer
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.util.concurrent.TimeUnit

class CdnFailoverInterceptorTest {
    private class Chain(val input: Request, val statuses: List<Int>, val cancelOnError: Boolean = false) : Interceptor.Chain {
        val requests = mutableListOf<Request>()
        val testCall = OkHttpClient().newCall(input)
        val closed = mutableListOf<Int>()
        override fun request() = input
        override fun proceed(request: Request): Response {
            val index = requests.size
            requests += request
            val code = statuses[index]
            if (code == 0) { if (cancelOnError) testCall.cancel(); throw IOException("Synthetic failure") }
            val stream = object : ForwardingSource(Buffer().writeUtf8("media")) {
                override fun close() { closed += index; super.close() }
            }.buffer()
            val body = object : ResponseBody() {
                override fun contentType(): MediaType? = null
                override fun contentLength() = 5L
                override fun source(): BufferedSource = stream
            }
            return Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(code).message("Test").body(body).build()
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
    private fun request(part: Int = 1) = Request.Builder().url("https://original.example/asset/$part.ts?sig=keep")
        .header("Range", "bytes=100-200").header("Referer", "https://player.example/").build()
    private fun interceptor() = CdnFailoverInterceptor { url -> listOf("a.example", "b.example").map { url.replace("original.example", it) } }

    @Test fun `a successful backup serves the next fragment first and can fail over again`() {
        val interceptor = interceptor()
        val first = Chain(request(), listOf(503, 206))
        interceptor.intercept(first).close()
        val next = Chain(request(2), listOf(206))
        interceptor.intercept(next).close()
        assertEquals(listOf("b.example"), next.requests.map { it.url.host })
        val failed = Chain(request(3), listOf(503, 206))
        interceptor.intercept(failed).close()
        assertEquals(listOf("b.example", "a.example"), failed.requests.map { it.url.host })
    }
    @Test fun `fallback keeps original signatures Range and Referer and closes failed bodies`() {
        val chain = Chain(request(), listOf(503, 403, 206))
        val response = interceptor().intercept(chain)
        assertEquals(listOf(0, 1), chain.closed)
        assertEquals(request().url, chain.requests.last().url)
        assertTrue(chain.requests.all { it.header("Range") == "bytes=100-200" && it.header("Referer") == "https://player.example/" })
        response.close()
        assertEquals(listOf(0, 1, 2), chain.closed)
    }
    @Test fun `canceled calls never start another mirror`() {
        val chain = Chain(request(), listOf(0, 200), cancelOnError = true)
        assertTrue(runCatching { interceptor().intercept(chain) }.exceptionOrNull() is IOException)
        assertEquals(1, chain.requests.size)
        val canceled = Chain(request(), listOf(200))
        canceled.testCall.cancel()
        assertTrue(runCatching { interceptor().intercept(canceled) }.exceptionOrNull() is IOException)
        assertTrue(canceled.requests.isEmpty())
    }
    @Test fun `requests without routing remain unchanged`() {
        val chain = Chain(request(), listOf(200))
        CdnFailoverInterceptor { emptyList() }.intercept(chain).close()
        assertEquals(request().url, chain.requests.single().url)
    }
    @Test fun `all failed HTTP replies return the final response with its body open`() {
        val chain = Chain(request(), listOf(502, 503, 504))
        val response = interceptor().intercept(chain)
        assertEquals(504, response.code)
        assertEquals(listOf(0, 1), chain.closed)
        assertEquals("media", response.body.string())
    }
}
