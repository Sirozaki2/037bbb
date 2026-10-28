package com.thaianime

import java.io.File
import java.io.IOException
import kotlinx.coroutines.runBlocking
import kotlin.coroutines.cancellation.CancellationException
import org.jsoup.Jsoup
import org.junit.Assert.*
import org.junit.Test

class TeamAnimeTest {
    private fun sample(site: TeamAnimeSite, name: String) = Samples.doc(File("../samples/${if (site == TeamAnimeSite.HIT) "hit-anime.com" else "anime-your.com"}/$name.html"))
    @Test fun `both new catalogs and search pages stay scoped and preserve lazy posters`() {
        for (site in listOf(TeamAnimeSite.HIT, TeamAnimeSite.YOUR)) {
            val parser = TeamAnimeParser(site)
            for (page in listOf("home","thai","page2","search")) {
                val cards = parser.cards(sample(site,page))
                assertEquals("$site/$page",20,cards.size)
                assertEquals(20,cards.map { it.url }.distinct().size)
                assertTrue(cards.all { it.url.startsWith(site.base+"/") && it.poster?.startsWith("https://") == true })
            }
            assertTrue(parser.hasNext(sample(site,"page2")))
            assertTrue(parser.hasNext(sample(site,"home")))
            assertNotEquals(parser.cards(sample(site,"thai")).first().url, parser.cards(sample(site,"page2")).first().url)
        }
    }
    @Test fun `all advertised home categories return cards and movie listings keep movie type`() {
        for (site in listOf(TeamAnimeSite.HIT,TeamAnimeSite.YOUR)) {
            val parser = TeamAnimeParser(site)
            for (page in listOf("sub","chinese","movies")) {
                val cards = parser.cards(sample(site,page))
                assertEquals("$site/$page",20,cards.size)
                if (page == "movies") assertTrue(cards.all { it.movie })
            }
            assertEquals(5,site.categories.size)
        }
    }
    @Test fun `movies retain the actual episode instead of sidebar recommendations`() {
        for (site in listOf(TeamAnimeSite.HIT,TeamAnimeSite.YOUR)) {
            val doc = sample(site,"movie")
            val detail = TeamAnimeParser(site).detail(doc)
            assertTrue(detail.movie)
            assertEquals(1,detail.episodes.size)
            assertEquals(1,detail.episodes.single().number)
            assertTrue(site.isEpisode(detail.episodes.single().url))
            doc.selectFirst("main h1")!!.text("A film")
            assertTrue(TeamAnimeParser(site).detail(doc).movie)
        }
    }
    @Test fun `new series have all numbered Thai episodes and no duplicated responsive titles`() {
        for (site in listOf(TeamAnimeSite.HIT,TeamAnimeSite.YOUR)) {
            val detail = TeamAnimeParser(site).detail(sample(site,"detail"))
            assertEquals((1..138).toList(),detail.episodes.map { it.number })
            assertTrue(detail.episodes.all { it.dubbed && site.isEpisode(it.url) })
            assertTrue(detail.episodes.none { Regex("ตอนที่").findAll(it.title).count()>1 })
            assertNotNull(detail.poster)
            assertFalse(detail.movie)
        }
    }
    @Test fun `Your aliases normalize only known hosts and retain encoded parameters`() {
        val site = TeamAnimeSite.YOUR
        assertEquals("https://anime-your.com/ep/12/?q=a%26b",site.local(site.base,"https://animeyour.com/ep/12/?q=a%26b"))
        assertNull(site.local(site.base,"https://animeyour.com.attacker.example/ep/12/"))
        assertNull(site.local(site.base,"https://hit-anime.com/video/12/"))
        assertNull(site.local(site.base,"javascript:alert(1)"))
        assertEquals("https://anime-your.com/lang/sub-th/page/3/?q=a%26b",TeamAnimeParser(site).pageUrl("https://anime-your.com/lang/sub-th/page/2/?q=a%26b",3))
    }
    @Test fun `home redirects cannot masquerade as an episode playback page`() {
        for (site in TeamAnimeSite.values()) {
            val requested = site.base+site.episodePrefix+"123/"
            assertTrue(runCatching { site.validatePlaybackPage(requested,site.base+"/") }.exceptionOrNull() is IllegalArgumentException)
            site.validatePlaybackPage(requested,requested)
            site.validatePlaybackPage("https://indy.team-indy.net/server2/123/","https://indy.team-indy.net/server2/123/")
        }
    }
    @Test fun `Hit select retains both servers and ignores report and unrelated options`() {
        val doc = sample(TeamAnimeSite.HIT,"episode")
        doc.body().append("<select><option value='https://ads.example/ad.mp4'>Ad</option></select>")
        val sources = TeamAnimePlayer.sources(doc)
        assertEquals(2,sources.size)
        assertEquals("https://indy.team-indy.net/server2/aSjZuPlltF/",sources.first().url)
        assertEquals("https://hit.team-indy.net/embed/aSjZuPlltF/",sources.last().url)
        assertFalse(TeamAnimePlayer.stream(sample(TeamAnimeSite.HIT,"backup"),"test")!!.hls)
    }
    @Test fun `Your selects advertised HLS backup ahead of blocked primary`() {
        val sources = TeamAnimePlayer.sources(sample(TeamAnimeSite.YOUR,"episode"))
        assertEquals(2,sources.size)
        assertEquals("https://abcdxzy.xyz/embed2/AyiMFSd2k/",sources.first().url)
        assertEquals("https://moji.team-indy.net/embed/AyiMFSd2k/",sources.last().url)
    }
    @Test fun `Your HLS stream preserves port referer and media type`() {
        val stream = TeamAnimePlayer.stream(sample(TeamAnimeSite.YOUR,"backup"),"Server 2")!!
        assertEquals("https://moji.abcdxzy.xyz:8443/vod/AyiMFSd2k/video.mp4/playlist.m3u8",stream.url)
        assertEquals("https://abcdxzy.xyz/embed2/AyiMFSd2k/",stream.referer)
        assertTrue(stream.hls)
    }
    @Test fun `HLS content ID and player source binding exclude ads and unrelated URLs`() {
        val doc = sample(TeamAnimeSite.YOUR,"backup")
        for (html in listOf(doc.html().replace("/vod/AyiMFSd2k/","/vod/other/"),doc.html().replace("moji.abcdxzy.xyz","ads.example"),doc.html().replace("source: linkplay","advertising: linkplay"))) {
            assertNull(TeamAnimePlayer.stream(Jsoup.parse(html,doc.baseUri()),"test"))
        }
        doc.setBaseUri("https://unrelated.example/embed2/AyiMFSd2k/")
        assertNull(TeamAnimePlayer.stream(doc,"test"))
    }
    @Test fun `broken primary cannot remove either sites working backup`() = runBlocking {
        for (site in listOf(TeamAnimeSite.HIT,TeamAnimeSite.YOUR)) {
            val streams = mutableListOf<TeamAnimeStream>()
            var extracts=0
            TeamAnimePlayer.resolve(sample(site,"episode"),get={ url,_ ->
                if (url.contains("/server2/") || url.contains("/embed2/")) sample(site,"backup") else throw IOException("403")
            },extract={ _,_ -> extracts++; throw IOException("Unsupported") },emit={ streams+=it })
            assertEquals(1,streams.size);assertEquals(1,extracts)
        }
    }
    @Test fun `extractor cancellation propagates and does not advance to next server`() = runBlocking {
        var gets=0
        try {
            TeamAnimePlayer.resolve(sample(TeamAnimeSite.YOUR,"episode"),get={ _,_ -> gets++;throw IOException("Blocked") },
                extract={ _,_ -> throw CancellationException("cancel") },emit={ fail("unexpected stream") })
            fail("must cancel")
        } catch (_:CancellationException) { assertEquals(1,gets) }
    }
}
