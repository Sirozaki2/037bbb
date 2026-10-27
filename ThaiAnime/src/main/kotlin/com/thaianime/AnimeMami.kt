package com.thaianime

import com.fasterxml.jackson.databind.ObjectMapper
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.ByteString.Companion.decodeBase64
import org.jsoup.nodes.Document

internal data class AnimeMamiStream(val url: String, val hls: Boolean, val subtitle: String?, val subtitleLanguage: String)

/** Mirrors Player/Show's video prop and same-CDN JSON playlist unwrapping. */
internal object AnimeMami {
    private val json = ObjectMapper()
    fun isPlayer(url: String): Boolean = url.toHttpUrlOrNull()?.let {
        it.host == "animemami.xyz" && it.encodedPath.matches(Regex("/v/[A-Za-z0-9_-]+/?"))
    } == true

    fun stream(doc: Document): AnimeMamiStream? {
        if (!isPlayer(doc.baseUri())) return null
        val page = runCatching { json.readTree(doc.selectFirst("#app[data-page]")?.attr("data-page")) }.getOrNull() ?: return null
        if (page.path("component").asText() != "Player/Show") return null
        val props = page.path("props")
        val video = props.path("video")
        // Default/error videos and advertising props are deliberately excluded.
        val url = AnimeParser.url(doc.baseUri(), video.path("url").asText()) ?: return null
        val path = url.toHttpUrlOrNull()?.encodedPath.orEmpty()
        val hls = video.path("type").asText() == "hls" || path.endsWith(".m3u8") || path.endsWith(".txt")
        if (!hls && !Regex("(?i)\\.(mp4|m4v|webm)$").containsMatchIn(path)) return null
        val subtitle = video.path("subtitle_path").asText("").takeIf { it.isNotBlank() }?.let {
            AnimeParser.url(doc.baseUri(), "/storage/$it")
        }
        val language = props.path("settings").path("subtitle_languages").asText("")
            .substringBefore(',').trim().ifBlank { "Subtitles" }
        return AnimeMamiStream(url, hls, subtitle, language)
    }

    fun playlist(text: String): String? {
        val node = runCatching { json.readTree(text) }.getOrNull() ?: return null
        return node.path("p").asText("").decodeBase64()?.utf8()?.takeIf { it.startsWith("#EXTM3U") }
    }
}

internal class AnimeMamiInterceptor(private val host: String, private val upstream: Interceptor? = null) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.url.host != host) return chain.proceed(request)
        val response = upstream?.intercept(chain) ?: chain.proceed(request)
        if (response.code != 200 || request.method != "GET") return response
        val source = response.body.source()
        if (!source.request(1) || source.buffer[0] != '{'.code.toByte()) return response
        // Never buffer a whole media segment. Player wrappers are small JSON documents.
        val peek = response.peekBody(1_048_577).bytes()
        if (peek.size > 1_048_576) return response
        val playlist = AnimeMami.playlist(peek.toString(Charsets.UTF_8)) ?: return response
        response.body.close()
        return response.newBuilder().removeHeader("Content-Length").removeHeader("Content-Encoding")
            .header("Content-Type", "application/vnd.apple.mpegurl")
            .body(playlist.toResponseBody("application/vnd.apple.mpegurl".toMediaType())).build()
    }
}
