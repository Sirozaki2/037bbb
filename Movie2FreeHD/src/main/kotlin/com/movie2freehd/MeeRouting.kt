package com.movie2freehd

import com.fasterxml.jackson.databind.ObjectMapper
import com.providers.network.CdnFailoverInterceptor
import okhttp3.Interceptor
import okhttp3.Response
import java.net.URI

internal class MeeRouting private constructor(private val id: String, private val origins: List<String>) {
    fun urls(url: String): List<String> {
        val uri = runCatching { URI(url) }.getOrNull() ?: return emptyList()
        // Only the advertised video's media fragments use CDN rotation; signed playlists stay intact.
        if (!uri.path.orEmpty().matches(Regex("/files/${Regex.escape(id)}/(?:1080|720|480|360|240)p?/[^/]+"))) return emptyList()
        if (uri.host != "cdn.meeplayer.com" && origins.none { URI(it).host == uri.host }) return emptyList()
        if (!uri.path.matches(Regex("(?i).*\\.(ts|m4s|mp4|aac|bin|png|jpe?g)$"))) return emptyList()
        return (origins.map { it + uri.rawPath + (uri.rawQuery?.let { q -> "?$q" } ?: "") } + url).distinct()
    }
    fun encode(): String = json.writeValueAsString(mapOf("id" to id, "origins" to origins))

    companion object {
        private val json = ObjectMapper()
        fun create(id: String, origins: List<String>): MeeRouting? {
            if (!id.matches(Regex("[a-fA-F0-9]{32,64}"))) return null
            val valid = origins.mapNotNull { value -> runCatching {
                val uri = URI(value)
                if (uri.scheme == "https" && uri.host != null && uri.userInfo == null && uri.query == null && uri.fragment == null && uri.path.orEmpty() in listOf("", "/"))
                    "https://${uri.rawAuthority}" else null
            }.getOrNull() }.distinct()
            return valid.takeIf { it.isNotEmpty() }?.let { MeeRouting(id, it) }
        }
        fun decode(data: String?): MeeRouting? = runCatching {
            val root = json.readTree(data ?: return null)
            create(root.path("id").asText(), root.path("origins").map { it.asText() })
        }.getOrNull()
    }
}

internal class MeeRoutingInterceptor(routing: MeeRouting) : Interceptor {
    private val delegate = CdnFailoverInterceptor(routing::urls)
    override fun intercept(chain: Interceptor.Chain): Response = delegate.intercept(chain)
}
