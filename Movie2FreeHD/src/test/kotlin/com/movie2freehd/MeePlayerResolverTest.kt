package com.movie2freehd

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CancellationException

class MeePlayerResolverTest {
    private val id = "1234567890abcdef1234567890abcdef12"
    private val embed = "https://meeplayer.com/play/$id"
    private val page = "https://video1.meeplayer.com/p2p/$id?t=synthetic%2Btoken"
    @Test fun `follows redirect preserves token and resolves public playback API`() = runBlocking {
        val calls = mutableListOf<String>()
        val resolver = MeePlayerResolver(
            get = { url, ref ->
                calls += url
                if (url == embed) PlayerPage(page, "player") else {
                    assertEquals(page, ref)
                    PlayerPage(url, """{"video":{"md5":"$id","iStatus":1,"subtitle":"thai.vtt"}}""")
                }
            },
            post = { url, ref, payload ->
                assertEquals("https://video1.meeplayer.com/api/resolve", url)
                assertEquals(page, ref)
                assertEquals("synthetic+token", payload["token"])
                assertEquals(true, payload["hasSubtitle"])
                assertEquals(id, payload["videoId"])
                PlayerPage(url, """{"videoUrl":"https://meeplayer.com/hlsr2/$id/master","subtitleUrl":"/sub.vtt","cdnRotations":["https://cdn.example"]}""")
            }
        )
        val stream = resolver.resolve(embed, "https://movie2freehd.com/")!!
        assertEquals("https://video1.meeplayer.com/api/video/$id?t=synthetic%2Btoken", calls.last())
        assertEquals("https://meeplayer.com/hlsr2/$id/master", stream.url)
        assertEquals("https://video1.meeplayer.com/sub.vtt", stream.subtitle)
        assertNotNull(MeeRouting.decode(stream.routing))
    }
    @Test fun `unready videos and HTTP failures never request resolution`() = runBlocking {
        for ((status, body) in listOf(403 to "blocked", 200 to """{"video":{"md5":"$id","iStatus":0}}""")) {
            val resolver = MeePlayerResolver({ url, _ -> if (url == embed) PlayerPage(page, "") else PlayerPage(url, body, status) },
                { _, _, _ -> error("Must not resolve unavailable video") })
            assertNull(resolver.resolve(embed, "https://movie2freehd.com/"))
        }
    }
    @Test fun `blocked redirect and foreign host do not call playback APIs`() = runBlocking {
        for (url in listOf("https://meeplayer.com/blocked", "https://unrelated.example/p2p/$id")) {
            var calls = 0
            val resolver = MeePlayerResolver({ _, _ -> calls++; PlayerPage(url, "") }, { _, _, _ -> error("Unexpected POST") })
            assertNull(resolver.resolve(embed, "https://movie2freehd.com/"))
            assertEquals(1, calls)
        }
        assertFalse(MeePlayerResolver.isMeePlayer("https://meeplayer.com.evil.example/play/$id"))
    }
    @Test(expected = CancellationException::class) fun `cancellation is propagated`() = runBlocking {
        MeePlayerResolver({ _, _ -> throw CancellationException() }, { _, _, _ -> error("unused") }).resolve(embed, "")
        Unit
    }
}
