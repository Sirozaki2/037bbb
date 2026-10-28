package com.twentyfourhd

import org.jsoup.Jsoup
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class TwentyFourHDParserTest {
    private fun fixture(name: String) = File("../samples/regression/24hd.today/$name.html").readText().let { html ->
        val canonical = Jsoup.parse(html).selectFirst("link[rel=canonical]")?.attr("href") ?: "https://www.24hd.today/?s=dfog"
        Jsoup.parse(html, canonical)
    }

    @Test fun `card CSS is associated with its own poster`() {
        val cards = TwentyFourHDParser.cards(fixture("home"))
        assertEquals(36, cards.size)
        assertTrue(cards.all { it.poster?.startsWith("https://") == true })
        assertEquals(cards.size, cards.map { it.poster }.distinct().size)
        assertTrue(cards.first { it.url.endsWith("tow-2026/") }.poster!!.contains("Tow-2026"))
        assertTrue(TwentyFourHDParser.hasNext(fixture("home")))
        assertEquals("https://www.24hd.today/?e-page-4a0b95c=2", TwentyFourHDParser.pageUrl(fixture("home"), 2))
    }

    @Test fun `category tag and alternate search layout parsed`() {
        for (name in listOf("catagory", "tag")) {
            val cards = TwentyFourHDParser.cards(fixture(name))
            assertTrue(name, cards.isNotEmpty())
            assertTrue(cards.all { it.title.isNotBlank() && it.url.startsWith("https://www.24hd.today/") })
        }
    }

    @Test fun `empty search does not return sidebar latest titles`() {
        assertTrue(TwentyFourHDParser.cards(fixture("search")).isEmpty())
    }

    @Test fun `only nineteen actually available episodes returned despite title range thirty three`() {
        val d = TwentyFourHDParser.detail(fixture("series"))
        assertEquals((1..19).toList(), d.episodes.map { it.number })
        assertEquals(19, d.episodes.flatMap { it.players }.distinct().size)
        assertTrue(d.episodes[11].players.single().endsWith("/Tos4B_Y7FI-iy"))
        assertTrue(d.plot!!.contains("อาโฉ่ว"))
        assertEquals("พากย์ไทย", d.audio)
    }

    @Test fun `movie excludes trailers and deduplicates saved original iframe`() {
        val d = TwentyFourHDParser.detail(fixture("movie"))
        assertTrue(d.episodes.isEmpty())
        assertEquals(listOf("https://player77hdfree.xyz/embed/0DqKh-s8Zr_AT"), d.players)
        assertTrue(d.plot!!.contains("ลูกชายถูกลักพาตัว"))
    }

    @Test fun `playlist uses actual player config excluding ad URLs`() {
        val doc = Jsoup.parse("""<script>window.PLAYER_CONFIG = {"adverts":{"mp4Url":"https://ads.invalid/ad.mp4"},"vdoId":"real_ID","node":{"playlist":"vdohls.com"}};</script>""")
        assertEquals("https://vdohls.com/real_ID/playlist.m3u8", TwentyFourHDParser.stream(doc))
        assertNull(TwentyFourHDParser.stream(Jsoup.parse("<script>window.PLAYER_CONFIG = {};</script>")))
    }

    @Test fun `backup players belong to same episode and offline paths excluded`() {
        val doc = Jsoup.parse("""<h1>Series</h1><button class=swicth-ep data-link="https://host/first" data-link2="https://backup/second">EP.2</button><iframe name=box-player src="./movie_files/saved_resource.html"></iframe>""", "https://www.24hd.today/series")
        val d = TwentyFourHDParser.detail(doc)
        assertEquals(2, d.episodes.single().number)
        assertEquals(2, d.episodes.single().players.size)
        assertTrue(d.players.isEmpty())
    }

    @Test fun `pagination retains query after path`() {
        assertEquals("https://www.24hd.today/page/2/?s=reacher", TwentyFourHDParser.pageUrl("https://www.24hd.today/?s=reacher", 2))
    }

    @Test fun `all category and franchise menu links retained`() {
        val paths = fixture("home").select("a[href]").map { it.attr("href").removePrefix("https://www.24hd.today").trimEnd('/') }
            .filter { it.startsWith("/category/") || it.startsWith("/tag/") }.toSet()
        assertEquals(paths, SiteCategories.entries.map { it.first }.filter { it.isNotBlank() }.toSet())
    }

    @Test fun `load more pagination accepts relative next URL and stops at the last page`() {
        val doc = Jsoup.parse("""<div class=elementor-widget-loop-grid><div data-next-page="?e-page-grid=2"></div></div>""", "https://www.24hd.today/category/action/")
        assertTrue(TwentyFourHDParser.hasNext(doc))
        assertEquals("https://www.24hd.today/category/action/?e-page-grid=3", TwentyFourHDParser.pageUrl(doc, 3))
        doc.selectFirst("[data-next-page]")!!.attr("data-next-page", "")
        assertFalse(TwentyFourHDParser.hasNext(doc))
    }
    @Test fun `lazy image fallback skips data placeholder without card CSS`() {
        val doc = Jsoup.parse("""<div class=elementor-widget-loop-grid><div class=e-loop-item>
            <a class=e-con href="/series/"><img src="data:image/gif;base64,AA" data-src="/poster.jpg"></a>
            <h3 class=elementor-heading-title>Series EP. 1</h3>
        </div></div>""", "https://www.24hd.today/")
        assertEquals("https://www.24hd.today/poster.jpg", TwentyFourHDParser.cards(doc).single().poster)
    }

    @Test fun `category pagination replaces old page path and keeps search filter`() {
        assertEquals("https://www.24hd.today/category/action/page/3/?s=movie",
            TwentyFourHDParser.pageUrl("https://www.24hd.today/category/action/page/2/?s=movie", 3))
    }

    @Test fun `duplicate episode rows retain every distinct content player`() {
        val doc = Jsoup.parse("""<h1>Show</h1>
            <button class=swicth-ep data-link="https://player.example/main">EP.2</button>
            <button class=swicth-ep data-link="https://player.example/backup" data-link2="https://player.example/main">EP.2</button>""", "https://www.24hd.today/show/")
        assertEquals(listOf("https://player.example/main", "https://player.example/backup"), TwentyFourHDParser.detail(doc).episodes.single().players)
    }

    @Test fun `poster index distinguishes adjacent card IDs and grouped CSS selectors`() {
        val doc = Jsoup.parse("""<style>
            .e-loop-item-10 .poster {background-image:url('/ten.jpg')}
            .e-loop-item-1 .poster, .e-loop-item-2 .poster {background-image:url('/shared.jpg')}
            </style><div class=elementor-widget-loop-grid>
            <div class="e-loop-item e-loop-item-1"><a class=e-con href=/one/><h2 class=elementor-heading-title>One</h2></a></div>
            <div class="e-loop-item e-loop-item-10"><a class=e-con href=/ten/><h2 class=elementor-heading-title>Ten</h2></a></div>
            <div class="e-loop-item e-loop-item-2"><a class=e-con href=/two/><h2 class=elementor-heading-title>Two</h2></a></div>
            </div>""", "https://www.24hd.today/")
        assertEquals(listOf("https://www.24hd.today/shared.jpg", "https://www.24hd.today/ten.jpg", "https://www.24hd.today/shared.jpg"), TwentyFourHDParser.cards(doc).map { it.poster })
    }
}
