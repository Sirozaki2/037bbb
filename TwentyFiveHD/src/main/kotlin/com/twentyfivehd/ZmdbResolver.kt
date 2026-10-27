package com.twentyfivehd

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.jsoup.Jsoup
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import kotlin.coroutines.cancellation.CancellationException

internal data class WebPage(val url: String, val text: String, val status: Int = 200)
internal data class ZmdbEpisode(val name: String, val season: Int, val number: Int, val url: String)
internal data class VideoStream(val label: String, val url: String, val referer: String, val hls: Boolean)

internal class ZmdbResolver(private val get: suspend (String, String, Map<String, String>) -> WebPage) {
    suspend fun resolve(embed: String, referer: String): List<VideoStream> {
        val page = get(embed, referer, emptyMap())
        val bootstrap = bootstrap(page.text)
        if (bootstrap.path("content").path("isVipOnly").asBoolean() ||
            bootstrap.path("content").path("isComingSoon").asBoolean()) return emptyList()
        val origin = URI(page.url).let { "${it.scheme}://${it.rawAuthority}" }
        val params = query(embed).filterKeys { it in setOf("id", "type", "season", "episode") }
        require(!params["id"].isNullOrBlank()) { "ไม่พบรหัสวิดีโอ" }
        val endpoint = "$origin/api/embed/links?${encode(params)}"
        var token = bootstrap.path("linkToken").asText()
        suspend fun fetchLinks() = get(endpoint, page.url,
            if (token.isBlank()) emptyMap() else mapOf("Authorization" to "Bearer $token"))
        var response = fetchLinks()
        if (response.status == 401) {
            val refresh = get("$origin/api/embed/bootstrap?${encode(params.filterKeys { it == "id" || it == "type" })}", page.url, emptyMap())
            val fresh = mapper.readTree(refresh.text)
            if (!fresh.path("success").asBoolean()) return emptyList()
            token = fresh.path("linkToken").asText()
            response = fetchLinks()
        }
        if (response.status != 200) return emptyList()
        val result = mapper.readTree(response.text)
        if (!result.path("success").asBoolean()) return emptyList()
        // ZMDB's player resolves internal /play/ IDs through its own /api/video endpoint.
        // Multiple language buttons can refer to the same multi-audio HLS master.
        val groups = result.path("playerEmbedLinks").filter { !it.path("isVipOnly").asBoolean() }
            .filter { it.path("embedUrl").asText().isNotBlank() }
            .groupBy { videoId(it.path("embedUrl").asText()) ?: it.path("embedUrl").asText() }
        return groups.mapNotNull { (key, links) ->
            try {
                val source = links.first().path("embedUrl").asText()
                val label = links.map { it.path("language").asText() }.filter { it.isNotBlank() }.distinct().joinToString(" / ")
                    .let { "${links.first().path("serverLabel").asText("Server")} - $it".trimEnd(' ', '-') }
                if (videoId(source) == null) {
                    return@mapNotNull source.takeIf { it.startsWith("https://") || it.startsWith("http://") }
                        ?.let { VideoStream(label, it, page.url, false) }
                }
                val videoResponse = get("$origin/api/video/$key", page.url, emptyMap())
                if (videoResponse.status != 200) return@mapNotNull null
                val video = mapper.readTree(videoResponse.text)
                val hls = video.path("data").path("hlsUrl").asText()
                if (!video.path("success").asBoolean() || !hls.startsWith("https://")) return@mapNotNull null
                VideoStream(label, hls, page.url, true)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                null
            }
        }.distinctBy { it.url }
    }

    companion object {
        private val mapper = ObjectMapper()
        fun bootstrap(html: String): JsonNode = mapper.readTree(
            Jsoup.parse(html).selectFirst("script#bootstrap")?.data()
                ?: error("ไม่พบข้อมูลตัวเล่น ZMDB")
        )

        fun episodes(bootstrap: JsonNode, embed: String): List<ZmdbEpisode> =
            bootstrap.path("content").path("seasons").flatMap { season ->
                season.path("episodes").filter { it.path("hasLinks").asBoolean() || it.path("playerEmbedLinks").size() > 0 }
                    .map { episode ->
                        val s = season.path("seasonNumber").asInt(1)
                        val e = episode.path("episodeNumber").asInt()
                        val params = query(embed).toMutableMap().apply {
                            put("season", s.toString()); put("episode", e.toString())
                        }
                        ZmdbEpisode("ตอนที่ $e", s, e, embed.substringBefore('?').substringBefore('#') + "?" + encode(params))
                    }
            }.distinctBy { it.season to it.number }.sortedWith(compareBy({ it.season }, { it.number }))

        private fun videoId(url: String): String? =
            Regex("""(?:/play/|^hls:|^internal:)([a-f0-9]{24})(?:/|$|\?)""", RegexOption.IGNORE_CASE)
                .find(url)?.groupValues?.get(1)

        private fun query(url: String): Map<String, String> = URI(url).rawQuery.orEmpty().split('&')
            .filter { it.contains('=') }.associate {
                URLDecoder.decode(it.substringBefore('='), "UTF-8") to URLDecoder.decode(it.substringAfter('='), "UTF-8")
            }

        private fun encode(params: Map<String, String>): String = params.entries.joinToString("&") {
            "${URLEncoder.encode(it.key, "UTF-8")}=${URLEncoder.encode(it.value, "UTF-8")}" }
    }
}
