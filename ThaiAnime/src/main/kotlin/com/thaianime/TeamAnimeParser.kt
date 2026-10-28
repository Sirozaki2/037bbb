package com.thaianime

import com.providers.network.CatalogPagination
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/** Site-scoped listing/episode containers exclude sidebars and advertising. */
internal class TeamAnimeParser(private val site: TeamAnimeSite) {
    val base get() = site.base
    val categories get() = site.categories
    private val episodeNumber = Regex("(?:ตอนที่|ตอน|EP\\.?)\\s*(\\d+)", RegexOption.IGNORE_CASE)
    private val seasonNumber = Regex("(?:ภาค|season)\\s*\\(?\\s*(\\d+)", RegexOption.IGNORE_CASE)
    private val movieName = Regex("\\bmovie\\b|เดอะมูฟวี่|เดอะมูฟวี", RegexOption.IGNORE_CASE)
    private fun local(baseUrl: String, value: String): String? = site.local(baseUrl, value)
    private fun image(node: Element?, baseUrl: String): String? = node?.let {
        sequenceOf(it.attr("data-src"), it.attr("data-lazy-src"), it.attr("src"))
            .mapNotNull { value -> AnimeParser.url(baseUrl, value) }.firstOrNull()
    }
    fun cards(doc: Document): List<AnimeCard> = doc.select("main article.indy-article").mapNotNull { item ->
        val link = item.selectFirst(site.cardLink) ?: return@mapNotNull null
        val url = local(doc.baseUri(), link.attr("href")) ?: return@mapNotNull null
        val title = link.attr("title").ifBlank { link.text() }.trim()
        if (title.isBlank()) return@mapNotNull null
        AnimeCard(title, url, image(item.selectFirst(site.cardImage), doc.baseUri()),
            movieName.containsMatchIn(title) || doc.location().toHttpUrlOrNull()?.encodedPath == site.moviePath)
    }.distinctBy { it.url }

    fun detail(doc: Document): AnimeDetail {
        val title = doc.selectFirst("main h1")?.text().orEmpty().trim()
        require(title.isNotBlank()) { "${site.label} ไม่พบรายละเอียดเรื่อง" }
        val episodes = doc.select("main .header-episode a.ep-a-link[href]").mapNotNull { link ->
            val url = local(doc.baseUri(), link.attr("href"))?.takeIf(site::isEpisode)
                ?: return@mapNotNull null
            val text = link.attr("title").ifBlank { link.text() }.trim()
            if (text.isBlank() || text.contains("ยังไม่มา")) return@mapNotNull null
            val group = link.closest(".header-episode")?.selectFirst("h2")?.text().orEmpty().ifBlank { title }
            AnimeEpisode(text, url, episodeNumber.find(text)?.groupValues?.get(1)?.toIntOrNull(),
                seasonNumber.find(group)?.groupValues?.get(1)?.toIntOrNull(), group.contains("พากย์ไทย"))
        }.distinctBy { it.url }.sortedWith(compareBy({ it.season ?: 1 }, { it.number ?: Int.MAX_VALUE }))
        return AnimeDetail(title,
            image(doc.selectFirst(site.detailPoster), doc.baseUri())
                ?: AnimeParser.url(doc.baseUri(), doc.selectFirst("meta[property=og:image]")?.attr("content")),
            doc.selectFirst("meta[property=og:description]")?.attr("content"),
            doc.select("${site.metadata} a[href*=/genres/]").map { it.text() }.distinct(),
            (movieName.containsMatchIn(title) || doc.select("${site.metadata} a[href]").any {
                local(doc.baseUri(), it.attr("href"))?.toHttpUrlOrNull()?.encodedPath in listOf(site.moviePath, "/air/the-movie/")
            }), episodes)
    }
    fun pageUrl(url: String, page: Int): String = if (page <= 1) url else CatalogPagination.path(url, page)
    fun hasNext(doc: Document): Boolean = doc.select("main a.next[href], main a[rel=next][href], link[rel=next][href]")
        .any { local(doc.baseUri(), it.attr("href"))?.let { target -> CatalogPagination.isNext(doc.baseUri(), target) } == true }
    fun searchUrl(query: String): String = base.toHttpUrlOrNull()!!.newBuilder().addQueryParameter("s", query).build().toString()
}
