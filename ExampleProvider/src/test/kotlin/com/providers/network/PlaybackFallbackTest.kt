package com.providers.network

import com.lagradost.cloudstream3.ErrorLoadingException
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

@Suppress("DEPRECATION", "DEPRECATION_ERROR")
class PlaybackFallbackTest {
    private fun media() = ExtractorLink("fixture", "primary", "https://cdn.example/movie.mp4", "https://catalog.example/", 720, ExtractorLinkType.VIDEO)

    @Test fun `playable primary survives a later broken backup without opening a browser`() { runBlocking {
        val links = mutableListOf<ExtractorLink>()
        val result = withBrowserFallback("https://catalog.example/ep-2", links::add, { error("Already has media") }) { emit ->
            emit(media())
            throw IOException("Backup unavailable")
        }
        assertTrue(result)
        assertEquals("https://cdn.example/movie.mp4", links.single().url)
    } }

    @Test fun `empty extraction opens actual episode page without emitting HTML as video`() { runBlocking {
        val links = mutableListOf<ExtractorLink>()
        var opened: String? = null
        val error = runCatching {
            withBrowserFallback("https://catalog.example/ep-2?lang=th", links::add, { opened = it; true }) { true }
        }.exceptionOrNull()
        assertTrue(error is ErrorLoadingException)
        assertTrue(error!!.message.orEmpty().contains("เปิดเว็บสำรองแล้ว"))
        assertEquals("https://catalog.example/ep-2?lang=th", opened)
        assertTrue(links.isEmpty())
    } }

    @Test fun `extraction error offers web fallback and retains useful cause if window is unavailable`() { runBlocking {
        val error = runCatching {
            withBrowserFallback("https://catalog.example/movie", {}, { false }) { throw IOException("HTTP 403") }
        }.exceptionOrNull()
        assertTrue(error is ErrorLoadingException)
        assertTrue(error!!.message.orEmpty().contains("HTTP 403"))
        assertTrue(error.message.orEmpty().contains("รูปเฟือง"))
    } }

    @Test fun `cancellation never opens another window`() { runBlocking {
        val expected = CancellationException("User left playback")
        val error = runCatching {
            withBrowserFallback("https://catalog.example/movie", {}, { error("Must not open") }) { throw expected }
        }.exceptionOrNull()
        assertSame(expected, error)
    } }
}
