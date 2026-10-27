package com.movie2free

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jsoup.nodes.Document
import kotlin.coroutines.cancellation.CancellationException

internal data class Stream(val url: String, val referer: String, val label: String)

internal object Movie2FreeResolver {
    private fun knownHost(url: String): Boolean {
        val host = url.toHttpUrlOrNull()?.host ?: return false
        return listOf("ssplayer168.xyz", "fastcdnv2.com", "fastfastcdn.com").any { host == it || host.endsWith(".$it") }
    }
    suspend fun resolve(
        initial: String, referer: String,
        fetch: suspend (String, String) -> Document,
        emit: suspend (Stream) -> Unit,
        fallback: suspend (String, String) -> Unit,
    ) {
        val visited = mutableSetOf<String>()
        val emitted = mutableSetOf<String>()
        suspend fun walk(url: String, parent: String, label: String, depth: Int) {
            if (depth > 3 || visited.size >= 12 || !visited.add(url)) return
            try {
                if (!knownHost(url)) { fallback(url, parent); return }
                val doc = fetch(url, parent)
                Movie2FreeParser.streams(doc).forEach { stream ->
                    if (emitted.add(stream)) emit(Stream(stream, doc.baseUri(), label))
                }
                for (player in Movie2FreeParser.players(doc)) walk(player.url, doc.baseUri(), player.label, depth + 1)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                // A broken mirror must not suppress the remaining mirrors.
            }
        }
        walk(initial, referer, "ตัวเล่น", 0)
    }
}
