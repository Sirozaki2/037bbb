package com.movie2free

import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Response
import okhttp3.ResponseBody

/** FastCDN's captured player strips a 545-byte PNG envelope before TS demux. */
internal class FastCdnInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val path = request.url.encodedPath
        if (!path.endsWith(".webp") || !path.matches(Regex("/files(?:r2)?/[a-fA-F0-9]{32}/(?:[0-9]+p?|audio)/[^/]+\\.webp"))) {
            return chain.proceed(request)
        }
        // Return a full logical body with HTTP200. Media3 then skips the requested
        // logical byte position itself, instead of seeking into the PNG envelope.
        val response = chain.proceed(request.newBuilder().removeHeader("Range").build())
        val body = response.body
        if (response.code != 200) return response
        val source = body.source()
        if (!source.request(545L + 188L * 2 + 1)) return response
        val peek = source.buffer
        val png = byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a)
        if (png.indices.any { peek[it.toLong()] != png[it] } ||
            (0..2).any { peek[545L + 188L * it] != 0x47.toByte() }) return response
        source.skip(545L)
        val length = body.contentLength().let { if (it >= 545) it - 545 else -1 }
        val decoded = object : ResponseBody() {
            override fun contentType() = "video/mp2t".toMediaType()
            override fun contentLength() = length
            override fun source() = source
        }
        return response.newBuilder().removeHeader("Content-Length").removeHeader("Content-Range")
            .header("Content-Type", "video/mp2t").apply {
                if (length >= 0) header("Content-Length", length.toString())
            }.body(decoded).build()
    }
}
