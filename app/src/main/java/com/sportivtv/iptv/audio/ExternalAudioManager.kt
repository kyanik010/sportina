package com.sportivtv.iptv.audio

import android.content.Context
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Independent audio-only playback session for a second IPTV channel.
 *
 * The main ExoPlayer remains the only video player. This manager creates a
 * separate Media3 session with its video track type disabled, so the second
 * channel is decoded for audio only. It deliberately does not parse MPEG-TS
 * packets or send compressed PES bytes to AudioTrack.
 *
 * Important limitation: separate live channels do not share a broadcast clock.
 * The offset control performs a seek relative to the audio session's initial
 * position when the stream exposes a seekable timeline. It is not automatic
 * cross-channel lip-sync and must be validated with real provider streams.
 */
class ExternalAudioManager(context: Context) {

    companion object {
        private const val TAG = "ExternalAudioManager"
        private const val MAX_OFFSET_MS = 30_000L
    }

    enum class AudioState {
        IDLE,
        CONNECTING,
        PLAYING,
        SYNCING,
        ERROR,
        UNSUPPORTED
    }

    private val appContext = context.applicationContext

    private val _audioOffsetMs = MutableStateFlow(0L)
    val audioOffsetMs: StateFlow<Long> = _audioOffsetMs.asStateFlow()

    private val _audioState = MutableStateFlow(AudioState.IDLE)
    val audioState: StateFlow<AudioState> = _audioState.asStateFlow()

    private var audioPlayer: ExoPlayer? = null
    private var videoPlayer: ExoPlayer? = null
    private var previousVideoVolume: Float? = null
    private var audioStartPositionMs: Long = 0L
    private var pendingOffsetMs: Long = 0L
    private var activeUrl: String? = null

