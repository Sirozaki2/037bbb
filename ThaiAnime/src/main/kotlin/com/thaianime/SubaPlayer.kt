package com.thaianime

import org.jsoup.nodes.Document
import java.net.URI
import kotlin.coroutines.cancellation.CancellationException

internal data class SubaStream(val url: String, val referer: String, val label: String)

/** Follows the content iframe configured by Kokora, excluding its advertising sources. */
internal class SubaPlayer(private val get: suspend (String, String) -> Document) {
    suspend fun resolve(url: String, referer: String): List<SubaStream> {
        var failure: Exception? = null
        try {
            val doc = get(url, referer)
            val config = configUrl(doc)
            val target = config?.let { target(doc, get(it, doc.baseUri()).text()) }
            if (target != null) streams(get(target, doc.baseUri())).takeIf { it.isNotEmpty() }?.let { return it }
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            failure = error
        }
        // The primary player's numeric ID is also its content PHP ID (verified on both
        // supplied episodes). Fetch fresh source HTML, never invent a media filename.
        // player2 uses a different server; it must not be mapped to this primary route.
        val origin = primaryOrigin(url)
        if (origin != null) {
            try {
                streams(get(origin, url)).takeIf { it.isNotEmpty() }?.let { return it }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                if (failure == null) failure = error
            }
        }
        failure?.let { throw it }
        return emptyList()
    }
    companion object {
        // Capture the site's content-player iframe when the episode is rendered by JavaScript.
        val capturePattern = Regex("""^https?://(?:www\.)?suba-anime\.com/player2?/\d+/?(?:\?[^#]*)?$""")
        internal fun primaryOrigin(url: String): String? {
            if (!isPlayer(url)) return null
            val id = Regex("/player/(\\d+)/?").matchEntire(URI(url).path)?.groupValues?.get(1) ?: return null
            return "https://anim-esun.com/st1//player/$id.php"
        }
        fun isPlayer(url: String): Boolean = runCatching {
            val uri = URI(url)
            uri.host.removePrefix("www.") == "suba-anime.com" && uri.path.matches(Regex("/player2?/\\d+/?"))
        }.getOrDefault(false)
        fun configUrl(doc: Document): String? = doc.select("script[src]").mapNotNull { AnimeParser.url(doc.baseUri(), it.attr("src")) }
            .firstOrNull { URI(it).host == URI(doc.baseUri()).host && URI(it).path.endsWith("/banner/setkokora/") }
        fun target(doc: Document, config: String): String? {
            val scripts = doc.select("script").joinToString("\n") { it.data() }
            val id = Regex("""var\s+idpass\s*=\s*['"](/player/\d+\.php)['"]""").find(scripts)?.groupValues?.get(1) ?: return null
            val root = Regex("""var\s+url\s*=\s*['"]([^'"]+)['"]""").find(scripts)?.groupValues?.get(1) ?: return null
            val prefix = Regex("""['"]files['"]\s*:\s*\{\s*['"]type['"]\s*:\s*['"]iframe['"]\s*,\s*['"]url['"]\s*:\s*url\s*\+\s*['"]([a-zA-Z0-9_./-]+)['"]\s*\+\s*idpass""")
                .find(config)?.groupValues?.get(1) ?: return null
            val base = AnimeParser.url(doc.baseUri(), root) ?: return null
            if (URI(base).host != "anim-esun.com") return null
            return AnimeParser.url(doc.baseUri(), base + prefix + id)
        }
        fun streams(doc: Document): List<SubaStream> {
            if (URI(doc.baseUri()).host != "anim-esun.com") return emptyList()
            val script = doc.select("script").map { it.data() }.firstOrNull { it.contains("playerInstance.setup") } ?: return emptyList()
            val sources = Regex("""sources\s*:\s*\[([\s\S]*?)]""").find(script)?.groupValues?.get(1) ?: return emptyList()
            return Regex("""\{([^{}]+)\}""").findAll(sources).mapNotNull { match ->
                val value = Regex("""\bfile\s*:\s*['"]([^'"]+)['"]""").find(match.groupValues[1])?.groupValues?.get(1) ?: return@mapNotNull null
                val url = AnimeParser.url(doc.baseUri(), value) ?: return@mapNotNull null
                val uri = URI(url)
                if (uri.host != "anim-esun.com" || !uri.path.contains("/pl-1.2/") || uri.path.contains("/Ads_Video/") || !uri.path.matches(Regex("(?i).+\\.(?:m4v|mp4)$"))) return@mapNotNull null
                val label = Regex("""\blabel\s*:\s*['"]([^'"]+)['"]""").find(match.groupValues[1])?.groupValues?.get(1).orEmpty()
                SubaStream(url, doc.baseUri(), label)
            }.distinctBy { it.url }.toList()
        }
    }
}
