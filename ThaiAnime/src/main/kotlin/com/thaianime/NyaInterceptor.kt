package com.thaianime

import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Response
import okhttp3.ResponseBody

/** Nya's .webp HLS fragments contain PNG chunks followed by ordinary MPEG-TS. */
internal class NyaInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.method != "GET" || request.url.host != "cat.animenani.com" ||
            !request.url.encodedPath.matches(Regex("/o/[A-Za-z0-9_-]+/s/[A-Za-z0-9_-]+\\.webp"))) return chain.proceed(request)
        // Return the full logical media body: Media3 can skip its own requested byte position.
        val response = chain.proceed(request.newBuilder().removeHeader("Range").build())
        if (response.code != 200) return response
        val body = response.body
        val source = body.source()
        val png = byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a)
        if (!source.request(8) || png.indices.any { source.buffer[it.toLong()] != png[it] }) return response
        var offset = 8L
        while (offset <= 65_536L) {
            if (!source.request(offset + 377)) return response
            if ((0..2).all { source.buffer[offset + it * 188L] == 0x47.toByte() }) {
                val length = body.contentLength().let { if (it >= 0) it - offset else -1 }
                if (length >= 0 && length % 188 != 0L) return response
                source.skip(offset)
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
            val chunkLength = ((0..3).fold(0L) { value, index -> (value shl 8) or (source.buffer[offset + index].toLong() and 0xff) })
            if ((4..7).any { source.buffer[offset + it].toInt().toChar() !in 'A'..'Z' && source.buffer[offset + it].toInt().toChar() !in 'a'..'z' }) return response
            offset += chunkLength + 12
        }
        return response
    }
}
