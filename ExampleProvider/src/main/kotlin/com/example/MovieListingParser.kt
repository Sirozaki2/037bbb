package com.example

import org.jsoup.nodes.Document
import java.net.URI

internal data class MovieListing(val title: String, val url: String, val posterUrl: String?) {
    val isSeries: Boolean
        get() = URI(url).path.startsWith("/series/") ||
            title.contains("ตอนที่", true) || title.contains("EP.", true) ||
            title.contains("Season", true)
}

internal object MovieListingParser {
    fun pageUrl(baseUrl: String, page: Int): String {
        val uri = URI(baseUrl)
        val path = uri.rawPath.trimEnd('/').replace(Regex("/page/\\d+$"), "")
        return "${uri.scheme}://${uri.rawAuthority}$path/" + (if (page > 1) "page/$page/" else "") +
            (uri.rawQuery?.let { "?$it" } ?: "")
    }

    fun parse(document: Document): List<MovieListing> = document.select(
        "div.moviefilm, div.postbox, article, div.movie-box, div.item, div.col-movie"
    ).mapNotNull { card ->
        val anchor = card.selectFirst(".movief a[href]") ?: card.selectFirst("a[href]")
            ?: return@mapNotNull null
        val url = anchor.absUrl("href").takeIf { it.isHttpUrl() } ?: return@mapNotNull null
        val image = card.selectFirst("img")
        // The image link has no title, and the first paragraph contains the IMDb score.
        val title = card.selectFirst(".movief a, h2, h3, .title, .movie-title")?.text().orEmpty()
            .ifBlank { anchor.attr("title") }
            .ifBlank { image?.attr("alt").orEmpty() }
            .ifBlank { card.selectFirst("p")?.text().orEmpty() }.trim()
        if (title.isBlank()) return@mapNotNull null
        val poster = image?.let { img ->
            sequenceOf("data-src", "data-lazy-src", "src")
                .filter { img.attr(it).isNotBlank() }
                .map { img.absUrl(it) }.firstOrNull { it.isHttpUrl() }
        }
        MovieListing(title, url, poster)
    }.distinctBy { it.url }

    fun hasNextPage(document: Document): Boolean = document.select(
        "link[rel=next][href], a[rel=next][href], a.nextpostslink[href]"
    ).any {
        val href = it.attr("href").trim()
        val url = it.absUrl("href")
        href.isNotBlank() && !href.startsWith('#') && url.isHttpUrl() &&
            url.substringBefore('#') != document.baseUri().substringBefore('#')
    }

    private fun String.isHttpUrl(): Boolean = startsWith("https://") || startsWith("http://")
}
