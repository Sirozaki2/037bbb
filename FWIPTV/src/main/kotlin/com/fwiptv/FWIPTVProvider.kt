package com.fwiptv

import android.content.Context
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.network.CloudflareKiller
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import com.lagradost.cloudstream3.utils.*
import java.net.URLEncoder

@CloudstreamPlugin
class FWIPTVPlugin : Plugin() {
    override fun load(context: Context) { registerMainAPI(FWIPTVProvider()) }
}

class FWIPTVProvider : MainAPI() {
    override var mainUrl = "https://fwip-tv.com"
    override var name = "FWIP TV"
    override var lang = "th"
    override val hasMainPage = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)
    override var sequentialMainPage = true
    override var sequentialMainPageDelay = 100L
    override val mainPage = SiteCategories.entries.map { (path, title) -> mainPage(mainUrl + path, title) }
    private val cloudflare by lazy { CloudflareKiller() }
    private suspend fun document(url: String, fresh: Boolean = false): org.jsoup.nodes.Document {
        val response = if (fresh) app.get(url, interceptor = cloudflare, cacheTime = 0) else app.get(url, interceptor = cloudflare)
        if (response.code !in 200..299) throw ErrorLoadingException("FWIP TV ไม่พร้อมใช้งาน (HTTP ${response.code})")
        return response.document.apply { setBaseUri(response.url) }
    }
    private fun MovieCard.response(): SearchResponse = if (series) {
        newTvSeriesSearchResponse(title, url, TvType.TvSeries) { posterUrl = poster; posterHeaders = mapOf("Referer" to "$mainUrl/") }
    } else {
        newMovieSearchResponse(title, url, TvType.Movie) { posterUrl = poster; posterHeaders = mapOf("Referer" to "$mainUrl/") }
    }
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val doc = document(FWIPParser.pageUrl(request.data, page))
        return newHomePageResponse(listOf(HomePageList(request.name, FWIPParser.cards(doc).map { it.response() })), FWIPParser.hasNext(doc))
    }
    override suspend fun search(query: String): List<SearchResponse> = FWIPParser.cards(document("$mainUrl/?s=${URLEncoder.encode(query, "UTF-8")}")).map { it.response() }
    override suspend fun load(url: String): LoadResponse {
        val detail = FWIPParser.detail(document(url))
        return if (detail.series) newTvSeriesLoadResponse(detail.title, url, TvType.TvSeries, detail.episodes.map { ep ->
            newEpisode(ep.url) { name = ep.title; episode = ep.number; season = ep.season; posterUrl = ep.poster }
        }) { posterUrl = detail.poster; posterHeaders = mapOf("Referer" to url); plot = detail.plot; tags = detail.tags }
        else newMovieLoadResponse(detail.title, url, TvType.Movie, url) {
            posterUrl = detail.poster; posterHeaders = mapOf("Referer" to url); plot = detail.plot; tags = detail.tags
        }
    }
    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        val embed = FWIPParser.embed(document(data, fresh = true)) ?: return false
        var found = false
        // The site's player123up host is blocked in the sandbox. Delegate only to registered
        // Cloudstream extractors; never return an embed HTML page or a preroll ad as a video.
        loadExtractor(embed, data, subtitleCallback) { found = true; callback(it) }
        return found
    }
}
