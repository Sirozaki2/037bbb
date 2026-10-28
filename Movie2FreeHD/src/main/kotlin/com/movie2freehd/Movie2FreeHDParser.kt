package com.movie2freehd

import com.fasterxml.jackson.databind.ObjectMapper
import com.providers.network.CatalogPagination
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URI

internal data class MovieCard(val title: String, val url: String, val poster: String?, val series: Boolean)
internal data class PlayerOption(val post: String, val number: String, val type: String, val label: String)
internal data class PlayerLink(val url: String, val label: String)
internal data class EpisodeInfo(val title: String, val season: Int, val number: Int, val players: List<PlayerLink>)
internal data class MovieDetail(val title: String, val poster: String?, val plot: String?, val tags: List<String>, val episodes: List<EpisodeInfo>, val series: Boolean)

internal object Movie2FreeHDParser {
    private val json = ObjectMapper()
    private val cssUrl = Regex("""url\(\s*["']?([^"')]+)["']?\s*\)""")
    fun httpUrl(base: String, value: String): String? = runCatching {
        if (value.isBlank() || value.startsWith('#') || value.contains("_files/")) return null
        URI(base).resolve(value.trim()).takeIf { it.scheme in listOf("https", "http") && it.host != null }?.toASCIIString()
    }.getOrNull()

    private fun poster(e: Element, base: String): String? {
        val image = e.selectFirst("img")
        return listOfNotNull(image?.attr("data-src"), image?.attr("data-lazy-src"), image?.attr("src"),
            e.selectFirst(".movie-box-poster")?.attr("style")?.let { cssUrl.find(it)?.groupValues?.get(1) })
            .firstNotNullOfOrNull { httpUrl(base, it) }
    }

    fun cards(doc: Document): List<MovieCard> = doc.select(".custom-movie-grid .movie-box, .search-page .result-item")
        .mapNotNull { e ->
            val a = e.selectFirst(".movie-title a[href], .details .title a[href]") ?: return@mapNotNull null
            val url = httpUrl(doc.baseUri(), a.attr("href")) ?: return@mapNotNull null
            val title = a.text().trim().takeIf { it.isNotBlank() } ?: return@mapNotNull null
            MovieCard(title, url, poster(e, doc.baseUri()), URI(url).path.startsWith("/series/"))
        }.distinctBy { it.url }

    fun detail(doc: Document): MovieDetail {
        val title = doc.selectFirst("h1.m2f-title")?.text()?.trim().orEmpty()
        require(title.isNotBlank()) { "ไม่พบรายละเอียด Movie2FreeHD" }
        val episodes = doc.select(".m2f-season-row").flatMap { row ->
            val season = Regex("\\d+").find(row.selectFirst(".m2f-season-label")?.text().orEmpty())?.value?.toIntOrNull()
                ?: return@flatMap emptyList()
            row.select("button.m2f-ep").mapNotNull { ep ->
                val number = ep.selectFirst(".m2f-ep-num")?.text()?.removePrefix("#")?.trim()?.toIntOrNull()
                    ?: return@mapNotNull null
                val sources = runCatching { json.readTree(ep.attr("data-sources")) }.getOrNull()
                val values = sources?.takeIf { it.isObject }?.fields()?.asSequence()?.map { field ->
                    field.value.asText() to when (field.key) { "thai" -> "พากย์ไทย"; "sub" -> "ซับไทย"; else -> field.key }
                }?.toList().orEmpty() + (ep.attr("data-url") to "")
                val urls = mergePlayers(values.mapNotNull { (value, label) -> httpUrl(doc.baseUri(), value)?.let { PlayerLink(it, label) } })
                if (urls.isEmpty()) null else EpisodeInfo(ep.attr("data-label").ifBlank { "ตอนที่ $number" }, season, number, urls)
            }
        }.groupBy { it.season to it.number }.values.map { rows ->
            rows.first().copy(players = mergePlayers(rows.flatMap { it.players }))
        }.sortedWith(compareBy({ it.season }, { it.number }))
        return MovieDetail(title, doc.selectFirst(".m2f-poster-big")?.let { poster(it, doc.baseUri()) },
            doc.selectFirst(".m2f-synopsis")?.text()?.takeIf { it.isNotBlank() },
            doc.select(".m2f-genres a").map { it.text() }.distinct(), episodes,
            URI(doc.baseUri()).path.startsWith("/series/") || doc.selectFirst(".m2f-seasons") != null)
    }

    fun mergePlayers(players: List<PlayerLink>): List<PlayerLink> = players.groupBy { it.url }.map { (url, links) ->
        PlayerLink(url, links.flatMap { it.label.split(" / ") }.filter { it.isNotBlank() }.distinct().joinToString(" / ").ifBlank { "ตัวเล่น" })
    }

    fun options(doc: Document): List<PlayerOption> = doc.select(".dooplay_player_option").mapNotNull { e ->
        val post = e.attr("data-post"); val number = e.attr("data-nume"); val type = e.attr("data-type")
        if (!post.matches(Regex("\\d+")) || !number.matches(Regex("\\d+")) || type !in listOf("movie", "tv")) null
        else PlayerOption(post, number, type, e.selectFirst(".title")?.text().orEmpty())
    }.distinctBy { Triple(it.post, it.number, it.type) }

    fun embed(response: String, base: String): String? = runCatching {
        httpUrl(base, json.readTree(response).path("embed_url").asText())
    }.getOrNull()

    fun pageUrl(url: String, page: Int): String = if (page <= 1) url else CatalogPagination.path(url, page)
    fun hasNext(doc: Document): Boolean =
        CatalogPagination.hasNext(doc, ".pagination a.next[href]")
}
