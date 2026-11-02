package com.sportivtv.iptv.subscription

import android.content.Context
import com.sportivtv.iptv.data.M3UParser
import com.sportivtv.iptv.model.Channel
import com.sportivtv.iptv.model.Subscription
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Subscription Manager.
 * Handles adding, testing, loading, and managing IPTV subscriptions.
 *
 * Downloads M3U playlists from provider URLs, parses them, and caches channels.
 * All network operations are on background threads.
 */
class SubscriptionManager(context: Context) {

    private val appContext = context.applicationContext
    private val m3uParser = M3UParser()
    private val secureStorage = SecureCredentialStorage(context)

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    // In-memory subscription store (in production, this would use Room DB)
    private val subscriptions = mutableMapOf<Long, Subscription>()
    private var nextId = 1L

    /**
     * Add a new subscription.
     */
    fun addSubscription(
        name: String,
        playlistUrl: String,
        epgUrl: String? = null,
        username: String? = null,
        password: String? = null
    ): Subscription {
        val id = nextId++
        val sub = Subscription(
            id = id,
            name = name,
            playlistUrl = playlistUrl,
            epgUrl = epgUrl,
            username = username,
            password = password
        )
        subscriptions[id] = sub

        // Securely store credentials if provided
        username?.let { secureStorage.storeUsername(id, it) }
        password?.let { secureStorage.storePassword(id, it) }

        return sub
    }

    /**
     * Update an existing subscription.
     */
    fun updateSubscription(subscription: Subscription) {
        subscriptions[subscription.id] = subscription
        subscription.username?.let { secureStorage.storeUsername(subscription.id, it) }
        subscription.password?.let { secureStorage.storePassword(subscription.id, it) }
    }

    /**
     * Delete a subscription and its credentials.
     */
    fun deleteSubscription(id: Long) {
        subscriptions.remove(id)
        secureStorage.removeCredentials(id)
    }

    /**
     * Get all subscriptions.
     */
    fun getSubscriptions(): List<Subscription> = subscriptions.values.toList()

    /**
     * Get a subscription by ID.
     */
    fun getSubscription(id: Long): Subscription? = subscriptions[id]

    /**
     * Test connection to a subscription's playlist URL.
     * Downloads the M3U file and verifies it's valid.
     * @return Result with channel count on success, error message on failure.
     */
    suspend fun testSubscription(url: String): TestResult = withContext(Dispatchers.IO) {
        try {
            val channels = downloadAndParsePlaylist(url, 0)
            if (channels.isEmpty()) {
                TestResult(false, "قائمة تشغيل فارغة", 0)
            } else {
                TestResult(true, "تم الاتصال بنجاح", channels.size)
            }
        } catch (e: Exception) {
            TestResult(false, e.message ?: "فشل الاتصال", 0)
        }
    }

    /**
     * Load channels from a subscription.
     * Downloads the M3U playlist, parses it, and returns channel list.
     * @param subscriptionId The subscription to load
     * @return List of channels (empty on failure)
     */
    suspend fun loadChannels(subscriptionId: Long): List<Channel> = withContext(Dispatchers.IO) {
        val sub = subscriptions[subscriptionId] ?: return@withContext emptyList()
        downloadAndParsePlaylist(sub.playlistUrl, subscriptionId)
    }

    /**
     * Load channels from all active subscriptions.
     */
    suspend fun loadAllChannels(): List<Channel> = withContext(Dispatchers.IO) {
        val allChannels = mutableListOf<Channel>()
        for (sub in subscriptions.values.filter { it.isActive }) {
            try {
                val channels = downloadAndParsePlaylist(sub.playlistUrl, sub.id)
                allChannels.addAll(channels)
            } catch (e: Exception) {
                // Continue with other subscriptions; don't fail entirely
            }
        }
        allChannels
    }

    /**
     * Download M3U playlist and parse it.
     */
    private fun downloadAndParsePlaylist(url: String, subscriptionId: Long): List<Channel> {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "SportivTV/1.0")
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("HTTP ${response.code}")
            }
            val body = response.body?.string()
                ?: throw Exception("Empty response body")

            return m3uParser.parse(body, subscriptionId)
        }
    }

    data class TestResult(
        val success: Boolean,
        val message: String,
        val channelCount: Int
    )
}
