package com.fwiptv

import org.jsoup.Jsoup
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class FWIPParserTest {
    private fun fixture(name: String) = File("../samples/fwip-tv.com/$name.html").readText().let {
        Jsoup.parse(it, Jsoup.parse(it).selectFirst("link[rel=canonical]")?.attr("href") ?: "https://fwip-tv.com/?s=")
    }
    @Test fun `all archived layouts have titled cards and real lazy posters`() {
        for (name in listOf("home", "search", "category", "country", "year", "audio")) {
            val cards = FWIPParser.cards(fixture(name))
            assertTrue(name, cards.isNotEmpty())
            assertEquals(cards.size, cards.map { it.url }.distinct().size)
            assertTrue(cards.all { it.poster?.startsWith("https://") == true && !it.poster.contains("_files/") })
        }
    }
    @Test fun `series contains nine real episodes in season one`() {
        val detail = FWIPParser.detail(fixture("series"))
        assertTrue(detail.series)
        assertEquals((1..9).toList(), detail.episodes.map { it.number })
        assertTrue(detail.episodes.all { it.season == 1 && it.url.contains("/ep/") })
        assertNotNull(detail.plot)
        assertNotNull(detail.poster)
    }
    @Test fun `movie and episode select content embed rather than trailer and preroll ads`() {
        assertFalse(FWIPParser.detail(fixture("movie")).series)
        assertEquals("https://nom-na.player123up.xyz/embed/94686938-6220-4604-83ae-b813d2217b7b", FWIPParser.embed(fixture("movie")))
        assertEquals("https://nom-na.player123up.xyz/embed/8644cd2e-d793-436e-8614-816c21a83848", FWIPParser.embed(fixture("episode")))
        assertNull(FWIPParser.embed(fixture("series")))
    }
    @Test fun `empty archive excludes sidebar and related titles`() {
        val doc = Jsoup.parse("""<div class=movie-home><div id=movie-archive-grid></div><aside><article class=movie-card><h3 class=movie-card__title><a href="/ad/">Ad</a></h3></article></aside></div>""", "https://fwip-tv.com/")
        assertTrue(FWIPParser.cards(doc).isEmpty())
    }
    @Test fun `search pagination preserves query and supplied next link`() {
        assertEquals("https://fwip-tv.com/page/2/?s=", FWIPParser.pageUrl("https://fwip-tv.com/?s=", 2))
        assertTrue(FWIPParser.hasNext(fixture("search")))
    }
    @Test fun `captured category paths are included once`() {
        val paths = fixture("home").select("a[href]").map { java.net.URLDecoder.decode(it.attr("href").removePrefix("https://fwip-tv.com").trimEnd('/'), "UTF-8") }
            .filter { it.startsWith("/genre/") || it.startsWith("/year/") || it.startsWith("/country/") || it.startsWith("/audio/") }.toSet()
        assertEquals(paths, SiteCategories.entries.map { it.first }.filter { !it.startsWith("/?") }.toSet())
        assertEquals(SiteCategories.entries.size, SiteCategories.entries.map { it.first }.distinct().size)
    }
}
