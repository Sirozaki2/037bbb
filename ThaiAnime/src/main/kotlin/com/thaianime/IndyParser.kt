package com.thaianime

import com.providers.network.CatalogPagination
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/** Indy has its own listing/episode containers; sidebar and ad links are never catalogue entries. */
internal object IndyParser {
    const val base = "https://indy-anime.net"
    val categories = listOf(
        "/" to "อัปเดตล่าสุด",
        "/lang/dub-tha-ani/" to "พากย์ไทย",
        "/lang/sub-thai/" to "ซับไทย",
        "/anime/anime-chinese/" to "อนิเมะจีน",
        "/anime/themovie/" to "เดอะมูฟวี่",
    )
    private val episodeNumber = Regex("(?:ตอนที่|ตอน|EP\\.?)\\s*(\\d+)", RegexOption.IGNORE_CASE)
    private val seasonNumber = Regex("(?:ภาค|season)\\s*\\(?\\s*(\\d+)", RegexOption.IGNORE_CASE)
    private val movieName = Regex("\\bmovie\\b|เดอะมูฟวี่|เดอะมูฟวี", RegexOption.IGNORE_CASE)
    private fun local(baseUrl: String, value: String): String? = AnimeParser.url(baseUrl, value)
        ?.takeIf { it.toHttpUrlOrNull()?.host == "indy-anime.net" }
    private fun image(node: Element?, baseUrl: String): String? = node?.let {
        sequenceOf(it.attr("data-src"), it.attr("data-lazy-src"), it.attr("src"))
            .mapNotNull { value -> AnimeParser.url(baseUrl, value) }.firstOrNull()
    }
    fun cards(doc: Document): List<AnimeCard> = doc.select("main article.indy-article").mapNotNull { item ->
        val link = item.selectFirst(".film-name a[href]") ?: return@mapNotNull null
        val url = local(doc.baseUri(), link.attr("href")) ?: return@mapNotNull null
        val title = link.attr("title").ifBlank { link.text() }.trim()
        if (title.isBlank()) return@mapNotNull null
        AnimeCard(title, url, image(item.selectFirst(".film-poster img"), doc.baseUri()),
            movieName.containsMatchIn(title) || doc.location().toHttpUrlOrNull()?.encodedPath == "/anime/themovie/")
    }.distinctBy { it.url }

    fun detail(doc: Document): AnimeDetail {
        val title = doc.selectFirst("main h1")?.text().orEmpty().trim()
        require(title.isNotBlank()) { "Indy-Anime ไม่พบรายละเอียดเรื่อง" }
        val episodes = doc.select("main .header-episode a.ep-a-link[href]").mapNotNull { link ->
            val url = local(doc.baseUri(), link.attr("href"))?.takeIf { it.toHttpUrlOrNull()?.encodedPath?.startsWith("/watch/") == true }
                ?: return@mapNotNull null
            val text = link.text().trim()
            if (text.isBlank() || text.contains("ยังไม่มา")) return@mapNotNull null
            val group = link.closest(".header-episode")?.selectFirst("h2")?.text().orEmpty().ifBlank { title }
            AnimeEpisode(text, url, episodeNumber.find(text)?.groupValues?.get(1)?.toIntOrNull(),
                seasonNumber.find(group)?.groupValues?.get(1)?.toIntOrNull(), group.contains("พากย์ไทย"))
        }.distinctBy { it.url }.sortedWith(compareBy({ it.season ?: 1 }, { it.number ?: Int.MAX_VALUE }))
        return AnimeDetail(title,
            image(doc.selectFirst("main .p-thumbnail img"), doc.baseUri())
                ?: AnimeParser.url(doc.baseUri(), doc.selectFirst("meta[property=og:image]")?.attr("content")),
            doc.selectFirst("meta[property=og:description]")?.attr("content"),
            doc.select("main .indy-entry-content a[href*=/genres/]").map { it.text() }.distinct(),
            (movieName.containsMatchIn(title) || doc.select("main .indy-entry-content a[href]").any {
                local(doc.baseUri(), it.attr("href"))?.toHttpUrlOrNull()?.encodedPath in listOf("/anime/themovie/", "/air/the-movie/")
            }), episodes)
    }
    fun pageUrl(url: String, page: Int): String = if (page <= 1) url else CatalogPagination.path(url, page)
    fun hasNext(doc: Document): Boolean = doc.select("main a.next[href], main a[rel=next][href], link[rel=next][href]")
        .any { local(doc.baseUri(), it.attr("href"))?.let { target -> CatalogPagination.isNext(doc.baseUri(), target) } == true }
    fun searchUrl(query: String): String = base.toHttpUrlOrNull()!!.newBuilder().addQueryParameter("s", query).build().toString()
}
