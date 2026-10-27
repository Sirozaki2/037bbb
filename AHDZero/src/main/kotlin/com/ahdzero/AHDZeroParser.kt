package com.ahdzero

import com.fasterxml.jackson.databind.ObjectMapper
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URI

internal data class AnimeCard(val title: String, val url: String, val poster: String?, val movie: Boolean)
internal data class AnimeEpisode(val title: String, val url: String, val number: Int?)
internal data class AnimeDetail(val title: String, val poster: String?, val plot: String?, val movie: Boolean, val episodes: List<AnimeEpisode>)

internal object AHDZeroParser {
    private val json = ObjectMapper()
    private val episodeNumber = Regex("(?:ตอนที่|Episode|EP\\.?)\\s*(\\d+)", RegexOption.IGNORE_CASE)

    fun httpUrl(base: String, value: String): String? = runCatching {
        if (value.isBlank() || value.contains("_files/") || value.startsWith("data:")) return null
        URI(base).resolve(value).takeIf { it.scheme in listOf("http", "https") && it.host != null }?.toASCIIString()
    }.getOrNull()

    private fun poster(e: Element, base: String): String? {
        val img = e.selectFirst(".poster-card img, img") ?: return null
        return listOf(img.attr("data-src"), img.attr("src"), img.attr("srcset").substringBefore(',').substringBefore(' '))
            .firstNotNullOfOrNull { httpUrl(base, it) }
    }

    fun cards(doc: Document): List<AnimeCard> {
        // The home carousel repeats on subsequent pages; the grid contains the paginated updates.
        val grid = doc.select("main div.grid").lastOrNull { it.select("a.group.block:has(.poster-card)").isNotEmpty() }
        val elements = (grid ?: doc).select("a.group.block:has(.poster-card)")
        return elements.mapNotNull { e ->
            val url = httpUrl(doc.baseUri(), e.attr("href")) ?: return@mapNotNull null
            val title = e.selectFirst("img[alt]")?.attr("alt")?.trim().orEmpty()
            if (title.isBlank()) return@mapNotNull null
            AnimeCard(title, url, poster(e, doc.baseUri()), e.text().contains("มูฟวี่"))
        }.distinctBy { it.url }
    }

    fun detail(doc: Document): AnimeDetail {
        val title = doc.selectFirst("h1")?.text()?.trim().orEmpty()
        require(title.isNotBlank()) { "ไม่พบข้อมูลเรื่องจาก AHDZero" }
        val episodes = doc.select("a.ep-row[href]").mapNotNull { e ->
            val url = httpUrl(doc.baseUri(), e.attr("href")) ?: return@mapNotNull null
            AnimeEpisode(e.text().removePrefix("▶").trim(), url, episodeNumber.find(e.text())?.groupValues?.get(1)?.toIntOrNull())
        }.distinctBy { it.url }.sortedBy { it.number ?: Int.MAX_VALUE }
        val movie = doc.select("main .font-mono").any { it.ownText().trim().matches(Regex("Movie(?:\\s*·.*)?", RegexOption.IGNORE_CASE)) }
        return AnimeDetail(title, httpUrl(doc.baseUri(), doc.selectFirst("meta[property=og:image]")?.attr("content").orEmpty()),
            doc.selectFirst(".anime-desc")?.text()?.takeIf { it.isNotBlank() }, movie, episodes)
    }

    fun embed(doc: Document): String? {
        // VideoObject preserves the original URL even in browser-saved HTML.
        for (script in doc.select("script[type=application/ld+json]")) {
            val node = runCatching { json.readTree(script.data()) }.getOrNull() ?: continue
            val url = node.findValue("embedUrl")?.asText().orEmpty()
            httpUrl(doc.baseUri(), url)?.let { return it }
        }
        return doc.select("iframe").firstNotNullOfOrNull { e ->
            listOf(e.attr("data-src"), e.attr("src")).firstNotNullOfOrNull { httpUrl(doc.baseUri(), it)?.takeIf { u -> URI(u).host == "app.akuma-stream.com" } }
        }
    }

    fun stream(doc: Document): String? = doc.select("media-player source[src], media-provider source[src]")
        .firstNotNullOfOrNull { e -> httpUrl(doc.baseUri(), e.attr("src"))?.takeIf { URI(it).path.endsWith(".m3u8") } }

    fun pageUrl(url: String, page: Int): String {
        if (page <= 1) return url
        val base = url.replace(Regex("([?&])page=\\d+(&?)")) { if (it.groupValues[2].isNotEmpty()) it.groupValues[1] else "" }.trimEnd('?', '&')
        return base + (if ('?' in base) "&" else "?") + "page=$page"
    }

    fun hasNext(doc: Document, page: Int): Boolean = doc.select("nav[aria-label=Pagination] a[href]").any {
        Regex("[?&]page=(\\d+)").find(it.attr("href"))?.groupValues?.get(1)?.toIntOrNull()?.let { n -> n > page } == true
    }
}
