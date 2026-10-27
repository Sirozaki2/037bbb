package com.moviedofree

import android.content.Context
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import com.lagradost.cloudstream3.utils.*
import java.net.URLEncoder
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

@CloudstreamPlugin
class MovieDoFreePlugin : Plugin() {
    override fun load(context: Context) { registerMainAPI(MovieDoFreeProvider()) }
}

class MovieDoFreeProvider : MainAPI() {
    override var mainUrl = "https://moviedofree.com"
    override var name = "Moviedofree"
    override var lang = "th"
    override val hasMainPage = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.Movie)
    override var sequentialMainPage = false
    private val listingRequests = Semaphore(3)
    override val mainPage = SiteCategories.entries.map { (path, title) -> mainPage(mainUrl + path, title) }

    private fun MovieCard.response(): SearchResponse = newMovieSearchResponse(title, url, TvType.Movie) { posterUrl = poster; posterHeaders = mapOf("Referer" to "$mainUrl/") }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse = listingRequests.withPermit {
        val url = MovieDoFreeParser.pageUrl(request.data, page)
        val doc = app.get(url).document.apply { setBaseUri(url) }
        newHomePageResponse(listOf(HomePageList(request.name, MovieDoFreeParser.cards(doc).map { it.response() })), MovieDoFreeParser.hasNext(doc))
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/search_movie/?keyword=${URLEncoder.encode(query, "UTF-8")}"
        return MovieDoFreeParser.cards(app.get(url).document.apply { setBaseUri(url) }).map { it.response() }
    }

    override suspend fun load(url: String): LoadResponse {
        val detail = MovieDoFreeParser.detail(app.get(url).document.apply { setBaseUri(url) })
        return newMovieLoadResponse(detail.title, url, TvType.Movie, url) {
            posterUrl = detail.poster; posterHeaders = mapOf("Referer" to url); plot = detail.plot
        }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        val detail = MovieDoFreeParser.detail(app.get(data, cacheTime = 0).document.apply { setBaseUri(data) })
        val player = app.get(detail.player, referer = data, cacheTime = 0)
        val streams = MovieDoFreeParser.streams(player.text, player.url)
        for (url in streams) {
            callback(newExtractorLink(name, name, url, ExtractorLinkType.M3U8) {
                referer = player.url
                quality = Qualities.Unknown.value
            })
        }
        return streams.isNotEmpty()
    }
}
