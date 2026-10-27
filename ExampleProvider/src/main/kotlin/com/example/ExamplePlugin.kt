package com.example

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context
import androidx.appcompat.app.AppCompatActivity

@CloudstreamPlugin
class ExamplePlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(ExampleProvider())
    }
}