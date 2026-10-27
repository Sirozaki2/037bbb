package com.thaianime

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.junit.Assert.*
import org.junit.Test
import java.io.File

internal object Samples {
    private fun original(file: File) = Regex("saved from url=\\(\\d+\\)(.*?) -->").find(file.readText())?.groupValues?.get(1)
        ?: error("Missing original URL in ${file.name}")
    fun file(site: Site, prefix: String): File {
        val host = java.net.URI(site.base).host.removePrefix("www.")
        return File("../samples/$host").listFiles()!!.filter { it.isFile && it.name.startsWith(prefix) && it.extension == "html" }.sortedBy { it.name }.first()
    }
    fun doc(file: File, restoreFrames: Boolean = false): Document {
        val doc = Jsoup.parse(file.readText(), original(file))
        // Chrome rewrites iframe src to local files. Restore only URLs recorded by Chrome
        // in the corresponding saved child document; production never resolves disk paths.
        if (restoreFrames) doc.select("iframe[src]").forEach { frame ->
            val local = File(file.parentFile, frame.attr("src").removePrefix("./"))
            if (local.isFile) frame.attr("src", original(local))
        }
        return doc
    }
    fun doc(site: Site, prefix: String, restoreFrames: Boolean = false) = doc(file(site, prefix), restoreFrames)
}

