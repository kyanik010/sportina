package com.sportivtv.iptv.favorites

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.sportivtv.iptv.model.Channel
import com.sportivtv.iptv.model.ChannelCategory
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Favorites Manager unit tests.
 */
@RunWith(RobolectricTestRunner::class)
class FavoritesManagerTest {

    private lateinit var favoritesManager: FavoritesManager
    private val testChannel = Channel(
        id = "test_ch_1",
        name = "Test Channel",
        streamUrl = "http://example.com/stream",
        category = ChannelCategory.SPORTS
    )

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        favoritesManager = FavoritesManager(context)
        // Clear any existing favorites
        favoritesManager.removeFavorite(testChannel.id)
    }

    @Test
    fun `add favorite adds channel to favorites`() {
        favoritesManager.addFavorite(testChannel)
        assertTrue(favoritesManager.isFavorite(testChannel.id))
        assertEquals(1, favoritesManager.getFavorites().size)
    }

    @Test
    fun `remove favorite removes channel`() {
        favoritesManager.addFavorite(testChannel)
        favoritesManager.removeFavorite(testChannel.id)
        assertFalse(favoritesManager.isFavorite(testChannel.id))
    }

    @Test
    fun `toggle favorite adds then removes`() {
        val added = favoritesManager.toggleFavorite(testChannel)
        assertTrue(added)
        assertTrue(favoritesManager.isFavorite(testChannel.id))

        val removed = favoritesManager.toggleFavorite(testChannel)
        assertFalse(removed)
        assertFalse(favoritesManager.isFavorite(testChannel.id))
    }

    @Test
    fun `isFavorite returns false for non-existent channel`() {
        assertFalse(favoritesManager.isFavorite("non_existent_id"))
    }

    @Test
    fun `add favorite twice does not duplicate`() {
        favoritesManager.addFavorite(testChannel)
        favoritesManager.addFavorite(testChannel)
        assertEquals(1, favoritesManager.getFavorites().size)
    }
}
