package com.thaianime

import com.fasterxml.jackson.databind.ObjectMapper
import okio.ByteString.Companion.decodeBase64
import org.jsoup.nodes.Document
import java.net.URI
import java.net.URLDecoder

internal data class PlayerSource(val url: String, val label: String)
internal data class PlayerOption(val url: String, val label: String)

/** Reads only the observed content players, never ad videos or arbitrary inline URLs. */
internal object AnimePlayer {
    private val json = ObjectMapper()
    fun options(doc: Document): List<PlayerOption> {
        val config = Regex("""var\s+dtAjax\s*=\s*(\{[^;]+\});""").find(doc.html())?.groupValues?.get(1) ?: return emptyList()
        val node = runCatching { json.readTree(config) }.getOrNull() ?: return emptyList()
        if (node.path("play_method").asText() != "wp_json") return emptyList()
        val api = AnimeParser.url(doc.baseUri(), node.path(if (URI(doc.baseUri()).host == "anime-waku.com") "url_api" else "player_api").asText()) ?: return emptyList()
        if (URI(api).host != URI(doc.baseUri()).host) return emptyList()
        val version = node.path("player_v").asText("")
        return doc.select(".dooplay_player_option[data-post][data-nume][data-type]").mapNotNull { el ->
            val post = el.attr("data-post"); val num = el.attr("data-nume"); val type = el.attr("data-type")
            if (!post.matches(Regex("\\d+")) || !num.matches(Regex("\\d+")) || type !in listOf("tv", "movie")) null
            else PlayerOption("${api.trimEnd('/')}/$post/$type/$num" + if (version.matches(Regex("[a-zA-Z0-9]+"))) "?v=$version" else "", el.selectFirst(".title")?.text().orEmpty())
        }
    }
    fun ajaxEmbed(text: String, base: String): String? {
        val value = runCatching { json.readTree(text).path("embed_url").asText() }.getOrNull() ?: return null
        return AnimeParser.url(base, value) ?: org.jsoup.Jsoup.parse(value, base).selectFirst("iframe[src]")?.let { AnimeParser.url(base, it.attr("src")) }
    }
    fun sources(site: Site, doc: Document): List<PlayerSource> {
        val result = mutableListOf<PlayerSource>()
        fun add(value: String?, label: String = "ตัวเล่น") { AnimeParser.url(doc.baseUri(), value)?.let { result += PlayerSource(it, label) } }
        when (site) {
            Site.NEKO -> {
                doc.select("script[type=application/ld+json]").forEach { script ->
                    val node = runCatching { json.readTree(script.data()) }.getOrNull()
                    if (node?.path("@type")?.asText() == "VideoObject") add(node.path("embedUrl").asText())
                }
                doc.select("main iframe[src][allowfullscreen]").forEach { add(it.attr("src")) }
            }
            Site.RUKA, Site.WAKU -> doc.select("#dooplay_player_response iframe[src]").forEach { add(it.attr("src")) }
            Site.SEVEN -> Regex("""var\s+PLAYER_URL\s*=\s*["']([^"']+)["']""").find(doc.html())?.let { add(it.groupValues[1]) }
            Site.SUBA -> {
                doc.select("#show_video_play iframe").forEach { add(it.attr("data-src").ifBlank { it.attr("src") }) }
                doc.select("script").filter { it.data().contains("function load_video_http") }.forEach { script ->
                    Regex("""var\s+url\s*=\s*["']([^"']+)["']""").findAll(script.data()).forEach { add(it.groupValues[1]) }
                }
            }
        }
        return result.distinctBy { it.url }
    }
    fun canUnwrap(url: String, site: Site): Boolean {
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        val host = uri.host.orEmpty().removePrefix("www.")
        return (host == URI(site.base).host.removePrefix("www.") && when(site) {
            Site.WAKU -> uri.path.startsWith("/player/")
            Site.NEKO -> uri.path.startsWith("/player/embed")
            Site.SEVEN -> uri.path.matches(Regex("/player(?:y|vy)/\\d+/?"))
            else -> false
        }) || (host == "nya.animenani.com" && uri.path.startsWith("/embed/"))
    }
    fun unwrap(doc: Document): List<PlayerSource> {
        val host = URI(doc.baseUri()).host.orEmpty().removePrefix("www.")
        if (host == "nekomiku.net") {
            val target = Regex("""var\s+target\s*=\s*("(?:\\.|[^"\\])*")""").find(doc.html())?.groupValues?.get(1)
            val decoded = target?.let { runCatching { json.readTree(it).asText() }.getOrNull() }
            return listOfNotNull(AnimeParser.url(doc.baseUri(), decoded)?.let { PlayerSource(it, "ตัวเล่น") })
        }
        if (host == "anime-waku.com") return doc.select("#embedvideo[src]").mapNotNull { frame ->
            AnimeParser.url(doc.baseUri(), frame.attr("src"))?.let { PlayerSource(it, "ตัวเล่น") }
        }.distinctBy { it.url }
        if (host == "anime-seven.com") return doc.select("iframe[src]").mapNotNull { frame ->
            AnimeParser.url(doc.baseUri(), frame.attr("src"))?.let { PlayerSource(it, "ตัวเล่น") }
        }.distinctBy { it.url }
        return emptyList()
    }
    fun akuma(doc: Document): String? {
        if (URI(doc.baseUri()).host != "app.akuma-stream.com") return null
        return doc.select("media-player source[src], video source[src]").firstNotNullOfOrNull {
            AnimeParser.url(doc.baseUri(), it.attr("src"))?.takeIf { url ->
                val uri = URI(url)
                uri.host == "app.akuma-stream.com" && uri.path.startsWith("/api/manifest/") && uri.path.endsWith(".m3u8")
            }
        }
    }
    fun manifest(doc: Document): String? {
        if (URI(doc.baseUri()).host != "nya.animenani.com") return null
        val scripts = doc.select("script").firstOrNull { it.data().contains("__PLAYER_EMBED_BOOTSTRAP__") }?.data() ?: return null
        val encoded = Regex("""const\s+s\s*=\s*"([A-Za-z0-9_=-]+)"""").find(scripts)?.groupValues?.get(1) ?: return null
        val node = runCatching {
            val text = encoded.reversed().replace('-', '+').replace('_', '/').decodeBase64()?.utf8() ?: return null
            json.readTree(URLDecoder.decode(text.replace("+", "%2B"), "UTF-8"))
        }.getOrNull() ?: return null
        if (node.path("status").asText() != "ready") return null
        return AnimeParser.url(doc.baseUri(), node.path("manifestUrl").asText())
    }
}
