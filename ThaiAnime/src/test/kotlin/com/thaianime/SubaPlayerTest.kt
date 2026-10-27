package com.thaianime

import kotlinx.coroutines.runBlocking
import org.jsoup.Jsoup
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class SubaPlayerTest {
    private val player = Samples.doc(File("../samples/suba-anime.com/player-source.html"))
    private val origin = Samples.doc(File("../samples/suba-anime.com/origin-player.html"))
    private val config = """kokora.create({'files': { 'type': 'iframe', 'url': url + 'st1/' + idpass, 'onError': null }});"""
    @Test fun `content chain preserves double slash and encodes spaces in both video qualities`() = runBlocking {
        val seen = mutableListOf<Pair<String, String>>()
        val resolver = SubaPlayer { url, ref ->
            seen += url to ref
            when(url) {
                player.baseUri() -> player
                SubaPlayer.configUrl(player) -> Jsoup.parseBodyFragment(config)
                origin.baseUri() -> origin
                else -> error("Unexpected URL")
            }
        }
        val streams = resolver.resolve(player.baseUri(), "https://suba-anime.com/hotel-inhumans-ss2/ep-1")
        assertEquals(3, seen.size)
        assertEquals(player.baseUri(), seen[2].second)
        assertEquals(listOf("720p", "360p"), streams.map { it.label })
        assertEquals("https://anim-esun.com/st1//pl-1.2/Hotel%20Inhumans%20SS2/Hotel%20Inhumans%20SS2%2001-720.m4v", streams.first().url)
        assertTrue(streams.all { it.referer == origin.baseUri() })
    }
    @Test fun `ad video and unrelated origin cannot become a content stream`() {
        assertEquals(origin.baseUri(), SubaPlayer.target(player, config))
        val fake = Jsoup.parse("""<script>playerInstance.setup({sources:[{file:'https://anim-esun.com/st1/pl-1.2/Ads_Video/ad.mp4',label:'AD'}]});</script>""", origin.baseUri())
        assertTrue(SubaPlayer.streams(fake).isEmpty())
        fake.setBaseUri("https://ads.invalid/player/id")
        assertTrue(SubaPlayer.streams(fake).isEmpty())
        assertNull(SubaPlayer.target(player, "unrelated script"))
    }
    @Test fun `only the observed Suba player paths are handled`() {
        assertTrue(SubaPlayer.isPlayer("https://suba-anime.com/player/123"))
        assertTrue(SubaPlayer.isPlayer("https://suba-anime.com/player2/123"))
        assertFalse(SubaPlayer.isPlayer("https://suba-anime.com/ads/123"))
        assertFalse(SubaPlayer.isPlayer("https://evil.invalid/player/123"))
    }
}
