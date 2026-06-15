package com.dean.navplayer.ui

import android.content.Intent
import android.os.Bundle
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.media3.session.MediaController
import androidx.recyclerview.widget.LinearLayoutManager
import com.dean.navplayer.NavPlayerApp
import com.dean.navplayer.data.CoverArtLoader
import com.dean.navplayer.data.SubsonicClient
import com.dean.navplayer.R
import com.dean.navplayer.databinding.ActivityNowPlayingBinding
import com.dean.navplayer.playback.PlaybackService
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.Job

class NowPlayingActivity : AppCompatActivity() {
    private lateinit var binding: ActivityNowPlayingBinding
    private lateinit var app: NavPlayerApp
    private lateinit var playerConnector: MediaControllerConnector
    private var queueAdapter: QueueAdapter? = null
    private var coverLoadJob: Job? = null
    private var coverMediaId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        HeadUnitUi.apply(this)
        binding = ActivityNowPlayingBinding.inflate(layoutInflater)
        setContentView(binding.root)
        app = application as NavPlayerApp
        playerConnector = MediaControllerConnector(
            this,
            PlaybackService::class.java,
            lifecycleScope,
            playerCallbacks,
        )

        binding.closeButton.setOnClickListener { finish() }
        binding.queueList.layoutManager = LinearLayoutManager(this)

        binding.preButton.setOnClickListener { playerConnector.controller?.seekToPreviousMediaItem() }
        binding.playPauseButton.setOnClickListener {
            val c = playerConnector.controller ?: return@setOnClickListener
            if (c.isPlaying) c.pause() else c.play()
        }
        binding.nextButton.setOnClickListener { playerConnector.controller?.seekToNextMediaItem() }

        binding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    playerConnector.userSeeking = true
                    val duration = playerConnector.controller?.duration ?: return
                    if (duration > 0) {
                        binding.positionText.text = MediaControllerConnector.formatMs(progress * duration / 100)
                    }
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                playerConnector.userSeeking = true
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                val duration = playerConnector.controller?.duration ?: return
                if (duration > 0) {
                    playerConnector.controller?.seekTo(binding.seekBar.progress * duration / 100)
                }
                playerConnector.userSeeking = false
            }
        })
    }

    override fun onStart() {
        super.onStart()
        playerConnector.connect()
    }

    override fun onResume() {
        super.onResume()
        applyDrivingMode()
        playerConnector.controller?.let { syncUi(it) }
    }

    private fun applyDrivingMode() {
        HeadUnitUi.applyDrivingMode(this, binding, app.prefs.drivingMode)
        queueAdapter?.setRowMinHeightPx(HeadUnitUi.queueRowMinHeight(this, app.prefs.drivingMode))
    }

    override fun onStop() {
        playerConnector.release()
        super.onStop()
    }

    private val playerCallbacks = object : MediaControllerConnector.Callbacks {
        override fun onControllerReady(controller: MediaController) {
            syncUi(controller)
            HeadUnitUi.setKeepScreenOn(this@NowPlayingActivity, controller.isPlaying)
        }

        override fun onControllerReleased() = Unit

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            updatePlayPauseIcon(isPlaying)
            HeadUnitUi.setKeepScreenOn(this@NowPlayingActivity, isPlaying)
        }

        override fun onMediaItemTransition(controller: MediaController) {
            syncUi(controller)
        }

        override fun onPlaybackStateChanged(controller: MediaController) {
            syncUi(controller)
        }

        override fun onPlayerError(error: androidx.media3.common.PlaybackException) = Unit

        override fun onSyncProgress(controller: MediaController) {
            syncProgress(controller)
            updateQueueLoadingHint(controller.mediaItemCount)
        }
    }

    private fun updateQueueLoadingHint(playerItemCount: Int) {
        binding.queueLoadingHint.isVisible = app.playbackQueue.isQueueLoading(playerItemCount)
    }

    private fun syncUi(controller: MediaController) {
        if (controller.mediaItemCount == 0) {
            finish()
            return
        }

        val metadata = controller.mediaMetadata
        binding.trackTitle.text = metadata.title?.toString().orEmpty()
        binding.trackArtist.text = metadata.artist?.toString().orEmpty()
        updatePlayPauseIcon(controller.isPlaying)
        syncProgress(controller)
        syncQueue(controller)
        updateQueueLoadingHint(controller.mediaItemCount)
        loadCoverForCurrentTrack(controller.currentMediaItem?.mediaId)
    }

    private fun syncProgress(controller: MediaController) {
        val duration = controller.duration
        if (duration > 0) {
            binding.seekBar.max = 100
            binding.seekBar.progress = ((controller.currentPosition * 100) / duration).toInt().coerceIn(0, 100)
            binding.positionText.text = MediaControllerConnector.formatMs(controller.currentPosition)
            binding.durationText.text = "/ ${MediaControllerConnector.formatMs(duration)}"
        }
    }

    private fun syncQueue(controller: MediaController) {
        val current = controller.currentMediaItemIndex
        val count = controller.mediaItemCount
        val start = max(0, current - PREVIOUS_COUNT)
        val end = min(current + UPCOMING_COUNT, count)
        val items = (start until end).map { index ->
            val meta = controller.getMediaItemAt(index).mediaMetadata
            QueueItem(
                playerIndex = index,
                title = meta.title?.toString().orEmpty(),
                artist = meta.artist?.toString().orEmpty(),
                isCurrent = index == current,
            )
        }
        val adapter = queueAdapter ?: QueueAdapter(
            onSelect = { index -> jumpToQueueIndex(index) },
            rowMinHeightPx = HeadUnitUi.queueRowMinHeight(this, app.prefs.drivingMode),
        ).also {
            queueAdapter = it
            binding.queueList.adapter = it
        }
        adapter.submitItems(items)
    }

    private fun jumpToQueueIndex(index: Int) {
        startService(
            Intent(this, PlaybackService::class.java).apply {
                action = PlaybackService.ACTION_SEEK_TO_INDEX
                putExtra(PlaybackService.EXTRA_MEDIA_INDEX, index)
            },
        )
    }

    private fun updatePlayPauseIcon(playing: Boolean) {
        binding.playPauseButton.setImageResource(
            if (playing) R.drawable.ic_pause else R.drawable.ic_play,
        )
    }

    private fun loadCoverForCurrentTrack(mediaId: String?) {
        coverLoadJob?.cancel()
        coverMediaId = mediaId
        if (mediaId.isNullOrBlank()) {
            binding.coverArt.setImageResource(R.drawable.ic_cover_placeholder)
            binding.backgroundArt.setImageResource(R.drawable.ic_cover_placeholder)
            return
        }
        val config = app.credentials.load() ?: return
        val px = resources.getDimensionPixelSize(R.dimen.cover_art_now_playing)
        coverLoadJob = CoverArtLoader.load(
            lifecycleScope,
            app.subsonic,
            config,
            mediaId,
            SubsonicClient.COVER_SIZE_LARGE,
            maxSidePx = px,
            isCurrent = { mediaId == coverMediaId },
            apply = { binding.coverArt.setImageBitmap(it) },
            applyBackground = { binding.backgroundArt.setImageBitmap(it) },
        )
    }

    companion object {
        private const val PREVIOUS_COUNT = 15
        private const val UPCOMING_COUNT = 15
    }
}
