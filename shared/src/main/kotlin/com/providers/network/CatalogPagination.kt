package com.providers.network

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jsoup.nodes.Document
import java.net.URI

/** Shared URL rules; each site keeps its own pagination style and listing selector. */
internal object CatalogPagination {
    private val pageSuffix = Regex("/page/\\d+$")

    fun path(base: String, page: Int): String {
        val uri = URI(base)
        val path = uri.rawPath.trimEnd('/').replace(pageSuffix, "")
        return "${uri.scheme}://${uri.rawAuthority}$path/" + (if (page > 1) "page/$page/" else "") +
            (uri.rawQuery?.let { "?$it" } ?: "")
    }

    fun query(base: String, page: Int): String {
        if (page <= 1) return base
        val uri = URI(base)
        val args = uri.rawQuery.orEmpty().split('&').filter { it.isNotBlank() && it.substringBefore('=') != "page" } + "page=$page"
        return "${uri.scheme}://${uri.rawAuthority}${uri.rawPath}?${args.joinToString("&")}"
    }

    fun isNext(base: String, value: String): Boolean {
        val href = value.trim()
        if (href.isEmpty() || href.startsWith('#')) return false
        val current = base.toHttpUrlOrNull()?.newBuilder()?.fragment(null)?.build() ?: return false
        val next = current.resolve(href)?.newBuilder()?.fragment(null)?.build() ?: return false
        return next.username.isEmpty() && next.password.isEmpty() && next != current
    }

    fun hasNext(document: Document, selector: String): Boolean = document.select(selector)
        .any { isNext(document.baseUri(), it.attr("href")) }
}
