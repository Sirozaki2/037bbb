package com.twentyfourhd

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.core.Base64Variants
import org.jsoup.nodes.Document
import java.net.URI

internal data class SiteCard(val title: String, val url: String, val poster: String?, val series: Boolean)
internal data class SiteEpisode(val number: Int, val title: String, val players: List<String>)
internal data class SiteDetail(val title: String, val poster: String?, val plot: String?, val episodes: List<SiteEpisode>, val players: List<String>, val audio: String?)

internal object TwentyFourHDParser {
    private val json = ObjectMapper()
    private val number = Regex("(?:EP\\.?|ตอนที่)\\s*(\\d+)", RegexOption.IGNORE_CASE)
    private val cssUrl = Regex("""background-image\s*:\s*url\(["']?([^"')]+)["']?\)""", RegexOption.IGNORE_CASE)

    fun httpUrl(base: String, value: String): String? = runCatching {
        if (value.isBlank() || value.contains("_files/") || value.startsWith("data:")) return null
        URI(base).resolve(value).takeIf { it.scheme in listOf("https", "http") && it.host != null }?.toASCIIString()
    }.getOrNull()

    private fun listing(doc: Document) = doc.select(".elementor-widget-loop-grid").let { grids ->
        grids.firstOrNull { it.selectFirst("[data-next-page]") != null } ?: grids.lastOrNull()
    } ?: doc

    fun cards(doc: Document): List<SiteCard> {
        val styles = doc.select("style").joinToString("\n") { style ->
            val lazyCss = runCatching { String(Base64Variants.getDefaultVariant().decode(style.attr("data-vip24hd-css")), Charsets.UTF_8) }.getOrDefault("")
            style.data() + "\n" + lazyCss
        }
        return listing(doc).select(".e-loop-item, article.elementor-post").mapNotNull { e ->
            val a = e.selectFirst("a.e-con[href], .elementor-post__title a[href]") ?: return@mapNotNull null
            val url = httpUrl(doc.baseUri(), a.attr("href")) ?: return@mapNotNull null
            val title = e.select(".elementor-heading-title").lastOrNull()?.text()?.trim()
                ?: e.selectFirst(".elementor-post__title")?.text()?.trim().orEmpty()
            if (title.isBlank()) return@mapNotNull null
            val id = e.classNames().firstOrNull { it.matches(Regex("e-loop-item-\\d+")) }
            // Each card has its own CSS rule; selecting the first background globally repeats one poster.
            val block = id?.let { Regex("\\.${Regex.escape(it)}\\s[^{}]+\\{[^{}]*}").findAll(styles).map { it.value }.firstOrNull { cssUrl.containsMatchIn(it) } }
            val image: String? = block?.let { cssUrl.find(it)?.groupValues?.get(1) }
                ?: e.selectFirst("img")?.attr("src")
            SiteCard(title, url, image?.let { httpUrl(doc.baseUri(), it) }, number.containsMatchIn(title) || title.contains("Season", true))
        }.distinctBy { it.url }
    }

    fun detail(doc: Document): SiteDetail {
        val title = doc.selectFirst("h1")?.text()?.trim().orEmpty()
        require(title.isNotBlank()) { "ไม่พบข้อมูลเรื่องจาก 24HD" }
        val episodes = doc.select("button.swicth-ep").mapNotNull { e ->
            val n = number.find(e.text())?.groupValues?.get(1)?.toIntOrNull() ?: return@mapNotNull null
            val players = listOf("data-link", "data-link2").mapNotNull { httpUrl(doc.baseUri(), e.attr(it)) }.distinct()
            if (players.isEmpty()) null else SiteEpisode(n, e.text(), players)
        }.distinctBy { it.number }.sortedBy { it.number }
        val players = (doc.select("a[target=box-player][href]").map { it.attr("href") } +
            doc.select("iframe[name=box-player]").flatMap { listOf(it.attr("data-src"), it.attr("src")) })
            .mapNotNull { httpUrl(doc.baseUri(), it) }.distinct()
        val audio = doc.select("h2").firstOrNull { it.text().startsWith("เสียง") }?.text()?.substringAfter(':')?.trim()
        return SiteDetail(title, httpUrl(doc.baseUri(), doc.selectFirst("meta[property=og:image]")?.attr("content").orEmpty()),
            doc.selectFirst(".elementor-widget-theme-post-content")?.text()?.takeIf { it.isNotBlank() }, episodes, players, audio)
    }

    fun stream(doc: Document): String? {
        val config = doc.select("script").firstNotNullOfOrNull { script ->
            Regex("window\\.PLAYER_CONFIG\\s*=\\s*(\\{.*});?", RegexOption.DOT_MATCHES_ALL).find(script.data())?.groupValues?.get(1)
        } ?: return null
        val node = runCatching { json.readTree(config) }.getOrNull() ?: return null
        val id = node.path("vdoId").asText()
        val host = node.path("node").path("playlist").asText()
        if (!id.matches(Regex("[A-Za-z0-9_-]+")) || !host.matches(Regex("[A-Za-z0-9.-]+"))) return null
        return "https://$host/$id/playlist.m3u8"
    }

    fun pageUrl(url: String, page: Int): String {
        if (page <= 1) return url
        val parts = url.split('?', limit = 2)
        return parts[0].trimEnd('/') + "/page/$page/" + (parts.getOrNull(1)?.let { "?$it" } ?: "")
    }

    fun pageUrl(doc: Document, page: Int): String {
        if (page <= 1) return doc.baseUri()
        val next = listing(doc).selectFirst("[data-next-page]")?.attr("data-next-page")
        if (next != null && Regex("[?&]e-page-[^=]+=\\d+").containsMatchIn(next)) {
            return next.replace(Regex("([?&]e-page-[^=]+=)\\d+")) { it.groupValues[1] + page }
        }
        return pageUrl(doc.baseUri(), page)
    }

    fun hasNext(doc: Document): Boolean = listing(doc).select("a.next[href], a[rel=next][href]").isNotEmpty()
}
