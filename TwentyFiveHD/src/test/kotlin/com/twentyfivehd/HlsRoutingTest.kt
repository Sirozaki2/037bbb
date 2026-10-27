package com.twentyfivehd

import org.junit.Assert.*
import org.junit.Test

class HlsRoutingTest {
    private val asset = "/hls/1234567890abcdef12345678"
    private val master = "https://gateway.example$asset/t.token/_master?gw_enc=o4"
    private val manifest = """{"PATHWAY-PRIORITY":["b","a","."],"PATHWAY-CLONES":[
        {"ID":"a","URI-REPLACEMENT":{"HOST":"cdn-a.example"}},
        {"ID":"b","URI-REPLACEMENT":{"HOST":"cdn-b.example"}}
    ]}"""
    private val routing = HlsRouting.fromManifest(master, manifest)

    @Test fun `routes video audio init and key files in advertised priority order`() {
        for (file in listOf("1080p/1.m4s", "_a_1/1.aac", "1080p/init.mp4", "encryption.key")) {
            assertEquals(listOf("https://cdn-b.example$asset/$file?exp=123&keep=yes", "https://cdn-a.example$asset/$file?exp=123&keep=yes"),
                routing.mediaUrls("https://gateway.example$asset/$file?sig=signed&exp=123&gw_enc=o4&keep=yes"))
        }
    }

    @Test fun `keeps signed video audio and master playlists at gateway`() {
        for (path in listOf("1080p/_index", "_a_1/_index", "720p/index.m3u8", "t.token/_master"))
            assertTrue(routing.mediaUrls("https://gateway.example$asset/$path?sig=signed&gw_enc=o4").isEmpty())
    }

    @Test fun `routes subtitle playlists and subtitle files without dropping tracks`() {
        for (path in listOf("_s/_tha", "_s/_eng", "_s/tha.vtt"))
            assertEquals("https://cdn-b.example$asset/$path", routing.mediaUrls("https://gateway.example$asset/$path?gw_enc=o4").first())
    }

    @Test fun `does not route other assets or unrelated requests`() {
        assertTrue(routing.mediaUrls("https://gateway.example/hls/abcdef1234567890abcdef12/1.ts").isEmpty())
        assertTrue(routing.mediaUrls("https://gateway.example/ads/ad.mp4").isEmpty())
    }

    @Test fun `reads relative steering URL and round trips playback metadata`() {
        assertEquals("https://gateway.example/hls/routing.json", HlsRouting.steeringUrl(master,
            "#EXTM3U\n#EXT-X-CONTENT-STEERING:SERVER-URI=\"/hls/routing.json\",PATHWAY-ID=\".\""))
        val url = "https://gateway.example$asset/1.m4s"
        assertEquals(routing.mediaUrls(url), HlsRouting.decode(routing.encode())!!.mediaUrls(url))
        assertNull(HlsRouting.decode(null))
        assertNull(HlsRouting.steeringUrl(master, "#EXTM3U"))
    }

    @Test fun `ignores malformed routing hosts and deduplicates mirrors`() {
        val route = HlsRouting.fromManifest(master, """{"PATHWAY-CLONES":[
            {"ID":"a","URI-REPLACEMENT":{"HOST":"cdn.example"}},
            {"ID":"b","URI-REPLACEMENT":{"HOST":"cdn.example"}},
            {"ID":"c","URI-REPLACEMENT":{"HOST":"cdn.example/unexpected"}}
        ]}""")
        assertEquals(1, route.mediaUrls("https://gateway.example$asset/1.ts").size)
    }
}
