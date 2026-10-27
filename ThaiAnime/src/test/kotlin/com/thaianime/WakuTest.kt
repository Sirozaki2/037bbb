package com.thaianime

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class WakuTest {
    private val root = File("../samples/Anime-Waku")
    private fun doc(name: String, frames: Boolean = false) = Samples.doc(File(root, name), frames)
    @Test fun `saved home and search return unique same-site anime without ads`() {
        val home = AnimeParser.cards(Site.WAKU, Samples.doc(root.listFiles()!!.first { it.name.startsWith("Anime-Waku") && it.isFile }))
        val search = AnimeParser.cards(Site.WAKU, doc("search.html"))
        assertTrue(home.isNotEmpty()); assertTrue(search.isNotEmpty())
        (home + search).forEach { assertTrue(java.net.URI(it.url).path.startsWith("/anime/") || java.net.URI(it.url).path.startsWith("/movies/"))
            assertEquals("anime-waku.com", java.net.URI(it.url).host) }
        assertTrue(home.all { it.poster?.startsWith("https://") == true })
        // Chrome rewrote search image URLs to disk paths without retaining remote originals.
        assertTrue(search.all { it.poster == null })
        val live = org.jsoup.Jsoup.parse("""<div class=search-page><div class=result-item><div class=title><a href=/anime/title/>Title</a></div><img src=/cover.jpg></div></div>""", Site.WAKU.base)
        assertEquals("https://anime-waku.com/cover.jpg", AnimeParser.cards(Site.WAKU, live).single().poster)
        assertEquals(home.size, home.map { it.url }.distinct().size)
        assertTrue(search.any { it.title.contains("Kikansha") })
        assertTrue(search.single { it.url.contains("/movies/") }.movie)
    }
    @Test fun `series preserves three real episodes in order and season two`() {
        val detail = AnimeParser.detail(Site.WAKU, doc("Series.html"))
        assertFalse(detail.movie)
        assertEquals(listOf(1, 2, 3), detail.episodes.map { it.number })
        assertTrue(detail.episodes.all { it.season == 2 && !it.dubbed })
        assertEquals("https://anime-waku.com/ep/113437/", detail.episodes.first().url)
    }
    @Test fun `two captured episodes resolve content wrapper bootstrap and REST fallback`() {
        for (episode in listOf(1, 2)) {
            val page = doc("episode$episode.html", true)
            val wrapper = AnimePlayer.sources(Site.WAKU, page).single().url
            assertTrue(AnimePlayer.canUnwrap(wrapper, Site.WAKU))
            val target = AnimePlayer.unwrap(doc("episode${episode}_files/saved_resource.html", true)).single().url
            val child = File(root, "episode${episode}_files").listFiles()!!.first { it.name.startsWith("mov_") }
            assertEquals(Samples.doc(child).baseUri(), target)
            assertTrue(AnimePlayer.manifest(Samples.doc(child))!!.startsWith("https://"))
            val options = AnimePlayer.options(page)
            assertEquals(1, options.size)
            assertTrue(options.single().url.startsWith("https://anime-waku.com/wp-json/dooplayer/v1/post/"))
            assertTrue(options.single().url.endsWith("/tv/1"))
        }
    }
    @Test fun `navigation matches supplied menus and WordPress pagination retains search`() {
        assertEquals("https://anime-waku.com/?s=a+b", AnimeParser.searchUrl(Site.WAKU, "a b"))
        assertEquals("https://anime-waku.com/page/2/?s=mahou", AnimeParser.pageUrl(Site.WAKU, "https://anime-waku.com/?s=mahou", 2))
        val home = Samples.doc(root.listFiles()!!.first { it.name.startsWith("Anime-Waku") && it.isFile })
        val links = home.select("a[href]").map { java.net.URI(it.attr("href")).rawPath }.toSet()
        assertTrue(AnimeCategories.homeEntries.getValue(Site.WAKU).all { it.first in links })
    }
}
