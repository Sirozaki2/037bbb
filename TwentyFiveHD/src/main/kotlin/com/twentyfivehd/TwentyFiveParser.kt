package com.twentyfivehd

import com.providers.network.CatalogPagination
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

internal data class MovieCard(val title: String, val url: String, val poster: String?, val series: Boolean)
internal data class MovieDetail(val title: String, val poster: String?, val plot: String?, val embed: String, val tags: List<String>)

internal object TwentyFiveParser {
    private fun poster(image: Element?): String? = image?.let { img ->
        listOf("data-src", "data-lazy-src", "data-original", "src").asSequence().map { img.absUrl(it) }
            .firstOrNull { it.startsWith("https://") || it.startsWith("http://") }
    }
    fun cards(document: Document): List<MovieCard> = document.select(".main-movie .movie_box").mapNotNull { card ->
        val anchor = card.selectFirst("a[href]") ?: return@mapNotNull null
        val url = anchor.absUrl("href")
        val title = anchor.attr("aria-label").ifBlank { anchor.selectFirst("p")?.text().orEmpty() }.trim()
        if (!url.startsWith("https://") || title.isBlank()) return@mapNotNull null
        MovieCard(title, url, poster(card.selectFirst("img")),
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
            poster(document.selectFirst(".thumb-img img")),
            document.selectFirst(".movie-content")?.text()?.takeIf { it.isNotBlank() },
            embed, document.select(".movie-tags a").map { it.text() }
        )
    }

    fun pageUrl(url: String, page: Int): String = if (page <= 1) url else CatalogPagination.path(url, page)

    fun hasNext(document: Document): Boolean =
        CatalogPagination.hasNext(document, "a.next.page-numbers[href]")
}
