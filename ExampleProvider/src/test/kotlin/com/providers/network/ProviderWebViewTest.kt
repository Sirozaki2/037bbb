package com.providers.network

import android.app.Activity
import android.content.Intent
import android.content.ActivityNotFoundException
import org.robolectric.shadows.ShadowDialog
import android.os.Looper
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebView
import android.widget.Button
import android.view.ViewGroup
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class ProviderWebViewTest {
    @Test fun `web fallback retains cookies agent and failure reason until explicitly closed`() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val activity = controller.get()
        CookieManager.getInstance().setCookie("https://catalog.example", "session=keep")
        var callbacks = 0
        var ua: String? = null
        val browser = ProviderWebView(activity, "https://catalog.example/ep-2", "โหลดหน้าเว็บไม่ได้ (HTTP 403)", { ua = it }) { callbacks++ }
        browser.show()
        assertTrue(browser.dialog.isShowing)
        assertEquals("https://catalog.example/ep-2", shadowOf(browser.webView).lastLoadedUrl)
        assertTrue(browser.webView.settings.javaScriptEnabled)
        assertTrue(browser.webView.settings.domStorageEnabled)
        assertFalse(ua.isNullOrBlank())
        assertEquals("session=keep", CookieManager.getInstance().getCookie("https://catalog.example"))
        val labels = descendants(browser.dialog.window!!.decorView).filterIsInstance<android.widget.TextView>().map { it.text.toString() }
        assertTrue(labels.contains("โหลดหน้าเว็บไม่ได้ (HTTP 403)"))
        assertFalse(labels.any { it.contains("ยืนยัน Cloudflare") })
        shadowOf(Looper.getMainLooper()).idleFor(90, TimeUnit.SECONDS)
        assertTrue(browser.dialog.isShowing)
        assertEquals(0, callbacks)
        browser.dismiss()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1, callbacks)
        controller.destroy()
        shadowOf(Looper.getMainLooper()).idleFor(90, TimeUnit.SECONDS)
        assertEquals(1, callbacks)
    }

    @Test fun `Activity destruction releases the web fallback once`() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        var callbacks = 0
        val browser = ProviderWebView(controller.get(), "https://catalog.example") { callbacks++ }
        browser.show()
        controller.destroy()
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(browser.dialog.isShowing)
        assertEquals(1, callbacks)
    }

    private fun descendants(view: View): List<View> = listOf(view) +
        if (view is ViewGroup) (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()

    @Test fun `remote pointer clicks at moved coordinates stays in bounds and exits with Back`() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val touches = mutableListOf<Triple<Int, Float, Float>>()
        val target = object : WebView(controller.get()) {
            override fun onTouchEvent(event: MotionEvent): Boolean {
                touches += Triple(event.action, event.x, event.y)
                return true
            }
        }
        val pointer = RemoteCursor(controller.get(), target)
        pointer.layout(0, 0, 700, 350)
        pointer.onKeyDown(KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT))
        pointer.onKeyDown(KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER))
        assertEquals(listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP), touches.map { it.first })
        assertEquals(pointer.pointerX, touches.first().second)
        assertEquals(pointer.pointerY, touches.first().third)
        repeat(200) { pointer.onKeyDown(KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT)) }
        assertEquals(699f, pointer.pointerX)
        var exited = false
        pointer.exit = { exited = true }
        assertTrue(pointer.onKeyDown(KeyEvent.KEYCODE_BACK, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK)))
        assertTrue(exited)
        target.destroy()
        controller.destroy()
    }

    @Test fun `Back leaves remote pointer mode without closing the browser`() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val browser = ProviderWebView(controller.get(), "https://catalog.example")
        browser.show()
        fun descendants(view: View): List<View> = listOf(view) +
            if (view is ViewGroup) (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()
        val views = descendants(browser.dialog.window!!.decorView)
        views.filterIsInstance<Button>().first { it.text == "เคอร์เซอร์รีโมต" }.performClick()
        val pointer = views.filterIsInstance<RemoteCursor>().single()
        assertEquals(View.VISIBLE, pointer.visibility)
        browser.dialog.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK))
        browser.dialog.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK))
        assertEquals(View.GONE, pointer.visibility)
        assertTrue(browser.dialog.isShowing)
        browser.dismiss()
        controller.destroy()
    }

    class NoBrowserActivity : Activity() {
        override fun startActivity(intent: Intent) { throw ActivityNotFoundException("No TV browser installed") }
    }

    @Test fun `external fallback opens the exact episode when WebView cannot be used`() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val url = "https://catalog.example/ep-2?lang=th&token=a%2Bb"
        assertTrue(ProviderBrowser.openExternal(controller.get(), url))
        val intent = shadowOf(controller.get()).nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals(url, intent.dataString)
        controller.destroy()
    }

    @Test fun `TV without installed browser receives installation guidance instead of crashing`() {
        val controller = Robolectric.buildActivity(NoBrowserActivity::class.java).setup()
        assertFalse(ProviderBrowser.openExternal(controller.get(), "https://catalog.example"))
        val dialog = ShadowDialog.getLatestDialog()
        assertTrue(dialog.isShowing)
        dialog.dismiss()
        controller.destroy()
    }

    @Test fun `only real HTTP websites can be opened`() {
        assertTrue(ProviderWebView.isWebUrl("https://catalog.example/ep/1?token=a%2Bb"))
        listOf("javascript:alert(1)", "intent://foo", "file:///tmp/a", "https://user:pass@catalog.example/", "https:///movie").forEach {
            assertFalse(it, ProviderWebView.isWebUrl(it))
        }
    }
}
