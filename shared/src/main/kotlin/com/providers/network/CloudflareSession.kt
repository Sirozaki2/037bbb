package com.providers.network

import android.webkit.CookieManager
import com.lagradost.cloudstream3.network.WebViewResolver
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.util.concurrent.ConcurrentHashMap

/** Keeps WebView clearance across providers and refreshes rejected cookies once per host. */
internal class CloudflareSession(
    private val cookies: (String) -> String? = { url ->
        runCatching { CookieManager.getInstance().getCookie(url) }.getOrNull()
    },
    private val userAgent: () -> String? = { WebViewResolver.webViewUserAgent },
    private val clock: () -> Long = { System.nanoTime() / 1_000_000 },
    private val solve: (String, () -> Boolean) -> Unit = { url, cleared ->
        runBlocking {
            WebViewResolver(
                interceptUrl = Regex(".^"),
                additionalUrls = listOf(Regex(".")),
                userAgent = null,
                useOkhttp = false,
                timeout = 12_000L,
            ).resolveUsingWebView(url) { cleared() }
        }
    },
) : Interceptor {
    private class HostState {
        var retryAfter = 0L
    }
    private val hosts = ConcurrentHashMap<String, HostState>()

    fun getCookieHeaders(url: String): Map<String, String> = buildMap {
        cookies(url)?.takeIf { it.isNotBlank() }?.let { put("Cookie", it) }
        userAgent()?.let { put("User-Agent", it) }
    }

    private fun withCookies(request: Request): Request {
        val headers = getCookieHeaders(request.url.toString())
        return request.newBuilder().apply {
            // Keep caller cookies (e.g. player sessions) as well as WebView clearance.
            val values = listOfNotNull(request.header("Cookie"), headers["Cookie"])
                .flatMap { it.split(';') }.map { it.trim() }.filter { it.contains('=') }
                .associateBy { it.substringBefore('=') }.values
            if (values.isNotEmpty()) header("Cookie", values.joinToString("; "))
            if (clearance(headers["Cookie"]) != null) headers["User-Agent"]?.let { header("User-Agent", it) }
        }.build()
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val request = withCookies(original)
        var response = chain.proceed(request)
        if (!isChallenge(response)) return response

        val state = hosts.getOrPut(original.url.host) { HostState() }
        synchronized(state) {
            // Another category request may already have refreshed this host's clearance.
            val refreshed = withCookies(original)
            if (refreshed.header("Cookie") != request.header("Cookie")) {
                response.close()
                response = chain.proceed(refreshed)
                if (!isChallenge(response)) return response
            }
            if (clock() < state.retryAfter) return response
            val before = clearance(cookies(original.url.toString()))
            try {
                solve(original.url.toString()) {
                    val after = clearance(cookies(original.url.toString()))
                    after != null && after != before
                }
            } catch (error: Exception) {
                if (error is java.util.concurrent.CancellationException) {
                    response.close()
                    throw error
                }
                // Devices without a working WebView still receive the original HTTP error.
            }
            val after = clearance(cookies(original.url.toString()))
            if (after != null && after != before) {
                response.close()
                response = chain.proceed(withCookies(original))
            }
            state.retryAfter = if (isChallenge(response)) clock() + 30_000L else 0L
            return response
        }
    }

    companion object {
        internal fun clearance(value: String?): String? = value?.split(';')
            ?.map { it.trim() }?.firstOrNull { it.startsWith("cf_clearance=") }
            ?.substringAfter('=')?.takeIf { it.isNotBlank() }

        internal fun isChallenge(response: Response): Boolean {
            if (response.code !in listOf(403, 503)) return false
            if (response.header("cf-mitigated") == "challenge") return true
            if (!response.header("Server").orEmpty().contains("cloudflare", ignoreCase = true)) return false
            val html = response.peekBody(65_536).string()
            return html.contains("/cdn-cgi/challenge-platform/") || html.contains("_cf_chl_opt") ||
                html.contains("cf-browser-verification")
        }
    }
}
