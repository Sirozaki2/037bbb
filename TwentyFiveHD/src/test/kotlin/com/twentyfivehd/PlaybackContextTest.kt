package com.twentyfivehd

import org.junit.Assert.*
import org.junit.Test

class PlaybackContextTest {
    @Test fun `saved embed URLs retain query tokens and episode selection`() {
        val old = "https://player.example/embed?id=12&type=tv&season=2&episode=3&token=a%2Bb%3D"
        assertEquals(PlaybackContext(old, old), PlaybackContext.decode(old))
    }
    @Test fun `new context preserves both exact player and original series page`() {
        val context = PlaybackContext("https://player.example/embed?id=12&season=2&episode=3", "https://25-hd.com/series/?a=%2B")
        assertEquals(context, PlaybackContext.decode(context.encode()))
    }
}
