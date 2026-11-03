package com.sportivtv.iptv.ui

import android.content.Context
import android.os.Bundle
import android.widget.Switch
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.sportivtv.iptv.R

/**
 * Settings Activity.
 * Player settings, audio settings, interface settings, about.
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var autoReconnectSwitch: Switch
    private lateinit var externalAudioSwitch: Switch
    private lateinit var bufferSeekBar: SeekBar
    private lateinit var bufferText: TextView
    private lateinit var aboutText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val prefs = getSharedPreferences("sportiv_settings", Context.MODE_PRIVATE)

        autoReconnectSwitch = findViewById(R.id.settings_auto_reconnect)
        externalAudioSwitch = findViewById(R.id.settings_external_audio)
        bufferSeekBar = findViewById(R.id.settings_buffer_seekbar)
        bufferText = findViewById(R.id.settings_buffer_text)
        aboutText = findViewById(R.id.settings_about_text)

        // Load saved settings
        autoReconnectSwitch.isChecked = prefs.getBoolean("auto_reconnect", true)
        externalAudioSwitch.isChecked = prefs.getBoolean("external_audio", false)
        val bufferSize = prefs.getInt("buffer_ms", 30000)
        bufferSeekBar.progress = (bufferSize / 1000 - 5).coerceIn(0, 55)
        bufferText.text = getString(R.string.buffer_seconds, bufferSeekBar.progress + 5)

        aboutText.text = "${getString(R.string.about_description)}\n${getString(R.string.about_version, "1.0.0")}\n${getString(R.string.about_arch)}"

        // Save on change
        autoReconnectSwitch.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean("auto_reconnect", checked).apply()
        }

        externalAudioSwitch.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean("external_audio", checked).apply()
        }

        bufferSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val seconds = progress + 5
                val ms = seconds * 1000
                bufferText.text = getString(R.string.buffer_seconds, seconds)
                prefs.edit().putInt("buffer_ms", ms).apply()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }
}
