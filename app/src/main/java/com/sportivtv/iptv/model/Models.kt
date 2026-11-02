package com.sportivtv.iptv.model

import kotlinx.serialization.Serializable

/**
 * IPTV Subscription entity.
 * Represents a provider connection with M3U playlist and optional EPG.
 */
@Serializable
data class Subscription(
    val id: Long = 0,
    val name: String,
    val playlistUrl: String,
    val epgUrl: String? = null,
    val username: String? = null,
    val password: String? = null,
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val lastSyncAt: Long? = null,
    val channelCount: Int = 0
)

/**
 * Parsed M3U channel entry.
 */
data class Channel(
    val id: String,               // tvg-id from M3U
    val name: String,             // Channel display name
    val logoUrl: String? = null,  // tvg-logo
    val streamUrl: String,        // Stream URL
    val groupTitle: String? = null, // group-title from M3U
    val category: ChannelCategory = ChannelCategory.OTHER,
    val epgChannelId: String? = null, // Maps to XMLTV channel id
    val subscriptionId: Long = 0
) {
    /**
     * Auto-categorize channel based on name/group keywords.
     */
    companion object {
        fun categorize(name: String, group: String?): ChannelCategory {
            val combined = (name + " " + (group ?: "")).lowercase()
            return when {
                combined.containsAny("sport", "رياض", "كرة", "football", "soccer", "bein", "nile sport") -> ChannelCategory.SPORTS
                combined.containsAny("movie", "film", "سينما", "افلام", "cinema") -> ChannelCategory.MOVIES
                combined.containsAny("news", "اخبار", "جزيرة", " العربية", "al arabiya") -> ChannelCategory.NEWS
                combined.containsAny("kid", "طفل", "اطفال", "cartoon", "سبايس", "toyor") -> ChannelCategory.KIDS
                combined.containsAny("doc", "وثائ", "national geographic", "discovery") -> ChannelCategory.DOCUMENTARY
                combined.containsAny("music", "موسيق", "clip", "mtv", "melody") -> ChannelCategory.MUSIC
                combined.containsAny("entertain", "ترفيه", "variety", "general") -> ChannelCategory.ENTERTAINMENT
                else -> ChannelCategory.OTHER
            }
        }

        private fun String.containsAny(vararg keywords: String): Boolean {
            return keywords.any { this.contains(it) }
        }
    }
}

/**
 * Channel category enum for filtering.
 */
enum class ChannelCategory(val key: String, val arabicName: String) {
    SPORTS("sports", "رياضة"),
    MOVIES("movies", "أفلام"),
    NEWS("news", "أخبار"),
    ENTERTAINMENT("entertainment", "ترفيه"),
    KIDS("kids", "أطفال"),
    DOCUMENTARY("documentary", "وثائقي"),
    MUSIC("music", "موسيقى"),
    ARABIC("arabic", "عربي"),
    INTERNATIONAL("international", "دولي"),
    OTHER("other", "أخرى"),
    ALL("all", "الكل")
}

/**
 * EPG program entry from XMLTV.
 */
data class EpgProgram(
    val channelId: String,
    val title: String,
    val description: String? = null,
    val startTime: Long,  // epoch millis
    val endTime: Long,    // epoch millis
    val category: String? = null
)

/**
 * Favorite channel record.
 */
data class Favorite(
    val id: Long = 0,
    val channelId: String,
    val channelName: String,
    val channelLogo: String? = null,
    val streamUrl: String,
    val addedAt: Long = System.currentTimeMillis()
)

/**
 * Watch state - persisted for resume.
 */
data class WatchState(
    val channelId: String,
    val channelName: String,
    val positionMs: Long = 0,
    val lastWatchedAt: Long = System.currentTimeMillis()
)

/**
 * Player settings.
 */
data class PlayerSettings(
    val hardwareAcceleration: Boolean = true,
    val bufferSizeMs: Int = 30000,
    val autoReconnect: Boolean = true,
    val maxReconnectAttempts: Int = 5,
    val reconnectDelayMs: Long = 2000,
    val defaultAudioOffsetMs: Long = 0,
    val externalAudioEnabled: Boolean = false,
    val preferredAspectRatio: String = "fit"
)
