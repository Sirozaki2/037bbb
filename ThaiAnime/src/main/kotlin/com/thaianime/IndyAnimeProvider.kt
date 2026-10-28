package com.thaianime

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.providers.network.withBrowserFallback
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.jsoup.nodes.Document
import kotlin.coroutines.cancellation.CancellationException

internal class IndyAnimeProvider : MainAPI() {
    override var mainUrl = IndyParser.base
    override var name = "Indy-Anime"
    override var lang = "th"
    override val hasMainPage = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.Anime, TvType.AnimeMovie)
    override var sequentialMainPage = false
    private val listingRequests = Semaphore(3)
    override val mainPage = IndyParser.categories.map { (path, title) -> mainPage(mainUrl + path, title) }
    private suspend fun document(url: String, referer: String = mainUrl, fresh: Boolean = false): Document {
        val response = if (fresh) app.get(url, referer = referer, interceptor = AnimeNetwork.browserSession, cacheTime = 0)
            else app.get(url, referer = referer, interceptor = AnimeNetwork.browserSession)
        if (response.code !in 200..299) throw ErrorLoadingException("Indy-Anime โหลดหน้าเว็บไม่ได้ (HTTP ${response.code})")
        return response.document.apply { setBaseUri(response.url) }
    }
    private fun AnimeCard.response(): SearchResponse = newAnimeSearchResponse(title, url, if (movie) TvType.AnimeMovie else TvType.Anime) {
        posterUrl = poster
        posterHeaders = mapOf("Referer" to "$mainUrl/")
        addDubStatus(if (title.contains("พากย์ไทย")) DubStatus.Dubbed else DubStatus.Subbed)
    }
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse = listingRequests.withPermit {
        val doc = document(IndyParser.pageUrl(request.data, page))
        newHomePageResponse(listOf(HomePageList(request.name, IndyParser.cards(doc).map { it.response() })), IndyParser.hasNext(doc))
    }
    override suspend fun search(query: String): List<SearchResponse> = IndyParser.cards(document(IndyParser.searchUrl(query))).map { it.response() }
    override suspend fun load(url: String): LoadResponse {
        val detail = IndyParser.detail(document(url))
        if (detail.movie) return newMovieLoadResponse(detail.title, url, TvType.AnimeMovie, url) {
            posterUrl = detail.poster; plot = detail.plot; tags = detail.tags; posterHeaders = mapOf("Referer" to url)
        }
        return newAnimeLoadResponse(detail.title, url, TvType.Anime) {
            posterUrl = detail.poster; plot = detail.plot; tags = detail.tags; posterHeaders = mapOf("Referer" to url)
            detail.episodes.groupBy { if (it.dubbed) DubStatus.Dubbed else DubStatus.Subbed }.forEach { (dub, episodes) ->
                addEpisodes(dub, episodes.map { ep -> newEpisode(ep.url) { name = ep.title; episode = ep.number; season = ep.season } })
            }
        }
    }
    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean =
        withBrowserFallback(data, callback) { emit ->
            val emitted = mutableSetOf<String>()
            val unique: (ExtractorLink) -> Unit = { if (emitted.add(it.url)) emit(it) }
            suspend fun resolve(doc: Document) = IndyPlayer.resolve(doc,
                get = { url, referer -> document(url, referer, fresh = true) },
                extract = { source, referer -> loadExtractor(source.url, referer, subtitleCallback, unique); Unit },
                emit = { stream -> unique(newExtractorLink(name, "$name - ${stream.label}", stream.url, ExtractorLinkType.VIDEO) {
                    referer = stream.referer; quality = Qualities.Unknown.value
                }) },
            )
            val doc = document(data, fresh = true)
            if (IndyPlayer.sources(doc).isNotEmpty()) resolve(doc)
            else {
                // Movies have a separate /watch/ page; never invent the episode URL from a title.
                val detail = IndyParser.detail(doc)
                if (detail.movie) detail.episodes.forEach { episode ->
                    try { resolve(document(episode.url, data, fresh = true)) } catch (error: Exception) {
                        if (error is CancellationException) throw error
                    }
                }
            }
            if (emitted.isEmpty()) throw ErrorLoadingException("Indy-Anime ตัวเล่นที่เว็บระบุใช้ไม่ได้หรือยังไม่รองรับ ลองเลือกเซิร์ฟเวอร์ในเว็บสำรอง")
            true
        }
}
