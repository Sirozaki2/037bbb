package com.example

import org.jsoup.Jsoup
import org.junit.Assert.*
import org.junit.Test

class MovieDetailsParserTest {
    @Test
    fun `uses clean og title and actual site synopsis`() {
        val details = MovieDetailsParser.parse(Jsoup.parse("""
            <meta property="og:title" content="Digger (2026) ดิกเกอร์">
            <meta property="og:image" content="https://images.example/digger.jpg">
            <meta name="description" content="Generic SEO description">
            <h1>Digger<div class="Breadcrumb">หน้าแรก &gt; หนังฝรั่ง</div></h1>
            <div class="konuozet"><p>เรื่องย่อจริง <b>จากเว็บไซต์</b></p></div>
        """))
        assertEquals("Digger (2026) ดิกเกอร์", details.title)
        assertEquals("เรื่องย่อจริง จากเว็บไซต์", details.plot)
        assertEquals("https://images.example/digger.jpg", details.posterUrl)
    }

    @Test
    fun `removes breadcrumb without modifying document and labels description fallback`() {
        val document = Jsoup.parse("""
            <meta property="og:title" content="">
            <meta property="og:description" content="">
            <meta name="description" content="ดูหนัง Digger หมวดหนังฝรั่ง คะแนน IMDb 7.5">
            <h1><a><img alt="Search Google"></a>Digger (2026)<div class="Breadcrumb">หน้าแรก</div></h1>
            <div class="konuozet"><p> </p></div>
        """)
        val details = MovieDetailsParser.parse(document)
        assertEquals("Digger (2026)", details.title)
        assertEquals("ข้อมูลจากเว็บไซต์: ดูหนัง Digger หมวดหนังฝรั่ง คะแนน IMDb 7.5", details.plot)
        assertTrue(document.selectFirst("h1")!!.text().contains("หน้าแรก"))
    }

    @Test
    fun `skips empty synopsis candidates and preserves legacy detail selectors`() {
        val details = MovieDetailsParser.parse(Jsoup.parse("""
            <h1>Legacy</h1><div class="synopsis"> </div>
            <div class="entry-content"><p></p><p>Legacy plot</p><img src="/poster.jpg"></div>
        """))
        assertEquals("Legacy plot", details.plot)
        assertEquals("/poster.jpg", details.posterUrl)
    }

    @Test
    fun `does not fabricate a missing synopsis`() {
        val details = MovieDetailsParser.parse(Jsoup.parse("<h1>Empty</h1><div class='konuozet'> </div>"))
        assertNull(details.plot)
    }
}
