package com.example

import org.jsoup.Jsoup
import org.jsoup.nodes.Document

internal data class MovieDetails(val title: String, val posterUrl: String?, val plot: String?)

internal object MovieDetailsParser {
    fun parse(document: Document): MovieDetails {
        val heading = document.selectFirst(".filmcontent h1, h1, .entry-title, .movie-title")?.clone()
        heading?.select(".Breadcrumb, .breadcrumb, nav, img")?.remove()
        val title = document.selectFirst("meta[property=og:title]")?.attr("content")
            ?.trim()?.takeIf { it.isNotEmpty() }
            ?: heading?.text()?.trim()?.takeIf { it.isNotEmpty() }
            ?: "Unknown"
        val poster = document.selectFirst("meta[property=og:image]")?.attr("content")
            ?.takeIf { it.isNotBlank() }
            ?: document.selectFirst(".filmaltiimg img, div.poster img, .entry-content img")?.attr("src")
        val synopsis = document.select(".konuozet, .synopsis, div.entry-content p")
            .map { it.text().trim() }.firstOrNull { it.isNotEmpty() }
        val description = document.select("meta[property=og:description], meta[name=description]")
            .map { Jsoup.parseBodyFragment(it.attr("content")).text().trim() }
            .firstOrNull { it.isNotEmpty() }
        // Some entries have no synopsis at source; label the site's SEO description honestly.
        val plot = synopsis ?: description?.let { "ข้อมูลจากเว็บไซต์: $it" }
        return MovieDetails(title, poster, plot)
    }
}
