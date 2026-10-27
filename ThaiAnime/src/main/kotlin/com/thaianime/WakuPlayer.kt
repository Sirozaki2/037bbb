package com.thaianime

import okhttp3.Request
import org.jsoup.nodes.Document
import kotlin.coroutines.cancellation.CancellationException

internal data class WakuBrowserStream(val url: String, val referer: String, val headers: Map<String, String>)

/** Waku's raw episode HTML may contain only options; saved Chrome HTML already has an iframe. */
internal object WakuPlayer {
    // The opaque /o/<id>/a route is the HLS master used by both supplied nya player bootstraps.
    val manifestPattern = Regex("""^https://cat\.animenani\.com/o/[A-Za-z0-9_-]+/a(?:\?[^#]*)?$""")

    suspend fun sources(
        doc: Document,
        getJson: suspend (String, String) -> String?,
        resolve: suspend (PlayerSource, String) -> Unit,
        hasLinks: () -> Boolean,
    ) {
        AnimePlayer.sources(Site.WAKU, doc).forEach { resolve(it, doc.baseUri()) }
        if (hasLinks()) return
        for (option in AnimePlayer.options(doc)) {
            try {
                val json = getJson(option.url, doc.baseUri()) ?: continue
                val embed = AnimePlayer.ajaxEmbed(json, doc.baseUri()) ?: continue
                resolve(PlayerSource(embed, option.label), doc.baseUri())
            } catch (error: Exception) { if (error is CancellationException) throw error }
        }
    }

    fun browserStream(request: Request): WakuBrowserStream? {
        val url = request.url.toString()
        if (request.method != "GET" || !manifestPattern.matches(url)) return null
        // Keep actual browser headers, including its content-player Referer and User-Agent.
        val referer = request.header("Referer").orEmpty()
        val headers = request.headers.toMap().filterKeys { !it.equals("Referer", ignoreCase = true) }
        return WakuBrowserStream(url, referer, headers)
    }
}
