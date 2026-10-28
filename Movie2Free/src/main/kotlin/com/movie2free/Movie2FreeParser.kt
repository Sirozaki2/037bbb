package com.movie2free

import com.fasterxml.jackson.databind.ObjectMapper
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import com.providers.network.CatalogPagination
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

internal data class Card(val title: String, val url: String, val poster: String?)
internal data class Detail(val title: String, val poster: String?, val plot: String?, val year: Int?, val tags: List<String>)
internal data class Player(val url: String, val label: String)

internal object Movie2FreeParser {
    private val json = ObjectMapper()
    fun url(base: String, value: String): String? {
        if (value.isBlank() || value.trim().startsWith('#') || value.contains("_files/")) return null
        return base.toHttpUrlOrNull()?.resolve(value.trim())?.takeIf { it.username.isEmpty() && it.password.isEmpty() }?.toString()
    }
    private fun poster(element: Element?, base: String): String? = element?.select("img")?.firstNotNullOfOrNull { image ->
        listOf("data-src", "data-lazy-src", "src").firstNotNullOfOrNull { url(base, image.attr(it)) }
    }
    fun cards(doc: Document): List<Card> = doc.select(".mf-main .mf-grid article.mf-film").mapNotNull { card ->
        val link = card.selectFirst(".mf-film-title a[href]") ?: return@mapNotNull null
        val href = url(doc.baseUri(), link.attr("href")) ?: return@mapNotNull null
        link.text().trim().takeIf { it.isNotEmpty() }?.let { Card(it, href, poster(card, doc.baseUri())) }
    }.distinctBy { it.url }

    // The site's search form capture has an empty query. Its actual autocomplete
    // script documents this JSON endpoint and fields, avoiding guessed result HTML.
    fun searchResults(text: String, base: String): List<Card> {
        val rows = runCatching { json.readTree(text) }.getOrNull()?.takeIf { it.isArray } ?: return emptyList()
        return rows.mapNotNull { row ->
            val href = url(base, row.path("url").asText()) ?: return@mapNotNull null
            if (href.toHttpUrlOrNull()?.host != base.toHttpUrlOrNull()?.host) return@mapNotNull null
            row.path("title").asText().trim().takeIf { it.isNotEmpty() }?.let {
                Card(org.jsoup.Jsoup.parse(it).text(), href, url(base, row.path("thumb").asText()))
            }
        }.distinctBy { it.url }
    }
    fun detail(doc: Document): Detail {
        val title = doc.selectFirst("h1.mf-title")?.text()?.trim().orEmpty()
        require(title.isNotEmpty()) { "ไม่พบรายละเอียด Movie2Free.co" }
        return Detail(title, poster(doc.selectFirst(".mf-poster"), doc.baseUri())
            ?: url(doc.baseUri(), doc.selectFirst("meta[property=og:image]")?.attr("content").orEmpty()),
            doc.select(".mf-prose[itemprop=description] p").text().ifBlank { null },
            doc.selectFirst("[itemprop=datePublished]")?.text()?.trim()?.toIntOrNull(),
            doc.select("[itemprop=genre] a").map { it.text() }.distinct())
    }
    fun embed(doc: Document): String? = doc.selectFirst(".mf-player-screen iframe")?.let {
        url(doc.baseUri(), it.attr("data-src")) ?: url(doc.baseUri(), it.attr("src"))
    }
    fun players(doc: Document): List<Player> = (doc.select(".linkserver[data-url]").mapNotNull {
        url(doc.baseUri(), it.attr("data-url"))?.let { href -> Player(href, it.text().trim()) }
    } + doc.select("iframe#main-iframe, iframe#backup-iframe").mapNotNull {
        url(doc.baseUri(), it.attr("src"))?.let { href -> Player(href, "ตัวเล่น") }
    }).distinctBy { it.url }

    fun streams(doc: Document): List<String> {
        // Only the content variables used by the captured players, never ad.file.
        val pattern = Regex("""(?:\bconst\s+url|\bwindow\.hls)\s*=\s*(["'])([^"'\r\n]+)\1""")
        return doc.select("script:not([src])").flatMap { script ->
            pattern.findAll(script.data()).mapNotNull { url(doc.baseUri(), it.groupValues[2].replace("\\/", "/")) }.toList()
        }.filter { it.toHttpUrlOrNull()?.encodedPath?.endsWith(".m3u8", true) == true }.distinct()
    }
    fun pageUrl(base: String, page: Int): String {
        if (page <= 1) return base
        val parsed = requireNotNull(base.toHttpUrlOrNull())
        val path = parsed.encodedPath.trimEnd('/').replace(Regex("/page/\\d+$"), "")
        return parsed.newBuilder().encodedPath("$path/page/$page/").build().toString()
    }
    fun hasNext(doc: Document): Boolean =
        CatalogPagination.hasNext(doc, "a.next.page-numbers[href], link[rel=next][href]")
}
