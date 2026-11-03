package com.sportivtv.iptv

import android.app.Application
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import java.util.Locale

/**
 * Application entry point.
 * Forces Arabic locale and RTL layout direction globally.
 */
class SportivTVApp : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this

        // Force Arabic locale for RTL support
        forceArabicLocale()

        // Use dark mode always (IPTV apps are best in dark)
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
    }

    private fun forceArabicLocale() {
        val locale = Locale("ar")
        Locale.setDefault(locale)
        val config = resources.configuration
        config.setLocale(locale)
        @Suppress("DEPRECATION")
        resources.updateConfiguration(config, resources.displayMetrics)
    }

    companion object {
        lateinit var instance: SportivTVApp
            private set

        fun get(): SportivTVApp = instance

        fun applicationContext(): Context = instance.applicationContext
    }
}
