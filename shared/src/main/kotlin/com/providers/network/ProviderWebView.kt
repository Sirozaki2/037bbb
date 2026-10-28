package com.providers.network

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import android.app.Dialog
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/** A visible, shared-cookie browser. No JS bridge or certificate-error override. */
@SuppressLint("SetJavaScriptEnabled")
internal class ProviderWebView(
    private val activity: Activity,
    private val url: String,
    private val cleared: (() -> Boolean)? = null,
    private val onUserAgent: (String) -> Unit = {},
    private val finished: (verified: Boolean, cancelled: Boolean) -> Unit = { _, _ -> },
) {
    internal val dialog = Dialog(activity)
    internal val webView = WebView(activity)
    private val handler = Handler(Looper.getMainLooper())
    private val root = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.rgb(24, 24, 24)) }
    private val frame = FrameLayout(activity)
    private var customView: View? = null
    private var customCallback: WebChromeClient.CustomViewCallback? = null
    private var verified = false
    private var cancelled = true
    private var disposed = false
    private var lifecycleRegistered = false
    private val cursor = RemoteCursor(activity, webView)
    private val lifecycle = object : Application.ActivityLifecycleCallbacks {
        override fun onActivityDestroyed(target: Activity) { if (target === activity) dialog.dismiss() }
        override fun onActivityCreated(target: Activity, state: Bundle?) = Unit
        override fun onActivityStarted(target: Activity) = Unit
        override fun onActivityResumed(target: Activity) = Unit
        override fun onActivityPaused(target: Activity) = Unit
        override fun onActivityStopped(target: Activity) = Unit
        override fun onActivitySaveInstanceState(target: Activity, state: Bundle) = Unit
    }
    private val poll = object : Runnable {
        override fun run() {
            if (disposed) return
            if (cleared?.invoke() == true) {
                verified = true
                cancelled = false
                dialog.dismiss()
            } else handler.postDelayed(this, 500)
        }
    }
    private val timeout = Runnable { cancelled = false; dialog.dismiss() }

    init {
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(root)
        val title = TextView(activity).apply {
            text = if (cleared != null) "ยืนยัน Cloudflare แล้วแอปจะลองโหลดอีกครั้ง (75 วินาที)" else "เว็บสำรอง — เลือกตัวเล่นและกดเล่นในหน้าเว็บ"
            setTextColor(Color.WHITE); setPadding(12, 8, 12, 4)
        }
        root.addView(title)
        root.addView(TextView(activity).apply {
            text = Uri.parse(url).host.orEmpty() + " • รีโมต: เปิดเคอร์เซอร์ → ลูกศรเลื่อน → OK กด → Back กลับปุ่ม"
            setTextColor(Color.LTGRAY); setPadding(12, 0, 12, 4)
        })
        val buttons = LinearLayout(activity)
        val toolbar = HorizontalScrollView(activity).apply { addView(buttons) }
        root.addView(toolbar)
        fun button(label: String, action: () -> Unit): Button = Button(activity).apply {
            text = label; setOnClickListener { action() }; buttons.addView(this)
        }
        button("ย้อนกลับ") { if (!hideFullscreen() && webView.canGoBack()) webView.goBack() }
        val cursorButton = button("เคอร์เซอร์รีโมต") { cursor.visibility = View.VISIBLE; cursor.requestFocus() }
        cursor.exit = { cursor.visibility = View.GONE; cursorButton.requestFocus() }
        button("เลื่อนขึ้น") { webView.pageUp(false) }
        button("เลื่อนลง") { webView.pageDown(false) }
        button("โหลดใหม่") { webView.reload() }
        button("เปิดเบราว์เซอร์") {
            if (ProviderBrowser.openExternal(activity, webView.url ?: url)) {
                Toast.makeText(activity, "คุกกี้ของเบราว์เซอร์ภายนอกไม่ส่งกลับเข้าแอป", Toast.LENGTH_LONG).show()
            }
        }
        button("ปิด") { dialog.dismiss() }
        root.addView(frame, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        frame.addView(webView, FrameLayout.LayoutParams(-1, -1))
        frame.addView(cursor, FrameLayout.LayoutParams(-1, -1))
        cursor.visibility = View.GONE
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = true
            allowFileAccess = false
            allowContentAccess = false
        }
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
        onUserAgent(webView.settings.userAgentString)
        webView.webViewClient = object : WebViewClient() {
            @Deprecated("Required for Android 5")
            override fun shouldOverrideUrlLoading(view: WebView, target: String): Boolean = !isWebUrl(target)
        }
        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                if (customView != null) { callback.onCustomViewHidden(); return }
                customView = view; customCallback = callback
                webView.visibility = View.GONE
                cursor.visibility = View.GONE
                frame.addView(view, FrameLayout.LayoutParams(-1, -1))
                view.requestFocus()
            }
            override fun onHideCustomView() { hideFullscreen() }
        }
        dialog.setOnKeyListener { _, key, event ->
            if (key != KeyEvent.KEYCODE_BACK) false
            else {
                if (event.action == KeyEvent.ACTION_UP) when {
                    cursor.visibility == View.VISIBLE -> cursor.exit()
                    hideFullscreen() -> Unit
                    webView.canGoBack() -> webView.goBack()
                    else -> dialog.dismiss()
                }
                true
            }
        }
        dialog.setOnDismissListener { dispose() }
    }

    fun show() {
        check(isWebUrl(url)) { "Unsupported website URL" }
        try {
            dialog.show()
            dialog.window?.setLayout((activity.resources.displayMetrics.widthPixels * .96).toInt(), (activity.resources.displayMetrics.heightPixels * .94).toInt())
            activity.application.registerActivityLifecycleCallbacks(lifecycle)
            lifecycleRegistered = true
            webView.loadUrl(url)
            if (cleared != null) {
                handler.post(poll)
                handler.postDelayed(timeout, 75_000)
            }
        } catch (error: Exception) {
            dispose()
            throw error
        }
    }

    fun dismiss() { dialog.dismiss() }

    private fun hideFullscreen(): Boolean {
        val view = customView ?: return false
        frame.removeView(view)
        customView = null
        customCallback?.onCustomViewHidden()
        customCallback = null
        webView.visibility = View.VISIBLE
        return true
    }

    private fun dispose() {
        if (disposed) return
        disposed = true
        handler.removeCallbacksAndMessages(null)
        if (lifecycleRegistered) activity.application.unregisterActivityLifecycleCallbacks(lifecycle)
        hideFullscreen()
        CookieManager.getInstance().flush()
        frame.removeView(webView)
        webView.stopLoading()
        webView.destroy()
        finished(verified, cancelled)
    }

    companion object {
        internal fun isWebUrl(url: String): Boolean = runCatching {
            val uri = java.net.URI(url)
            uri.scheme in listOf("https", "http") && !uri.host.isNullOrBlank() && uri.rawUserInfo == null
        }.getOrDefault(false)
    }
}

