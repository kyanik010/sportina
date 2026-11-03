package com.sportivtv.iptv.player

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.sportivtv.iptv.R
import com.sportivtv.iptv.audio.ExternalAudioManager
import com.sportivtv.iptv.favorites.FavoritesManager
import com.sportivtv.iptv.model.Channel
import com.sportivtv.iptv.model.WatchState
import com.sportivtv.iptv.subscription.SubscriptionManager
import com.sportivtv.iptv.data.ChannelRepository
import kotlinx.coroutines.launch

/**
 * Player Activity - Advanced IPTV Player
 *
 * Features (Phase 4):
 * - Channel switching (next/prev via D-pad or buttons)
 * - Playback controls (play/pause)
 * - In-player channel list (side panel)
 * - Reconnection logic (auto-retry with exponential backoff)
 * - Error handling (user-visible error messages)
 * - Watch state persistence (resume last watched channel)
 *
 * Features (Phase 5):
 * - External audio toggle
 * - Manual audio sync offset control (SeekBar)
 * - External audio does NOT crash video if it fails
 *
 * Phase 3:
 * - Favorites toggle from player
 */
class PlayerActivity : ComponentActivity() {

    private lateinit var player: ExoPlayer
    private lateinit var playerFactory: PlayerFactory
    private lateinit var channelRepo: ChannelRepository
    private lateinit var favoritesManager: FavoritesManager
    private lateinit var externalAudio: ExternalAudioManager

    // UI elements
    private lateinit var playerView: androidx.media3.ui.PlayerView
    private lateinit var controlsOverlay: View
    private lateinit var channelListPanel: View
    private lateinit var channelListRecyclerView: RecyclerView
    private lateinit var channelListAdapter: ChannelListAdapter
    private lateinit var loadingProgressBar: ProgressBar
    private lateinit var errorText: TextView
    private lateinit var errorRetryButton: View
    private lateinit var channelNameText: TextView
    private lateinit var epgNowText: TextView
    private lateinit var epgNextText: TextView
    private lateinit var audioOffsetSeekBar: SeekBar
    private lateinit var audioOffsetText: TextView
    private lateinit var btnNextChannel: ImageButton
    private lateinit var btnPrevChannel: ImageButton
    private lateinit var btnChannelList: ImageButton
    private lateinit var btnFavorite: ImageButton
    private lateinit var btnAudioSync: ImageButton
    private lateinit var btnReconnect: ImageButton

    // State
    private var channels: List<Channel> = emptyList()
    private var currentChannelIndex = 0
    private var currentChannel: Channel? = null
    private var reconnectAttempts = 0
    private var autoReconnectEnabled = true
    private var externalAudioEnabled = false
    private var bufferSizeMs = 30000
    private val maxReconnectAttempts = 5
    private val handler = Handler(Looper.getMainLooper())

    // Auto-hide controls
    private val controlsHideDelay = 5000L
    private val hideControlsRunnable = Runnable {
        controlsOverlay.visibility = View.GONE
        channelListPanel.visibility = View.GONE
    }

