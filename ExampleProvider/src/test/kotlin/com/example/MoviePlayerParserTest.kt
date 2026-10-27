package com.example

import org.jsoup.Jsoup
import org.junit.Assert.*
import org.junit.Test

class MoviePlayerParserTest {
    private val base = "https://www.037-hdd.com/movie/"

    @Test
    fun `reads older dub and soundtrack groups without language in iframe attributes`() {
        val players = MoviePlayerParser.parse(Jsoup.parse("""
            <nav><a>ดูหนังออนไลน์ Soundtrack</a></nav><h1>Fast 9</h1>
            <div class="filmicerik2"><p><iframe src="/dub-ticket"></iframe></p>
            <div><a>Soundtrack บรรยายไทย</a></div>
            <p><iframe src="/sub-ticket"></iframe></p></div>
            <iframe src="https://www.youtube.com/embed/trailer"></iframe>
        """, base))
        assertEquals(listOf("พากย์ไทย", "ซับไทย"), players.map { it.language })
        assertEquals(listOf("https://www.037-hdd.com/dub-ticket", "https://www.037-hdd.com/sub-ticket"),
            players.map { it.url })
        assertEquals("พากย์ไทย - ตัวเล่นหลัก(V2)", MoviePlayerParser.sourceLabel(players[0], "ตัวเล่นหลัก(V2)"))
        assertEquals("ซับไทย - ตัวเล่นหลัก(V2)", MoviePlayerParser.sourceLabel(players[1], "ตัวเล่นหลัก(V2)"))
    }

    @Test
    fun `reads modern language titles and soundtrack image dividers`() {
        val players = MoviePlayerParser.parse(Jsoup.parse("""
            <h1>Spider-Man</h1><div class="filmicerik2">
            <iframe src="/thai" title="Thai Player"></iframe>
            <div><img src="/Soundtrack.png" alt="Soundtrack"></div>
            <iframe src="/sub" title="Soundtrack Player"></iframe></div>
        """, base))
        assertEquals(listOf("พากย์ไทย", "ซับไทย"), players.map { it.language })
    }

    @Test
    fun `does not guess a language for an unlabelled single player`() {
        val player = MoviePlayerParser.parse(Jsoup.parse("""
            <a>ดูหนังออนไลน์ Soundtrack</a><h1>A movie</h1><iframe src="/player"></iframe>
        """, base)).single()
        assertNull(player.language)
        assertEquals("ไม่ระบุภาษา - V2", MoviePlayerParser.sourceLabel(player, "V2"))
    }

    @Test
    fun `preserves actual player language over an outer page group`() {
        val player = MoviePlayer("ตัวเล่นหลัก", "$base/player", "พากย์ไทย")
        assertEquals("ซับไทย - V2", MoviePlayerParser.sourceLabel(player, "ซับไทย - V2"))
    }

    @Test
    fun `retains subtitled only titles and avoids treating lazy posters as video players`() {
        val players = MoviePlayerParser.parse(Jsoup.parse("""
            <h1>Movie [Soundtrack บรรยายไทย]</h1><img data-src="https://images.example/poster.jpg">
            <iframe data-src="/player"></iframe><iframe src="/player"></iframe>
        """, base))
        assertEquals(1, players.size)
        assertEquals("ซับไทย", players.single().language)
    }

    @Test
    fun `deduplicates rotating HLS tokens while preserving mirrors and audio parameters`() {
        val url = "https://playlist.streamhls1688.com/sources/movie/tiktok/master.m3u8"
        assertEquals(StreamHlsResolver.streamKey("$url?hls_token=first"),
            StreamHlsResolver.streamKey("$url?hls_token=second"))
        assertNotEquals(StreamHlsResolver.streamKey("$url?audio=thai&hls_token=first"),
            StreamHlsResolver.streamKey("$url?audio=english&hls_token=second"))
        assertNotEquals(StreamHlsResolver.streamKey("$url?hls_token=first"),
            StreamHlsResolver.streamKey(url.replace("tiktok", "discord") + "?hls_token=second"))
    }
}
