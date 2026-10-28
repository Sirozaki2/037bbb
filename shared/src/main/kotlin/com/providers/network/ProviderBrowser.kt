package com.providers.network

import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import com.lagradost.cloudstream3.CommonActivity
import com.lagradost.cloudstream3.network.WebViewResolver
import com.lagradost.cloudstream3.plugins.Plugin
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

internal object ProviderBrowser {
    private val showing = AtomicBoolean(false)

    /** Settings remains reachable even when the catalog cannot load. */
    fun install(plugin: Plugin, sites: List<Pair<String, String>>) {
        plugin.openSettings = {
            val activity = CommonActivity.activity
            if (activity != null && !activity.isFinishing && !activity.isDestroyed) {
                AlertDialog.Builder(activity).setTitle("เปิดเว็บสำรอง")
                    .setItems(sites.map { it.first }.toTypedArray()) { _, index ->
                        watch(sites[index].second)
                    }.setNegativeButton("ปิด", null).show()
            }
        }
    }

    suspend fun open(url: String, reason: String? = null): Boolean = suspendCancellableCoroutine { continuation ->
        Handler(Looper.getMainLooper()).post {
            if (continuation.isActive) continuation.resume(watch(url, reason))
        }
    }

    private fun watch(url: String, reason: String? = null): Boolean {
        val activity = CommonActivity.activity ?: return false
        if (activity.isFinishing || activity.isDestroyed || showing.get() || !ProviderWebView.isWebUrl(url)) return false
        if (show(url, reason) != null) return true
        // Some TV firmware has no usable WebView. A separately installed browser can still play the page.
        return openExternal(activity, url)
    }

    internal fun openExternal(activity: Activity, url: String): Boolean {
        if (!ProviderWebView.isWebUrl(url)) return false
        return try {
            activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            true
        } catch (_: ActivityNotFoundException) {
            AlertDialog.Builder(activity).setTitle("เปิดเว็บไม่ได้")
                .setMessage("ไม่พบเบราว์เซอร์ที่เปิดเว็บนี้ได้ กรุณาติดตั้งเบราว์เซอร์ และตรวจสอบหรืออัปเดต WebView ของอุปกรณ์ แล้วลองอีกครั้ง")
                .setPositiveButton("ปิด", null).show()
            false
        } catch (_: SecurityException) {
            false
        }
    }

    private fun show(url: String, reason: String?): ProviderWebView? {
        val activity = CommonActivity.activity ?: return null
        if (!ProviderWebView.isWebUrl(url) || activity.isFinishing || activity.isDestroyed || !showing.compareAndSet(false, true)) return null
        return try {
            ProviderWebView(activity, url, reason, { WebViewResolver.webViewUserAgent = it }) {
                showing.set(false)
            }.also { it.show() }
        } catch (_: Exception) {
            showing.set(false)
            null
        }
    }
}
