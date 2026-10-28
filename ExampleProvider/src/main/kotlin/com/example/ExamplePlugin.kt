package com.example

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context
import com.providers.network.ProviderBrowser

@CloudstreamPlugin
class ExamplePlugin : Plugin() {
    override fun load(context: Context) {
        val provider = ExampleProvider()
        registerMainAPI(provider)
        ProviderBrowser.install(this, listOf(
            provider.name to provider.mainUrl,
            "Movie2Free.co (ปิดไว้: เปิดเว็บเท่านั้น)" to "https://movie2free.co",
            "FWIP TV (ปิดไว้: เปิดเว็บเท่านั้น)" to "https://fwip-tv.com",
        ))
    }
}