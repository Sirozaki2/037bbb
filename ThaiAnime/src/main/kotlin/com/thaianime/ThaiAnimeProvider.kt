package com.thaianime

import android.content.Context
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.network.CloudflareKiller
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import com.lagradost.cloudstream3.utils.*
import kotlin.coroutines.cancellation.CancellationException

@CloudstreamPlugin
class ThaiAnimePlugin : Plugin() {
    override fun load(context: Context) {
        Site.enabled.forEach { registerMainAPI(ThaiAnimeProvider(it)) }
    }
}

internal class ThaiAnimeProvider(private val site: Site) : MainAPI() {
    override var mainUrl = site.base
    override var name = site.label
    override var lang = "th"
    override val hasMainPage = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.Anime, TvType.AnimeMovie)
    override var sequentialMainPage = true
    override var sequentialMainPageDelay = 100L
    override val mainPage = AnimeCategories.homeEntries.getValue(site).map { (path, title) -> mainPage(mainUrl + path, title) }
    private val cloudflare by lazy { CloudflareKiller() }
    private suspend fun document(url: String, fresh: Boolean = false, referer: String = mainUrl): org.jsoup.nodes.Document {
        val response = if (fresh) app.get(url, referer = referer, interceptor = cloudflare, cacheTime = 0) else app.get(url, referer = referer, interceptor = cloudflare)
        if (response.code !in 200..299) throw ErrorLoadingException("$name โหลดหน้าเว็บไม่ได้ (HTTP ${response.code})")
        return response.document.apply { setBaseUri(response.url) }
    }
    private fun AnimeCard.response(): SearchResponse = newAnimeSearchResponse(title, url, if (movie) TvType.AnimeMovie else TvType.Anime) {
        posterUrl = poster; posterHeaders = mapOf("Referer" to "$mainUrl/")
        if (title.contains("พากย์ไทย")) addDubStatus(DubStatus.Dubbed)
        if (title.contains("ซับไทย") || !title.contains("พากย์ไทย")) addDubStatus(DubStatus.Subbed)
    }
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val doc = document(AnimeParser.pageUrl(site, request.data, page))
        return newHomePageResponse(listOf(HomePageList(request.name, AnimeParser.cards(site, doc).map { it.response() })), AnimeParser.next(site, doc) != null)
    }
    override suspend fun search(query: String): List<SearchResponse> = AnimeParser.cards(site, document(AnimeParser.searchUrl(site, query))).map { it.response() }
    override suspend fun load(url: String): LoadResponse {
        val detail = AnimeParser.detail(site, document(url))
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
    override fun getVideoInterceptor(extractorLink: ExtractorLink): okhttp3.Interceptor? {
        if (extractorLink.extractorData != "animemami-playlist") return null
        val host = runCatching { java.net.URI(extractorLink.url).host }.getOrNull() ?: return null
        return AnimeMamiInterceptor(host, cloudflare)
    }
    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        val visited = mutableSetOf<String>()
        val emitted = mutableSetOf<String>()
        val emit: (ExtractorLink) -> Unit = { if (emitted.add(it.url)) callback(it) }
        suspend fun resolve(source: PlayerSource, referer: String, depth: Int) {
            if (depth > 5 || !visited.add(source.url) || visited.size > 20) return
            try {
                if (site == Site.RUKA && AnimeMami.isPlayer(source.url)) {
                    val doc = document(source.url, fresh = true, referer = referer)
                    AnimeMami.stream(doc)?.let { stream ->
                        stream.subtitle?.let { subtitleCallback(newSubtitleFile(stream.subtitleLanguage, it)) }
                        emit(newExtractorLink(name, "$name - AnimeMami", stream.url,
                            if (stream.hls) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO) {
                            this.referer = doc.baseUri()
                            quality = Qualities.Unknown.value
                            if (stream.hls) extractorData = "animemami-playlist"
                        })
                    }
                } else if (java.net.URI(source.url).host == "app.akuma-stream.com") {
                    val doc = document(source.url, fresh = true, referer = referer)
                    AnimePlayer.akuma(doc)?.let { stream ->
                        emit(newExtractorLink(name, "$name - Akuma", stream, ExtractorLinkType.M3U8) {
                            this.referer = doc.baseUri()
                            quality = Qualities.Unknown.value
                        })
                    }
                } else if (site == Site.SUBA && SubaPlayer.isPlayer(source.url)) {
                    val resolver = SubaPlayer { url, ref ->
                        val response = app.get(url, referer = ref, interceptor = cloudflare, cacheTime = 0)
                        if (response.code !in 200..299) throw ErrorLoadingException("Suba player HTTP ${response.code}")
                        response.document.apply { setBaseUri(response.url) }
                    }
                    val streams = resolver.resolve(source.url, referer)
                    streams.forEach { stream -> emit(newExtractorLink(name, "$name - ${stream.label}", stream.url, ExtractorLinkType.VIDEO) {
                        this.referer = stream.referer
                        quality = Regex("\\d+").find(stream.label)?.value?.toIntOrNull() ?: Qualities.Unknown.value
                    }) }
                    if (streams.isEmpty()) loadExtractor(source.url, referer, subtitleCallback, emit)
                } else if (AnimePlayer.canUnwrap(source.url, site)) {
                    val doc = document(source.url, fresh = true, referer = referer)
                    val manifest = AnimePlayer.manifest(doc)
                    if (manifest != null) emit(newExtractorLink(name, "$name - ${source.label}", manifest, ExtractorLinkType.M3U8) {
                        this.referer = source.url; quality = Qualities.Unknown.value
                    })
                    else AnimePlayer.unwrap(doc).forEach { resolve(it.copy(label = source.label), source.url, depth + 1) }
                } else loadExtractor(source.url, referer, subtitleCallback, emit)
            } catch (error: Exception) { if (error is CancellationException) throw error }
        }
        suspend fun page(url: String, depth: Int) {
            if (depth > 1) return
            val doc = document(url, fresh = true)
            val detail = runCatching { AnimeParser.detail(site, doc) }.getOrNull()
            if (depth == 0 && detail?.movie == true && detail.episodes.isNotEmpty()) {
                detail.episodes.forEach { ep ->
                    try { page(ep.url, depth + 1) } catch (error: Exception) { if (error is CancellationException) throw error }
                }
                return
            }
            AnimePlayer.sources(site, doc).forEach { resolve(it, url, 0) }
            AnimePlayer.options(doc).forEach { option ->
                try {
                    val response = app.get(option.url, referer = url, interceptor = cloudflare, cacheTime = 0)
                    if (response.code == 200) AnimePlayer.ajaxEmbed(response.text, url)?.let { resolve(PlayerSource(it, option.label), url, 0) }
                } catch (error: Exception) { if (error is CancellationException) throw error }
            }
        }
        page(data, 0)
        if (emitted.isEmpty()) throw ErrorLoadingException("$name ไม่พบลิงก์ที่เล่นได้ โฮสต์วิดีโออาจบล็อกหรือยังไม่รองรับ ลองตัวเล่นอื่นในเว็บ")
        return true
    }
}
