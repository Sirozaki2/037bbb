package com.example

import com.fasterxml.jackson.databind.ObjectMapper
import kotlinx.coroutines.delay
import org.jsoup.Jsoup
import java.net.URI
import kotlin.coroutines.cancellation.CancellationException

internal data class PlayerPage(val url: String, val text: String)
internal data class StreamSource(val label: String, val url: String, val referer: String)

/** HTTP is injected so the complete redirect/session/source flow can be tested off device. */
internal class StreamHlsResolver(
    private val get: suspend (url: String, referer: String) -> PlayerPage,
    private val post: suspend (url: String, referer: String, json: Map<String, String>) -> PlayerPage
) {
    private val mapper = ObjectMapper()

    suspend fun resolve(iframeUrl: String, referer: String): List<StreamSource> {
        val page = get(iframeUrl, referer)
        val direct = hlsSources(page, "StreamHLS")
        if (direct.isNotEmpty()) return direct

        val sourcePath = Regex("""["'](/player/source/v1\.[a-zA-Z0-9._-]+)["']""")
            .find(page.text)?.groupValues?.get(1)
        if (sourcePath != null) {
            return hlsSources(get(resolveUrl(page.url, sourcePath) ?: return emptyList(), page.url), "StreamHLS")
        }

        val document = Jsoup.parse(page.text, page.url)
        val configs = document.select("[data-session-url][data-playback-url]").mapNotNull { element ->
            val session = resolveUrl(page.url, element.attr("data-session-url")) ?: return@mapNotNull null
            val playback = resolveUrl(page.url, element.attr("data-playback-url")) ?: return@mapNotNull null
            PlayerConfig(session, playback, element.text().trim())
        }.toMutableList()
        // With language buttons, their contexts are authoritative. Otherwise use the main config.
        if (configs.isEmpty()) {
            val session = scriptValue(page.text, "sessionUrl")?.let { resolveUrl(page.url, it) }
            val playback = scriptValue(page.text, "playbackUrl")?.let { resolveUrl(page.url, it) }
            if (session != null && playback != null) configs.add(PlayerConfig(session, playback, ""))
        }

        val links = mutableListOf<StreamSource>()
        for (config in configs.distinctBy { it.sessionUrl }) {
            try {
                val session = mapper.readTree(get(config.sessionUrl, page.url).text)
                if (!session.path("success").asBoolean(false)) continue
                val gate = session.path("ad_gate")
                val gateToken = gate.path("gate_token").asText("")
                val waitSeconds = gate.path("min_gate_seconds").asLong(0)
                // Respect a server-advertised wait; leave interactive challenges to the website.
                if (waitSeconds !in 0..60 || session.path("turnstile_required").asBoolean(false)) continue
                if (waitSeconds > 0) delay(waitSeconds * 1000)
                val language = session.path("language").asText("").trim().ifBlank { config.label }
                for (source in session.path("sources")) {
                    try {
                        val id = source.path("id").asText("")
                        if (id.isBlank()) continue
                        val payload = mutableMapOf("source_id" to id)
                        if (gateToken.isNotBlank()) payload["gate_token"] = gateToken
                        val playback = mapper.readTree(post(config.playbackUrl, page.url, payload).text)
                        if (!playback.path("success").asBoolean(false)) continue
                        val embed = resolveUrl(page.url, playback.path("embed_url").asText("")) ?: continue
                        val label = listOf(language, source.path("label").asText(id))
                            .filter { it.isNotBlank() }.joinToString(" - ")
                        links += hlsSources(get(embed, page.url), label)
                    } catch (error: Exception) {
                        if (error is CancellationException) throw error
                        // A broken mirror must not hide the other sources.
                    }
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
            }
        }

        // Preserve older embeds that expose ctx but do not have a session endpoint.
        if (configs.isEmpty()) {
            val id = Regex("""/(?:player|embed|e|v)/([a-zA-Z0-9_-]+)""").find(page.url)?.groupValues?.get(1)
            val ctx = Regex("""ctx=([a-zA-Z0-9._-]+)""").find(page.text)?.groupValues?.get(1)
                ?: scriptValue(page.text, "ctx")
            if (id != null && ctx != null) {
                val api = resolveUrl(page.url, "/api/player/$id/playback?ctx=$ctx")!!
                val playback = mapper.readTree(post(api, page.url, emptyMap()).text)
                val embed = resolveUrl(page.url, playback.path("embed_url").asText(""))
                if (embed != null) links += hlsSources(get(embed, page.url), "StreamHLS")
            }
        }
        return links.distinctBy { it.url }
    }

    private data class PlayerConfig(val sessionUrl: String, val playbackUrl: String, val label: String)

    private fun scriptValue(html: String, key: String): String? =
        Regex("""["']?$key["']?\s*:\s*["']([^"']+)["']""")
            .find(html)?.groupValues?.get(1)?.replace("\\/", "/")

    private fun hlsSources(page: PlayerPage, label: String): List<StreamSource> =
        Regex("""(?:\b(?:var|let|const)\s+m3u8Url\s*=\s*|["']m3u8Url["']\s*:\s*)["']([^"']+\.m3u8[^"']*)["']""")
            .findAll(page.text).mapNotNull { match ->
                resolveUrl(page.url, match.groupValues[1].replace("\\/", "/"))
                    ?.let { StreamSource(label, it, page.url) }
            }.distinctBy { it.url }.toList()

    companion object {
        /** Signed tokens rotate on each embed request; the underlying HLS source stays the same. */
        fun streamKey(url: String): String = runCatching {
            val uri = URI(url)
            val query = uri.rawQuery?.split('&')?.filterNot {
                it.substringBefore('=') == "hls_token"
            }?.joinToString("&").orEmpty()
            "${uri.scheme}://${uri.rawAuthority}${uri.rawPath}" + if (query.isEmpty()) "" else "?$query"
        }.getOrDefault(url)

        fun resolveUrl(base: String, path: String): String? {
            if (path.isBlank()) return null
            return runCatching {
                URI(base).resolve(path).takeIf {
                    it.scheme in listOf("http", "https") && !it.host.isNullOrBlank()
                }?.toString()
            }.getOrNull()
        }

        fun origin(url: String): String = URI(url).let { "${it.scheme}://${it.rawAuthority}" }
    }
}
