package com.thaianime

import org.jsoup.Jsoup
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class AnimePlayerTest {
    @Test fun `Akuma content source retains signed query and excludes other hosts`() {
        val html = java.io.File("../samples/regression/nekomiku.net/akuma-content.html").readText()
        val base = "https://app.akuma-stream.com/watch/b50fd31f-55ed-4bac-a0bd-a453fbb74c5b?autoplay=1"
        val doc = org.jsoup.Jsoup.parse(html, base)
        assertEquals("https://app.akuma-stream.com/api/manifest/b50fd31f-55ed-4bac-a0bd-a453fbb74c5b/master.m3u8?token=synthetic-test-token", AnimePlayer.akuma(doc))
        assertNull(AnimePlayer.akuma(org.jsoup.Jsoup.parse(html, "https://other.example/")))
        assertNull(AnimePlayer.akuma(org.jsoup.Jsoup.parse("<video><source src='https://ads.example/ad.m3u8'></video>", base)))
    }

    @Test fun `Neko uses content VideoObject and never Spotify or fake preroll media`() {
        val doc = Samples.doc(Site.NEKO, "episode1", true)
        val sources = AnimePlayer.sources(Site.NEKO, doc)
        assertTrue(sources.any { it.url == "https://app.akuma-stream.com/watch/b965b436-ba90-4bf5-99b0-ba3d892c9ac2" })
        val frame = sources.single { it.url.contains("/player/embed?") }
        assertTrue(AnimePlayer.canUnwrap(frame.url, Site.NEKO))
        val f = Samples.file(Site.NEKO, "episode1").parentFile!!.walkTopDown().first { it.name == "embed.html" && it.readText().contains("b965b436") }
        val unwrapped = AnimePlayer.unwrap(Samples.doc(f))
        assertEquals(1, unwrapped.size)
        assertTrue(unwrapped.single().url.contains("app.akuma-stream.com/watch/b965b436"))
        assertTrue(unwrapped.none { it.url.contains("NQN8N") || it.url.contains("vast") })
    }
    @Test fun `AnimeRuka refreshes REST options with page supplied version and skips trailer`() {
        val doc = Samples.doc(Site.RUKA, "episode1", true)
        val options = AnimePlayer.options(doc)
        assertEquals(3, options.size)
        assertEquals("https://animeruka.com/wp-json/dooplayer/v2/47767/tv/1?v=a2cb00fe", options.first().url)
        assertEquals(listOf("หลัก", "สำรอง1", "สำรอง2"), options.map { it.label })
        doc.selectFirst("#playeroptionsul")!!.append("<li class=dooplay_player_option data-post=47767 data-nume=trailer data-type=tv>Trailer</li>")
        assertEquals(3, AnimePlayer.options(doc).size)
        assertEquals("https://player.abyssplayer.com/uyxbAeVJ7", AnimePlayer.sources(Site.RUKA, doc).single().url)
        assertEquals("https://animemami.xyz/v/id", AnimePlayer.ajaxEmbed("""{"embed_url":"https://animemami.xyz/v/id"}""", Site.RUKA.base))
        assertNull(AnimePlayer.ajaxEmbed("blocked", Site.RUKA.base))
    }
    @Test fun `AnimeSeven follows saved nested players to a fresh bootstrap manifest`() {
        val doc = Samples.doc(Site.SEVEN, "episode1", true)
        assertEquals("https://www.anime-seven.com/playery/231504", AnimePlayer.sources(Site.SEVEN, doc).single().url)
        val root = Samples.file(Site.SEVEN, "episode1").parentFile!!
        val outer = root.walkTopDown().first { it.name == "231504.html" }
        val inner = root.walkTopDown().first { it.name == "231504(1).html" }
        val leaf = root.walkTopDown().first { it.name == "mov_67rkvmE8zgd.html" }
        assertEquals("https://www.anime-seven.com/playervy/231504", AnimePlayer.unwrap(Samples.doc(outer, true)).single().url)
        assertEquals("https://nya.animenani.com/embed/mov_67rkvmE8zgdRAj", AnimePlayer.unwrap(Samples.doc(inner, true)).single().url)
        val manifest = AnimePlayer.manifest(Samples.doc(leaf))
        assertEquals("https://cat.animenani.com/o/fuujqkRTOr5RKj/a", manifest)
        val other = Jsoup.parse(leaf.readText(), "https://ads.invalid/embed/id")
        assertNull(AnimePlayer.manifest(other))
        val invalid = Jsoup.parse("<script>window.__PLAYER_EMBED_BOOTSTRAP__ = {}; const s=\"invalid\";</script>", "https://nya.animenani.com/embed/id")
        assertNull(AnimePlayer.manifest(invalid))
    }
    @Test fun `Suba extracts every real backup without treating loaded advert as a video`() {
        val doc = Samples.doc(Site.SUBA, "episode1", true)
        val sources = AnimePlayer.sources(Site.SUBA, doc).map { it.url }
        assertEquals(setOf("https://suba-anime.com/player/522953", "https://suba-anime.com/player2/522953", "https://ok.ru/videoembed/16431775812183"), sources.toSet())
        assertTrue(sources.none { it.endsWith(".mp4") || it.startsWith("blob:") })
        assertFalse(AnimePlayer.canUnwrap(sources.first(), Site.SUBA))
    }
    @Test fun `malformed player configs and unrelated embeds cannot become playback links`() {
        val doc = Jsoup.parse("""<iframe src="https://ads.invalid/preroll.mp4"></iframe><video src="blob:https://host/id"></video><script>var dtAjax={bad};</script>""", Site.RUKA.base)
        assertTrue(AnimePlayer.options(doc).isEmpty())
        Site.entries.forEach { assertTrue(AnimePlayer.sources(it, doc).isEmpty()) }
        assertFalse(AnimePlayer.canUnwrap("https://www.anime-seven.com.evil.invalid/playery/1", Site.SEVEN))
        assertFalse(AnimePlayer.canUnwrap("https://www.anime-seven.com/login", Site.SEVEN))
    }
}
