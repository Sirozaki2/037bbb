package com.twentyfivehd

import com.fasterxml.jackson.databind.ObjectMapper
import java.net.URI
import java.io.IOException
import okhttp3.Interceptor
import okhttp3.Response

/** Follow the CDN routes advertised by the player's HLS content-steering manifest. */
internal class HlsRouting(private val master: String, private val hosts: List<String>) {
    private val assetPath = Regex("/hls/[a-fA-F0-9]{24}/").find(URI(master).path)?.value

    fun mediaUrls(url: String): List<String> {
        val uri = runCatching { URI(url) }.getOrNull() ?: return emptyList()
        if (assetPath == null || !uri.path.startsWith(assetPath) || !isMedia(uri.path)) return emptyList()
        val query = uri.rawQuery?.split('&')?.filterNot {
            it.substringBefore('=') in setOf("gw_enc", "sig")
        }?.joinToString("&")?.takeIf { it.isNotEmpty() }
        return hosts.map { "https://$it${uri.rawPath}" + (query?.let { q -> "?$q" } ?: "") }
    }

    fun encode(): String = mapper.writeValueAsString(mapOf("master" to master, "hosts" to hosts))

    companion object {
        private val mapper = ObjectMapper()
        private fun isMedia(path: String): Boolean =
            Regex("/\\_s/[^/.]+$", RegexOption.IGNORE_CASE).containsMatchIn(path) ||
                Regex("\\.(bin|m4s|mp4|ts|aac|vtt|key)$", RegexOption.IGNORE_CASE).containsMatchIn(path)

        fun steeringUrl(master: String, text: String): String? = text.lineSequence()
            .firstOrNull { it.startsWith("#EXT-X-CONTENT-STEERING:") }
            ?.let { Regex("SERVER-URI=\"([^\"]+)\"").find(it)?.groupValues?.get(1) }
            ?.let { URI(master).resolve(it).toString() }
            ?.takeIf { it.startsWith("https://") }

        fun fromManifest(master: String, json: String): HlsRouting {
            val root = mapper.readTree(json)
            val clones = root.path("PATHWAY-CLONES").associate {
                it.path("ID").asText() to it.path("URI-REPLACEMENT").path("HOST").asText()
            }
            val priority = root.path("PATHWAY-PRIORITY").map { it.asText() }
            val hosts = (priority.mapNotNull { clones[it] } + clones.values).filter { host ->
                host.isNotBlank() && runCatching {
                    val uri = URI("https://$host")
                    uri.host != null && uri.rawAuthority == host && uri.userInfo == null &&
                        uri.rawPath.isEmpty() && uri.query == null && uri.fragment == null
                }.getOrDefault(false)
            }.distinct()
            return HlsRouting(master, hosts)
        }

        fun decode(data: String?): HlsRouting? = runCatching {
            val node = mapper.readTree(data ?: return null)
            HlsRouting(node.path("master").asText(), node.path("hosts").map { it.asText() })
        }.getOrNull()
    }
}

internal class HlsRoutingInterceptor(private val routing: HlsRouting) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val urls = routing.mediaUrls(request.url.toString())
        if (urls.isEmpty()) return chain.proceed(request)
        var lastError: IOException? = null
        for ((index, url) in urls.withIndex()) {
            try {
                val response = chain.proceed(request.newBuilder().url(url).build())
                if (response.isSuccessful || index == urls.lastIndex) return response
                response.close()
            } catch (error: IOException) {
                lastError = error
            }
        }
        throw lastError ?: IOException("ไม่พบเซิร์ฟเวอร์วิดีโอที่พร้อมใช้งาน")
    }
}
