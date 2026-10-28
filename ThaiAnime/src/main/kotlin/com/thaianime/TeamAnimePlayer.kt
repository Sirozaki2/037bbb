package com.thaianime

import com.fasterxml.jackson.databind.ObjectMapper
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jsoup.nodes.Document
import kotlin.coroutines.cancellation.CancellationException

internal data class TeamAnimeStream(val url: String, val referer: String, val label: String, val hls: Boolean = false)

/** Uses advertised mirrors only; never guesses a media URL or emits an embed page as video. */
internal object TeamAnimePlayer {
    private val json = ObjectMapper()
    private val embedPath = Regex("/(?:embed|embed3|server2)/([A-Za-z0-9_-]+)/?")
    private val linkValue = Regex("""\bvar\s+link\s*=\s*("(?:\\.|[^"\\])*")\s*;""")
    private val hlsValue = Regex("""\bvar\s+linkplay\s*=\s*['"]([^'"]+)['"]\s*;""")
    private val hlsSource = Regex("""\bsource\s*:\s*linkplay\s*[,}]""")
    private val clapprSource = Regex("""\bsource\s*:\s*link\s*[,}]""")
    fun sources(doc: Document): List<PlayerSource> {
        val sources = doc.select("#mpPlayer .server-item .mp-s-sl[data-id]").mapNotNull { button ->
            AnimeParser.url(doc.baseUri(), button.attr("data-id"))?.let {
                PlayerSource(it, button.text().trim().ifBlank { "สำรอง" })
            }
        } + doc.select("#mpPlayer select.mpSourceSelect option[value]").mapNotNull { option ->
            AnimeParser.url(doc.baseUri(), option.attr("value"))?.let { PlayerSource(it, option.text().trim().ifBlank { "สำรอง" }) }
        } + doc.select("#mpPlayer .mpIframe iframe").mapNotNull { frame ->
            AnimeParser.url(doc.baseUri(), frame.attr("data-src").ifBlank { frame.attr("src") })?.let { PlayerSource(it, "ตัวเล่นหลัก") }
        }
        // The observed server2 is directly playable without the blocked embed/JavaScript route.
        return sources.distinctBy { it.url }.sortedBy { if (it.url.toHttpUrlOrNull()?.let { url -> url.encodedPath.startsWith("/server2/") || (url.host == "abcdxzy.xyz" && url.encodedPath.startsWith("/embed2/")) } == true) 0 else 1 }
    }
    fun stream(doc: Document, label: String): TeamAnimeStream? {
        val player = doc.baseUri().toHttpUrlOrNull() ?: return null
        if (player.host == "abcdxzy.xyz") return hlsStream(doc, label)
        if (player.host != "indy.team-indy.net") return null
        val id = embedPath.matchEntire(player.encodedPath)?.groupValues?.get(1) ?: return null
        return doc.select("script").firstNotNullOfOrNull { script ->
            val code = script.data()
            if (!code.contains("new Clappr.Player") || !clapprSource.containsMatchIn(code)) return@firstNotNullOfOrNull null
            val literal = linkValue.find(code)?.groupValues?.get(1) ?: return@firstNotNullOfOrNull null
            val value = runCatching { json.readTree(literal).asText() }.getOrNull() ?: return@firstNotNullOfOrNull null
            val media = AnimeParser.url(doc.baseUri(), value)?.toHttpUrlOrNull() ?: return@firstNotNullOfOrNull null
            if (!media.host.matches(Regex("sv\\d+\\.team-indy\\.net")) || media.encodedPath != "/video/$id/video.mp4") return@firstNotNullOfOrNull null
            TeamAnimeStream(media.toString(), doc.baseUri(), label)
        }
    }
    private fun hlsStream(doc: Document, label: String): TeamAnimeStream? {
        val player = doc.baseUri().toHttpUrlOrNull() ?: return null
        val id = Regex("/embed2/([A-Za-z0-9_-]+)/?").matchEntire(player.encodedPath)?.groupValues?.get(1) ?: return null
        return doc.select("script").firstNotNullOfOrNull { script ->
            val code = script.data()
            if (!code.contains("new Clappr.Player") || !hlsSource.containsMatchIn(code)) return@firstNotNullOfOrNull null
            val value = hlsValue.find(code)?.groupValues?.get(1) ?: return@firstNotNullOfOrNull null
            val media = AnimeParser.url(doc.baseUri(), value)?.toHttpUrlOrNull() ?: return@firstNotNullOfOrNull null
            if (media.host != "moji.abcdxzy.xyz" || media.encodedPath != "/vod/$id/video.mp4/playlist.m3u8") return@firstNotNullOfOrNull null
            TeamAnimeStream(media.toString(), doc.baseUri(), label, hls = true)
        }
    }
    suspend fun resolve(
        doc: Document,
        get: suspend (String, String) -> Document,
        extract: suspend (PlayerSource, String) -> Unit,
        emit: suspend (TeamAnimeStream) -> Unit,
    ) {
        for (source in sources(doc)) {
            val stream = try {
                stream(get(source.url, doc.baseUri()), source.label)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                null
            }
            if (stream != null) emit(stream)
            else try {
                extract(source, doc.baseUri())
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                // One blocked/broken server must not discard another advertised server's result.
            }
        }
    }
}