    /**
     * Starts a second Media3 playback session with video rendering disabled.
     * The primary video is muted only after the external audio track is ready.
     */
    fun start(
        streamUrl: String,
        videoPlayer: ExoPlayer,
        initialOffsetMs: Long = 0L
    ): Boolean {
        if (!streamUrl.startsWith("http://") && !streamUrl.startsWith("https://")) {
            _audioState.value = AudioState.UNSUPPORTED
            return false
        }

        if (activeUrl == streamUrl && audioPlayer?.isPlaying == true) return true

        stop()

        this.videoPlayer = videoPlayer
        pendingOffsetMs = initialOffsetMs.coerceIn(-MAX_OFFSET_MS, MAX_OFFSET_MS)
        _audioOffsetMs.value = pendingOffsetMs
        activeUrl = streamUrl
        _audioState.value = AudioState.CONNECTING

        return try {
            val player = ExoPlayer.Builder(appContext).build().also { secondary ->
                secondary.trackSelectionParameters =
                    secondary.trackSelectionParameters.buildUpon()
                        .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, true)
                        .build()

                secondary.volume = 1f
                secondary.addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        when (playbackState) {
                            Player.STATE_READY -> activateAudioIfAvailable(secondary)
                            Player.STATE_ENDED -> failAndRestore("انتهى بث مصدر الصوت الخارجي")
                            Player.STATE_IDLE -> Unit
                            Player.STATE_BUFFERING -> {
                                if (_audioState.value == AudioState.IDLE) {
                                    _audioState.value = AudioState.CONNECTING
                                }
                            }
                        }
                    }

                    override fun onTracksChanged(tracks: Tracks) {
                        if (secondary.playbackState == Player.STATE_READY) {
                            activateAudioIfAvailable(secondary)
                        }
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        Log.e(TAG, "External audio playback failed: ${error.errorCodeName}", error)
                        failAndRestore("تعذر تشغيل مصدر الصوت الخارجي")
                    }
                })

                secondary.setMediaItem(MediaItem.fromUri(streamUrl))
                secondary.prepare()
                secondary.playWhenReady = true
            }

            audioPlayer = player
            true
        } catch (error: Exception) {
            Log.e(TAG, "Unable to create audio-only playback session", error)
            failAndRestore("تعذر تهيئة مصدر الصوت الخارجي")
            false
        }
    }

    private fun activateAudioIfAvailable(player: ExoPlayer) {
        val hasSelectedAudio = player.currentTracks.groups.any { group ->
            group.type == C.TRACK_TYPE_AUDIO && group.isSelected
        }

        if (!hasSelectedAudio) {
            failAndRestore("مصدر البث لا يحتوي على مسار صوت مدعوم")
            return
        }

        if (previousVideoVolume == null) {
            videoPlayer?.let { primary ->
                previousVideoVolume = primary.volume
                primary.volume = 0f
            }
        }

        if (audioStartPositionMs == 0L) {
            audioStartPositionMs = player.currentPosition
            applyOffsetToCurrentTimeline()
        }

        _audioState.value = AudioState.PLAYING
    }

    /**
     * Positive values delay the external audio; negative values advance it.
     * This uses the audio stream's own seekable timeline, when available.
     */
    fun setAudioOffset(offsetMs: Long) {
        pendingOffsetMs = offsetMs.coerceIn(-MAX_OFFSET_MS, MAX_OFFSET_MS)
        _audioOffsetMs.value = pendingOffsetMs

        val player = audioPlayer ?: return
        if (player.playbackState != Player.STATE_READY) return

        _audioState.value = AudioState.SYNCING
        applyOffsetToCurrentTimeline()
        _audioState.value = AudioState.PLAYING
    }

    private fun applyOffsetToCurrentTimeline() {
        val player = audioPlayer ?: return
        if (!player.isCurrentMediaItemSeekable) {
            Log.w(TAG, "External audio stream is not seekable; manual timeline offset cannot be applied")
            return
        }

        val targetPosition = (audioStartPositionMs - pendingOffsetMs).coerceAtLeast(0L)
        player.seekTo(targetPosition)
    }

    fun getAudioOffset(): Long = _audioOffsetMs.value

    fun isActive(): Boolean =
        audioPlayer != null && _audioState.value in setOf(
            AudioState.CONNECTING,
            AudioState.PLAYING,
            AudioState.SYNCING
        )

    /**
     * Stops only the external audio session and restores the primary player's
     * exact previous volume. The primary video session is never released here.
     */
    fun stop(videoPlayer: ExoPlayer? = this.videoPlayer) {
        val secondary = audioPlayer
        audioPlayer = null
        activeUrl = null

        try {
            secondary?.stop()
            secondary?.release()
        } catch (error: Exception) {
            Log.w(TAG, "Error releasing external audio session", error)
        }

        val primary = videoPlayer ?: this.videoPlayer
        previousVideoVolume?.let { oldVolume ->
            try {
                primary?.volume = oldVolume
            } catch (error: Exception) {
                Log.w(TAG, "Unable to restore primary player volume", error)
            }
        }

        previousVideoVolume = null
        this.videoPlayer = null
        audioStartPositionMs = 0L
        pendingOffsetMs = 0L
        _audioOffsetMs.value = 0L
        _audioState.value = AudioState.IDLE
    }

    private fun failAndRestore(message: String) {
        Log.w(TAG, message)
        val primary = videoPlayer
        stop(primary)
        _audioState.value = AudioState.ERROR
    }

    fun release() {
        stop()
    }

    fun getStatusText(): String = when (_audioState.value) {
        AudioState.IDLE -> "غير نشط"
        AudioState.CONNECTING -> "جارٍ الاتصال بمصدر الصوت…"
        AudioState.PLAYING -> "الصوت الخارجي يعمل"
        AudioState.SYNCING -> "جارٍ ضبط مزامنة الصوت…"
        AudioState.ERROR -> "تعذر تشغيل الصوت الخارجي"
        AudioState.UNSUPPORTED -> "مصدر الصوت أو تنسيقه غير مدعوم"
    }
}
