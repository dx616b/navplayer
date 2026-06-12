package com.dean.navplayer.ui

import android.content.Intent
import android.os.Bundle
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.recyclerview.widget.LinearLayoutManager
import com.dean.navplayer.NavPlayerApp
import com.dean.navplayer.data.CoverArtBitmap
import com.dean.navplayer.data.SubsonicClient
import com.dean.navplayer.R
import com.dean.navplayer.databinding.ActivityNowPlayingBinding
import com.dean.navplayer.playback.PlaybackService
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class NowPlayingActivity : AppCompatActivity() {
    private lateinit var binding: ActivityNowPlayingBinding
    private lateinit var app: NavPlayerApp
    private var controller: MediaController? = null
    private var progressJob: Job? = null
    private var userSeeking = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        HeadUnitUi.apply(this)
        binding = ActivityNowPlayingBinding.inflate(layoutInflater)
        setContentView(binding.root)
        app = application as NavPlayerApp

        binding.closeButton.setOnClickListener { finish() }
        binding.queueList.layoutManager = LinearLayoutManager(this)

        binding.preButton.setOnClickListener { controller?.seekToPreviousMediaItem() }
        binding.playPauseButton.setOnClickListener {
            val c = controller ?: return@setOnClickListener
            if (c.isPlaying) c.pause() else c.play()
        }
        binding.nextButton.setOnClickListener { controller?.seekToNextMediaItem() }

        binding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    userSeeking = true
                    val duration = controller?.duration ?: return
                    if (duration > 0) {
                        binding.positionText.text = formatMs(progress * duration / 100)
                    }
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                userSeeking = true
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                val duration = controller?.duration ?: return
                if (duration > 0) {
                    controller?.seekTo(binding.seekBar.progress * duration / 100)
                }
                userSeeking = false
            }
        })

    }

    override fun onStart() {
        super.onStart()
        if (controller == null) {
            connectController()
        }
    }

    override fun onResume() {
        super.onResume()
        applyDrivingMode()
        syncUi()
    }

    private fun applyDrivingMode() {
        HeadUnitUi.applyDrivingMode(this, binding, (application as NavPlayerApp).prefs.drivingMode)
    }

    override fun onStop() {
        progressJob?.cancel()
        controller?.removeListener(playerListener)
        controller?.release()
        controller = null
        super.onStop()
    }

    private fun connectController() {
        startService(Intent(this, PlaybackService::class.java))
        val token = androidx.media3.session.SessionToken(
            this,
            android.content.ComponentName(this, PlaybackService::class.java),
        )
        val future: ListenableFuture<MediaController> = MediaController.Builder(this, token).buildAsync()
        future.addListener(
            {
                runCatching { future.get(10, TimeUnit.SECONDS) }
                    .onSuccess { c ->
                        controller = c
                        c.addListener(playerListener)
                        syncUi()
                        HeadUnitUi.setKeepScreenOn(this@NowPlayingActivity, c.isPlaying)
                        startProgressLoop()
                    }
            },
            MoreExecutors.directExecutor(),
        )
    }

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            updatePlayPauseIcon(isPlaying)
            HeadUnitUi.setKeepScreenOn(this@NowPlayingActivity, isPlaying)
        }

        override fun onMediaItemTransition(
            mediaItem: androidx.media3.common.MediaItem?,
            reason: Int,
        ) {
            syncUi()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            syncUi()
        }
    }

    private fun startProgressLoop() {
        progressJob?.cancel()
        progressJob = lifecycleScope.launch {
            while (isActive) {
                if (!userSeeking) {
                    syncProgress()
                }
                delay(500)
            }
        }
    }

    private fun syncUi() {
        val c = controller ?: return
        if (c.mediaItemCount == 0) {
            finish()
            return
        }

        val metadata = c.mediaMetadata
        binding.trackTitle.text = metadata.title?.toString().orEmpty()
        binding.trackArtist.text = metadata.artist?.toString().orEmpty()
        updatePlayPauseIcon(c.isPlaying)
        syncProgress()
        syncQueue(c)
        loadCoverForCurrentTrack(c.currentMediaItem?.mediaId)
    }

    private fun syncProgress() {
        val c = controller ?: return
        val duration = c.duration
        if (duration > 0) {
            binding.seekBar.max = 100
            binding.seekBar.progress = ((c.currentPosition * 100) / duration).toInt().coerceIn(0, 100)
            binding.positionText.text = formatMs(c.currentPosition)
            binding.durationText.text = "/ ${formatMs(duration)}"
        }
    }

    private fun syncQueue(c: MediaController) {
        val current = c.currentMediaItemIndex
        val count = c.mediaItemCount
        val start = max(0, current - PREVIOUS_COUNT)
        val end = min(current + UPCOMING_COUNT, count)
        val items = (start until end).map { index ->
            val meta = c.getMediaItemAt(index).mediaMetadata
            QueueItem(
                playerIndex = index,
                title = meta.title?.toString().orEmpty(),
                artist = meta.artist?.toString().orEmpty(),
                isCurrent = index == current,
            )
        }
        binding.queueList.adapter = QueueAdapter(items) { index ->
            controller?.seekToDefaultPosition(index)
        }
    }

    private fun updatePlayPauseIcon(playing: Boolean) {
        binding.playPauseButton.setImageResource(
            if (playing) R.drawable.ic_pause else R.drawable.ic_play,
        )
    }

    private fun loadCoverForCurrentTrack(mediaId: String?) {
        if (mediaId.isNullOrBlank()) return
        val config = app.credentials.load() ?: return
        lifecycleScope.launch {
            val file = app.subsonic.getCoverArtFile(
                config,
                mediaId,
                SubsonicClient.COVER_SIZE_LARGE,
            ) ?: return@launch
            val px = resources.getDimensionPixelSize(R.dimen.cover_art_now_playing)
            val bitmap = CoverArtBitmap.decode(file, px) ?: return@launch
            binding.coverArt.setImageBitmap(bitmap)
            binding.backgroundArt.setImageBitmap(bitmap)
        }
    }

    private fun formatMs(ms: Long): String {
        val totalSec = (ms / 1000).toInt()
        val min = totalSec / 60
        val sec = totalSec % 60
        return "%d:%02d".format(min, sec)
    }

    companion object {
        private const val PREVIOUS_COUNT = 15
        private const val UPCOMING_COUNT = 15
    }
}
