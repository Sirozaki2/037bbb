package com.twentyfivehd

import android.content.Context
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import com.lagradost.cloudstream3.utils.*
import java.net.URLEncoder

@CloudstreamPlugin
class TwentyFiveHDPlugin : Plugin() {
    override fun load(context: Context) { registerMainAPI(TwentyFiveHDProvider()) }
}

class TwentyFiveHDProvider : MainAPI() {
    override var mainUrl = "https://25-hd.com"
    override var name = "25-HD"
    override var lang = "th"
    override val hasMainPage = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)
    override var sequentialMainPage = true
    override var sequentialMainPageDelay = 100L
    override val mainPage = SiteCategories.entries.map { (path, title) -> mainPage(mainUrl + path, title) }

    private fun MovieCard.response(): SearchResponse = if (series) {
        newTvSeriesSearchResponse(title, url, TvType.TvSeries) { posterUrl = poster }
    } else {
        newMovieSearchResponse(title, url, TvType.Movie) { posterUrl = poster }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = if (page == 1) request.data else "${request.data.trimEnd('/')}/page/$page/"
        val doc = app.get(url).document.apply { setBaseUri(url) }
        return newHomePageResponse(listOf(HomePageList(request.name, TwentyFiveParser.cards(doc).map { it.response() })), TwentyFiveParser.hasNext(doc))
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/?s=${URLEncoder.encode(query, "UTF-8")}"
        return TwentyFiveParser.cards(app.get(url).document.apply { setBaseUri(url) }).map { it.response() }
    }

    override suspend fun load(url: String): LoadResponse {
        val detail = TwentyFiveParser.detail(app.get(url).document.apply { setBaseUri(url) })
        val bootstrap = ZmdbResolver.bootstrap(app.get(detail.embed, referer = url, cacheTime = 0).text)
        if (bootstrap.path("content").path("mediaType").asText() == "tv") {
            val episodes = ZmdbResolver.episodes(bootstrap, detail.embed).map {
                newEpisode(it.url) { name = it.name; season = it.season; episode = it.number }
            }
            return newTvSeriesLoadResponse(detail.title, url, TvType.TvSeries, episodes) {
                posterUrl = detail.poster; plot = detail.plot; tags = detail.tags
            }
        }
        return newMovieLoadResponse(detail.title, url, TvType.Movie, detail.embed) {
            posterUrl = detail.poster; plot = detail.plot; tags = detail.tags
        }
    }

    override fun getVideoInterceptor(extractorLink: ExtractorLink): okhttp3.Interceptor? =
        HlsRouting.decode(extractorLink.extractorData)?.let { HlsRoutingInterceptor(it) }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        val resolver = ZmdbResolver { url, referer, headers ->
            val response = app.get(url, referer = referer, headers = headers, cacheTime = 0)
            WebPage(response.url, response.text, response.code)
        }
        var found = false
        val emit: (ExtractorLink) -> Unit = { found = true; callback(it) }
        for (stream in resolver.resolve(data, "$mainUrl/")) {
            if (stream.hls) {
                val master = app.get(stream.url, referer = stream.referer, cacheTime = 0)
                val routing = HlsRouting.steeringUrl(stream.url, master.text)?.let { routingUrl ->
                    HlsRouting.fromManifest(stream.url, app.get(routingUrl, referer = stream.referer, cacheTime = 0).text)
                }
                emit(newExtractorLink(name, "$name - ${stream.label}", stream.url, ExtractorLinkType.M3U8) {
                    referer = stream.referer
                    extractorData = routing?.encode()
                    quality = Qualities.Unknown.value
                })
            } else loadExtractor(stream.url, stream.referer, subtitleCallback, emit)
        }
        return found
    }
}
