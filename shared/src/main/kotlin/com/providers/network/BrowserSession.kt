package com.providers.network

import android.webkit.CookieManager
import com.lagradost.cloudstream3.network.WebViewResolver
import okhttp3.Interceptor
import okhttp3.Response

/** Shares existing website cookies only. Never launches a browser or retries a request. */
internal class BrowserSession(
    private val cookies: (String) -> String? = { url ->
        runCatching { CookieManager.getInstance().getCookie(url) }.getOrNull()
    },
    private val userAgent: () -> String? = { WebViewResolver.webViewUserAgent },
) : Interceptor {
    fun getCookieHeaders(url: String): Map<String, String> = buildMap {
        cookies(url)?.takeIf { it.isNotBlank() }?.let { put("Cookie", it) }
        userAgent()?.takeIf { it.isNotBlank() }?.let { put("User-Agent", it) }
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val browser = getCookieHeaders(request.url.toString())
        val merged = request.newBuilder().apply {
            val values = listOfNotNull(request.header("Cookie"), browser["Cookie"])
                .flatMap { it.split(';') }.map { it.trim() }.filter { it.contains('=') }
                .associateBy { it.substringBefore('=') }.values
            if (values.isNotEmpty()) header("Cookie", values.joinToString("; "))
            // Explicit player agents are part of the player contract.
            if (request.header("User-Agent") == null && browser["Cookie"] != null) {
                browser["User-Agent"]?.let { header("User-Agent", it) }
            }
        }.build()
        return chain.proceed(merged)
    }
}
