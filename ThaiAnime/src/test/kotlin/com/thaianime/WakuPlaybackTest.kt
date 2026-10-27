package com.thaianime

import kotlinx.coroutines.runBlocking
import okhttp3.Request
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import kotlin.coroutines.cancellation.CancellationException

class WakuPlaybackTest {
    private val root = File("../samples/Anime-Waku")
    private fun episode() = Samples.doc(File(root, "episode1.html"), true)

    @Test fun `raw episode with no rendered iframe follows website REST contract to content manifest`() = runBlocking {
        val doc = episode()
        doc.select("#dooplay_player_response iframe").remove()
        assertTrue(AnimePlayer.sources(Site.WAKU, doc).isEmpty())
        // Independent evidence: the site's captured JS passes type/source as query parameters.
        val script = File(root, "episode1_files/front.ajax.js.ดาวน์โหลด").readText()
        assertTrue(script.contains("getJSON(dtAjax.url_api+a+\"?type=\"+type+\"&source=\"+nume"))
        val wrapper = Samples.doc(File(root, "episode1_files/saved_resource.html"), true)
        val inner = Samples.doc(File(root, "episode1_files/mov_G3JkxbPKmYr7iS.html"))
        val apiCalls = mutableListOf<String>()
        val requests = mutableListOf<Pair<String, String>>()
        val streams = mutableListOf<Pair<String, String>>()
        suspend fun resolve(source: PlayerSource, referer: String) {
            requests += source.url to referer
            val player = when (source.url) {
                wrapper.baseUri() -> wrapper
                inner.baseUri() -> inner
                else -> error("Unexpected content request ${source.url}")
            }
            val manifest = AnimePlayer.manifest(player)
            if (manifest != null) streams += manifest to player.baseUri()
            else AnimePlayer.unwrap(player).forEach { resolve(it, player.baseUri()) }
        }
        WakuPlayer.sources(doc,
            getJson = { url, referer ->
                assertEquals("https://anime-waku.com/wp-json/dooplayer/v1/post/113437?type=tv&source=1", url)
                assertEquals(doc.baseUri(), referer)
                apiCalls += url
                """{"embed_url":"${wrapper.baseUri()}","type":"iframe"}"""
            }, resolve = { source, ref -> resolve(source, ref) }, hasLinks = { streams.isNotEmpty() },
        )
        assertEquals(1, apiCalls.size)
        assertEquals(listOf(wrapper.baseUri() to doc.baseUri(), inner.baseUri() to wrapper.baseUri()), requests)
        assertEquals(listOf(AnimePlayer.manifest(inner)!! to inner.baseUri()), streams)
    }

    @Test fun `API iframe HTML resolves its source instead of becoming an encoded relative path`() {
        val base = "https://anime-waku.com/ep/113437/"
        assertEquals("https://anime-waku.com/player/token/", AnimePlayer.ajaxEmbed(
            """{"embed_url":"<iframe src='/player/token/'></iframe>","type":"dtshcode"}""", base))
        assertNull(AnimePlayer.ajaxEmbed("""{"embed_url":"<script>advertisement</script>"}""", base))
        assertNull(AnimePlayer.ajaxEmbed("""{"embed_url":"<iframe src='javascript:alert(1)'></iframe>"}""", base))
    }

    @Test fun `successful rendered iframe does not repeat REST request`() = runBlocking {
        var links = 0
        WakuPlayer.sources(episode(),
            getJson = { _, _ -> error("REST should not be requested after a usable iframe") },
            resolve = { _, _ -> links++ }, hasLinks = { links > 0 },
        )
        assertEquals(1, links)
    }

    @Test fun `broken API option does not stop backups and cancellation propagates`() = runBlocking {
        val doc = episode()
        doc.select("#dooplay_player_response iframe").remove()
        doc.selectFirst("#playeroptionsul")!!.append("<li class=dooplay_player_option data-post=113437 data-nume=2 data-type=tv>สำรอง</li>")
        val resolved = mutableListOf<String>()
        WakuPlayer.sources(doc,
            getJson = { url, _ ->
                if (url.endsWith("source=1")) throw java.io.IOException("synthetic failed mirror")
                """{"embed_url":"https://anime-waku.com/player/backup/"}"""
            }, resolve = { source, _ -> resolved += source.url }, hasLinks = { false },
        )
        assertEquals(listOf("https://anime-waku.com/player/backup/"), resolved)
        try {
            WakuPlayer.sources(doc, getJson = { _, _ -> throw CancellationException("cancel") }, resolve = { _, _ -> }, hasLinks = { false })
            fail("Cancellation must propagate")
        } catch (_: CancellationException) { }
    }

    @Test fun `browser capture accepts actual content master route and preserves request headers`() {
        for (episode in listOf(1, 2)) {
            val dir = File(root, "episode${episode}_files")
            val inner = Samples.doc(dir.listFiles()!!.single { it.name.startsWith("mov_") })
            val manifest = AnimePlayer.manifest(inner)!!
            val request = Request.Builder().url(manifest).header("Referer", inner.baseUri())
                .header("User-Agent", "Synthetic WebView UA").header("Cookie", "synthetic=fixture").build()
            val stream = WakuPlayer.browserStream(request)!!
            assertEquals(manifest, stream.url)
            assertEquals(inner.baseUri(), stream.referer)
            assertEquals("Synthetic WebView UA", stream.headers["User-Agent"])
            assertEquals("synthetic=fixture", stream.headers["Cookie"])
            assertFalse(stream.headers.keys.any { it.equals("Referer", true) })
        }
        listOf("https://ads.invalid/ad.m3u8", "https://cat.animenani.com/ads/a.mp4", "https://cat.animenani.com.evil.invalid/o/id/a",
            "https://cat.animenani.com/o/id/a/more", "http://cat.animenani.com/o/id/a").forEach {
            assertNull(WakuPlayer.browserStream(Request.Builder().url(it).build()))
        }
        val post = Request.Builder().url("https://cat.animenani.com/o/id/a").method("HEAD", null).build()
        assertNull(WakuPlayer.browserStream(post))
    }
}
