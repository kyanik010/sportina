package com.sportivtv.iptv.epg

import android.content.Context
import com.sportivtv.iptv.data.EpgParser
import com.sportivtv.iptv.model.EpgProgram
import com.sportivtv.iptv.subscription.SubscriptionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * EPG Manager.
 * Downloads and caches XMLTV EPG data from subscription providers.
 *
 * Provides current/next program info for channels.
 * Caches EPG data to avoid repeated downloads.
 */
class EpgManager(
    context: Context,
    private val subscriptionManager: SubscriptionManager
) {
    private val epgParser = EpgParser()
    private var programsCache: List<EpgProgram> = emptyList()
    private var lastLoadTime: Long = 0
    private val cacheExpiryMs = 30 * 60 * 1000L // 30 minutes

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    /**
     * Load EPG data from all subscriptions that have EPG URLs.
     */
    suspend fun loadEpgData(forceRefresh: Boolean = false): List<EpgProgram> = withContext(Dispatchers.IO) {
        if (!forceRefresh && programsCache.isNotEmpty() &&
            System.currentTimeMillis() - lastLoadTime < cacheExpiryMs) {
            return@withContext programsCache
        }

        val allPrograms = mutableListOf<EpgProgram>()
        for (sub in subscriptionManager.getSubscriptions()) {
            val epgUrl = sub.epgUrl ?: continue
            try {
                val request = Request.Builder()
                    .url(epgUrl)
                    .header("User-Agent", "SportivTV/1.0")
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val xml = response.body?.string()
                        if (!xml.isNullOrBlank()) {
                            allPrograms.addAll(epgParser.parse(xml))
                        }
                    }
                }
            } catch (e: Exception) {
                // Continue with other EPG sources
            }
        }

        programsCache = allPrograms
        lastLoadTime = System.currentTimeMillis()
        allPrograms
    }

    /**
     * Get current and next program for a channel.
     */
    suspend fun getCurrentAndNext(channelEpgId: String?): Pair<EpgProgram?, EpgProgram?> {
        if (channelEpgId.isNullOrBlank()) return Pair(null, null)
        val programs = loadEpgData()
        return epgParser.getCurrentAndNext(programs, channelEpgId)
    }

    /**
     * Get all programs for a channel.
     */
    suspend fun getProgramsForChannel(channelEpgId: String?): List<EpgProgram> {
        if (channelEpgId.isNullOrBlank()) return emptyList()
        val programs = loadEpgData()
        return programs.filter { it.channelId == channelEpgId }.sortedBy { it.startTime }
    }

    /**
     * Check if EPG data is available.
     */
    fun hasEpgData(): Boolean = programsCache.isNotEmpty()

    /**
     * Clear EPG cache.
     */
    fun clearCache() {
        programsCache = emptyList()
        lastLoadTime = 0
    }
}
