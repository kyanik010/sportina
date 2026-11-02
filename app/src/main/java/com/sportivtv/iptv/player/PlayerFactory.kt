package com.sportivtv.iptv.player

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.LoadControl
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * ExoPlayer Factory.
 * Creates configured ExoPlayer instances optimized for IPTV live streaming.
 *
 * Key design decisions:
 * - Uses OkHttp as HTTP data source for better redirect/timeout handling
 * - Tuned buffer for live streams (lower initial buffer, sustained buffer for stability)
 * - Hardware acceleration enabled by default
 * - HLS support for .m3u8 streams
 *
 * This is the ONLY video engine. No second player is spawned for audio.
 * External audio is handled by [ExternalAudioManager] which uses a separate AudioTrack,
 * NOT a second ExoPlayer instance.
 */
class PlayerFactory(private val context: Context) {

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .retryOnConnectionFailure(true)
            .build()
    }

    /**
     * Create an ExoPlayer configured for IPTV live streaming.
     *
     * @param bufferMs Target buffer in milliseconds
     * @param hardwareAccelerated Whether to use hardware decoding
     */
    fun createPlayer(
        bufferMs: Int = 30000,
        hardwareAccelerated: Boolean = true
    ): ExoPlayer {
        // Load control: tune for live streams
        // minBuffer: enough to start quickly
        // maxBuffer: enough to sustain through network hiccups
        // bufferForPlayback: how much before playback starts
        val loadControl: LoadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs= */ bufferMs / 2,
                /* maxBufferMs= */ bufferMs * 2,
                /* bufferForPlaybackMs= */ 1000,
                /* bufferForPlaybackAfterRebufferMs= */ 3000
            )
            .setTargetBufferBytes(DefaultLoadControl.DEFAULT_TARGET_BUFFER_BYTES)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        // Track selector for quality selection
        val trackSelector = DefaultTrackSelector(context)

        // OkHttp data source factory for robust HTTP handling
        val httpDataSourceFactory = OkHttpDataSource.Factory(httpClient)
            .setUserAgent("SportivTV/1.0")

        val dataSourceFactory: DataSource.Factory = DefaultDataSource.Factory(context, httpDataSourceFactory)

        // Media source factory with HLS support
        val mediaSourceFactory = DefaultMediaSourceFactory(context)
            .setDataSourceFactory(dataSourceFactory)

        val player = ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            .setTrackSelector(trackSelector)
            .build()

        // Configure for live streaming
        player.playWhenReady = true
        player.repeatMode = Player.REPEAT_MODE_OFF

        return player
    }

    /**
     * Create a MediaSource for a stream URL.
     * Supports HLS (.m3u8), DASH (.mpd), and progressive (.ts, .mp4).
     */
    fun createMediaSource(player: ExoPlayer, streamUrl: String): MediaSource {
        val mediaItem = MediaItem.Builder()
            .setUri(Uri.parse(streamUrl))
            .build()

        val dataSourceFactory = DefaultDataSource.Factory(context,
            OkHttpDataSource.Factory(httpClient).setUserAgent("SportivTV/1.0"))

        return DefaultMediaSourceFactory(context)
            .setDataSourceFactory(dataSourceFactory)
            .createMediaSource(mediaItem)
    }

    /**
     * Create a simple MediaItem from URL.
     */
    fun createMediaItem(streamUrl: String): MediaItem {
        return MediaItem.Builder()
            .setUri(Uri.parse(streamUrl))
            .build()
    }
}
