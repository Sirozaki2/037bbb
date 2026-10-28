package com.providers.network

import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.atomic.AtomicReference

/** Reuse a working CDN within a playback interceptor, retaining the exact original URL as backup. */
internal class CdnFailoverInterceptor(private val alternatives: (String) -> List<String>) : Interceptor {
    private val preferredOrigin = AtomicReference<String?>(null)

    private fun origin(url: HttpUrl): String = "${url.scheme}://${url.host}:${url.port}"

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val advertised = alternatives(request.url.toString())
        if (advertised.isEmpty()) return chain.proceed(request)
        val candidates = (advertised + request.url.toString()).distinct().map { request.newBuilder().url(it).build() }
        val preferred = preferredOrigin.get()
        val ordered = candidates.filter { origin(it.url) == preferred } + candidates.filter { origin(it.url) != preferred }
        var failure: IOException? = null
        for ((index, candidate) in ordered.withIndex()) {
            if (chain.call().isCanceled()) throw IOException("Canceled")
            try {
                val response = chain.proceed(candidate)
                if (response.isSuccessful) {
                    preferredOrigin.set(origin(candidate.url))
                    return response
                }
                if (index == ordered.lastIndex) return response
                response.close()
            } catch (error: IOException) {
                if (chain.call().isCanceled()) throw error
                failure = error
            }
        }
        throw failure ?: IOException("ไม่พบเซิร์ฟเวอร์วิดีโอที่พร้อมใช้งาน")
    }
}
