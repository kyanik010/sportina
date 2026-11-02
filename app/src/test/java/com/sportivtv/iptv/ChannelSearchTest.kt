package com.sportivtv.iptv.search

import com.sportivtv.iptv.model.Channel
import com.sportivtv.iptv.model.ChannelCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Channel Search unit tests.
 */
class ChannelSearchTest {

    private val search = ChannelSearch()

    private val testChannels = listOf(
        Channel(id = "1", name = "beIN SPORTS 1 HD", streamUrl = "http://example.com/1", category = ChannelCategory.SPORTS),
        Channel(id = "2", name = "beIN SPORTS 2 HD", streamUrl = "http://example.com/2", category = ChannelCategory.SPORTS),
        Channel(id = "3", name = "Al Jazeera", streamUrl = "http://example.com/3", category = ChannelCategory.NEWS),
        Channel(id = "4", name = "MBC 2", streamUrl = "http://example.com/4", category = ChannelCategory.MOVIES),
        Channel(id = "5", name = "قناة رياضية", streamUrl = "http://example.com/5", category = ChannelCategory.SPORTS)
    )

    @Test
    fun `search by English name returns matching channels`() {
        val results = search.search(testChannels, "beIN")
        assertEquals(2, results.size)
        assertTrue(results.all { it.name.contains("beIN") })
    }

    @Test
    fun `search by Arabic name returns matching channels`() {
        val results = search.search(testChannels, "رياض")
        assertEquals(1, results.size)
        assertEquals("قناة رياضية", results[0].name)
    }

    @Test
    fun `search with blank query returns all channels`() {
        val results = search.search(testChannels, "")
        assertEquals(5, results.size)
    }

    @Test
    fun `search with no matches returns empty list`() {
        val results = search.search(testChannels, "xyz_nonexistent")
        assertTrue(results.isEmpty())
    }

    @Test
    fun `search normalizes Arabic diacritics`() {
        val results = search.search(testChannels, "رياضية")
        assertEquals(1, results.size)
    }
}
