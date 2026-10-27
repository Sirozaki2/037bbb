package com.thaianime

import com.fasterxml.jackson.databind.ObjectMapper
import okhttp3.*
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.ByteString.Companion.encodeUtf8
import org.jsoup.Jsoup
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.concurrent.TimeUnit

class AnimeMamiTest {
    private val page = "https://animemami.xyz/v/IsnnQ24Kh63ihTU"
    private val json = ObjectMapper()
    private val playlist = "#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=800000\nlevel.txt?sig=keep\n"
    private fun doc(video: Map<String, Any>, settings: Map<String, Any> = emptyMap()) = Jsoup.parse("<div id=app></div>", page).apply {
        selectFirst("#app")!!.attr("data-page", json.writeValueAsString(mapOf("component" to "Player/Show", "props" to mapOf(
            "video" to video, "settings" to settings, "vast_ads" to listOf(mapOf("url" to "https://ad.invalid/ad.m3u8"))
        ))))
    }
    @Test fun `supplied AnimeMami page resolves content prop rather than ad or default video`() {
        val file = File("../samples/animeruka.com").walkTopDown().first { it.name == "IsnnQ24Kh63ihTU.html" }
        val stream = AnimeMami.stream(Samples.doc(file))!!
        assertTrue(stream.hls)
        assertTrue(stream.url.startsWith("https://cdn2.maimeorder.com/hls/"))
        assertTrue(stream.url.endsWith(".txt"))
        assertNull(stream.subtitle)
        assertEquals("thai", stream.subtitleLanguage)
    }
    @Test fun `content type and subtitles follow player props preserving signed queries`() {
        val stream = AnimeMami.stream(doc(mapOf("url" to "https://cdn.example/master.txt?sig=keep", "type" to "direct", "subtitle_path" to "captions/ep.vtt"),
            mapOf("subtitle_languages" to "Thai,English")))!!
        assertEquals("https://cdn.example/master.txt?sig=keep", stream.url)
        assertTrue(stream.hls)
        assertEquals("https://animemami.xyz/storage/captions/ep.vtt", stream.subtitle)
        assertEquals("Thai", stream.subtitleLanguage)
        assertFalse(AnimeMami.stream(doc(mapOf("url" to "https://cdn.example/movie.mp4", "type" to "direct")))!!.hls)
    }
    @Test fun `missing malformed or unrelated data cannot become playback links`() {
        assertNull(AnimeMami.stream(doc(emptyMap(),mapOf("default_video" to "https://ad.invalid/default.mp4"))))
        for (url in listOf("javascript:alert(1)","blob:https://animemami.xyz/id","https://user:pass@cdn.example/a.m3u8","/error/no_video")) {
            assertNull(AnimeMami.stream(doc(mapOf("url" to url))))
        }
        val valid = doc(mapOf("url" to "/master.m3u8"))
        valid.setBaseUri("https://animemami.xyz.evil.invalid/v/id")
        assertNull(AnimeMami.stream(valid))
        assertFalse(AnimeMami.isPlayer("https://animemami.xyz/login"))
        assertNull(AnimeMami.stream(Jsoup.parse("<div id=app data-page='broken'></div>",page)))
    }
    private class Chain(private val request: Request, private val bytes: ByteArray, private val status: Int = 200) : Interceptor.Chain {
        override fun request() = request
        override fun proceed(request: Request): Response = Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
            .code(status).message("Test").header("Content-Length",bytes.size.toString()).header("Content-Type","application/json")
            .body(bytes.toResponseBody()).build()
        override fun connection(): Connection? = null
        override fun call(): Call = error("Unused")
        override fun connectTimeoutMillis() = 1000
        override fun readTimeoutMillis() = 1000
        override fun writeTimeoutMillis() = 1000
        override fun withConnectTimeout(timeout: Int, unit: TimeUnit) = this
        override fun withReadTimeout(timeout: Int, unit: TimeUnit) = this
        override fun withWriteTimeout(timeout: Int, unit: TimeUnit) = this
    }
    @Test fun `same CDN wrapper decodes to HLS and retains signed relative segment URIs`() {
        val wrapped=json.writeValueAsString(mapOf("p" to playlist.encodeUtf8().base64())).toByteArray()
        val request=Request.Builder().url("https://cdn.example/master.txt?sig=keep").header("Referer",page).build()
        var delegated=false
        val upstream=Interceptor { chain -> delegated=true; assertEquals(page,chain.request().header("Referer"));chain.proceed(chain.request()) }
        AnimeMamiInterceptor("cdn.example",upstream).intercept(Chain(request,wrapped)).use {
            assertEquals(playlist,it.body.string())
            assertEquals("application/vnd.apple.mpegurl",it.header("Content-Type"))
            assertNull(it.header("Content-Length"))
            assertEquals(request.url,it.request.url)
        }
        assertTrue(delegated)
    }
    @Test fun `media plain playlists bad wrappers and error responses are unchanged`() {
        val cases=listOf(playlist.toByteArray() to 200, byteArrayOf(0,0,0,24,102,116,121,112) to 200,
            "{bad}".toByteArray() to 200, "{\"p\":\"invalid\"}".toByteArray() to 200,
            json.writeValueAsString(mapOf("p" to "<html>error</html>".encodeUtf8().base64())).toByteArray() to 200,
            "{\"p\":\"denied\"}".toByteArray() to 403, ByteArray(1_048_578){if(it==0)123 else 32} to 200)
        for((bytes,status) in cases) {
            val request=Request.Builder().url("https://cdn.example/part").build()
            AnimeMamiInterceptor("cdn.example").intercept(Chain(request,bytes,status)).use {
                assertArrayEquals(bytes,it.body.bytes());assertEquals(status,it.code)
            }
        }
    }
    @Test fun `other hosts do not receive the playlist or Cloudflare adapter`() {
        val bytes=json.writeValueAsString(mapOf("p" to playlist.encodeUtf8().base64())).toByteArray()
        val request=Request.Builder().url("https://other.example/master.txt").build()
        AnimeMamiInterceptor("cdn.example",Interceptor { error("Must not delegate other hosts") }).intercept(Chain(request,bytes)).use {
            assertArrayEquals(bytes,it.body.bytes())
        }
    }
}
