package com.twentyfivehd

import org.jsoup.nodes.Document

internal data class MovieCard(val title: String, val url: String, val poster: String?, val series: Boolean)
internal data class MovieDetail(val title: String, val poster: String?, val plot: String?, val embed: String, val tags: List<String>)

internal object TwentyFiveParser {
    fun cards(document: Document): List<MovieCard> = document.select(".main-movie .movie_box").mapNotNull { card ->
        val anchor = card.selectFirst("a[href]") ?: return@mapNotNull null
        val url = anchor.absUrl("href")
        val title = anchor.attr("aria-label").ifBlank { anchor.selectFirst("p")?.text().orEmpty() }.trim()
        if (!url.startsWith("https://") || title.isBlank()) return@mapNotNull null
        MovieCard(title, url, card.selectFirst("img")?.absUrl("src")?.takeIf { it.isNotBlank() },
            card.selectFirst(".box-movie-episode") != null)
    }.distinctBy { it.url }

    fun detail(document: Document): MovieDetail {
        val player = document.selectFirst("#pe-iframe[data-original-src]")
        val embed = player?.absUrl("data-original-src")?.takeIf { it.isNotBlank() }
            ?: document.selectFirst(".fake-player-container[data-embed-url]")?.absUrl("data-embed-url")
            ?: error("ไม่พบตัวเล่นของ 25-HD")
        return MovieDetail(
            document.selectFirst("meta[property=og:title]")?.attr("content")
                ?: document.selectFirst("h1")?.text().orEmpty(),
            document.selectFirst(".thumb-img img")?.absUrl("src"),
            document.selectFirst(".movie-content")?.text()?.takeIf { it.isNotBlank() },
            embed, document.select(".movie-tags a").map { it.text() }
        )
    }

    fun hasNext(document: Document): Boolean = document.select("a.next.page-numbers[href]").isNotEmpty()
}
