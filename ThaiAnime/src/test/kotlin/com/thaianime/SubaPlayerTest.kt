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
    @Test fun `blocked player uses fresh primary content page without WebView or config`() = runBlocking {
        val seen = mutableListOf<String>()
        val resolver = SubaPlayer { url, ref ->
            seen += url
            when (url) {
                player.baseUri() -> throw java.io.IOException("HTTP 403 Cloudflare")
                origin.baseUri() -> { assertEquals(player.baseUri(), ref); origin }
                else -> error("Unexpected URL: $url")
            }
        }
        assertEquals(listOf("720p", "360p"), resolver.resolve(player.baseUri(), Site.SUBA.base).map { it.label })
        assertEquals(listOf(player.baseUri(), origin.baseUri()), seen)
    }
    @Test fun `origin fallback never guesses backup server or unrelated URLs`() {
        assertEquals("https://anim-esun.com/st1//player/523066.php", SubaPlayer.primaryOrigin("https://suba-anime.com/player/523066"))
        listOf("https://suba-anime.com/player2/522953", "https://evil.invalid/player/522953", "https://suba-anime.com/ads/522953").forEach {
            assertNull(SubaPlayer.primaryOrigin(it))
        }
    }
    @Test fun `blocked config also falls back while empty or advertising origins stay rejected`() = runBlocking {
        for (content in listOf(origin, Jsoup.parse("""<script>playerInstance.setup({sources:[{file:'https://anim-esun.com/st1/pl-1.2/Ads_Video/ad.mp4'}]});</script>""", origin.baseUri()))) {
            val resolver = SubaPlayer { url, _ ->
                when (url) {
                    player.baseUri() -> player
                    SubaPlayer.configUrl(player) -> throw java.io.IOException("Config HTTP403")
                    origin.baseUri() -> content
                    else -> error("Unexpected URL")
                }
            }
            if (content === origin) assertEquals(2, resolver.resolve(player.baseUri(), Site.SUBA.base).size)
            else {
                try {
                    resolver.resolve(player.baseUri(), Site.SUBA.base)
                    fail("Blocked config must remain visible when no content exists")
                } catch (error: java.io.IOException) { assertEquals("Config HTTP403", error.message) }
            }
        }
    }
    @Test fun `cancellation never triggers an origin fallback`() = runBlocking {
        var calls = 0
        val resolver = SubaPlayer { _, _ -> calls++; throw java.util.concurrent.CancellationException("Stopped") }
        try {
            resolver.resolve(player.baseUri(), Site.SUBA.base)
            fail("Cancellation expected")
        } catch (_: java.util.concurrent.CancellationException) { assertEquals(1, calls) }
    }
}
