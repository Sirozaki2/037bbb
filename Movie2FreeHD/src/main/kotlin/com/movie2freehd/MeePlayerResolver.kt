package com.movie2freehd

import com.fasterxml.jackson.databind.ObjectMapper
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder

internal data class PlayerPage(val url: String, val text: String, val status: Int = 200)
internal data class ResolvedStream(val url: String, val referer: String, val subtitle: String?, val routing: String?)

/** Uses the same video/resolve endpoints as Meeplayer's public player. Never caches expiring playback URLs. */
internal class MeePlayerResolver(
    private val get: suspend (String, String) -> PlayerPage,
    private val post: suspend (String, String, Map<String, Any>) -> PlayerPage
) {
    suspend fun resolve(embed: String, referer: String): ResolvedStream? {
        val page = get(embed, referer)
        if (page.status !in 200..299) return null
        val uri = URI(page.url)
        if (!isMeePlayer(page.url)) return null
        val id = Regex("/(?:p2p|play)/([a-fA-F0-9]{32,64})(?:/|$)").find(uri.path)?.groupValues?.get(1) ?: return null
        val origin = "${uri.scheme}://${uri.rawAuthority}"
        val token = uri.rawQuery.orEmpty().split('&').firstOrNull { it.substringBefore('=') == "t" }
            ?.substringAfter('=', "")?.let { URLDecoder.decode(it, "UTF-8") }.orEmpty()
        val video = get("$origin/api/video/$id" + if (token.isNotEmpty()) "?t=${URLEncoder.encode(token, "UTF-8")}" else "", page.url)
        if (video.status != 200) return null
        val node = json.readTree(video.text).path("video")
        val md5 = node.path("md5").asText()
        if (node.path("iStatus").asInt() != 1 || !md5.matches(Regex("[a-fA-F0-9]{32,64}"))) return null
        val payload = mutableMapOf<String, Any>("md5" to md5, "videoId" to id, "hasSubtitle" to node.path("subtitle").asText("").isNotBlank())
        if (token.isNotEmpty()) payload["token"] = token
        val response = post("$origin/api/resolve", page.url, payload)
        if (response.status != 200) return null
        val result = json.readTree(response.text)
        val url = Movie2FreeHDParser.httpUrl(page.url, result.path("videoUrl").asText()) ?: return null
        val subtitle = Movie2FreeHDParser.httpUrl(page.url, result.path("subtitleUrl").asText(""))
        val routing = MeeRouting.create(md5, result.path("cdnRotations").map { it.asText() })
        return ResolvedStream(url, page.url, subtitle, routing?.encode())
    }

    companion object {
        private val json = ObjectMapper()
        fun isMeePlayer(url: String): Boolean = runCatching {
            val host = URI(url).host.orEmpty().lowercase()
            host == "meeplayer.com" || host.endsWith(".meeplayer.com")
        }.getOrDefault(false)
    }
}
