package com.movie2free

import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.jsoup.Jsoup
import org.junit.Assert.*
import org.junit.Test
import kotlin.coroutines.cancellation.CancellationException

class BrowserDiscoveryTest {
    private val page = "https://movie2free.co/example/"
    private val player = "https://fastcdnv2.com/embed/content123"
    private val media = "https://fastfastcdn.com/hlsr2/content123/master.m3u8"
    private fun movie() = Jsoup.parse("""<div class="mf-player-screen"><iframe data-src="$player"></iframe></div>""", page)
    private fun content() = Jsoup.parse("""<script>const url = "$media";</script>""", player)

    @Test fun `native primary keeps original page Referer and avoids browser when media exists`() = runBlocking {
        val links = mutableListOf<Stream>()
        Movie2FreeResolver.resolvePage(page, "https://movie2free.co", { url, ref ->
            if (url == page) { assertEquals("https://movie2free.co", ref); movie() }
            else { assertEquals(player, url); assertEquals(page, ref); content() }
        }, { error("Native links already available") }, { links += it }, { _, _ -> error("Unexpected host") }, { links.isNotEmpty() })
        assertEquals(media, links.single().url)
        assertEquals(player, links.single().referer)
    }

    @Test fun `blocked HTTP page can resolve a fresh known player discovered by the browser`() = runBlocking {
        val seen = mutableListOf<String>()
        val links = mutableListOf<Stream>()
        Movie2FreeResolver.resolvePage(page, "https://movie2free.co", { url, ref ->
            seen += url
            if (url == page) throw IOException("Movie2Free HTTP 403")
            assertEquals(player, url); assertEquals(page, ref)
            content()
        }, { player to page }, { links += it }, { _, _ -> error("Unexpected host") }, { links.isNotEmpty() })
        assertEquals(listOf(page, player), seen)
        assertEquals(media, links.single().url)
    }

    @Test fun `untrusted browser capture is never fetched or emitted and denial is retained`() = runBlocking {
        val denied = IOException("Movie2Free HTTP 403")
        val failure = runCatching {
            Movie2FreeResolver.resolvePage(page, "https://movie2free.co", { url, _ ->
                assertEquals(page, url); throw denied
            }, { "https://ads.example/ad.mp4" to page }, { error("Not content") }, { _, _ -> error("Not content") }, { false })
        }.exceptionOrNull()
        assertSame(denied, failure)
    }

    @Test fun `leaving native extraction never starts browser discovery`() = runBlocking {
        val cancelled = CancellationException("User left")
        val failure = runCatching {
            Movie2FreeResolver.resolvePage(page, "https://movie2free.co", { _, _ -> throw cancelled },
                { error("Cancelled") }, { error("Cancelled") }, { _, _ -> error("Cancelled") }, { false })
        }.exceptionOrNull()
        assertSame(cancelled, failure)
    }

    @Test fun `capture boundaries accept supplied wrappers and reject ads credentials and host suffix tricks`() {
        listOf(player, "https://backup.ssplayer168.xyz/api/embed/index.php?id=8108",
            "https://backup.ssplayer168.xyz/api/embed/bunny/index.php?id=8108",
            "https://backup.ssplayer168.xyz/api/embed/jw/main.php?id=8108").forEach {
            assertTrue(it, Movie2FreeResolver.browserPattern.matches(it))
        }
        listOf("https://fastcdnv2.com.evil.test/embed/content123", "https://user:pass@fastcdnv2.com/embed/content123",
            "https://fastcdnv2.com/Ads_Video/ad.mp4", "https://fastcdnv2.com/embed/content123#fragment",
            "https://ads.example/embed/content123").forEach {
            assertFalse(it, Movie2FreeResolver.browserPattern.matches(it))
        }
    }
}
