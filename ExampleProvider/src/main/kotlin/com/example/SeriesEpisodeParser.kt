package com.example

import org.jsoup.nodes.Document

internal data class SeriesEpisode(
    val name: String,
    val url: String,
    val season: Int,
    val number: Int
)

internal object SeriesEpisodeParser {
    private val seasonPattern = Regex("""(?:\bSeason\s*|ซีซั่น\s*|ซีซัน\s*|\bS(?=\d+E))([0-9]+)""", RegexOption.IGNORE_CASE)
    private val episodePattern = Regex("""(?:ตอนที่\s*|\bEpisode\s*|\bEP\.?\s*|S\d+E)([0-9]+)""", RegexOption.IGNORE_CASE)

    fun parse(document: Document, title: String): List<SeriesEpisode> {
        val defaultSeason = seasonPattern.find(title)?.groupValues?.get(1)?.toIntOrNull() ?: 1
        val links = document.select(
            "#source-select .ss-item a:has(.episope), #source-select .ss-item a[href*=/episode/], " +
                "div.ep-list a, ul.episodes li a, select.ep-select option"
        )
        val seen = mutableSetOf<String>()
        return links.mapNotNull { element ->
            val attribute = listOf("value", "data-url", "href")
                .firstOrNull { element.attr(it).isNotBlank() } ?: return@mapNotNull null
            if (element.attr(attribute).startsWith("#")) return@mapNotNull null
            val url = element.absUrl(attribute).substringBefore('#')
            if (!(url.startsWith("https://") || url.startsWith("http://")) || !seen.add(url)) {
                return@mapNotNull null
            }
            val name = element.attr("title").ifBlank { element.text() }.trim()
            val number = element.attr("data-episode").toIntOrNull()
                ?: episodePattern.find(name)?.groupValues?.get(1)?.toIntOrNull()
                ?: seen.size
            val season = element.attr("data-season").toIntOrNull()
                ?: seasonPattern.find(name)?.groupValues?.get(1)?.toIntOrNull()
                ?: defaultSeason
            SeriesEpisode(name.ifBlank { "ตอนที่ $number" }, url, season, number)
        }.sortedWith(compareBy({ it.season }, { it.number }))
    }
}
