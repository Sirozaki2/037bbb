package com.providers.network

import org.jsoup.Jsoup
import org.junit.Assert.*
import org.junit.Test

class CatalogPaginationTest {
    @Test fun `path pagination retains raw query escapes and replaces the page suffix`() {
        val base = "https://catalog.example/category/%e0%b8%ab/page/2/?q=a%2Bb%20c&lang=th"
        assertEquals("https://catalog.example/category/%e0%b8%ab/page/3/?q=a%2Bb%20c&lang=th", CatalogPagination.path(base, 3))
        assertEquals("https://catalog.example/category/%e0%b8%ab/?q=a%2Bb%20c&lang=th", CatalogPagination.path(base, 1))
    }
    @Test fun `query pagination replaces duplicate page parameters and preserves other filters`() {
        assertEquals("https://catalog.example/category/2?q=a%2Bb&lang=th&page=4",
            CatalogPagination.query("https://catalog.example/category/2?page=2&q=a%2Bb&page=3&lang=th#top", 4))
        assertEquals("https://catalog.example/?page=2", CatalogPagination.query("https://catalog.example/", 2))
    }
    @Test fun `non navigation and normalized current links cannot request another page`() {
        val base = "https://catalog.example/category/2?page=2"
        for (value in listOf("", " ", "#", "#top", "?page=2", "https://catalog.example:443/category/2?page=2#top", "javascript:next()", "data:text/html,next", "https://user@catalog.example/page/3/")) {
            assertFalse(value, CatalogPagination.isNext(base, value))
        }
        assertTrue(CatalogPagination.isNext(base, "?page=3#top"))
        assertTrue(CatalogPagination.isNext(base, "/category/2?page=3"))
    }
    @Test fun `next controls are scoped by the provider selector`() {
        val doc = Jsoup.parse("""<a class=next href=/advertisement/>Ad</a><nav class=pages><a class=next href=#>Next</a></nav>""", "https://catalog.example/")
        assertFalse(CatalogPagination.hasNext(doc, ".pages a.next[href]"))
        doc.selectFirst(".pages a")!!.attr("href", "/page/2/")
        assertTrue(CatalogPagination.hasNext(doc, ".pages a.next[href]"))
    }
}
