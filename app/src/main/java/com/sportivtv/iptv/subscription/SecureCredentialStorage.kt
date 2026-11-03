package com.sportivtv.iptv.subscription

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Secure credential storage for IPTV subscription passwords.
 * Uses AndroidX EncryptedSharedPreferences (AES-256-GCM).
 *
 * NEVER stores credentials in plain text SharedPreferences.
 * NEVER logs or exposes subscription passwords.
 */
class SecureCredentialStorage(context: Context) {

    private val prefs: SharedPreferences

    init {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        prefs = EncryptedSharedPreferences.create(
            context,
            "sportiv_secure_credentials",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    /**
     * Store password for a subscription.
     */
    fun storePassword(subscriptionId: Long, password: String) {
        prefs.edit().putString("pwd_$subscriptionId", password).apply()
    }

    /**
     * Retrieve password for a subscription.
     */
    fun getPassword(subscriptionId: Long): String? {
        return prefs.getString("pwd_$subscriptionId", null)
    }

    /**
     * Remove credentials for a subscription.
     */
    fun removeCredentials(subscriptionId: Long) {
        prefs.edit().remove("pwd_$subscriptionId").remove("user_$subscriptionId").remove("url_$subscriptionId").apply()
    }

    /**
     * Store username for a subscription (not as sensitive, but kept here for cohesion).
     */
    fun storeUsername(subscriptionId: Long, username: String) {
        prefs.edit().putString("user_$subscriptionId", username).apply()
    }

    fun getUsername(subscriptionId: Long): String? {
        return prefs.getString("user_$subscriptionId", null)
    }

    fun storePlaylistUrl(subscriptionId: Long, url: String) {
        prefs.edit().putString("url_$subscriptionId", url).apply()
    }

    fun getPlaylistUrl(subscriptionId: Long): String? {
        return prefs.getString("url_$subscriptionId", null)
    }
}
