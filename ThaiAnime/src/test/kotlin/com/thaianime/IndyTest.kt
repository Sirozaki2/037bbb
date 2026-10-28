package com.thaianime

import java.io.File
import java.io.IOException
import kotlinx.coroutines.runBlocking
import kotlin.coroutines.cancellation.CancellationException
import org.jsoup.Jsoup
import org.junit.Assert.*
import org.junit.Test

class IndyTest {
    private val parser = TeamAnimeParser(TeamAnimeSite.INDY)
    private fun sample(name: String) = Samples.doc(File("../samples/indy-anime.net/$name.html"))
    @Test fun `catalog pages exclude sidebar and keep language labels and lazy posters`() {
        val cards = parser.cards(sample("thai-page2"))
        assertEquals(20, cards.size)
        assertEquals(20, cards.map { it.url }.distinct().size)
        assertTrue(cards.all { it.title.contains("พากย์ไทย") && it.poster?.startsWith("https://") == true })
        assertTrue(parser.hasNext(sample("thai-page2")))
        assertTrue(parser.cards(sample("home")).isNotEmpty())
    }
    @Test fun `search returns only actual result cards even when recommendations exist`() {
        val doc = sample("search")
        val expected = doc.select("main article.indy-article .film-name a").map { it.attr("href") }
        assertEquals(2, expected.size)
        assertEquals(expected, parser.cards(doc).map { it.url })
        assertFalse(parser.hasNext(doc))
    }
    @Test fun `series episodes stay in order with the group language and no recommendations`() {
        val detail = parser.detail(sample("thai-series"))
        assertEquals((1..138).toList(), detail.episodes.map { it.number })
        assertTrue(detail.episodes.all { it.dubbed })
        assertFalse(detail.movie)
        assertTrue(detail.tags.contains("แฟนตาซี"))
        val sub = parser.detail(sample("sub-series"))
        assertTrue(sub.episodes.isNotEmpty())
        assertTrue(sub.episodes.none { it.dubbed })
    }
    @Test fun `movie keeps its observed separate watch URL`() {
        val movie = parser.detail(sample("movie"))
        assertTrue(movie.movie)
        assertEquals("https://indy-anime.net/watch/renegade-immortal-slaying-the-blood-progenitor-episode-1/", movie.episodes.single().url)
    }
    @Test fun `movie classification uses metadata even without movie in its title`() {
        val doc = sample("movie")
        doc.selectFirst("main h1")!!.text("Renegade Immortal")
        assertTrue(parser.detail(doc).movie)
    }
    @Test fun `pagination preserves filters and search encodes the query`() {
        assertEquals("https://indy-anime.net/lang/sub-thai/page/3/?s=a%26b", parser.pageUrl("https://indy-anime.net/lang/sub-thai/page/2/?s=a%26b",3))
        assertEquals("https://indy-anime.net/?s=a%26b%20%2B", parser.searchUrl("a&b +"))
        val doc = Jsoup.parse("<main><a class=next href='#'>next</a></main><link rel=next href='https://ads.example/page/2/'>", parser.base)
        assertFalse(parser.hasNext(doc))
    }
    @Test fun `all advertised servers are retained with the direct backup first and ads excluded`() {
        val doc = sample("episode")
        doc.body().append("<iframe src='https://ads.example/ad.mp4'></iframe><span data-id='https://ads.example/ad.mp4'>ad</span>")
        val sources = TeamAnimePlayer.sources(doc)
        assertEquals(3, sources.size)
        assertEquals("https://indy.team-indy.net/server2/i0X8pkjvHG/", sources.first().url)
        assertTrue(sources.all { it.url.startsWith("https://indy.team-indy.net/") })
    }
    @Test fun `observed Clappr source retains embed referer without inventing a video URL`() {
        val stream = TeamAnimePlayer.stream(sample("server2"), "สำรอง")!!
        assertEquals("https://sv2.team-indy.net/video/i0X8pkjvHG/video.mp4", stream.url)
        assertEquals("https://indy.team-indy.net/server2/i0X8pkjvHG/", stream.referer)
        val blocked = Jsoup.parse("<h1>Blocked</h1>", stream.referer)
        assertNull(TeamAnimePlayer.stream(blocked,"test"))
    }
    @Test fun `unrelated video IDs hosts and scripts cannot become content streams`() {
        val original = sample("server2")
        val text = original.html()
        for (changed in listOf(text.replace("/video/i0X8pkjvHG/", "/video/advert/"), text.replace("sv2.team-indy.net", "ads.example"), text.replace("source: link", "advertising: link"))) {
            assertNull(TeamAnimePlayer.stream(Jsoup.parse(changed,original.baseUri()),"test"))
        }
        original.setBaseUri("https://unknown.example/server2/i0X8pkjvHG/")
        assertNull(TeamAnimePlayer.stream(original,"test"))
    }
    @Test fun `broken mirrors do not discard the working backup and each extractor runs once`() = runBlocking {
        val seen = mutableListOf<String>(); val extracted = mutableListOf<String>(); val streams = mutableListOf<TeamAnimeStream>()
        TeamAnimePlayer.resolve(sample("episode"), get = { url, ref ->
            seen += url; assertTrue(ref.startsWith(parser.base+"/watch/"))
            if (url.contains("/server2/")) sample("server2") else throw IOException("403")
        }, extract = { source, _ -> extracted += source.url; throw IOException("Unsupported") }, emit = { streams += it })
        assertEquals(3,seen.size); assertEquals(2,extracted.size); assertEquals(1,streams.size)
    }
    @Test fun `cancellation stops before another server is attempted`() = runBlocking {
        var count = 0
        try {
            TeamAnimePlayer.resolve(sample("episode"), get = { _, _ -> count++; throw CancellationException("stop") },
                extract = { _, _ -> fail("must not extract after cancel") }, emit = { fail("must not emit") })
            fail("Cancellation must propagate")
        } catch (_: CancellationException) { assertEquals(1,count) }
    }
}
