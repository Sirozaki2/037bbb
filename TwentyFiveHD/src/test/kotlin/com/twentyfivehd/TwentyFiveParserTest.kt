package com.twentyfivehd

import org.jsoup.Jsoup
import org.junit.Assert.*
import org.junit.Test

class TwentyFiveParserTest {
    @Test fun `cards use movie titles not language badges and distinguish series`() {
        val doc = Jsoup.parse("""
            <a href="https://ads.example"><img src="/ad.gif"></a>
            <div class="main-movie">
            <div class="movie_box"><a href="/film/" aria-label="A movie"><img src="/poster.jpg"><p>A movie</p><span class="movie-lang">ซับไทย</span></a></div>
            <div class="movie_box"><a href="/show/"><img src="/show.jpg"><p>A show</p><span class="box-movie-episode">EP 19/30</span></a></div>
            <div class="movie_box"><a href="/film/" aria-label="A movie"></a></div>
            <a class="next page-numbers" href="/page/2/">Next</a>
            </div>
        """, "https://25-hd.com/")
        val cards = TwentyFiveParser.cards(doc)
        assertEquals(listOf("A movie", "A show"), cards.map { it.title })
        assertEquals(listOf(false, true), cards.map { it.series })
        assertEquals("https://25-hd.com/poster.jpg", cards.first().poster)
        assertTrue(TwentyFiveParser.hasNext(doc))
    }

    @Test fun `pagination excludes featured cards repeated above the main movie grid`() {
        fun page(number: Int) = Jsoup.parse("""
            <div class="grid-movie-ct"><div class="movie_box"><a href="/featured/" aria-label="Featured"></a></div></div>
            <div class="main-movie"><div class="grid-movie-index"><div class="movie_box">
                <a href="/page-$number-movie/" aria-label="Page $number movie"></a>
            </div></div></div>
        """, "https://25-hd.com/page/$number/")
        assertEquals("Page 1 movie", TwentyFiveParser.cards(page(1)).single().title)
        assertEquals("Page 2 movie", TwentyFiveParser.cards(page(2)).single().title)
    }

    @Test fun `detail reads the lazy embed and actual synopsis rather than ads or header branding`() {
        val doc = Jsoup.parse("""
            <meta property="og:title" content="A movie"><h1>Site branding</h1>
            <div class="thumb-img"><img src="/poster.jpg"></div>
            <div class="movie-content"><p>Actual synopsis.</p></div>
            <div class="movie-tags"><a>Action</a></div>
            <iframe src="https://ads.example"></iframe>
            <iframe id="pe-iframe" src="" data-original-src="https://zmdb.net/embed?id=123&amp;type=movie"></iframe>
        """, "https://25-hd.com/film/")
        val detail = TwentyFiveParser.detail(doc)
        assertEquals("A movie", detail.title)
        assertEquals("Actual synopsis.", detail.plot)
        assertEquals("https://zmdb.net/embed?id=123&type=movie", detail.embed)
        assertEquals(listOf("Action"), detail.tags)
    }

    @Test fun `episode list excludes unreleased entries and preserves season episode identities`() {
        val json = ZmdbResolver.bootstrap("""<script id="bootstrap" type="application/json">{"content":{"seasons":[
            {"seasonNumber":2,"episodes":[{"episodeNumber":3,"hasLinks":true},{"episodeNumber":4}]},
            {"seasonNumber":1,"episodes":[{"episodeNumber":1,"playerEmbedLinks":[{"language":"ไทย"}]}]}
        ]}}</script>""")
        val episodes = ZmdbResolver.episodes(json, "https://zmdb.net/embed?id=123&type=tv")
        assertEquals(listOf(1, 2), episodes.map { it.season })
        assertEquals(listOf(1, 3), episodes.map { it.number })
        assertEquals("https://zmdb.net/embed?id=123&type=tv&season=2&episode=3", episodes.last().url)
        assertFalse(episodes.last().url.contains("token"))
    }

    @Test fun `lazy poster loads real URL for cards and detail`() {
        val doc = Jsoup.parse("""<div class=main-movie><div class=movie_box><a href="/film/" aria-label="Movie">
            <img src="data:image/gif;base64,AA" data-src="/real.jpg"></a></div></div>
            <meta property=og:title content="Movie"><div class=thumb-img><img src="data:image/gif;base64,AA" data-lazy-src="/detail.jpg"></div>
            <div class=fake-player-container data-embed-url="https://zmdb.net/embed?id=1"></div>""", "https://25-hd.com/")
        assertEquals("https://25-hd.com/real.jpg", TwentyFiveParser.cards(doc).single().poster)
        assertEquals("https://25-hd.com/detail.jpg", TwentyFiveParser.detail(doc).poster)
    }
    @Test fun `year menu labels match their destination year`() {
        for ((path, label) in SiteCategories.entries.filter { it.first.startsWith("/years/") }) {
            assertTrue("$label points to $path", label.contains(path.substringAfterLast('/')))
        }
    }

    @Test fun `disabled and current page controls do not request another page`() {
        val base = "https://25-hd.com/page/2/"
        for (href in listOf("", "#", "#top", base, "javascript:void(0)")) {
            val doc = org.jsoup.Jsoup.parse("""<div class=pagination><a class="next page-numbers" href="$href">Next</a></div>""", base)
            assertFalse(href, TwentyFiveParser.hasNext(doc))
        }
        val doc = org.jsoup.Jsoup.parse("""<div class=pagination><a class="next page-numbers" href="/page/3/">Next</a></div>""", base)
        assertTrue(TwentyFiveParser.hasNext(doc))
    }

    @Test fun `pagination replaces an existing page while preserving category filters`() {
        assertEquals("https://25-hd.com/category/action/page/3/?year=2024&lang=th",
            TwentyFiveParser.pageUrl("https://25-hd.com/category/action/page/2/?year=2024&lang=th", 3))
    }
}
