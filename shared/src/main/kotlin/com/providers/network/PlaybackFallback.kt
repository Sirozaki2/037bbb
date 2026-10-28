package com.providers.network

import com.lagradost.cloudstream3.ErrorLoadingException
import com.lagradost.cloudstream3.utils.ExtractorLink
import kotlin.coroutines.cancellation.CancellationException

/** Only media reaches the native player; a website is opened as a separate fallback. */
internal suspend fun withBrowserFallback(
    pageUrl: String,
    callback: (ExtractorLink) -> Unit,
    openBrowser: (suspend (String) -> Boolean)? = null,
    extract: suspend ((ExtractorLink) -> Unit) -> Boolean,
): Boolean {
    var emitted = false
    var failure: Exception? = null
    try {
        extract { emitted = true; callback(it) }
    } catch (error: Exception) {
        if (error is CancellationException) throw error
        failure = error
    }
    if (emitted) return true
    val reason = failure?.message?.takeIf { it.isNotBlank() }
        ?: "หน้าเว็บไม่ส่งลิงก์วิดีโอที่ตัวอ่านรองรับ"
    val opened = openBrowser?.invoke(pageUrl) ?: ProviderBrowser.open(pageUrl, reason)
    throw ErrorLoadingException(
        (if (opened) "เปิดเว็บสำรองแล้ว กรุณาเลือกตัวเล่นและกดเล่นในหน้าเว็บ"
        else "ยังอ่านลิงก์วิดีโอไม่ได้ กรุณาลองตัวเล่นอื่นหรือเปิดหน้าตอนในเบราว์เซอร์") + " ($reason)"
    )
}
