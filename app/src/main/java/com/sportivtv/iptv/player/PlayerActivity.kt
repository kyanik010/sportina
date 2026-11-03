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

class PlayerActivity : ComponentActivity() {
    private lateinit var player: ExoPlayer
    private lateinit var playerFactory: PlayerFactory
    private lateinit var channelRepo: ChannelRepository
    private lateinit var favoritesManager: FavoritesManager
    private lateinit var externalAudio: ExternalAudioManager
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
    private var channels: List<Channel> = emptyList()
    private var currentChannelIndex = -1
    private var currentChannel: Channel? = null
    private var reconnectAttempts = 0
    private val maxReconnectAttempts = 5
    private val handler = Handler(Looper.getMainLooper())
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
        channelListAdapter = ChannelListAdapter { channel ->
            switchToChannel(channel)
            hideChannelList()
        }
        channelListRecyclerView.layoutManager = LinearLayoutManager(this)
        channelListRecyclerView.adapter = channelListAdapter
        btnNextChannel.setOnClickListener { switchToNextChannel() }
        btnPrevChannel.setOnClickListener { switchToPrevChannel() }
        btnChannelList.setOnClickListener { toggleChannelList() }
        btnFavorite.setOnClickListener { toggleFavorite() }
        btnAudioSync.setOnClickListener { showExternalAudioSourcePicker() }
        btnAudioSync.setOnLongClickListener { toggleAudioSync(); true }
        btnReconnect.setOnClickListener { reconnect() }
        errorRetryButton.setOnClickListener { reconnect() }
        // Match the manager's supported +/-30 second offset range.
        audioOffsetSeekBar.max = 600
        audioOffsetSeekBar.progress = 300
        audioOffsetSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val offsetMs = (progress - 300L) * 100L
                audioOffsetText.text = "${offsetMs}ms"
                externalAudio.setAudioOffset(offsetMs)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })
        playerView.setOnClickListener { toggleControls() }
    }

    private fun initPlayer() {
        player = playerFactory.createPlayer()
        playerView.player = player
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                when (state) {
                    Player.STATE_BUFFERING -> {
                        loadingProgressBar.visibility = View.VISIBLE
                        errorText.visibility = View.GONE
                    }
                    Player.STATE_READY -> {
                        loadingProgressBar.visibility = View.GONE
                        errorText.visibility = View.GONE
                    }
                    Player.STATE_IDLE -> loadingProgressBar.visibility = View.GONE
                }
            }
            override fun onPlayerError(error: PlaybackException) { handlePlayerError(error) }
        })
    }

    private fun loadChannels() {
        lifecycleScope.launch {
            channels = channelRepo.loadChannels(forceRefresh = true)
            channelListAdapter.updateChannels(channels)
            val channelId = intent.getStringExtra(EXTRA_CHANNEL_ID).orEmpty()
            val channelName = intent.getStringExtra(EXTRA_CHANNEL_NAME).orEmpty()
            val streamUrl = intent.getStringExtra(EXTRA_STREAM_URL).orEmpty()
            if (streamUrl.isNotBlank()) {
                val match = channels.firstOrNull { it.id == channelId || it.streamUrl == streamUrl }
                currentChannelIndex = match?.let { channels.indexOf(it) } ?: -1
                val channel = match ?: Channel(
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

    private fun playChannel(channel: Channel) {
        currentChannel = channel
        channelNameText.text = channel.name
        reconnectAttempts = 0
        saveWatchState(channel)
        if (externalAudio.isActive()) externalAudio.stop(player)
        player.setMediaItem(playerFactory.createMediaItem(channel.streamUrl))
        player.prepare()
        player.playWhenReady = true
        loadEpgForChannel(channel)
        updateFavoriteButton(channel)
        showControls()
    }

    private fun switchToChannel(channel: Channel) {
        currentChannelIndex = channels.indexOfFirst { it.id == channel.id || it.streamUrl == channel.streamUrl }
        playChannel(channel)
    }

    private fun switchToNextChannel() {
        if (channels.isEmpty()) return
        currentChannelIndex = if (currentChannelIndex < 0) 0 else (currentChannelIndex + 1) % channels.size
        playChannel(channels[currentChannelIndex])
    }

    private fun switchToPrevChannel() {
        if (channels.isEmpty()) return
        currentChannelIndex = if (currentChannelIndex < 0) channels.lastIndex
            else (currentChannelIndex - 1 + channels.size) % channels.size
        playChannel(channels[currentChannelIndex])
    }

    private fun toggleChannelList() {
        if (channelListPanel.visibility == View.VISIBLE) hideChannelList() else {
            channelListPanel.visibility = View.VISIBLE
            if (currentChannelIndex >= 0 && currentChannelIndex < channelListAdapter.itemCount) {
                channelListRecyclerView.scrollToPosition(currentChannelIndex)
            }
        }
        resetHideControlsTimer()
    }

    private fun hideChannelList() { channelListPanel.visibility = View.GONE }

    private fun toggleFavorite() {
        val channel = currentChannel ?: return
        updateFavoriteButton(channel, favoritesManager.toggleFavorite(channel))
    }

    private fun updateFavoriteButton(channel: Channel, isFav: Boolean? = null) {
        val favorite = isFav ?: favoritesManager.isFavorite(channel.id)
        btnFavorite.setImageResource(if (favorite) R.drawable.ic_star_filled else R.drawable.ic_star_outline)
    }

    private fun showExternalAudioSourcePicker() {
        val videoChannel = currentChannel ?: return
        val audioSources = channels.filter { it.id != videoChannel.id && it.streamUrl != videoChannel.streamUrl }
        if (audioSources.isEmpty()) {
            Toast.makeText(this, R.string.audio_no_other_channels, Toast.LENGTH_SHORT).show()
            return
        }
        val options = arrayOf(getString(R.string.audio_stop_external)) + audioSources.map { it.name }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(R.string.audio_choose_source)
            .setItems(options) { _, which ->
                if (which == 0) {
                    externalAudio.stop(player)
                    Toast.makeText(this, R.string.audio_original_restored, Toast.LENGTH_SHORT).show()
                } else {
                    val source = audioSources[which - 1]
                    val accepted = externalAudio.start(source.streamUrl, player)
                    Toast.makeText(
                        this,
                        if (accepted) getString(R.string.audio_connecting, source.name)
                        else getString(R.string.audio_start_failed),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
        resetHideControlsTimer()
    }

    private fun toggleAudioSync() {
        val visible = audioOffsetSeekBar.visibility == View.VISIBLE
        audioOffsetSeekBar.visibility = if (visible) View.GONE else View.VISIBLE
        audioOffsetText.visibility = if (visible) View.GONE else View.VISIBLE
        resetHideControlsTimer()
    }

    private fun reconnect() {
        val channel = currentChannel ?: return
        if (reconnectAttempts >= maxReconnectAttempts) {
            errorText.text = getString(R.string.player_error)
            errorText.visibility = View.VISIBLE
            return
        }
        reconnectAttempts++
        player.stop()
        player.setMediaItem(playerFactory.createMediaItem(channel.streamUrl))
        player.prepare()
        player.playWhenReady = true
    }

    private fun handlePlayerError(error: PlaybackException) {
        loadingProgressBar.visibility = View.GONE
        errorText.text = getString(R.string.player_error_reconnect)
        errorText.visibility = View.VISIBLE
        if (reconnectAttempts < maxReconnectAttempts) {
            handler.postDelayed({ reconnect() }, 1500L * reconnectAttempts.coerceAtLeast(1))
        }
    }

    private fun saveWatchState(channel: Channel) {
        getSharedPreferences("sportiv_watch_state", MODE_PRIVATE).edit()
            .putString("channel_id", channel.id)
            .putString("channel_name", channel.name)
            .putLong("last_watched_at", System.currentTimeMillis())
            .apply()
    }

    private fun loadEpgForChannel(channel: Channel) {
        epgNowText.text = getString(R.string.epg_no_data)
        epgNextText.text = ""
    }

    private fun showControls() {
        controlsOverlay.visibility = View.VISIBLE
        resetHideControlsTimer()
    }

    private fun toggleControls() {
        if (controlsOverlay.visibility == View.VISIBLE) {
            controlsOverlay.visibility = View.GONE
            hideChannelList()
        } else showControls()
    }

    private fun resetHideControlsTimer() {
        handler.removeCallbacks(hideControlsRunnable)
        handler.postDelayed(hideControlsRunnable, controlsHideDelay)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_RIGHT -> if (controlsOverlay.visibility != View.VISIBLE) {
                switchToNextChannel(); return true
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> if (controlsOverlay.visibility != View.VISIBLE) {
                switchToPrevChannel(); return true
            }
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> showControls()
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                if (controlsOverlay.visibility != View.VISIBLE) toggleControls()
            }
            KeyEvent.KEYCODE_CHANNEL_UP -> { switchToNextChannel(); return true }
            KeyEvent.KEYCODE_CHANNEL_DOWN -> { switchToPrevChannel(); return true }
            KeyEvent.KEYCODE_MEDIA_PLAY -> { player.play(); return true }
            KeyEvent.KEYCODE_MEDIA_PAUSE -> { player.pause(); return true }
            KeyEvent.KEYCODE_MEDIA_STOP -> { finish(); return true }
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
