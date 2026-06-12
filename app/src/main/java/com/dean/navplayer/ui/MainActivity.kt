package com.dean.navplayer.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.recyclerview.widget.GridLayoutManager
import com.dean.navplayer.NavPlayerApp
import com.dean.navplayer.R
import com.dean.navplayer.data.CoverArtLoader
import com.dean.navplayer.data.SubsonicClient
import com.dean.navplayer.data.Track
import com.dean.navplayer.databinding.ActivityMainBinding
import com.dean.navplayer.playback.PlaybackService
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var app: NavPlayerApp
    private var controller: MediaController? = null
    private var progressJob: Job? = null
    private var userSeeking = false
    private var coverLoadJob: Job? = null
    private var coverMediaId: String? = null

    private val settingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        applyDrivingMode()
        if (result.resultCode == RESULT_OK) {
            loadPlaylists()
        } else if (app.credentials.load() == null) {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        HeadUnitUi.apply(this)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        app = application as NavPlayerApp

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (app.credentials.load() == null) {
            settingsLauncher.launch(Intent(this, SettingsActivity::class.java))
        }

        binding.playlistList.layoutManager = GridLayoutManager(this, 2)
        binding.settingsButton.setOnClickListener {
            settingsLauncher.launch(Intent(this, SettingsActivity::class.java))
        }
        binding.playlistsHeader.setOnLongClickListener {
            settingsLauncher.launch(Intent(this, SettingsActivity::class.java))
            Toast.makeText(this, R.string.settings, Toast.LENGTH_SHORT).show()
            true
        }
        binding.randomButton.setOnClickListener { loadAndPlayRandom() }

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

        binding.playerBar.setOnClickListener {
            if ((controller?.mediaItemCount ?: 0) > 0) {
                startActivity(Intent(this, NowPlayingActivity::class.java))
            }
        }

        applyDrivingMode()
        loadPlaylists()
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
        syncUiFromPlayer()
    }

    private fun applyDrivingMode() {
        HeadUnitUi.applyDrivingMode(this, binding, app.prefs.drivingMode)
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
                        syncUiFromPlayer()
                        HeadUnitUi.setKeepScreenOn(this@MainActivity, c.isPlaying)
                        startProgressLoop()
                    }
            },
            MoreExecutors.directExecutor(),
        )
    }

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            updatePlayPauseIcon(isPlaying)
            HeadUnitUi.setKeepScreenOn(this@MainActivity, isPlaying)
        }

        override fun onMediaItemTransition(
            mediaItem: androidx.media3.common.MediaItem?,
            reason: Int,
        ) {
            syncUiFromPlayer()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            syncUiFromPlayer()
        }

        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            showError(error)
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

    private fun syncUiFromPlayer() {
        val c = controller ?: return
        val metadata = c.mediaMetadata
        binding.trackTitle.text = metadata.title?.toString().orEmpty()
        binding.trackArtist.text = metadata.artist?.toString().orEmpty()
        updatePlayPauseIcon(c.isPlaying)
        syncProgress()
        loadCoverForCurrentTrack(c.currentMediaItem?.mediaId)
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
            return
        }
        val config = app.credentials.load() ?: return
        val px = resources.getDimensionPixelSize(R.dimen.cover_art_player_bar)
        coverLoadJob = CoverArtLoader.load(
            lifecycleScope,
            app.subsonic,
            config,
            mediaId,
            SubsonicClient.COVER_SIZE_PLAYER,
            maxSidePx = px,
            isCurrent = { mediaId == coverMediaId },
            apply = { binding.coverArt.setImageBitmap(it) },
        )
    }

    private fun loadPlaylists() {
        val config = app.credentials.load() ?: return
        binding.loadingIndicator.isVisible = true
        lifecycleScope.launch {
            runCatching { app.subsonic.getPlaylists(config) }
                .onSuccess { list ->
                    binding.loadingIndicator.isVisible = false
                    binding.playlistList.adapter = PlaylistAdapter(
                        list,
                        lifecycleScope,
                        app.subsonic,
                        config,
                        HeadUnitUi.playlistRowMinHeight(this@MainActivity, app.prefs.drivingMode),
                        HeadUnitUi.playlistCoverSizePx(this@MainActivity, app.prefs.drivingMode),
                    ) { playlist ->
                        lifecycleScope.launch {
                            binding.loadingIndicator.isVisible = true
                            runCatching { app.subsonic.getPlaylistTracks(config, playlist.id) }
                                .onSuccess { playTracks(it, R.string.no_playlist_songs) }
                                .onFailure { showError(it) }
                            binding.loadingIndicator.isVisible = false
                        }
                    }
                }
                .onFailure {
                    binding.loadingIndicator.isVisible = false
                    showError(it)
                }
        }
    }

    private fun loadAndPlayRandom() {
        val config = app.credentials.load() ?: return
        binding.loadingIndicator.isVisible = true
        lifecycleScope.launch {
            runCatching { app.subsonic.getRandomSongs(config) }
                .onSuccess { playTracks(it.shuffled(), randomMode = true) }
                .onFailure { showError(it) }
            binding.loadingIndicator.isVisible = false
        }
    }

    private fun playTracks(
        tracks: List<Track>,
        emptyMessage: Int = R.string.no_library_songs,
        randomMode: Boolean = false,
    ) {
        if (tracks.isEmpty()) {
            Toast.makeText(this, emptyMessage, Toast.LENGTH_SHORT).show()
            return
        }
        app.playbackQueue.set(tracks)
        val intent = Intent(this, PlaybackService::class.java).apply {
            action = PlaybackService.ACTION_SET_QUEUE
            putExtra(PlaybackService.EXTRA_RANDOM_MODE, randomMode)
        }
        ContextCompat.startForegroundService(this, intent)
    }

    private fun showError(error: Throwable) {
        Toast.makeText(
            this,
            error.message ?: getString(R.string.connection_failed),
            Toast.LENGTH_LONG,
        ).show()
    }

    private fun formatMs(ms: Long): String {
        val totalSec = (ms / 1000).toInt()
        val min = totalSec / 60
        val sec = totalSec % 60
        return "%d:%02d".format(min, sec)
    }
}