/** Pointer for WebView controls that cannot receive DPAD focus (including challenge widgets). */
internal class RemoteCursor(activity: Activity, private val target: WebView) : View(activity) {
    var exit: () -> Unit = {}
    internal var pointerX = 0f
    internal var pointerY = 0f
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.YELLOW; strokeWidth = 3f; style = Paint.Style.STROKE }
    init { isFocusable = true; isFocusableInTouchMode = true; contentDescription = "เคอร์เซอร์เว็บ: ใช้ลูกศรเลื่อนและ OK เพื่อกด" }
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) { pointerX = w / 2f; pointerY = h / 2f }
    override fun onDraw(canvas: Canvas) { canvas.drawCircle(pointerX, pointerY, 12f, paint); canvas.drawLine(pointerX - 18, pointerY, pointerX + 18, pointerY, paint); canvas.drawLine(pointerX, pointerY - 18, pointerX, pointerY + 18, paint) }
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val step = (height / 35f).coerceAtLeast(12f)
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> pointerX -= step
            KeyEvent.KEYCODE_DPAD_RIGHT -> pointerX += step
            KeyEvent.KEYCODE_DPAD_UP -> pointerY -= step
            KeyEvent.KEYCODE_DPAD_DOWN -> pointerY += step
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                if (event.repeatCount == 0) {
                    val now = SystemClock.uptimeMillis()
                    listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP).forEach { action ->
                        MotionEvent.obtain(now, now, action, pointerX, pointerY, 0).also { target.dispatchTouchEvent(it); it.recycle() }
                    }
                }
            }
            KeyEvent.KEYCODE_BACK -> { exit(); return true }
            else -> return super.onKeyDown(keyCode, event)
        }
        pointerX = pointerX.coerceIn(0f, (width - 1).coerceAtLeast(0).toFloat())
        pointerY = pointerY.coerceIn(0f, (height - 1).coerceAtLeast(0).toFloat())
        invalidate()
        return true
    }
    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean = when (keyCode) {
        KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> true
        else -> super.onKeyUp(keyCode, event)
    }
}
