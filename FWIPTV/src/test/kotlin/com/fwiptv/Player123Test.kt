package com.fwiptv

import org.junit.Assert.*
import org.junit.Test

class Player123Test {
    @Test fun `real captured embed maps to API and parent origin`() {
        val request = Player123.request("https://nom-na.player123up.xyz/embed/33202198-a103-4423-9eee-0746c6b9798c", "https://fwip-tv.com/movie/?query=1")!!
        assertEquals("https://nom-na.player123up.xyz/api/backend", request.api)
        assertEquals("33202198-a103-4423-9eee-0746c6b9798c", request.id)
        assertEquals(mapOf("x-player-origin" to "https://nom-na.player123up.xyz", "x-embed-origin" to "https://fwip-tv.com"), request.headers)
        assertNull(Player123.request("https://player123up.xyz.evil.invalid/embed/123", "https://fwip-tv.com"))
        assertNull(Player123.request("https://nom-na.player123up.xyz/not-embed/123", "https://fwip-tv.com"))
    }
    @Test fun `secure source retains backend prefix query and media type`() {
        val parsed = Player123.result("""{"url":"/playback/secure/session/master.m3u8?token=sample","backupUrl":"https://backup.example/embed/id"}""", "https://player123up.xyz/api/backend")
        assertEquals("https://player123up.xyz/api/backend/playback/secure/session/master.m3u8?token=sample", parsed.source!!.url)
        assertTrue(parsed.source.hls)
        assertEquals("https://backup.example/embed/id", parsed.backup)
    }
    @Test fun `invalid and opaque responses never become fake video links`() {
        for (body in listOf("<html>blocked</html>", "{}", """{"url":"javascript:alert(1)"}""", """{"url":"/playback/secure/opaque"}""")) {
            assertNull(Player123.result(body, "https://player123up.xyz/api/backend").source)
        }
        assertFalse(Player123.result("""{"url":"https://cdn.example/video.mp4"}""", "https://player123up.xyz/api/backend").source!!.hls)
    }
    @Test fun `only available secondary language is requested and label is retained`() {
        assertEquals("other-id" to "พากย์ไทย", Player123.thaiVersion("""{"available":true,"videoId":"other-id","label":"พากย์ไทย"}"""))
        assertNull(Player123.thaiVersion("""{"available":false,"videoId":"other-id"}"""))
        assertNull(Player123.thaiVersion("""{"available":true,"videoId":"../bad"}"""))
    }
}