class AnimeParserTest {
    @Test fun `all four home layouts exclude ads and return unique content links`() {
        val counts = mapOf(Site.NEKO to 24, Site.RUKA to 32, Site.SEVEN to 40, Site.SUBA to 36)
        counts.forEach { (site, count) ->
            val cards = AnimeParser.cards(site, Samples.doc(site, "home"))
            assertEquals(site.label, count, cards.size)
            assertEquals(cards.size, cards.map { it.url }.distinct().size)
            assertTrue(cards.all { it.title.isNotBlank() && !it.url.contains("_files/") })
            if (site == Site.SUBA || site == Site.SEVEN) assertTrue(site.label, cards.all { it.poster?.startsWith("https://") == true })
            if (site == Site.NEKO) {
                // Chrome removed original src for images without srcset. Do not invent remote URLs.
                val preserved = Samples.doc(site, "home").select("main li img[srcset]").size
                assertEquals(preserved, cards.count { it.poster != null })
            }
        }
    }
    @Test fun `search uses result containers and excludes Suba latest recommendations`() {
        assertEquals(0, AnimeParser.cards(Site.NEKO, Samples.doc(Site.NEKO, "search")).size)
        assertEquals(0, AnimeParser.cards(Site.SEVEN, Samples.doc(Site.SEVEN, "search")).size)
        assertEquals(28, AnimeParser.cards(Site.RUKA, Samples.doc(Site.RUKA, "search")).size)
        val suba = AnimeParser.cards(Site.SUBA, Samples.doc(Site.SUBA, "search"))
        assertEquals(1, suba.size)
        assertTrue(suba.single().title.startsWith("SD Gundam"))
    }
    @Test fun `episodes preserve real numbers seasons and separate audio tracks`() {
        val neko = AnimeParser.detail(Site.NEKO, Samples.doc(Site.NEKO, "series"))
        assertFalse(neko.movie); assertEquals((1..13).toList(), neko.episodes.map { it.number })
        val ruka = AnimeParser.detail(Site.RUKA, Samples.doc(Site.RUKA, "series"))
        assertEquals(20, ruka.episodes.size)
        assertEquals((1..10).toList(), ruka.episodes.filter { it.dubbed }.map { it.number })
        assertEquals((1..10).toList(), ruka.episodes.filterNot { it.dubbed }.map { it.number })
        assertTrue(ruka.episodes.all { it.season == 2 })
        val seven = AnimeParser.detail(Site.SEVEN, Samples.doc(Site.SEVEN, "series"))
        assertEquals(5, seven.episodes.size)
        assertEquals((1..4).toList(), seven.episodes.filterNot { it.dubbed }.map { it.number })
        assertEquals(listOf(1), seven.episodes.filter { it.dubbed }.map { it.number })
        val suba = AnimeParser.detail(Site.SUBA, Samples.doc(Site.SUBA, "series"))
        assertEquals((1..4).toList(), suba.episodes.map { it.number })
        assertTrue(suba.episodes.none { it.url.contains('#') || it.title.contains("ยังไม่มา") })
    }
    @Test fun `movies remain movies while preserving every available audio link`() {
        Site.entries.forEach { site ->
            val detail = AnimeParser.detail(site, Samples.doc(site, "movie"))
            assertTrue(site.label, detail.movie)
            assertTrue(detail.title.isNotBlank()); assertNotNull(site.label, detail.poster)
            if (site != Site.RUKA) assertEquals(if (site == Site.SEVEN) 2 else 1, detail.episodes.size)
        }
    }
    @Test fun `categories use site pagination and keep filters`() {
        Site.entries.forEach { site ->
            val f = Samples.file(site, "category")
            val doc = Samples.doc(f)
            assertTrue(site.label, AnimeParser.cards(site, doc).isNotEmpty())
            assertNotNull(site.label, AnimeParser.next(site, doc))
        }
        assertEquals("https://nekomiku.net/genre/action?page=3", AnimeParser.pageUrl(Site.NEKO, "https://nekomiku.net/genre/action?page=2", 3))
        assertEquals("https://suba-anime.com/search?value=a+b&search=&page=2", AnimeParser.pageUrl(Site.SUBA, "https://suba-anime.com/search?value=a+b&search=", 2))
        assertEquals("https://animeruka.com/genre/action/page/3/", AnimeParser.pageUrl(Site.RUKA, "https://animeruka.com/genre/action/page/2/", 3))
        assertEquals("https://www.anime-seven.com/catalog/15/sub.html&number=3", AnimeParser.pageUrl(Site.SEVEN, "https://www.anime-seven.com/catalog/15/sub.html&number=2", 3))
    }
    @Test fun `search form parameters are encoded without losing Thai or ampersands`() {
        Site.entries.forEach { site ->
            assertTrue(AnimeParser.searchUrl(site, "ไทย &").contains("%26"))
            assertFalse(AnimeParser.searchUrl(site, "ไทย &").contains(" "))
        }
        assertEquals("https://nekomiku.net/search?q=a+b", AnimeParser.searchUrl(Site.NEKO, "a b"))
        assertEquals("https://www.anime-seven.com/index.php?search=a+b", AnimeParser.searchUrl(Site.SEVEN, "a b"))
    }
    @Test fun `navigation paths come from supplied menus and are unique`() {
        Site.entries.forEach { site ->
            val links = Samples.doc(site, "home").select("a[href]").map { java.net.URI(it.attr("href")).rawPath }.toSet()
            val entries = AnimeCategories.entries.getValue(site)
            assertEquals(entries.size, entries.map { it.first }.distinct().size)
            assertTrue(site.label, entries.all { it.first in links })
        }
    }
    @Test fun `live Neko card image resolves from relative src`() {
        val doc = Jsoup.parse("""<main><ul><li><a href="/anime/1"><img src="/cover.jpg"><p>Title</p></a></li></ul></main>""", Site.NEKO.base)
        assertEquals("https://nekomiku.net/cover.jpg", AnimeParser.cards(Site.NEKO, doc).single().poster)
        val movie = Jsoup.parse("""<div class=items><article class="item movies"><div class=poster><a href="/movies/title/"><h3>Title without movie suffix</h3></a></div></article></div>""", Site.RUKA.base)
        assertTrue(AnimeParser.cards(Site.RUKA, movie).single().movie)
    }
    @Test fun `invalid missing offline and placeholder URLs are rejected`() {
        listOf("", "#", "javascript:alert(1)", "blob:https://host/id", "./episode_files/video.html", "/anime/#+").forEach {
            assertNull(it, AnimeParser.url(Site.NEKO.base, it))
        }
    }
}
