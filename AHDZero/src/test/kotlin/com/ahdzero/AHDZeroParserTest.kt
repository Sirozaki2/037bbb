package com.ahdzero

import org.jsoup.Jsoup
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class AHDZeroParserTest {
    private fun fixture(name: String) = File("../samples/ahdzero.com/$name.html").readText().let { html ->
        val canonical = Jsoup.parse(html).selectFirst("link[rel=canonical]")!!.attr("href")
        Jsoup.parse(html, canonical)
    }

    @Test fun `home uses paginated updates instead of repeating carousel`() {
        val cards = AHDZeroParser.cards(fixture("home"))
        assertEquals(24, cards.size)
        assertEquals(cards.size, cards.map { it.url }.distinct().size)
        assertTrue(cards.all { it.poster?.startsWith("https://") == true && !it.poster.contains("_files/") })
        assertTrue(AHDZeroParser.hasNext(fixture("home"), 1))
    }

    @Test fun `search and category parse actual cards`() {
        for (file in listOf("search", "category")) {
            val cards = AHDZeroParser.cards(fixture(file))
            assertTrue(file, cards.isNotEmpty())
            assertTrue(cards.all { it.url.startsWith("https://ahdzero.com/anime/") && it.title.isNotBlank() })
        }
    }

    @Test fun `series retains all eight episode identities and synopsis`() {
        val d = AHDZeroParser.detail(fixture("series"))
        assertFalse(d.movie)
        assertEquals((1..8).toList(), d.episodes.map { it.number })
        assertEquals(8, d.episodes.map { it.url }.distinct().size)
        assertTrue(d.plot!!.contains("โอโตสึมิ"))
        assertTrue(d.episodes[1].url.endsWith("/episode/118084"))
    }

    @Test fun `movie is a movie with its actual episode URL`() {
        val d = AHDZeroParser.detail(fixture("movie"))
        assertTrue(d.movie)
        assertEquals(1, d.episodes.size)
        assertTrue(d.episodes.single().url.endsWith("/episode/119597"))
        assertTrue(d.plot!!.contains("Saotome"))
    }

    @Test fun `episode recovers original VideoObject embed not saved iframe or advertisement`() {
        val embed = AHDZeroParser.embed(fixture("episode"))
        assertEquals("https://app.akuma-stream.com/watch/b028921c-ff06-43ca-a12f-fdc8da899802", embed)
        val doc = Jsoup.parse("""<iframe src="https://ads.invalid/ad"></iframe><media-player><media-provider><source src="/api/master.m3u8?token=fresh"></media-provider></media-player>""", embed!!)
        assertEquals("https://app.akuma-stream.com/api/master.m3u8?token=fresh", AHDZeroParser.stream(doc))
    }

    @Test fun `pagination preserves genre and search query`() {
        assertEquals("https://ahdzero.com/search/results?q=one&page=3", AHDZeroParser.pageUrl("https://ahdzero.com/search/results?q=one&page=2", 3))
        assertEquals("https://ahdzero.com/category/3?page=2", AHDZeroParser.pageUrl("https://ahdzero.com/category/3", 2))
    }

    @Test fun `all supplied navigation paths included once`() {
        val paths = fixture("home").select("a[href]").map { it.attr("href").removePrefix("https://ahdzero.com").trimEnd('/') }
            .filter { it.startsWith("/genre/") || it.startsWith("/category/") }.toSet()
        assertEquals(paths, SiteCategories.entries.map { it.first }.filter { it.isNotBlank() }.toSet())
    }
}
