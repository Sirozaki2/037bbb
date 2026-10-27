package com.example

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class StreamHlsResolverTest {
    private val host = "https://playlist.streamhls1688.com"
    private val ticket = "https://www.037-hdd.com/?streamhls_ticket=1&target=example"
    private val detail = "https://www.037-hdd.com/movie/"
    private val embed = "$host/embed/movie?site_auth=test"
    private val config = """<script>window.HLS_PLAYER_CONFIG = {
        sessionUrl: "/api/player/movie/session?ctx=test",
        playbackUrl: "/api/player/movie/playback?ctx=test"
    };</script>"""

    @Test
    fun `follows redirected host and session payload then resolves HLS against source page`() = runBlocking {
        val calls = mutableListOf<String>()
        val resolver = StreamHlsResolver(
            get = { url, referer ->
                calls += url
                when (url) {
                    ticket -> { assertEquals(detail, referer); PlayerPage(embed, config) }
                    "$host/api/player/movie/session?ctx=test" -> {
                        assertEquals(embed, referer)
                        PlayerPage(url, """{"success":true,"language":"พากย์ไทย",
                            "sources":[{"id":"tiktok","label":"ตัวเล่นหลัก"}],
                            "ad_gate":{"gate_token":"synthetic-gate","min_gate_seconds":0}}""")
                    }
                    "$host/player/source/v1.test" -> PlayerPage(url, """
                        var m3u8Url = "/sources/movie/master.m3u8?hls_token=synthetic";
                        var config = {"m3u8Url":"\/sources\/movie\/master.m3u8?hls_token=synthetic"};
                    """)
                    else -> error("Unexpected GET: $url")
                }
            },
            post = { url, referer, json ->
                assertEquals("$host/api/player/movie/playback?ctx=test", url)
                assertEquals(embed, referer)
                assertEquals(mapOf("source_id" to "tiktok", "gate_token" to "synthetic-gate"), json)
                PlayerPage(url, """{"success":true,"embed_url":"/player/source/v1.test"}""")
            }
        )
        val result = resolver.resolve(ticket, detail).single()
        assertEquals("$host/sources/movie/master.m3u8?hls_token=synthetic", result.url)
        assertEquals("$host/player/source/v1.test", result.referer)
        assertEquals("พากย์ไทย - ตัวเล่นหลัก", result.label)
        assertEquals(3, calls.size)
    }

    @Test
    fun `uses language button contexts and continues after a broken mirror`() = runBlocking {
        val resolver = StreamHlsResolver(
            get = { url, _ ->
                when {
                    url == ticket -> PlayerPage(embed, config + """
                        <a data-session-url="/thai/session?ctx=thai" data-playback-url="/thai/play?ctx=thai">พากย์ไทย</a>
                        <a data-session-url="/sub/session?ctx=sub" data-playback-url="/sub/play?ctx=sub">ซับไทย</a>
                    """)
                    url.contains("/session?") -> PlayerPage(url, """{"success":true,"sources":[
                        {"id":"broken","label":"Broken"},{"id":"backup","label":"Backup"}]}""")
                    url.contains("/source/") -> PlayerPage(url, """let m3u8Url = "../master.m3u8";""")
                    else -> error("Unexpected URL $url")
                }
            },
            post = { url, _, json ->
                if (json["source_id"] == "broken") error("Mirror unavailable")
                val language = if (url.contains("/thai/")) "thai" else "sub"
                PlayerPage(url, """{"success":true,"embed_url":"/$language/source/embed"}""")
            }
        )
        val results = resolver.resolve(ticket, detail)
        assertEquals(listOf("พากย์ไทย - Backup", "ซับไทย - Backup"), results.map { it.label })
        assertEquals(listOf("$host/thai/master.m3u8", "$host/sub/master.m3u8"), results.map { it.url })
    }

    @Test
    fun `does not emit advertisement playlists or unsuccessful playback responses`() = runBlocking {
        val resolver = StreamHlsResolver(
            get = { url, _ ->
                if (url == ticket) PlayerPage(embed, config) else PlayerPage(url, """
                    {"success":true,"sources":[{"id":"main"}],"ad_gate":{"ads":[
                      {"media_url":"https://ads.example/master.m3u8"}]}}
                """)
            },
            post = { url, _, _ -> PlayerPage(url, """{"success":false,"embed_url":"/denied"}""") }
        )
        assertTrue(resolver.resolve(ticket, detail).isEmpty())
    }

    @Test
    fun `preserves direct HLS embeds with absolute and protocol relative sources`() = runBlocking {
        val resolver = StreamHlsResolver(
            get = { _, _ -> PlayerPage(embed, """
                const m3u8Url = "https://cdn.example/movie.m3u8?token=a&b=c";
                var config = {'m3u8Url':'//backup.example/other.m3u8'};
            """) },
            post = { _, _, _ -> error("Direct embeds must not POST") }
        )
        assertEquals(listOf("https://cdn.example/movie.m3u8?token=a&b=c", "https://backup.example/other.m3u8"),
            resolver.resolve(ticket, detail).map { it.url })
    }

    @Test
    fun `preserves older direct source wrappers`() = runBlocking {
        val resolver = StreamHlsResolver(
            get = { url, _ ->
                if (url == ticket) PlayerPage(embed, "<script>var next = '/player/source/v1.legacy';</script>")
                else PlayerPage(url, """var m3u8Url = "/old/master.m3u8";""")
            },
            post = { _, _, _ -> error("Direct source must not POST") }
        )
        assertEquals("$host/old/master.m3u8", resolver.resolve(ticket, detail).single().url)
    }

    @Test
    fun `preserves legacy ctx playback flow using final player ID`() = runBlocking {
        val resolver = StreamHlsResolver(
            get = { url, _ ->
                if (url == ticket) PlayerPage(embed, "<script>var config = {'ctx':'old-context'};</script>")
                else PlayerPage(url, """var m3u8Url = "/legacy/master.m3u8";""")
            },
            post = { url, _, _ ->
                assertEquals("$host/api/player/movie/playback?ctx=old-context", url)
                PlayerPage(url, """{"embed_url":"/player/source/v1.old"}""")
            }
        )
        assertEquals("$host/legacy/master.m3u8", resolver.resolve(ticket, detail).single().url)
    }

    @Test
    fun `labels returned media with session language when button text conflicts`() = runBlocking {
        val resolver = variantResolver("""
            <a data-session-url="/thai/session" data-playback-url="/thai/play">บรรยายไทย</a>
        """, embed)
        assertEquals("พากย์ไทย - V2", resolver.resolve(ticket, detail).single().label)
    }

    private fun variantResolver(buttons: String, finalUrl: String): StreamHlsResolver =
        StreamHlsResolver(
            get = { url, _ ->
                when {
                    url == ticket -> PlayerPage(finalUrl, buttons)
                    url.endsWith("/session") -> {
                        val language = if (url.contains("/thai/")) "พากย์ไทย" else "ซับไทย"
                        PlayerPage(url, """{"success":true,"language":"$language",
                            "sources":[{"id":"tiktok","label":"V2"}]}""")
                    }
                    else -> PlayerPage(url, """var m3u8Url = "master.m3u8";""")
                }
            },
            post = { url, _, _ ->
                val language = if (url.contains("/thai/")) "thai" else "sub"
                PlayerPage(url, """{"success":true,"embed_url":"/$language/source"}""")
            }
        )

    @Test
    fun `rejects empty malformed and non web source URLs`() {
        assertNull(StreamHlsResolver.resolveUrl(embed, ""))
        assertNull(StreamHlsResolver.resolveUrl(embed, "javascript:alert(1)"))
        assertNull(StreamHlsResolver.resolveUrl(embed, "https://bad host/source"))
        assertEquals("https://playlist.streamhls1688.com", StreamHlsResolver.origin(embed))
    }
}
