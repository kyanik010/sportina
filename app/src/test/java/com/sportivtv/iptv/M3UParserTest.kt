package com.sportivtv.iptv.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * M3U Parser unit tests.
 * Tests parsing of standard M3U/M3U8 IPTV playlists.
 */
class M3UParserTest {

    private val parser = M3UParser()

    @Test
    fun `parse standard M3U playlist with multiple channels`() {
        val m3u = """
            #EXTM3U
            #EXTINF:-1 tvg-id="bein1" tvg-name="beIN SPORTS 1" tvg-logo="http://example.com/logo1.png" group-title="Sports",beIN SPORTS 1 HD
            http://stream.example.com/bein1.m3u8
            #EXTINF:-1 tvg-id="bein2" tvg-name="beIN SPORTS 2" tvg-logo="http://example.com/logo2.png" group-title="Sports",beIN SPORTS 2 HD
            http://stream.example.com/bein2.m3u8
            #EXTINF:-1 tvg-id="aljazeera" tvg-name="Al Jazeera" group-title="News",Al Jazeera Arabic
            http://stream.example.com/aljazeera.ts
        """.trimIndent()

        val channels = parser.parse(m3u, subscriptionId = 1)

        assertEquals(3, channels.size)
        assertEquals("beIN SPORTS 1 HD", channels[0].name)
        assertEquals("bein1", channels[0].id)
        assertEquals("http://stream.example.com/bein1.m3u8", channels[0].streamUrl)
        assertEquals("Sports", channels[0].groupTitle)
        assertEquals("http://example.com/logo1.png", channels[0].logoUrl)
    }

    @Test
    fun `parse empty playlist returns empty list`() {
        val channels = parser.parse("", subscriptionId = 1)
        assertTrue(channels.isEmpty())
    }

    @Test
    fun `parse playlist with no valid streams returns empty`() {
        val m3u = """
            #EXTM3U
            #EXTINF:-1,Channel Without URL
        """.trimIndent()
        val channels = parser.parse(m3u, subscriptionId = 1)
        assertTrue(channels.isEmpty())
    }

    @Test
    fun `parse handles missing attributes gracefully`() {
        val m3u = """
            #EXTM3U
            #EXTINF:-1,Simple Channel
            http://example.com/stream.ts
        """.trimIndent()
        val channels = parser.parse(m3u, subscriptionId = 1)

        assertEquals(1, channels.size)
        assertEquals("Simple Channel", channels[0].name)
        assertEquals("http://example.com/stream.ts", channels[0].streamUrl)
    }

    @Test
    fun `auto-categorization identifies sports channels`() {
        val m3u = """
            #EXTM3U
            #EXTINF:-1 tvg-id="s1" group-title="Sports",beIN SPORTS 1
            http://example.com/s1.ts
            #EXTINF:-1 tvg-id="n1" group-title="News",Al Jazeera News
            http://example.com/n1.ts
            #EXTINF:-1 tvg-id="m1" group-title="Movies",Cinema HD
            http://example.com/m1.ts
        """.trimIndent()
        val channels = parser.parse(m3u, subscriptionId = 1)

        assertEquals(com.sportivtv.iptv.model.ChannelCategory.SPORTS, channels[0].category)
        assertEquals(com.sportivtv.iptv.model.ChannelCategory.NEWS, channels[1].category)
        assertEquals(com.sportivtv.iptv.model.ChannelCategory.MOVIES, channels[2].category)
    }

    @Test
    fun `parse handles RTMP and RTSP URLs`() {
        val m3u = """
            #EXTM3U
            #EXTINF:-1,RTMP Channel
            rtmp://example.com/live/stream
            #EXTINF:-1,RTSP Channel
            rtsp://example.com/live/stream2
        """.trimIndent()
        val channels = parser.parse(m3u, subscriptionId = 1)

        assertEquals(2, channels.size)
        assertTrue(channels[0].streamUrl.startsWith("rtmp://"))
        assertTrue(channels[1].streamUrl.startsWith("rtsp://"))
    }
}
