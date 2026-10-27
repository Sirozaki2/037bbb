package com.example

import org.jsoup.nodes.Document

internal data class MoviePlayer(val label: String, val url: String, val language: String? = null)

internal object MoviePlayerParser {
    fun language(text: String): String? = when {
        listOf("ซับไทย", "บรรยายไทย", "soundtrack").any { text.contains(it, ignoreCase = true) } -> "ซับไทย"
        text.contains("พากย์ไทย") || Regex("""\bthai\b""", RegexOption.IGNORE_CASE).containsMatchIn(text) -> "พากย์ไทย"
        else -> null
    }

    fun parse(document: Document): List<MoviePlayer> {
        val elements = document.getAllElements().toList()
        val candidates = document.select("iframe, [data-url], [data-iframe], .source-switcher [data-src]")
        val first = candidates.firstOrNull() ?: return emptyList()
        // Start at the movie heading, after navigation/category links that mention Soundtrack.
        val start = elements.take(elements.indexOf(first)).indexOfLast { it.tagName() == "h1" }.coerceAtLeast(0)
        val last = elements.indexOf(candidates.last())
        var sectionLanguage: String? = null
        val players = mutableListOf<MoviePlayer>()
        for (element in elements.subList(start, last + 1)) {
            if (element.tagName() != "script" && element.tagName() != "style") {
                val marker = if (element.tagName() == "img") {
                    "${element.attr("alt")} ${element.attr("src")}"
                } else element.ownText()
                language(marker)?.let { sectionLanguage = it }
            }
            if (element !in candidates) continue
            val raw = sequenceOf("data-url", "data-iframe", "data-src", "src")
                .map { element.attr(it) }.firstOrNull { it.isNotBlank() } ?: continue
            val url = StreamHlsResolver.resolveUrl(document.baseUri(), raw) ?: continue
            if (listOf("facebook", "youtube", "google").any { url.contains(it, ignoreCase = true) }) continue
            val explicit = language("${element.attr("title")} ${element.attr("data-language")}")
            val label = element.text().trim().ifBlank { "ตัวเล่นหลัก" }
            players += MoviePlayer(label, url, explicit ?: sectionLanguage)
        }
        // Older mixed-language pages put an unlabelled dub player above the Soundtrack divider.
        // This convention is confirmed by the reported Fast 9 page; never guess on a single player.
        val mixed = players.size > 1 && players.any { it.language == "ซับไทย" }
        return players.map {
            if (mixed && it.language == null) it.copy(language = "พากย์ไทย") else it
        }.distinctBy { it.url }
    }

    fun sourceLabel(player: MoviePlayer, serverLabel: String): String =
        if (language(serverLabel) != null) serverLabel
        else "${player.language ?: "ไม่ระบุภาษา"} - $serverLabel"
}
