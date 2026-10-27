package com.fwiptv

import com.fasterxml.jackson.databind.ObjectMapper
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

internal data class Player123Request(val api: String, val id: String, val headers: Map<String, String>)
internal data class Player123Source(val url: String, val hls: Boolean)
internal data class Player123Result(val source: Player123Source?, val backup: String?)

/** API contract captured in FWIP's actual player123up WatchPlayer script. */
internal object Player123 {
    private val json = ObjectMapper()
    fun request(embed: String, parent: String): Player123Request? {
        val uri = embed.toHttpUrlOrNull() ?: return null
        if (uri.host != "player123up.xyz" && !uri.host.endsWith(".player123up.xyz")) return null
        val parts = uri.pathSegments
        if (parts.size != 2 || parts[0] != "embed" || !parts[1].matches(Regex("[a-zA-Z0-9-]+"))) return null
        fun origin(url: okhttp3.HttpUrl) = url.newBuilder().encodedPath("/").query(null).fragment(null).build().toString().trimEnd('/')
        val headers = mutableMapOf("x-player-origin" to origin(uri))
        parent.toHttpUrlOrNull()?.let { headers["x-embed-origin"] = origin(it) }
        return Player123Request("${origin(uri)}/api/backend", parts[1], headers)
    }
    private fun httpUrl(base: String, value: String): String? = base.toHttpUrlOrNull()?.resolve(value)
        ?.takeIf { value.isNotBlank() && it.username.isEmpty() && it.password.isEmpty() }?.toString()
    fun result(text: String, api: String): Player123Result {
        val data = runCatching { json.readTree(text) }.getOrNull()
        val raw = data?.path("url")?.asText().orEmpty()
        // Mirrors resolvePlaybackSourceURL in the supplied JS. Secure routes are
        // relative to /api/backend, not to the embed page or site root.
        val value = if (raw.startsWith("/playback/secure/")) api.trimEnd('/') + raw else raw
        val url = httpUrl("${api.trimEnd('/')}/", value)
        val path = url?.toHttpUrlOrNull()?.encodedPath.orEmpty()
        // Do not present an opaque, unverified delivery format as a playable file.
        val source = if (url != null && Regex("(?i)\\.(m3u8|mp4|m4v|webm)$").containsMatchIn(path))
            Player123Source(url, path.endsWith(".m3u8", true)) else null
        return Player123Result(source, httpUrl("${api.trimEnd('/')}/", data?.path("backupUrl")?.asText().orEmpty()))
    }
    fun thaiVersion(text: String): Pair<String, String>? {
        val data = runCatching { json.readTree(text) }.getOrNull() ?: return null
        val id = data.path("videoId").asText()
        if (!data.path("available").asBoolean() || !id.matches(Regex("[a-zA-Z0-9-]+"))) return null
        return id to data.path("label").asText().ifBlank { "ซับไทย" }
    }
}
