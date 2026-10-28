package com.thaianime

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Shared WordPress layout, with explicit routes and selectors for each independently registered site. */
internal enum class TeamAnimeSite(
    val base: String,
    val label: String,
    val episodePrefix: String,
    val thaiPath: String,
    val subPath: String,
    val chinesePath: String,
    val moviePath: String,
) {
    INDY("https://indy-anime.net", "Indy-Anime", "/watch/", "/lang/dub-tha-ani/", "/lang/sub-thai/", "/anime/anime-chinese/", "/anime/themovie/"),
    HIT("https://hit-anime.com", "Hit-Anime", "/video/", "/category/anime-thai/", "/lang/sub-th/", "/category/anime-chinese-korea/", "/category/themovie/"),
    YOUR("https://anime-your.com", "AnimeYour", "/ep/", "/lang/anime-th/", "/lang/sub-th/", "/category/anime/chinese/", "/category/anime/themovie/");

    val categories get() = listOf("/" to "อัปเดตล่าสุด", thaiPath to "พากย์ไทย", subPath to "ซับไทย", chinesePath to "อนิเมะจีน", moviePath to "เดอะมูฟวี่")
    val cardLink get() = if (this == HIT) "a.post-thumbnail[href]" else ".film-name a[href]"
    val cardImage get() = if (this == HIT) ".post-thumbnail img" else ".film-poster img"
    val detailPoster get() = if (this == HIT) "main .post-thumnail img" else "main .p-thumbnail img"
    val metadata get() = if (this == HIT) "main .ez-entry-content" else "main .indy-entry-content"
    fun local(baseUrl: String, value: String): String? {
        val url = AnimeParser.url(baseUrl, value)?.toHttpUrlOrNull() ?: return null
        val host = base.toHttpUrlOrNull()!!.host
        if (url.host.removePrefix("www.") != host && !(this == YOUR && url.host.removePrefix("www.") == "animeyour.com")) return null
        return url.newBuilder().scheme("https").host(host).port(443).build().toString()
    }
    fun isEpisode(url: String): Boolean = local(base, url)?.toHttpUrlOrNull()?.encodedPath?.startsWith(episodePrefix) == true
    fun validatePlaybackPage(requested: String, actual: String) {
        require(!isEpisode(requested) || isEpisode(actual)) { "$label ลิงก์ตอนเปลี่ยนไปหน้าอื่น กรุณาเลือกตอนใหม่จากหน้ารายการ" }
    }
}
