package com.sportivtv.iptv.favorites

import android.content.Context
import android.content.SharedPreferences
import com.sportivtv.iptv.model.Channel
import com.sportivtv.iptv.model.Favorite
import org.json.JSONArray
import org.json.JSONObject

/**
 * Favorites Manager.
 * Stores favorite channels in SharedPreferences as JSON.
 *
 * In production this would use Room DB, but SharedPreferences is sufficient
 * for the typical number of favorite channels (usually < 100).
 */
class FavoritesManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("sportiv_favorites", Context.MODE_PRIVATE)

    /**
     * Get all favorite channels.
     */
    fun getFavorites(): List<Favorite> {
        val json = prefs.getString(KEY_FAVORITES, null) ?: return emptyList()
        return try {
            val array = JSONArray(json)
            (0 until array.length()).map { i ->
                val obj = array.getJSONObject(i)
                Favorite(
                    id = obj.getLong("id"),
                    channelId = obj.getString("channelId"),
                    channelName = obj.getString("channelName"),
                    channelLogo = obj.optString("channelLogo", null),
                    streamUrl = obj.getString("streamUrl"),
                    addedAt = obj.getLong("addedAt"),
                    category = runCatching {
                        ChannelCategory.valueOf(obj.optString("category", "OTHER"))
                    }.getOrDefault(ChannelCategory.OTHER)
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Add a channel to favorites.
     */
    fun addFavorite(channel: Channel) {
        val favorites = getFavorites().toMutableList()
        // Check if already exists
        if (favorites.any { it.channelId == channel.id }) return

        favorites.add(
            Favorite(
                channelId = channel.id,
                channelName = channel.name,
                channelLogo = channel.logoUrl,
                streamUrl = channel.streamUrl,
                category = channel.category
            )
        )
        saveFavorites(favorites)
    }

    /**
     * Remove a channel from favorites.
     */
    fun removeFavorite(channelId: String) {
        val favorites = getFavorites().toMutableList()
        favorites.removeAll { it.channelId == channelId }
        saveFavorites(favorites)
    }

    /**
     * Check if a channel is in favorites.
     */
    fun isFavorite(channelId: String): Boolean {
        return getFavorites().any { it.channelId == channelId }
    }

    /**
     * Toggle favorite status of a channel.
     * @return true if channel is now a favorite, false if removed.
     */
    fun toggleFavorite(channel: Channel): Boolean {
        return if (isFavorite(channel.id)) {
            removeFavorite(channel.id)
            false
        } else {
            addFavorite(channel)
            true
        }
    }

    private fun saveFavorites(favorites: List<Favorite>) {
        val array = JSONArray()
        favorites.forEach { fav ->
            val obj = JSONObject()
            obj.put("id", fav.id)
            obj.put("channelId", fav.channelId)
            obj.put("channelName", fav.channelName)
            obj.put("channelLogo", fav.channelLogo ?: JSONObject.NULL)
            obj.put("streamUrl", fav.streamUrl)
            obj.put("addedAt", fav.addedAt)
            obj.put("category", fav.category.name)
            array.put(obj)
        }
        prefs.edit().putString(KEY_FAVORITES, array.toString()).apply()
    }

    companion object {
        private const val KEY_FAVORITES = "favorites_list"
    }
}
