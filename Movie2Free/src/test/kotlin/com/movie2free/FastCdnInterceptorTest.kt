package com.movie2free

import okhttp3.*
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.TimeUnit

class FastCdnInterceptorTest {
    private val url = "https://cdn.example/files/1234567890abcdef1234567890abcdef/360/index0.webp?sig=keep"
    private class Chain(val input: Request, val bytes: ByteArray, val status: Int = 200) : Interceptor.Chain {
        lateinit var sent: Request
        override fun request() = input
        override fun proceed(request: Request): Response {
            sent = request
            return Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(status).message("Test")
                .header("Content-Length", bytes.size.toString()).body(bytes.toResponseBody()).build()
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
    private fun ts() = ByteArray(188 * 4).apply { (0..3).forEach { this[it * 188] = 0x47 } }
    @Test fun `strip only verified PNG envelope preserving TS and logical range semantics`() {
        val media = ts()
        val envelope = ByteArray(545).apply { byteArrayOf(0x89.toByte(),0x50,0x4e,0x47,0x0d,0x0a,0x1a,0x0a).copyInto(this) }
        for (range in listOf(null,"bytes=188-")) {
            val request = Request.Builder().url(url).header("Referer", "https://player.example/").apply { range?.let { header("Range", it) } }.build()
            val chain = Chain(request, envelope + media)
            FastCdnInterceptor().intercept(chain).use { response ->
                assertEquals(200,response.code)
                assertArrayEquals(media,response.body!!.bytes())
                assertEquals(media.size.toString(),response.header("Content-Length"))
                assertEquals("video/mp2t",response.header("Content-Type"))
            }
            assertNull(chain.sent.header("Range"))
            assertEquals(request.url,chain.sent.url)
            assertEquals(request.header("Referer"),chain.sent.header("Referer"))
        }
    }
    @Test fun `short non PNG valid TS and failed responses are unchanged`() {
        for ((bytes,status) in listOf(ts() to 200, ByteArray(2000) to 200,"denied".toByteArray() to 403)) {
            val chain=Chain(Request.Builder().url(url).build(),bytes,status)
            FastCdnInterceptor().intercept(chain).use { assertArrayEquals(bytes,it.body!!.bytes());assertEquals(status,it.code) }
        }
    }
    @Test fun `playlists and unrelated paths retain Range and body`() {
        val request=Request.Builder().url("https://cdn.example/ad.webp").header("Range","bytes=100-").build()
        val bytes=ByteArray(1500)
        val chain=Chain(request,bytes)
        FastCdnInterceptor().intercept(chain).use { assertArrayEquals(bytes,it.body!!.bytes()) }
        assertEquals("bytes=100-",chain.sent.header("Range"))
    }
}
