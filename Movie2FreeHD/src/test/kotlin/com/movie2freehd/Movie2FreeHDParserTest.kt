package com.movie2freehd

import org.jsoup.Jsoup
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class Movie2FreeHDParserTest {
    private val base = "https://movie2freehd.com/"
    private fun fixture(name: String) = File("../samples/regression/movie2freehd.com/$name.html").readText().let {
        Jsoup.parse(it, Jsoup.parse(it).selectFirst("link[rel=canonical]")!!.attr("href"))
    }
    @Test fun `home grid has CSS posters and search uses its separate layout`() {
        for (name in listOf("home", "search")) {
            val cards = Movie2FreeHDParser.cards(fixture(name))
            assertEquals(3, cards.size)
            assertTrue(cards.all { it.title.isNotBlank() && it.url.startsWith(base) && it.poster?.startsWith("https://") == true })
        }
        assertTrue(Movie2FreeHDParser.hasNext(fixture("home")))
    }
    @Test fun `empty search excludes featured and sidebar recommendations`() {
        val doc = Jsoup.parse("""<div class="search-page"></div><aside><div class="movie-box"><h3 class="movie-title"><a href="/movies/ad/">Ad</a></h3></div></aside>""", base)
        assertTrue(Movie2FreeHDParser.cards(doc).isEmpty())
    }
    @Test fun `movie options use actual IDs and exclude trailer options`() {
        val doc = fixture("movie")
        assertFalse(Movie2FreeHDParser.detail(doc).series)
        assertEquals("61093", Movie2FreeHDParser.options(doc).single().post)
        doc.body().append("<li class=dooplay_player_option data-post=61093 data-nume=trailer data-type=movie></li>")
        assertEquals(1, Movie2FreeHDParser.options(doc).size)
    }
    @Test fun `series preserves ten real episodes and deduplicates identical language links`() {
        val detail = Movie2FreeHDParser.detail(fixture("series"))
        assertTrue(detail.series)
        assertEquals((1..10).toList(), detail.episodes.map { it.number })
        assertTrue(detail.episodes.all { it.season == 1 })
        assertEquals(listOf(1, 1, 1, 1, 1, 1, 2, 2, 1, 1), detail.episodes.map { it.players.size })
        assertEquals("พากย์ไทย / ซับไทย", detail.episodes.first().players.single().label)
        assertEquals(listOf("พากย์ไทย", "ซับไทย"), detail.episodes[6].players.map { it.label })
    }
    @Test fun `missing episode URLs are not published and seasons remain distinct`() {
        val doc = Jsoup.parse("""<h1 class=m2f-title>Series</h1>
            <div class=m2f-season-row><span class=m2f-season-label>Season 2</span>
                <button class=m2f-ep data-url="/play/2"><span class=m2f-ep-num>#1</span></button>
                <button class=m2f-ep data-url="javascript:alert(1)"><span class=m2f-ep-num>#2</span></button>
            </div><div class=m2f-season-row><span class=m2f-season-label>Season 1</span>
                <button class=m2f-ep data-sources='{"thai":"/play/1","sub":"/play/sub"}'><span class=m2f-ep-num>#1</span></button>
            </div>""", base + "series/test/")
        val episodes = Movie2FreeHDParser.detail(doc).episodes
        assertEquals(listOf(1, 2), episodes.map { it.season })
        assertEquals(2, episodes.first().players.size)
    }
    @Test fun `archive pagination retains filters and never uses broken root page route`() {
        assertEquals(base + "movies/page/2/", Movie2FreeHDParser.pageUrl(base + "movies/", 2))
        assertEquals(base + "series/page/3/?tab=series", Movie2FreeHDParser.pageUrl(base + "series/page/2/?tab=series", 3))
        assertEquals(base + "page/2/?s=avatar", Movie2FreeHDParser.pageUrl(base + "?s=avatar", 2))
    }
    @Test fun `invalid AJAX response does not become a video URL`() {
        assertNull(Movie2FreeHDParser.embed("<html>Blocked</html>", base))
        assertNull(Movie2FreeHDParser.embed("""{"embed_url":"javascript:alert(1)"}""", base))
        assertEquals("https://meeplayer.com/play/test", Movie2FreeHDParser.embed("""{"embed_url":"https:\/\/meeplayer.com\/play\/test"}""", base))
    }
}