    companion object {
        const val EXTRA_CHANNEL_ID = "channel_id"
        const val EXTRA_CHANNEL_NAME = "channel_name"
        const val EXTRA_STREAM_URL = "stream_url"
        const val EXTRA_CHANNEL_LOGO = "channel_logo"
        const val EXTRA_EPG_ID = "epg_id"

        fun start(context: Context, channel: Channel) {
            val intent = Intent(context, PlayerActivity::class.java).apply {
                putExtra(EXTRA_CHANNEL_ID, channel.id)
                putExtra(EXTRA_CHANNEL_NAME, channel.name)
                putExtra(EXTRA_STREAM_URL, channel.streamUrl)
                channel.logoUrl?.let { putExtra(EXTRA_CHANNEL_LOGO, it) }
                channel.epgChannelId?.let { putExtra(EXTRA_EPG_ID, it) }
            }
            context.startActivity(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)

        // Keep screen on
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        initManagers()
        initViews()
        initPlayer()
        loadChannels()
    }

    private fun initManagers() {
        val subManager = SubscriptionManager(this)
        channelRepo = ChannelRepository(subManager)
        favoritesManager = FavoritesManager(this)
        val prefs = getSharedPreferences("sportiv_settings", MODE_PRIVATE)
        autoReconnectEnabled = prefs.getBoolean("auto_reconnect", true)
        externalAudioEnabled = prefs.getBoolean("external_audio", false)
        bufferSizeMs = prefs.getInt("buffer_ms", 30000).coerceIn(5000, 60000)
        playerFactory = PlayerFactory(this)
        externalAudio = ExternalAudioManager(this)
    }

    private fun initViews() {
        playerView = findViewById(R.id.player_view)
        controlsOverlay = findViewById(R.id.controls_overlay)
        channelListPanel = findViewById(R.id.channel_list_panel)
        channelListRecyclerView = findViewById(R.id.channel_list_rv)
        loadingProgressBar = findViewById(R.id.loading_progress)
        errorText = findViewById(R.id.error_text)
        errorRetryButton = findViewById(R.id.btn_error_retry)
        channelNameText = findViewById(R.id.channel_name_text)
        epgNowText = findViewById(R.id.epg_now_text)
        epgNextText = findViewById(R.id.epg_next_text)
        audioOffsetSeekBar = findViewById(R.id.audio_offset_seekbar)
        audioOffsetText = findViewById(R.id.audio_offset_text)
        btnNextChannel = findViewById(R.id.btn_next_channel)
        btnPrevChannel = findViewById(R.id.btn_prev_channel)
        btnChannelList = findViewById(R.id.btn_channel_list)
        btnFavorite = findViewById(R.id.btn_favorite)
        btnAudioSync = findViewById(R.id.btn_audio_sync)
        btnReconnect = findViewById(R.id.btn_reconnect)

        // Channel list adapter
        channelListAdapter = ChannelListAdapter { channel ->
            switchToChannel(channel)
            hideChannelList()
        }
        channelListRecyclerView.layoutManager = LinearLayoutManager(this)
        channelListRecyclerView.adapter = channelListAdapter

        // Button listeners
        btnNextChannel.setOnClickListener { switchToNextChannel() }
        btnPrevChannel.setOnClickListener { switchToPrevChannel() }
        btnChannelList.setOnClickListener { toggleChannelList() }
        btnFavorite.setOnClickListener { toggleFavorite() }
        btnAudioSync.setOnClickListener { showExternalAudioSourcePicker() }
        btnAudioSync.setOnLongClickListener { toggleAudioSync(); true }
        btnReconnect.setOnClickListener { reconnect() }
        errorRetryButton.setOnClickListener { reconnect() }

        // Match ExternalAudioManager's supported +/-30 second range in 100ms steps.
        audioOffsetSeekBar.max = 600
        audioOffsetSeekBar.progress = 300
        audioOffsetSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val offsetMs = (progress - 300L) * 100L
                audioOffsetText.text = "${offsetMs}ms"
                externalAudio.setAudioOffset(offsetMs)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Show/hide controls on player view click
        playerView.setOnClickListener {
            toggleControls()
        }
    }

    private fun initPlayer() {
        player = playerFactory.createPlayer(bufferMs = bufferSizeMs)
        playerView.player = player

        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                when (state) {
                    Player.STATE_BUFFERING -> {
                        loadingProgressBar.visibility = View.VISIBLE
                        errorText.visibility = View.GONE
                        errorRetryButton.visibility = View.GONE
                    }
                    Player.STATE_READY -> {
                        loadingProgressBar.visibility = View.GONE
                        errorText.visibility = View.GONE
                        reconnectAttempts = 0 // Reset on success
                        showControls()
                    }
                    Player.STATE_ENDED -> {
                        // Live streams shouldn't end; try reconnect
                        handleStreamEnd()
                    }
                    Player.STATE_IDLE -> {
                        loadingProgressBar.visibility = View.GONE
                    }
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                handlePlayerError(error)
            }
        })
    }

    private fun loadChannels() {
        lifecycleScope.launch {
            channels = channelRepo.loadChannels()
            channelListAdapter.updateChannels(channels)

            // Get channel from intent
            val channelId = intent.getStringExtra(EXTRA_CHANNEL_ID) ?: ""
            val channelName = intent.getStringExtra(EXTRA_CHANNEL_NAME) ?: ""
            val streamUrl = intent.getStringExtra(EXTRA_STREAM_URL) ?: ""

            if (streamUrl.isNotBlank()) {
                // Find index in channel list
                val matchingChannel = channels.firstOrNull {
                    it.id == channelId || it.streamUrl == streamUrl
                }
                currentChannelIndex = matchingChannel?.let { channels.indexOf(it) } ?: -1
                val channel = matchingChannel ?: Channel(
                    id = channelId.ifBlank { streamUrl },
                    name = channelName.ifBlank { getString(R.string.app_name) },
                    logoUrl = intent.getStringExtra(EXTRA_CHANNEL_LOGO),
                    streamUrl = streamUrl,
                    epgChannelId = intent.getStringExtra(EXTRA_EPG_ID)
                )

                playChannel(channel)
            }
        }
    }

    /**
     * Play a channel.
     */
    private fun playChannel(channel: Channel) {
        currentChannel = channel
        if (currentChannelIndex >= 0) channelListAdapter.setSelectedPosition(currentChannelIndex)
        channelNameText.text = channel.name
        reconnectAttempts = 0

        // Save watch state
        saveWatchState(channel)

        // Stop external audio if running
        if (externalAudio.isActive()) {
            externalAudio.stop(player)
        }

        // Set media and prepare
        player.setMediaItem(playerFactory.createMediaItem(channel.streamUrl))
        player.prepare()
        player.playWhenReady = true

        // Load EPG for this channel
        loadEpgForChannel(channel)

        // Update favorite button
        updateFavoriteButton(channel)

        showControls()
    }

    /**
     * Switch to a specific channel.
     */
    private fun switchToChannel(channel: Channel) {
        val index = channels.indexOfFirst { it.id == channel.id }
        if (index >= 0) currentChannelIndex = index
        playChannel(channel)
    }

    /**
     * Switch to next channel.
     */
    private fun switchToNextChannel() {
        if (channels.isEmpty()) return
        currentChannelIndex = if (currentChannelIndex < 0) 0 else (currentChannelIndex + 1) % channels.size
        playChannel(channels[currentChannelIndex])
    }

    /**
     * Switch to previous channel.
     */
    private fun switchToPrevChannel() {
        if (channels.isEmpty()) return
        currentChannelIndex = if (currentChannelIndex < 0) channels.lastIndex
            else (currentChannelIndex - 1 + channels.size) % channels.size
        playChannel(channels[currentChannelIndex])
    }

    /**
     * Toggle the channel list side panel.
     */
    private fun toggleChannelList() {
        if (channelListPanel.visibility == View.VISIBLE) {
            hideChannelList()
        } else {
            channelListPanel.visibility = View.VISIBLE
            // Scroll to current channel
            if (currentChannelIndex < channelListAdapter.itemCount) {
                channelListRecyclerView.scrollToPosition(currentChannelIndex)
            }
        }
        resetHideControlsTimer()
    }

    private fun hideChannelList() {
        channelListPanel.visibility = View.GONE
    }

    /**
     * Toggle favorite status of current channel.
     */
    private fun toggleFavorite() {
        val channel = currentChannel ?: return
        val isFav = favoritesManager.toggleFavorite(channel)
        updateFavoriteButton(channel, isFav)
    }

    private fun updateFavoriteButton(channel: Channel, isFav: Boolean? = null) {
        val favorite = isFav ?: favoritesManager.isFavorite(channel.id)
        btnFavorite.setImageResource(
            if (favorite) R.drawable.ic_star_filled else R.drawable.ic_star_outline
        )
    }

    /**
     * Select a different IPTV channel as an audio-only commentary source.
     * The primary video channel remains unchanged.
     */
    private fun showExternalAudioSourcePicker() {
        if (!externalAudioEnabled) {
            Toast.makeText(this, R.string.audio_enable_in_settings, Toast.LENGTH_LONG).show()
            return
        }
        val videoChannel = currentChannel ?: return
        val audioSources = channels.filter { it.id != videoChannel.id }
        if (audioSources.isEmpty()) {
            Toast.makeText(this, "لا توجد قنوات أخرى لاختيار الصوت منها", Toast.LENGTH_SHORT).show()
            return
        }

        val options = arrayOf("إيقاف الصوت الخارجي") + audioSources.map { it.name }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("اختر مصدر الصوت الخارجي")
            .setItems(options) { _, which ->
                if (which == 0) {
                    externalAudio.stop(player)
                    Toast.makeText(this, "تمت العودة إلى الصوت الأصلي", Toast.LENGTH_SHORT).show()
                } else {
                    val source = audioSources[which - 1]
                    val accepted = externalAudio.start(source.streamUrl, player)
                    val message = if (accepted) {
                        "جارٍ الاتصال بمصدر الصوت: ${source.name}"
                    } else {
                        "تعذر بدء مصدر الصوت المحدد"
                    }
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton("إلغاء", null)
            .show()
        resetHideControlsTimer()
    }

    /**
     * Long-press the audio button to show the manual offset control.
     */
    private fun toggleAudioSync() {
        val visibility = audioOffsetSeekBar.visibility
        if (visibility == View.VISIBLE) {
            audioOffsetSeekBar.visibility = View.GONE
            audioOffsetText.visibility = View.GONE
        } else {
            audioOffsetSeekBar.visibility = View.VISIBLE
            audioOffsetText.visibility = View.VISIBLE
        }
        resetHideControlsTimer()
    }

    /**
     * Reconnect to current stream.
     */
    private fun reconnect() {
        val channel = currentChannel ?: return
        errorText.visibility = View.GONE
        loadingProgressBar.visibility = View.VISIBLE
        playChannel(channel)
    }

    /**
     * Handle player errors with auto-reconnect.
     */
    private fun handlePlayerError(error: PlaybackException) {
        loadingProgressBar.visibility = View.GONE

        if (reconnectAttempts < maxReconnectAttempts) {
            reconnectAttempts++
            errorText.text = getString(R.string.player_error_reconnect)
            errorText.visibility = View.VISIBLE

            // Exponential backoff: 2s, 4s, 8s, 16s, 32s
            val delay = (2000L * (1 shl (reconnectAttempts - 1)))
            handler.postDelayed({
                currentChannel?.let { playChannel(it) }
            }, delay)
        } else {
            errorText.text = "${getString(R.string.player_error)}: ${error.errorCodeName}"
            errorText.visibility = View.VISIBLE
            errorRetryButton.visibility = View.VISIBLE
        }
    }

    /**
     * Handle stream end (live streams shouldn't end normally).
     */
    private fun handleStreamEnd() {
        if (reconnectAttempts < maxReconnectAttempts) {
            reconnectAttempts++
            handler.postDelayed({
                currentChannel?.let { playChannel(it) }
            }, 2000)
        }
    }

    /**
     * Load EPG data for current channel.
     */
    private fun loadEpgForChannel(channel: Channel) {
        if (channel.epgChannelId.isNullOrBlank()) {
            epgNowText.text = getString(R.string.epg_no_data)
            epgNextText.text = ""
            return
        }

        epgNowText.text = getString(R.string.epg_loading)

        lifecycleScope.launch {
            try {
                val epgManager = com.sportivtv.iptv.epg.EpgManager(this@PlayerActivity,
                    SubscriptionManager(this@PlayerActivity))
                val (current, next) = epgManager.getCurrentAndNext(channel.epgChannelId)
                epgNowText.text = current?.title ?: getString(R.string.epg_no_data)
                epgNextText.text = next?.title?.let { "${getString(R.string.epg_next)}: $it" } ?: ""
            } catch (e: Exception) {
                epgNowText.text = getString(R.string.epg_no_data)
                epgNextText.text = ""
            }
        }
    }

    /**
     * Save watch state for resume.
     */
    private fun saveWatchState(channel: Channel) {
        val prefs = getSharedPreferences("sportiv_watch_state", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("last_channel_id", channel.id)
            .putString("last_channel_name", channel.name)
            .putString("last_stream_url", channel.streamUrl)
            .putLong("last_watched_at", System.currentTimeMillis())
            .apply()
    }

    /**
     * Get last watched channel (for resume).
     */
    private fun getLastWatchedChannel(): WatchState? {
        val prefs = getSharedPreferences("sportiv_watch_state", Context.MODE_PRIVATE)
        val channelId = prefs.getString("last_channel_id", null) ?: return null
        val channelName = prefs.getString("last_channel_name", "") ?: ""
        val streamUrl = prefs.getString("last_stream_url", "") ?: ""
        val watchedAt = prefs.getLong("last_watched_at", 0)
        return WatchState(channelId, channelName, 0, watchedAt)
    }

    /**
     * Controls visibility management.
     */
    private fun showControls() {
        controlsOverlay.visibility = View.VISIBLE
        resetHideControlsTimer()
    }

    private fun toggleControls() {
        if (controlsOverlay.visibility == View.VISIBLE) {
            controlsOverlay.visibility = View.GONE
            hideChannelList()
        } else {
            showControls()
        }
    }

    private fun resetHideControlsTimer() {
        handler.removeCallbacks(hideControlsRunnable)
        handler.postDelayed(hideControlsRunnable, controlsHideDelay)
    }

    /**
     * D-pad / key event handling for Android TV.
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (controlsOverlay.visibility == View.VISIBLE) {
                    // Navigate in controls
                } else {
                    switchToNextChannel()
                    return true
                }
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                if (controlsOverlay.visibility == View.VISIBLE) {
                    // Navigate in controls
                } else {
                    switchToPrevChannel()
                    return true
                }
            }
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> {
                showControls()
                return true
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                toggleControls()
                return true
            }
            KeyEvent.KEYCODE_CHANNEL_UP -> {
                switchToNextChannel()
                return true
            }
            KeyEvent.KEYCODE_CHANNEL_DOWN -> {
                switchToPrevChannel()
                return true
            }
            KeyEvent.KEYCODE_MEDIA_PLAY -> {
                player.play()
                return true
            }
            KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                player.pause()
                return true
            }
            KeyEvent.KEYCODE_MEDIA_STOP -> {
                finish()
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        externalAudio.release()
        player.release()
        super.onDestroy()
    }
}
