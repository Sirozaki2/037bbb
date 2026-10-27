package com.example

import org.jsoup.Jsoup
import org.junit.Assert.*
import org.junit.Test

class MovieListingParserTest {
    private val baseUrl = "https://www.037-hdd.com/"

    @Test
    fun `reads site cards using movie title instead of IMDb paragraph`() {
        val document = Jsoup.parse("""
            <div class="moviefilm">
                <a href="/2026/10/23/example/"><img src="/poster.jpg" alt="Fallback"></a>
                <div class="movief"><a href="/2026/10/23/example/">Example (2026) หนังตัวอย่าง</a></div>
                <div class="imdb"><p>ดูหนังออนไลน์ IMDb: 7.5</p></div>
            </div>
        """.trimIndent(), baseUrl)

        val movie = MovieListingParser.parse(document).single()
        assertEquals("Example (2026) หนังตัวอย่าง", movie.title)
        assertEquals("${baseUrl}2026/10/23/example/", movie.url)
        assertEquals("${baseUrl}poster.jpg", movie.posterUrl)
        assertFalse(movie.isSeries)
    }

    @Test
    fun `resolves lazy posters and uses image alt when title link is missing`() {
        val document = Jsoup.parse("""
            <div class="moviefilm"><a href="/example/">
                <img data-src="" data-lazy-src="//images.example/poster.jpg"
                     src="/placeholder.jpg" alt="หนังตัวอย่าง">
            </a><div class="imdb"><p>IMDb: 7.5</p></div></div>
        """.trimIndent(), baseUrl)

        val movie = MovieListingParser.parse(document).single()
        assertEquals("หนังตัวอย่าง", movie.title)
        assertEquals("https://images.example/poster.jpg", movie.posterUrl)
    }

    @Test
    fun `preserves legacy cards and removes duplicate entries from nested containers`() {
        val document = Jsoup.parse("""
            <article><div class="item"><a href="/legacy/" title="Anchor title">
                <img src="/legacy.jpg" alt="Image title"></a><h2>Legacy movie</h2>
            </div></article>
        """.trimIndent(), baseUrl)

        val movie = MovieListingParser.parse(document).single()
        assertEquals("Legacy movie", movie.title)
        assertEquals("${baseUrl}legacy/", movie.url)
    }

    @Test
    fun `skips incomplete and non web entries`() {
        val document = Jsoup.parse("""
            <div class="moviefilm">No link</div>
            <div class="moviefilm"><a href="javascript:void(0)">Advertisement</a><h2>Ad</h2></div>
            <div class="moviefilm"><a href="/empty/"></a></div>
        """.trimIndent(), baseUrl)
        assertTrue(MovieListingParser.parse(document).isEmpty())
    }

    @Test
    fun `recognizes series without season or episode in their title`() {
        val document = Jsoup.parse("""
            <div class="moviefilm"><a href="/series/example/"><img alt="ซีรีส์ตัวอย่าง"></a></div>
        """.trimIndent(), baseUrl)
        val series = MovieListingParser.parse(document).single()
        assertTrue(series.isSeries)
        assertNull(series.posterUrl)
    }

    @Test
    fun `builds first and subsequent pages for home and category bases`() {
        assertEquals(baseUrl, MovieListingParser.pageUrl(baseUrl, 1))
        assertEquals("${baseUrl}page/2/", MovieListingParser.pageUrl(baseUrl, 2))
        val category = "${baseUrl}category/หนังไทย/"
        assertEquals(category, MovieListingParser.pageUrl(category, 1))
        assertEquals("${category}page/3/", MovieListingParser.pageUrl(category, 3))
    }

    @Test
    fun `uses pagination links and stops at the last page`() {
        assertTrue(MovieListingParser.hasNextPage(Jsoup.parse(
            """<link rel="next" href="/page/2/">""", baseUrl
        )))
        assertTrue(MovieListingParser.hasNextPage(Jsoup.parse(
            """<a class="nextpostslink" href="/page/2/">Next</a>""", baseUrl
        )))
        assertFalse(MovieListingParser.hasNextPage(Jsoup.parse(
            """<a class="previouspostslink" href="/page/1/">Previous</a>""", baseUrl
        )))
        assertFalse(MovieListingParser.hasNextPage(Jsoup.parse(
            """<a class="nextpostslink" href="#">Next</a>""", baseUrl
        )))
    }

    @Test
    fun `empty search results stay empty`() {
        val document = Jsoup.parse("<h1>No results found</h1>", baseUrl)
        assertTrue(MovieListingParser.parse(document).isEmpty())
        assertFalse(MovieListingParser.hasNextPage(document))
    }

    @Test fun `pagination preserves search filters and replaces an existing page suffix`() {
        assertEquals("https://www.037-hdd.com/page/2/?s=movie", MovieListingParser.pageUrl("https://www.037-hdd.com/?s=movie", 2))
        assertEquals("https://www.037-hdd.com/category/action/page/3/", MovieListingParser.pageUrl("https://www.037-hdd.com/category/action/page/2/", 3))
    }
}
