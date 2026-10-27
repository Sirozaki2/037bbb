package com.fwiptv

import android.content.Context
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.network.CloudflareKiller
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import com.lagradost.cloudstream3.utils.*
import java.net.URLEncoder
import kotlin.coroutines.cancellation.CancellationException

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
        val embed = FWIPParser.embed(document(data, fresh = true))
            ?: throw ErrorLoadingException("FWIP TV ไม่พบตัวเล่นของเรื่องนี้")
        var found = false
        // Registered extractors own host-specific playback. Never emit the embed HTML as video.
        try {
            loadExtractor(embed, data, subtitleCallback) { found = true; callback(it) }
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            if (!found) throw ErrorLoadingException("FWIP TV โหลดโฮสต์วิดีโอไม่ได้ ลองเปิดเรื่องนี้ในเว็บเพื่อตรวจสอบตัวเล่น")
        }
        if (!found) throw ErrorLoadingException("FWIP TV ยังไม่มีตัวอ่านที่เล่นโฮสต์วิดีโอนี้ได้ (${java.net.URI(embed).host})")
        return true
    }
}
