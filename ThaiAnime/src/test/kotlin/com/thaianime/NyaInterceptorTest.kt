package com.thaianime

import okhttp3.*
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

class NyaInterceptorTest {
    private val url = "https://cat.animenani.com/o/content/s/segment.webp"
    private class Chain(val input: Request, val bytes: ByteArray, val status: Int = 200) : Interceptor.Chain {
        lateinit var sent: Request
        override fun request() = input
        override fun proceed(request: Request): Response {
            sent = request
            return Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(status).message("Test")
                .header("Content-Length", bytes.size.toString()).header("Content-Type", "image/webp")
                .body(bytes.toResponseBody()).build()
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
    private fun envelope(vararg sizes: Int): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a))
        for (size in sizes) {
            out.write(byteArrayOf((size shr 24).toByte(), (size shr 16).toByte(), (size shr 8).toByte(), size.toByte()))
            out.write("IDAT".toByteArray()); out.write(ByteArray(size)); out.write(ByteArray(4))
        }
        return out.toByteArray()
    }
    @Test fun `variable PNG chunk envelopes are stripped only at verified TS boundary with logical range semantics`() {
        for (prefix in listOf(envelope(13), envelope(13, 9, 768, 4096, 1501))) {
            for (range in listOf(null, "bytes=188-")) {
                val request = Request.Builder().url(url).header("Referer", "https://nya.animenani.com/embed/mov_content")
                    .apply { range?.let { header("Range", it) } }.build()
                val chain = Chain(request, prefix + ts())
                NyaInterceptor().intercept(chain).use {
                    assertEquals(200, it.code)
                    assertArrayEquals(ts(), it.body.bytes())
                    assertEquals(ts().size.toString(), it.header("Content-Length"))
                    assertEquals("video/mp2t", it.header("Content-Type"))
                }
                assertNull(chain.sent.header("Range"))
                assertEquals(request.header("Referer"), chain.sent.header("Referer"))
            }
        }
    }
    @Test fun `plain media malformed oversized and error bodies are unchanged`() {
        val corrupt = envelope(1000).apply { this[8] = 0x7f }
        for ((body, status) in listOf(ts() to 200, corrupt to 200, envelope(66000) + ts() to 200,
            envelope(13) + ByteArray(1000) to 200, "denied".toByteArray() to 403,
            envelope(13) + ts() + byteArrayOf(1) to 200)) {
            val chain = Chain(Request.Builder().url(url).build(), body, status)
            NyaInterceptor().intercept(chain).use { assertEquals(status, it.code); assertArrayEquals(body, it.body.bytes()) }
        }
    }
    @Test fun `other hosts playlists and ad paths preserve headers and body`() {
        for (target in listOf("https://other.example/o/content/s/segment.webp", "https://cat.animenani.com/o/content/a", "https://cat.animenani.com/ads/ad.webp")) {
            val chain = Chain(Request.Builder().url(target).header("Range", "bytes=188-").build(), envelope(13) + ts())
            NyaInterceptor().intercept(chain).use { assertArrayEquals(chain.bytes, it.body.bytes()) }
            assertEquals("bytes=188-", chain.sent.header("Range"))
        }
    }
}
