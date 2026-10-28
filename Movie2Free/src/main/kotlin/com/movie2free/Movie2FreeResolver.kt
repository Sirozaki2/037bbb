package com.movie2free

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jsoup.nodes.Document
import kotlin.coroutines.cancellation.CancellationException

internal data class Stream(val url: String, val referer: String, val label: String)

internal object Movie2FreeResolver {
    // Only observed content wrapper routes; never capture ads or arbitrary inline resources.
    val browserPattern = Regex("""^https?://(?:(?:[A-Za-z0-9-]+\.)?ssplayer168\.xyz/api/embed/(?:index\.php|bunny/index\.php|jw/main\.php|v5/index\.php)\?id=\d+|(?:[A-Za-z0-9-]+\.)?(?:fastcdnv2\.com|fastfastcdn\.com)/embed/[A-Za-z0-9_-]+)(?:&[^#]*)?$""")

    suspend fun resolvePage(
        page: String,
        pageReferer: String,
        fetch: suspend (String, String) -> Document,
        browser: suspend () -> Pair<String, String>?,
        emit: suspend (Stream) -> Unit,
        fallback: suspend (String, String) -> Unit,
        hasLinks: () -> Boolean,
    ) {
        var failure: Exception? = null
        try {
            Movie2FreeParser.embed(fetch(page, pageReferer))?.let { resolve(it, page, fetch, emit, fallback) }
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            failure = error
        }
        if (hasLinks()) return
        try {
            browser()?.let { (url, referer) ->
                if (browserPattern.matches(url)) resolve(url, referer, fetch, emit, fallback)
            }
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            if (failure == null) failure = error
        }
        if (!hasLinks()) failure?.let { throw it }
    }

    private fun knownHost(url: String): Boolean {
        val host = url.toHttpUrlOrNull()?.host ?: return false
        return listOf("ssplayer168.xyz", "fastcdnv2.com", "fastfastcdn.com").any { host == it || host.endsWith(".$it") }
    }
    suspend fun resolve(
        initial: String, referer: String,
        fetch: suspend (String, String) -> Document,
        emit: suspend (Stream) -> Unit,
        fallback: suspend (String, String) -> Unit,
    ) {
        val visited = mutableSetOf<String>()
        val emitted = mutableSetOf<String>()
        suspend fun walk(url: String, parent: String, label: String, depth: Int) {
            if (depth > 3 || visited.size >= 12 || !visited.add(url)) return
            try {
                if (!knownHost(url)) { fallback(url, parent); return }
                val doc = fetch(url, parent)
                Movie2FreeParser.streams(doc).forEach { stream ->
                    if (emitted.add(stream)) emit(Stream(stream, doc.baseUri(), label))
                }
                for (player in Movie2FreeParser.players(doc)) walk(player.url, doc.baseUri(), player.label, depth + 1)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                // A broken mirror must not suppress the remaining mirrors.
            }
        }
        walk(initial, referer, "ตัวเล่น", 0)
    }
}
