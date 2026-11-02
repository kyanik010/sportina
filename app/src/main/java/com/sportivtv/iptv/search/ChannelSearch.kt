package com.sportivtv.iptv.search

import com.sportivtv.iptv.model.Channel

/**
 * Channel search engine.
 * Searches by name, group, and category with fuzzy matching.
 *
 * Supports Arabic and English search terms.
 */
class ChannelSearch {

    /**
     * Search channels by query string.
     * @param channels List of channels to search
     * @param query Search query (matches name or group)
     * @return Filtered list of matching channels
     */
    fun search(channels: List<Channel>, query: String): List<Channel> {
        if (query.isBlank()) return channels

        val normalizedQuery = normalize(query)

        return channels.filter { channel ->
            val normalizedName = normalize(channel.name)
            val normalizedGroup = normalize(channel.groupTitle ?: "")

            // Match if query appears in name or group
            normalizedName.contains(normalizedQuery) ||
            normalizedGroup.contains(normalizedQuery) ||
            // Also check if name starts with query (for quick type-ahead)
            normalizedName.startsWith(normalizedQuery)
        }
    }

    /**
     * Normalize text for search: lowercase, remove diacritics, trim.
     * Handles Arabic and English text.
     */
    private fun normalize(text: String): String {
        return text
            .lowercase()
            .trim()
            // Remove Arabic diacritics
            .replace("[\u064B-\u065F\u0670]", "")
            // Normalize Arabic letters
            .replace("\u0623", "\u0627") // أ → ا
            .replace("\u0625", "\u0627") // إ → ا
            .replace("\u0622", "\u0627") // آ → ا
            .replace("\u0649", "\u064A") // ى → ي
            .replace("\u0629", "\u0647") // ة → ه (common in search)
            // Remove extra spaces
            .replace("\\s+".toRegex(), " ")
    }
}
