package com.thaianime

import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.net.URI
import java.net.URLEncoder

internal enum class Site(val base: String, val label: String) {
    NEKO("https://nekomiku.net", "Neko-Miku"),
    RUKA("https://animeruka.com", "AnimeRuka"),
    SEVEN("https://www.anime-seven.com", "AnimeSeven"),
    SUBA("https://suba-anime.com", "Suba-Anime")
}
internal data class AnimeCard(val title: String, val url: String, val poster: String?, val movie: Boolean)
internal data class AnimeEpisode(val title: String, val url: String, val number: Int?, val season: Int?, val dubbed: Boolean)
internal data class AnimeDetail(val title: String, val poster: String?, val plot: String?, val tags: List<String>, val movie: Boolean, val episodes: List<AnimeEpisode>)

internal object AnimeParser {
    fun url(base: String, value: String?): String? = runCatching {
        val text = value?.trim().orEmpty()
        if (text.isBlank() || text.startsWith('#') || text.contains("_files/") || text.contains("#+")) return null
        val u = base.toHttpUrlOrNull()?.resolve(text) ?: return null
        if (u.username.isNotEmpty() || u.password.isNotEmpty() || u.fragment != null) null else u.toString()
    }.getOrNull()
    private fun img(el: Element?, base: String): String? {
        if (el == null) return null
        return sequenceOf(el.attr("data-src"), el.attr("data-lazy-src"), el.attr("src"), el.attr("srcset").substringBefore(' '))
            .mapNotNull { url(base, it) }.firstOrNull()
    }
    private fun meta(doc: Document, key: String) = doc.selectFirst("meta[property='$key']")?.attr("content")?.trim()
    private fun isMovie(text: String) = Regex("(?i)\\bmovie\\b|เดอะมูฟวี่|เดอะมูฟวี|\\[The Movie\\]").containsMatchIn(text)
    fun cards(site: Site, doc: Document): List<AnimeCard> {
        val nodes = when (site) {
            Site.NEKO -> doc.select("main li:has(a[href*=/anime/]):has(img)")
            Site.RUKA -> if (doc.selectFirst(".search-page") != null) doc.select(".search-page .result-item") else doc.select(".items article.item")
            Site.SEVEN -> doc.select(".title_movie").mapNotNull { it.parent() }
            Site.SUBA -> doc.selectFirst(".section_grid_item")?.select(".pic:has(.post_title)").orEmpty()
        }
        return nodes.mapNotNull { node ->
            val link = when (site) {
                Site.NEKO -> node.selectFirst("a[href*=/anime/]")
                Site.RUKA -> node.selectFirst(".title a[href], .poster a[href]")
                Site.SEVEN -> node.select(".title_movie a[href]").firstOrNull { it.text().isNotBlank() }
                Site.SUBA -> node.selectFirst(".post_title a[href]")
            } ?: return@mapNotNull null
            val href = url(doc.baseUri(), link.attr("href")) ?: return@mapNotNull null
            if (URI(href).host.removePrefix("www.") != URI(site.base).host.removePrefix("www.")) return@mapNotNull null
            val title = when (site) {
                Site.NEKO -> node.selectFirst("p")?.text()
                else -> link.text()
            }.orEmpty().trim().ifBlank { node.selectFirst("img")?.attr("alt").orEmpty() }
            val movie = isMovie(title + " " + node.text()) || (site == Site.RUKA && URI(href).path.startsWith("/movies/")) ||
                (site == Site.NEKO && URI(doc.baseUri()).path == "/category/3") ||
                (site == Site.SEVEN && URI(doc.baseUri()).path.startsWith("/catalog/17/"))
            if (title.isBlank()) null else AnimeCard(title, href, img(node.selectFirst("img"), doc.baseUri()), movie)
        }.distinctBy { it.url }
    }
    private fun number(text: String) = Regex("(?:ตอนที่|ตอน|[Ee][Pp]\\.?)\\s*(\\d+)").find(text)?.groupValues?.get(1)?.toIntOrNull()
    private fun season(text: String) = Regex("(?i)(?:ภาค|season)\\s*\\(?\\s*(\\d+)").find(text)?.groupValues?.get(1)?.toIntOrNull()
    fun detail(site: Site, doc: Document): AnimeDetail {
        val title = (when (site) {
            Site.SUBA -> doc.title().removeSuffix(" Anime-Suba")
            else -> doc.selectFirst("h1")?.text()
        }).orEmpty().trim()
        require(title.isNotBlank()) { "ไม่พบรายละเอียดเรื่อง" }
        val movie = when (site) {
            Site.NEKO -> doc.select("dl div").any { it.selectFirst("dt")?.text() == "ประเภท" && it.selectFirst("dd")?.text() == "Movie" }
            Site.RUKA -> URI(doc.baseUri()).path.startsWith("/movies/")
            else -> isMovie(title)
        }
        val links = when (site) {
            Site.NEKO -> doc.select("main a[href*=/watch/]").filter { it.closest("li") != null }
            Site.RUKA -> doc.select("#seasons .episodiotitle a[href]")
            Site.SEVEN -> doc.select("a[href*=/play/]")
            Site.SUBA -> doc.select(".Episode a[href]")
        }
        val episodes = links.mapNotNull { a ->
            val href = url(doc.baseUri(), a.attr("href")) ?: return@mapNotNull null
            val text = a.text().trim().trim('|').trim()
            if (text.isBlank() || text.contains("ยังไม่มา")) return@mapNotNull null
            val group = if (site == Site.RUKA) a.closest(".se-c")?.selectFirst(".se-q")?.text().orEmpty() else ""
            AnimeEpisode(text, href, number(text), season(group) ?: season(title), (group.ifBlank { text }).contains("พากย์ไทย"))
        }.distinctBy { it.url }.sortedWith(compareBy({ it.season ?: 1 }, { it.number ?: Int.MAX_VALUE }, { it.dubbed }))
        val poster = (if (site == Site.RUKA) img(doc.selectFirst(".sheader .poster img"), doc.baseUri()) else null)
            ?: url(doc.baseUri(), meta(doc, "og:image"))
        val plot = doc.selectFirst(if (site == Site.SUBA) ".content_summary" else if (site == Site.RUKA) ".wp-content p" else "meta[property=og:description]")
            ?.let { if (it.tagName() == "meta") it.attr("content") else it.text() }?.takeIf { it.isNotBlank() }
            ?: meta(doc, "og:description")
        val tags = doc.select(if (site == Site.NEKO) "main a[href*=/genre/]" else if (site == Site.RUKA) ".sgeneros a[href*=/genre/]" else "a[rel=tag]").map { it.text() }.distinct()
        return AnimeDetail(title, poster, plot, tags, movie, episodes)
    }
    fun next(site: Site, doc: Document): String? {
        val explicit = when (site) {
            Site.RUKA -> doc.selectFirst(".pagination a:has(#nextpagination), .resppages a:has(.icon-caret-right)")
            Site.SEVEN -> doc.selectFirst(".pagination li.active + li a[href]")
            else -> null
        }
        val link = explicit ?: doc.select("a[href]").firstOrNull { a ->
            val text = a.text().trim()
            a.attr("rel").split(' ').contains("next") || text in listOf("ถัดไป", "หน้าถัดไป", "Next", "Next »", "»", "Next page") || a.hasClass("next")
        } ?: return null
        val href = url(doc.baseUri(), link.attr("href")) ?: return null
        return href.takeIf { URI(it).host.removePrefix("www.") == URI(site.base).host.removePrefix("www.") && it != doc.baseUri() }
    }
    fun pageUrl(site: Site, base: String, page: Int): String {
        if (page <= 1) return base
        if (site == Site.SEVEN) return base.replace(Regex("&number=\\d+"), "").substringBefore('#') + "&number=$page"
        val uri = URI(base)
        if (site == Site.RUKA) {
            val path = uri.rawPath.replace(Regex("/page/\\d+/?$"), "/").trimEnd('/') + "/page/$page/"
            return "${uri.scheme}://${uri.rawAuthority}$path" + (uri.rawQuery?.let { "?$it" } ?: "")
        }
        val args = uri.rawQuery.orEmpty().split('&').filter { it.isNotBlank() && it.substringBefore('=') != "page" } + "page=$page"
        return "${uri.scheme}://${uri.rawAuthority}${uri.rawPath}?${args.joinToString("&")}"
    }
    fun searchUrl(site: Site, query: String): String {
        val q = URLEncoder.encode(query, "UTF-8")
        return site.base + when (site) { Site.NEKO -> "/search?q=$q"; Site.RUKA -> "/?s=$q"; Site.SEVEN -> "/index.php?search=$q"; Site.SUBA -> "/search?value=$q&search=" }
    }
}
