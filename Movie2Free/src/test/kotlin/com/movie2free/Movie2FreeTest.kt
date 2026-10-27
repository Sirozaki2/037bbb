package com.movie2free

import java.io.File
import kotlinx.coroutines.runBlocking
import org.jsoup.Jsoup
import org.junit.Assert.*
import org.junit.Test
import kotlin.coroutines.cancellation.CancellationException

class Movie2FreeTest {
    private fun fixture(path: String): org.jsoup.nodes.Document {
        val file = File("../samples/movie2free.co/$path")
        val text = file.readText()
        val base = Regex("saved from url=\\(\\d+\\)([^ ]+)").find(text)?.groupValues?.get(1) ?: error("Missing saved URL")
        val doc = Jsoup.parse(text, base)
        // Chrome rewrites iframe URLs when saving. Restore only URLs explicitly
        // recorded in the corresponding saved frame, without modifying fixtures.
        doc.select("iframe[src]").forEach { frame ->
            val local = frame.attr("src")
            if (local.startsWith("./")) {
                val saved = File(file.parentFile, local).takeIf { it.isFile }?.readText().orEmpty()
                Regex("saved from url=\\(\\d+\\)([^ ]+)").find(saved)?.groupValues?.get(1)?.let { frame.attr("src", it) }
            }
        }
        return doc
    }
    @Test fun `supplied home and category cards are distinct and empty search stays empty`() {
        for (path in listOf("home.html", "catagory.html")) {
            val doc = fixture(path); val cards = Movie2FreeParser.cards(doc)
            assertTrue(path, cards.size >= 20)
            assertEquals(cards.size, cards.map { it.url }.distinct().size)
            assertTrue(cards.all { it.title.isNotBlank() && it.url.startsWith("https://movie2free.co/") })
            assertTrue(Movie2FreeParser.hasNext(doc))
        }
        val home = Movie2FreeParser.cards(fixture("home.html"))
        assertEquals(36, home.size)
        assertTrue(home.first().title.startsWith("My Best Friend, His Girlfriend and Me"))
        assertFalse(home.any { it.title.startsWith("Kadhalikka Neramillai") })
        assertTrue(Movie2FreeParser.cards(fixture("search.html")).isEmpty())
    }
    @Test fun `movie metadata and content iframe exclude trailer and advertising`() {
        val doc = fixture("movie.html"); val detail = Movie2FreeParser.detail(doc)
        assertEquals("Drawn Together (2026)", detail.title)
        assertEquals(2026, detail.year)
        assertEquals(3, detail.tags.size)
        assertTrue(detail.plot!!.contains("Drawn Together"))
        assertEquals("https://backup.ssplayer168.xyz/api/embed/index.php?id=8108", Movie2FreeParser.embed(doc))
    }
    @Test fun `wrapper retains all real mirrors and ignores commented option`() {
        val players = Movie2FreeParser.players(fixture("movie_files/saved_resource.html"))
        assertEquals(3, players.size)
        assertEquals(listOf("FastCDN", "ตัวเล่นไว", "สำรอง"), players.map { it.label })
        assertEquals("https://backup.ssplayer168.xyz/api/embed/bunny/index.php?id=8108", players[1].url)
        assertFalse(players.any { "v5/" in it.url || "about:" in it.url })
    }
    @Test fun `content stream comes from actual player variable`() {
        val streams = Movie2FreeParser.streams(fixture("movie_files/754e325278b45238faf2f87c0b3c53fd.html"))
        assertEquals(listOf("https://fastfastcdn.com/hlsr2/754e325278b45238faf2f87c0b3c53fd/master.m3u8"), streams)
        val doc = Jsoup.parse("""<script>let ad={file:'https://ad.invalid/preroll.m3u8'}; window.hls='/movie.m3u8?token=1';window.hls='/movie.m3u8?token=1';</script>""", "https://cdn.example/player")
        assertEquals(listOf("https://cdn.example/movie.m3u8?token=1"), Movie2FreeParser.streams(doc))
    }
    @Test fun `ajax search uses documented fields and rejects foreign or invalid links`() {
        val results = Movie2FreeParser.searchResults("""[{"title":"A &amp; B","url":"/film/","thumb":"/poster.jpg"},{"title":"duplicate","url":"/film/"},{"title":"ad","url":"https://ads.invalid/"},{"title":"bad","url":"javascript:alert(1)"}]""", "https://movie2free.co")
        assertEquals(1, results.size); assertEquals("A & B", results.single().title)
        assertEquals("https://movie2free.co/poster.jpg", results.single().poster)
        assertTrue(Movie2FreeParser.searchResults("<html>blocked</html>", "https://movie2free.co").isEmpty())
    }
    @Test fun `pagination preserves filters and never emits saved local resources`() {
        assertEquals("https://movie2free.co/thriller/page/3/?x=1", Movie2FreeParser.pageUrl("https://movie2free.co/thriller/page/2/?x=1", 3))
        assertNull(Movie2FreeParser.url("https://movie2free.co", "./movie_files/poster.jpg"))
        assertNull(Movie2FreeParser.url("https://movie2free.co", "https://user:pass@host.invalid/"))
        assertNull(Movie2FreeParser.url("https://movie2free.co", "javascript:alert(1)"))
    }
    @Test fun `broken mirror cannot prevent another mirror and duplicate links emit once`() = runBlocking {
        val base = "https://backup.ssplayer168.xyz/api/embed/index.php?id=8108"
        val streams = mutableListOf<Stream>(); val fetched = mutableListOf<String>()
        Movie2FreeResolver.resolve(base, "https://movie2free.co/movie/", { url, _ ->
            fetched.add(url)
            when {
                url == base -> Jsoup.parse("""<button class=linkserver data-url='bad'>Bad</button><button class=linkserver data-url='good'>Good</button><button class=linkserver data-url='good'>Duplicate</button>""", url)
                url.endsWith("/bad") -> error("broken")
                else -> Jsoup.parse("""<script>window.hls='https://cdn.example/movie.m3u8';</script><iframe id=main-iframe src='$base'></iframe>""", url)
            }
        }, { streams.add(it) }, { _, _ -> error("unexpected fallback") })
        assertEquals(3, fetched.size); assertEquals(1, streams.size)
        assertEquals("Good", streams.single().label)
        assertTrue(streams.single().referer.endsWith("/good"))
    }
    @Test(expected = CancellationException::class) fun `resolver preserves cancellation`() = runBlocking {
        Movie2FreeResolver.resolve("https://fastcdnv2.com/embed/id", "https://movie2free.co/", { _, _ -> throw CancellationException() }, {}, { _, _ -> })
    }
}
