package com.movie2free

import android.content.Context
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.network.CloudflareKiller
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import com.lagradost.cloudstream3.utils.*
import java.net.URLEncoder

@CloudstreamPlugin
class Movie2FreePlugin : Plugin() {
    override fun load(context: Context) { registerMainAPI(Movie2FreeProvider()) }
}

class Movie2FreeProvider : MainAPI() {
    override var mainUrl = "https://movie2free.co"
    override var name = "Movie2Free.co"
    override var lang = "th"
    override val hasMainPage = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.Movie)
    override var sequentialMainPage = true
    override var sequentialMainPageDelay = 100L
    override val mainPage = listOf(
        "/" to "หนังล่าสุด", "/recommended-new-movies/" to "หนังแนะนำ", "/top-imdb/" to "คะแนน IMDb สูง",
        "/thai-movies/" to "หนังไทย", "/netflix/" to "Netflix", "/action/" to "แอคชั่น",
        "/drama/" to "ดราม่า", "/horror/" to "สยองขวัญ", "/comedy/" to "ตลก",
        "/sci-fi/" to "ไซไฟ", "/thriller/" to "ระทึกขวัญ", "/cartoon/" to "การ์ตูน",
    ).map { (path, title) -> mainPage(mainUrl + path, title) }
    private val cloudflare by lazy { CloudflareKiller() }
    private suspend fun document(url: String, referer: String = mainUrl, fresh: Boolean = false): org.jsoup.nodes.Document {
        val result = if (fresh) app.get(url, referer = referer, interceptor = cloudflare, cacheTime = 0)
            else app.get(url, referer = referer, interceptor = cloudflare)
        if (result.code !in 200..299) throw ErrorLoadingException("Movie2Free.co ไม่พร้อมใช้งาน (HTTP ${result.code})")
        return result.document.apply { setBaseUri(result.url) }
    }
    private fun Card.response(): SearchResponse = newMovieSearchResponse(title, url, TvType.Movie) {
        posterUrl = poster; posterHeaders = mapOf("Referer" to "$mainUrl/")
    }
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val doc = document(Movie2FreeParser.pageUrl(request.data, page))
        return newHomePageResponse(listOf(HomePageList(request.name, Movie2FreeParser.cards(doc).map { it.response() })), Movie2FreeParser.hasNext(doc))
    }
    override suspend fun search(query: String): List<SearchResponse> {
        if (query.isBlank()) return emptyList()
        val response = app.get("$mainUrl/wp-admin/admin-ajax.php?action=movie_search_ajax&q=${URLEncoder.encode(query.trim(), "UTF-8")}", interceptor = cloudflare)
        if (response.code !in 200..299) throw ErrorLoadingException("Movie2Free.co ค้นหาไม่สำเร็จ (HTTP ${response.code})")
        return Movie2FreeParser.searchResults(response.text, mainUrl).map { it.response() }
    }
    override suspend fun load(url: String): LoadResponse {
        val detail = Movie2FreeParser.detail(document(url))
        return newMovieLoadResponse(detail.title, url, TvType.Movie, url) {
            posterUrl = detail.poster; posterHeaders = mapOf("Referer" to url)
            plot = detail.plot; year = detail.year; tags = detail.tags
        }
    }
    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        val embed = Movie2FreeParser.embed(document(data, fresh = true))
            ?: throw ErrorLoadingException("Movie2Free.co ไม่พบตัวเล่นของเรื่องนี้")
        var found = false
        Movie2FreeResolver.resolve(embed, data, { url, parent -> document(url, parent, fresh = true) }, { stream ->
            callback(newExtractorLink(name, "$name ${stream.label}", stream.url, ExtractorLinkType.M3U8) {
                referer = stream.referer; quality = Qualities.Unknown.value
            })
            found = true
        }, { url, parent -> loadExtractor(url, parent, subtitleCallback) { callback(it); found = true } })
        if (!found) throw ErrorLoadingException("Movie2Free.co ยังไม่มีลิงก์ที่เล่นได้ ลองตัวเล่นบนเว็บไซต์อีกครั้ง")
        return true
    }
}
