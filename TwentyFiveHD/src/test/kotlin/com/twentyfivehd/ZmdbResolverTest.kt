package com.twentyfivehd

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import kotlin.coroutines.cancellation.CancellationException

class ZmdbResolverTest {
    private val id = "1234567890abcdef12345678"
    private val embed = "https://zmdb.net/embed?id=123&type=tv&season=2&episode=7"
    private val bootstrap = """<script id="bootstrap" type="application/json">{"linkToken":"synthetic","content":{"mediaType":"tv"}}</script>"""
    private val links = """{"success":true,"playerEmbedLinks":[
        {"embedUrl":"https://stream037.com/play/$id?audio=tha","language":"พากย์ไทย","serverLabel":"Server 1"},
        {"embedUrl":"https://stream037.com/play/$id?audio=eng&subtitle=tha","language":"ซับไทย","serverLabel":"Server 1"}
    ]}"""

    @Test fun `fetches the selected episode and preserves a single multi audio master`() = runBlocking {
        var videos = 0
        val streams = ZmdbResolver { url, referer, headers ->
            when {
                url == embed -> WebPage(url, bootstrap)
                url.contains("/api/embed/links?") -> {
                    assertEquals("https://zmdb.net/api/embed/links?id=123&type=tv&season=2&episode=7", url)
                    assertEquals(embed, referer)
                    assertEquals("Bearer synthetic", headers["Authorization"])
                    WebPage(url, links)
                }
                url == "https://zmdb.net/api/video/$id" -> {
                    videos++
                    assertTrue(headers.isEmpty())
                    WebPage(url, """{"success":true,"data":{"hlsUrl":"https://cdn.example/master.m3u8?sig=synthetic"}}""")
                }
                else -> error("Unexpected request")
            }
        }.resolve(embed, "https://25-hd.com/")
        assertEquals(1, videos)
        assertEquals("Server 1 - พากย์ไทย / ซับไทย", streams.single().label)
        assertEquals("https://cdn.example/master.m3u8?sig=synthetic", streams.single().url)
        assertTrue(streams.single().hls)
    }

    @Test fun `refreshes expired public bootstrap token once and retains selected episode`() = runBlocking {
        var calls = 0
        ZmdbResolver { url, _, headers ->
            when {
                url == embed -> WebPage(url, bootstrap)
                url.contains("/api/embed/links?") -> {
                    calls++
                    assertTrue(url.endsWith("season=2&episode=7"))
                    if (calls == 1) WebPage(url, "{}", 401) else {
                        assertEquals("Bearer renewed", headers["Authorization"])
                        WebPage(url, """{"success":true,"playerEmbedLinks":[]}""")
                    }
                }
                url == "https://zmdb.net/api/embed/bootstrap?id=123&type=tv" -> WebPage(url, """{"success":true,"linkToken":"renewed"}""")
                else -> error("Unexpected request")
            }
        }.resolve(embed, "https://25-hd.com/")
        assertEquals(2, calls)
    }

    @Test fun `VIP content does not request playback links`() = runBlocking {
        var calls = 0
        val result = ZmdbResolver { url, _, _ ->
            calls++
            WebPage(url, """<script id="bootstrap">{"content":{"isVipOnly":true}}</script>""")
        }.resolve(embed, "https://25-hd.com/")
        assertTrue(result.isEmpty())
        assertEquals(1, calls)
    }

    @Test fun `unavailable video does not prevent other mirrors or pass VIP links`() = runBlocking {
        val result = ZmdbResolver { url, _, _ ->
            when {
                url == embed -> WebPage(url, bootstrap)
                url.contains("/api/embed/links?") -> WebPage(url, """{"success":true,"playerEmbedLinks":[
                    {"embedUrl":"internal:$id"},
                    {"embedUrl":"https://external.example/backup","serverLabel":"Backup"},
                    {"embedUrl":"https://external.example/vip","isVipOnly":true}
                ]}""")
                url.contains("/api/video/") -> WebPage(url, """{"success":false}""", 404)
                else -> error("Unexpected request")
            }
        }.resolve(embed, "https://25-hd.com/")
        assertEquals("https://external.example/backup", result.single().url)
        assertFalse(result.single().hls)
    }

    @Test(expected = CancellationException::class)
    fun `cancellation propagates during video resolution`() = runBlocking {
        ZmdbResolver { url, _, _ ->
            when {
                url == embed -> WebPage(url, bootstrap)
                url.contains("/api/embed/links?") -> WebPage(url, links)
                else -> throw CancellationException()
            }
        }.resolve(embed, "https://25-hd.com/")
        Unit
    }
}
