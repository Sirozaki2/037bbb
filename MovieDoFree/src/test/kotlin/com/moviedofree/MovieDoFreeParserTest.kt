package com.moviedofree

import org.jsoup.Jsoup
import org.junit.Assert.*
import org.junit.Test

class MovieDoFreeParserTest {
    private val base = "https://moviedofree.com/"

    @Test fun `reads movie posters not IMDb stars and excludes sidebar cards`() {
        val card = """<div class="movie-box"><div class="movie-title"><a href="/movie/">A movie</a></div>
            <div class="movie-imdb"><img src="/star.png"></div><div class="movie-image"><img src="/poster.jpg"></div></div>"""
        val doc = Jsoup.parse("<aside>$card</aside><div class='column_1_center'><div class='row'>$card$card</div></div>", base)
        val cards = MovieDoFreeParser.cards(doc)
        assertEquals(1, cards.size)
        assertEquals("A movie", cards.single().title)
        assertEquals(base + "poster.jpg", cards.single().poster)
        assertEquals(base + "movie/", cards.single().url)
    }

    @Test fun `uses paginated grid after repeated featured movie row`() {
        fun card(url: String, title: String) = """<div class="movie-box"><div class="movie-title"><a href="$url">$title</a></div></div>"""
        val doc = Jsoup.parse("""<div class="column_1_center">
            <div class="row">${card("/featured/", "Featured")}</div>
            <div class="row">${card("/page2/", "Page two movie")}</div>
            <div class="row"><a href="https://ads.example">Ad</a></div>
        </div>""", base)
        assertEquals("Page two movie", MovieDoFreeParser.cards(doc).single().title)
    }

    @Test fun `empty search does not return sidebar recommendations`() {
        val doc = Jsoup.parse("""<div class="column_1_center">ไม่พบข้อมูล</div>
            <aside><div class="movie-box"><div class="movie-title"><a href="/recommended/">Recommended</a></div></div></aside>""", base)
        assertTrue(MovieDoFreeParser.cards(doc).isEmpty())
        assertFalse(MovieDoFreeParser.hasNext(doc))
    }

    @Test fun `detail selects main player and leaves missing synopsis empty`() {
        val doc = Jsoup.parse("""<h1>Site name</h1><div class="column_1_center">
            <div class="box-header"><div class="title"><h1>A movie</h1></div></div><center><img src="/poster.jpg"></center>
            <iframe src="https://ads.example"></iframe><iframe id="frame" src="/play.php?vid=M1"></iframe>
        </div>""", base)
        val detail = MovieDoFreeParser.detail(doc)
        assertEquals("A movie", detail.title)
        assertEquals(base + "poster.jpg", detail.poster)
        assertEquals(base + "play.php?vid=M1", detail.player)
        assertNull(detail.plot)
    }

    @Test fun `extracts signed main movie HLS and ignores advertisement video`() {
        val html = """
            jwplayer('adsplayer').setup({sources:[{file:'https://ads.example/ad.m3u8'}]});
            jwplayer("jwplayer").setup({sources:[{file:'/movie/master.m3u8?sig=synthetic',label:'Default'}]});
        """
        assertEquals(listOf(base + "movie/master.m3u8?sig=synthetic"), MovieDoFreeParser.streams(html, base + "play.php?vid=M1"))
        assertTrue(MovieDoFreeParser.streams("<h1>File unavailable</h1>", base).isEmpty())
    }

    @Test fun `year pagination retains the year query after the page path`() {
        assertEquals(base + "fillter-year/page/2/?y=2024", MovieDoFreeParser.pageUrl(base + "fillter-year?y=2024", 2))
        assertEquals(base + "netflix/page/3/", MovieDoFreeParser.pageUrl(base + "netflix/", 3))
    }

    @Test fun `lazy poster uses the real image rather than an inline placeholder`() {
        val doc = Jsoup.parse("""<div class="column_1_center"><div class="row"><div class="movie-box">
            <div class="movie-title"><a href="/movie/">Movie</a></div>
            <div class="movie-image"><img src="data:image/gif;base64,AA" data-src="/real.jpg"></div>
        </div></div></div>""", base)
        assertEquals(base + "real.jpg", MovieDoFreeParser.cards(doc).single().poster)
    }
}
