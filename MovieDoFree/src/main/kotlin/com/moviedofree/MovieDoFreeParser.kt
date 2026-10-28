package com.moviedofree

import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URI

internal data class MovieCard(val title: String, val url: String, val poster: String?)
internal data class MovieDetail(val title: String, val poster: String?, val plot: String?, val player: String)

internal object MovieDoFreeParser {
    private fun poster(image: Element?): String? {
        if (image == null) return null
        return listOf("data-src", "data-lazy-src", "data-original", "src").asSequence()
            .map { image.absUrl(it) }.firstOrNull { it.startsWith("https://") || it.startsWith("http://") }
    }

    fun pageUrl(url: String, page: Int): String {
        if (page <= 1) return url
        val uri = URI(url)
        val path = uri.rawPath.trimEnd('/').replace(Regex("/page/\\d+$"), "")
        return "${uri.scheme}://${uri.rawAuthority}$path/page/$page/" +
            (uri.rawQuery?.let { "?$it" } ?: "")
    }

    fun cards(document: Document): List<MovieCard> = document.select(".column_1_center > .row")
        .lastOrNull { it.selectFirst(".movie-box") != null }?.select(".movie-box").orEmpty().mapNotNull { card ->
        val anchor = card.selectFirst(".movie-title a[href]") ?: return@mapNotNull null
        val title = anchor.text().trim()
        val url = anchor.absUrl("href")
        if (title.isEmpty() || !url.startsWith("https://")) return@mapNotNull null
        MovieCard(title, url, poster(card.selectFirst(".movie-image img")))
    }.distinctBy { it.url }

    fun detail(document: Document): MovieDetail = MovieDetail(
        document.selectFirst(".box-header .title h1")?.text()?.trim().orEmpty(),
        poster(document.selectFirst(".column_1_center center img")),
        document.selectFirst(".entry-content p, .synopsis")?.text()?.takeIf { it.isNotBlank() },
        document.selectFirst("iframe#frame[src]")?.absUrl("src") ?: error("ไม่พบตัวเล่นของ Moviedofree")
    )

    fun streams(html: String, playerUrl: String): List<String> {
        // Only the main JWPlayer setup contains the movie; ignore the separate ads player.
        val sources = Regex("""jwplayer\(["']jwplayer["']\)\s*\.setup\s*\(\s*\{[\s\S]*?sources\s*:\s*\[([\s\S]*?)\]""")
            .find(html)?.groupValues?.get(1) ?: return emptyList()
        return Regex("""file\s*:\s*["']([^"']+)["']""").findAll(sources).mapNotNull {
            val url = runCatching { URI(playerUrl).resolve(it.groupValues[1].replace("\\/", "/")).toString() }.getOrNull()
            url?.takeIf { value -> value.startsWith("https://") && value.substringBefore('?').endsWith(".m3u8") }
        }.distinct().toList()
    }

    fun hasNext(document: Document): Boolean = document.select("a.next.page-numbers[href]").any {
        val href = it.attr("href").trim()
        val url = it.absUrl("href")
        href.isNotBlank() && !href.startsWith('#') &&
            (url.startsWith("https://") || url.startsWith("http://")) &&
            url.substringBefore('#') != document.baseUri().substringBefore('#')
    }
}
