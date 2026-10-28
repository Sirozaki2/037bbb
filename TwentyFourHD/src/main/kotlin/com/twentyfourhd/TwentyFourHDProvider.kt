package com.twentyfourhd

import android.content.Context
import com.lagradost.cloudstream3.*
import com.providers.network.CloudflareSession
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import com.lagradost.cloudstream3.utils.*
import java.net.URLEncoder
import java.util.concurrent.CancellationException

@CloudstreamPlugin
class TwentyFourHDPlugin : Plugin() {
    override fun load(context: Context) { registerMainAPI(TwentyFourHDProvider()) }
}

class TwentyFourHDProvider : MainAPI() {
    override var mainUrl = "https://www.24hd.today"
    override var name = "24HD"
    override var lang = "th"
    override val hasMainPage = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)
    override var sequentialMainPage = true
    override var sequentialMainPageDelay = 100L
    override val mainPage = SiteCategories.homeEntries.map { (path, title) -> mainPage(mainUrl + path, title) }
    private val cloudflare by lazy { CloudflareSession() }
    private suspend fun document(url: String, fresh: Boolean = false): org.jsoup.nodes.Document {
        val response = if (fresh) app.get(url, interceptor = cloudflare, cacheTime = 0) else app.get(url, interceptor = cloudflare)
        if (response.code !in 200..299) throw ErrorLoadingException("เว็บปฏิเสธการเข้าถึง (HTTP ${response.code})")
        return response.document.apply { setBaseUri(response.url) }
    }

    private fun SiteCard.response(): SearchResponse = if (series) {
        newTvSeriesSearchResponse(title, url, TvType.TvSeries) { posterUrl = poster; posterHeaders = mapOf("Referer" to "$mainUrl/") }
    } else {
        newMovieSearchResponse(title, url, TvType.Movie) { posterUrl = poster; posterHeaders = mapOf("Referer" to "$mainUrl/") }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val first = document(request.data)
        val doc = if (page <= 1) first else document(TwentyFourHDParser.pageUrl(first, page))
        return newHomePageResponse(listOf(HomePageList(request.name, TwentyFourHDParser.cards(doc).map { it.response() })), TwentyFourHDParser.hasNext(doc))
    }

    override suspend fun search(query: String): List<SearchResponse> = TwentyFourHDParser.cards(document("$mainUrl/?s=${URLEncoder.encode(query, "UTF-8")}")).map { it.response() }

    override suspend fun load(url: String): LoadResponse {
        val detail = TwentyFourHDParser.detail(document(url))
        if (detail.episodes.isNotEmpty()) {
            return newTvSeriesLoadResponse(detail.title, url, TvType.TvSeries, detail.episodes.map { ep ->
                // Store the page and episode number, then refresh its players when playback starts.
                newEpisode("$url#episode=${ep.number}") { name = ep.title; episode = ep.number; season = 1 }
            }) { posterUrl = detail.poster; posterHeaders = mapOf("Referer" to url); plot = detail.plot }
        }
        return newMovieLoadResponse(detail.title, url, TvType.Movie, url) { posterUrl = detail.poster; posterHeaders = mapOf("Referer" to url); plot = detail.plot }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        val page = data.substringBefore('#')
        val detail = TwentyFourHDParser.detail(document(page, fresh = true))
        val episode = data.substringAfter("#episode=", "").toIntOrNull()
        val players = if (episode != null) detail.episodes.firstOrNull { it.number == episode }?.players.orEmpty() else detail.players
        var found = false
        for ((index, url) in players.withIndex()) {
            // A broken backup must not discard links already found from another player.
            val result = runCatching {
                val response = app.get(url, referer = page, interceptor = cloudflare, cacheTime = 0)
                TwentyFourHDParser.stream(response.document.apply { setBaseUri(response.url) })?.let { stream ->
                    callback(newExtractorLink(name, listOfNotNull(detail.audio, if (index == 0) "ตัวเล่นหลัก" else "สำรอง $index").joinToString(" - "), stream, ExtractorLinkType.M3U8) {
                        referer = response.url; quality = Qualities.Unknown.value
                    })
                    true
                } ?: false
            }.getOrElse { error -> if (error is CancellationException) throw error; false }
            found = found || result
        }
        return found
    }
}
