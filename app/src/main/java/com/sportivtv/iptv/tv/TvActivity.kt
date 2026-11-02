package com.sportivtv.iptv.tv

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.sportivtv.iptv.R

/**
 * Android TV host Activity.
 * Hosts the TvMainFragment (BrowseSupportFragment) for Leanback TV UI.
 */
class TvActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tv)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.tv_fragment_container, TvMainFragment())
                .commitNow()
        }
    }
}
