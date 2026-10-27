package com.fwiptv

import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URI

internal data class MovieCard(val title: String, val url: String, val poster: String?, val series: Boolean)
internal data class EpisodeInfo(val title: String, val url: String, val season: Int, val number: Int, val poster: String?)
internal data class MovieDetail(val title: String, val poster: String?, val plot: String?, val tags: List<String>, val episodes: List<EpisodeInfo>, val series: Boolean)

internal object FWIPParser {
    fun httpUrl(base: String, value: String): String? = runCatching {
        if (value.isBlank() || value.startsWith('#') || value.contains("_files/")) return null
        URI(base).resolve(value).takeIf { it.scheme in listOf("https", "http") && it.host != null }?.toASCIIString()
    }.getOrNull()
    private fun poster(e: Element?, base: String): String? = e?.select("img")?.firstNotNullOfOrNull { img ->
        listOf(img.attr("data-src"), img.attr("src"), img.attr("data-srcset").substringBefore(' '), img.attr("srcset").substringBefore(' '))
            .firstNotNullOfOrNull { httpUrl(base, it) }
    }
    fun cards(doc: Document): List<MovieCard> {
        // Archive grid only: related/sidebar/featured sliders are not search results.
        val listing = doc.selectFirst("#movie-archive-grid") ?: doc.selectFirst(".movie-home:not(.movie-archive-layout) .home-content") ?: return emptyList()
        return listing.select("article.movie-card").mapNotNull { e ->
            val a = e.selectFirst(".movie-card__title a[href]") ?: return@mapNotNull null
            val url = httpUrl(doc.baseUri(), a.attr("href")) ?: return@mapNotNull null
            val title = a.text().trim().takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            MovieCard(title, url, poster(e, doc.baseUri()), e.select(".movie-card__genre").text().contains("ซีรี่") || Regex("(?i)\\bseason\\s*\\d+|ซีซั[่น]+\\s*\\d+").containsMatchIn(title))
        }.distinctBy { it.url }
    }
    fun detail(doc: Document): MovieDetail {
        val title = doc.selectFirst("h1#movie-title")?.text()?.trim().orEmpty()
        require(title.isNotBlank()) { "ไม่พบรายละเอียด FWIP TV" }
        val episodes = doc.select(".movie-series-season").flatMap { section ->
            val season = Regex("\\d+").find(section.selectFirst("h3")?.text().orEmpty())?.value?.toIntOrNull() ?: 1
            section.select("a.movie-series-episode-link[href]").mapNotNull { a ->
                val url = httpUrl(doc.baseUri(), a.attr("href")) ?: return@mapNotNull null
                val number = Regex("ตอนที่\\s*(\\d+)").find(a.selectFirst("strong")?.text().orEmpty())?.groupValues?.get(1)?.toIntOrNull() ?: return@mapNotNull null
                EpisodeInfo(a.selectFirst(".movie-series-episode-link__content")?.text().orEmpty(), url, season, number, poster(a, doc.baseUri()))
            }
        }.distinctBy { it.url }.sortedWith(compareBy({ it.season }, { it.number }))
        val tags = doc.select(".movie-detail-card a[href*=/genre/]").map { it.text() }.distinct()
        return MovieDetail(title, poster(doc.selectFirst(".movie-detail-card__poster"), doc.baseUri())
            ?: httpUrl(doc.baseUri(), doc.selectFirst("meta[property=og:image]")?.attr("content").orEmpty()),
            doc.selectFirst(".movie-content-panel--description")?.select("p")?.text()?.takeIf { it.isNotBlank() }, tags, episodes,
            doc.selectFirst(".movie-series-season") != null || tags.any { it.contains("ซีรี่") })
    }
    fun embed(doc: Document): String? = doc.selectFirst("[data-movie-player][data-embed-url]")
        ?.let { httpUrl(doc.baseUri(), it.attr("data-embed-url")) }

    fun pageUrl(url: String, page: Int): String {
        if (page <= 1) return url
        val uri = URI(url)
        val path = uri.rawPath.trimEnd('/').replace(Regex("/page/\\d+$"), "")
        return "${uri.scheme}://${uri.rawAuthority}$path/page/$page/" + (uri.rawQuery?.let { "?$it" } ?: "")
    }
    fun hasNext(doc: Document): Boolean = doc.select(".movie-pagination a.next[href]").any { httpUrl(doc.baseUri(), it.attr("href")) != null }
}
