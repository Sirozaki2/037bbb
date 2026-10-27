package com.example

import org.jsoup.Jsoup
import org.junit.Assert.*
import org.junit.Test

class SeriesEpisodeParserTest {
    private val base = "https://www.037-hdd.com/series/reacher/"

    @Test
    fun `reads all eight Reacher episodes from the actual source-select layout`() {
        val links = (1..8).joinToString("") { number ->
            """<a href="/episode/reacher-$number/" title="Reacher (2026) - ตอนที่ $number">
                <div class="episope"><svg></svg><span class="ep-f">Reacher (2026)</span>
                <span class="ep-b">- ตอนที่ $number</span></div></a>"""
        }
        val document = Jsoup.parse("""
            <a href="/episode/unrelated/">Sidebar</a>
            <div id="source-select"><h3>เลือกตอนต่อไป</h3><ul class="ss-item">$links</ul></div>
        """, base)
        val episodes = SeriesEpisodeParser.parse(document, "Reacher Season 4 (2026)")
        assertEquals((1..8).toList(), episodes.map { it.number })
        assertEquals(List(8) { 4 }, episodes.map { it.season })
        assertEquals((1..8).map { "https://www.037-hdd.com/episode/reacher-$it/" }, episodes.map { it.url })
        assertEquals("Reacher (2026) - ตอนที่ 8", episodes.last().name)
    }

    @Test
    fun `keeps actual numbering and sorts reversed lists instead of renumbering by position`() {
        val document = Jsoup.parse("""
            <ul class="episodes">
                <li><a href="/episode/8/">ตอนที่ 8</a></li>
                <li><a href="/episode/6/">EP.6</a></li>
                <li><a href="/episode/3/">Episode 3</a></li>
            </ul>
        """, base)
        val episodes = SeriesEpisodeParser.parse(document, "ซีซั่น4")
        assertEquals(listOf(3, 6, 8), episodes.map { it.number })
        assertTrue(episodes.all { it.season == 4 })
    }

    @Test
    fun `supports legacy dropdowns skips placeholders and deduplicates repeated links`() {
        val document = Jsoup.parse("""
            <select class="ep-select">
                <option value="">เลือกตอน</option><option value="#">เลือกตอน</option>
                <option value="javascript:play()">Invalid</option>
                <option value="/episode/one/">First</option>
                <option value="/episode/one/#player">Duplicate</option>
                <option data-url="//www.037-hdd.com/episode/two/">Second</option>
            </select>
        """, base)
        val episodes = SeriesEpisodeParser.parse(document, "A show")
        assertEquals(listOf(1, 2), episodes.map { it.number })
        assertEquals(listOf("First", "Second"), episodes.map { it.name })
        assertTrue(episodes.all { it.url.startsWith("https://www.037-hdd.com/episode/") })
    }

    @Test
    fun `reads season and episode attributes and SxxExx labels across seasons`() {
        val document = Jsoup.parse("""
            <div class="ep-list">
                <a href="/episode/s2e3/">S02E03</a>
                <a href="/episode/s1e8/" data-season="1" data-episode="8">Finale</a>
            </div>
        """, base)
        val episodes = SeriesEpisodeParser.parse(document, "A show Season 2")
        assertEquals(listOf(1, 2), episodes.map { it.season })
        assertEquals(listOf(8, 3), episodes.map { it.number })
    }

    @Test
    fun `recognizes Thai season spellings without mistaking release year for season`() {
        val document = Jsoup.parse("<div class='ep-list'><a href='/episode/1/'>ตอนที่ 1</a></div>", base)
        for (title in listOf("Reacher ซีซั่น4", "Reacher ซีซัน 4", "Reacher Season 4 (2026)")) {
            assertEquals(4, SeriesEpisodeParser.parse(document, title).single().season)
        }
        assertEquals(1, SeriesEpisodeParser.parse(document, "Reacher (2026)").single().season)
    }

    @Test
    fun `movie mirror selectors and unrelated episode recommendations are not series episodes`() {
        val document = Jsoup.parse("""
            <div id="source-select"><ul class="ss-item">
                <a href="/player/v2/">ตัวเล่นหลัก(V2)</a><a href="/player/backup/">ตัวเล่นสำรอง1</a>
            </ul></div>
            <aside><a href="/episode/other-show/">ตอนที่ 1</a></aside>
        """, base)
        assertTrue(SeriesEpisodeParser.parse(document, "A movie (2026)").isEmpty())
    }
}
