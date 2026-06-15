package com.dean.navplayer.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.media3.session.MediaController
import com.dean.navplayer.NavPlayerApp
import com.dean.navplayer.R
import com.dean.navplayer.data.CoverArtLoader
import com.dean.navplayer.data.PlaylistSummary
import com.dean.navplayer.data.SubsonicClient
import com.dean.navplayer.data.Track
import com.dean.navplayer.databinding.ActivityMainBinding
import com.dean.navplayer.playback.PlaybackService
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var app: NavPlayerApp
    private lateinit var playerConnector: MediaControllerConnector
    private var playlistAdapter: PlaylistAdapter? = null
    private var coverLoadJob: Job? = null
    private var coverMediaId: String? = null
    private var currentAccent: Int = 0
    private var playlistTracksJob: Job? = null
    private var randomLoadJob: Job? = null
    private var playbackAppendJob: Job? = null
    private var playingPlaylistId: String? = null
    private var playingRandom = false

    private val settingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        dismissSettingsHint()
        applyDrivingMode()
        if (result.resultCode == RESULT_OK) {
            loadPlaylists(forceRefresh = true)
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
        playerConnector = MediaControllerConnector(
            this,
            PlaybackService::class.java,
            lifecycleScope,
            playerCallbacks,
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (app.credentials.load() == null) {
            settingsLauncher.launch(Intent(this, SettingsActivity::class.java))
        }

        binding.playlistList.layoutManager = androidx.recyclerview.widget.GridLayoutManager(this, 2)
        binding.settingsButton.setOnClickListener {
            dismissSettingsHint()
            settingsLauncher.launch(Intent(this, SettingsActivity::class.java))
        }
        binding.playlistsHeader.setOnLongClickListener {
            dismissSettingsHint()
            settingsLauncher.launch(Intent(this, SettingsActivity::class.java))
            Toast.makeText(this, R.string.settings, Toast.LENGTH_SHORT).show()
            true
        }
        binding.randomButton.setOnClickListener { loadAndPlayRandom() }
        binding.retryButton.setOnClickListener { loadPlaylists(forceRefresh = true) }
        binding.emptyRandomButton.setOnClickListener { loadAndPlayRandom() }

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

        binding.playerBarTapTarget.setOnClickListener {
            if ((playerConnector.controller?.mediaItemCount ?: 0) > 0) {
                startActivity(Intent(this, NowPlayingActivity::class.java))
            }
        }

        updateSettingsHint()
        setupCoverStyling()
        applyDrivingMode()
        loadPlaylists()
    }

    private fun setupCoverStyling() {
        val corner = resources.getDimension(R.dimen.cover_corner_radius)
        val elevation = resources.getDimension(R.dimen.cover_elevation)
        CoverUi.roundCover(binding.coverArt, corner, elevation)
        val primary = ContextCompat.getColor(this, R.color.primary)
        CoverUi.applySeekAccent(this, binding.seekBar, primary)
        CoverUi.applyPlayPauseWhite(this, binding.playPauseButton)
    }

    override fun onStart() {
        super.onStart()
        playerConnector.connect()
    }

    override fun onResume() {
        super.onResume()
        applyDrivingMode()
        playerConnector.controller?.let { syncUiFromPlayer(it) }
    }

    private fun applyDrivingMode() {
        val driving = app.prefs.drivingMode
        HeadUnitUi.applyDrivingMode(this, binding, driving)
        playlistAdapter?.updateLayout(
            HeadUnitUi.playlistRowMinHeight(this, driving),
            HeadUnitUi.playlistCoverSizePx(this, driving),
            HeadUnitUi.playlistNameTextSp(driving),
        )
    }

    override fun onStop() {
        playerConnector.release()
        super.onStop()
    }

    private val playerCallbacks = object : MediaControllerConnector.Callbacks {
        override fun onControllerReady(controller: MediaController) {
            syncUiFromPlayer(controller)
            HeadUnitUi.setKeepScreenOn(this@MainActivity, controller.isPlaying)
        }

        override fun onControllerReleased() = Unit

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            updatePlayPauseIcon(isPlaying)
            HeadUnitUi.setKeepScreenOn(this@MainActivity, isPlaying)
        }

        override fun onMediaItemTransition(controller: MediaController) {
            syncUiFromPlayer(controller)
        }

        override fun onPlaybackStateChanged(controller: MediaController) {
            syncUiFromPlayer(controller)
        }

        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            showErrorToast(error)
        }

        override fun onSyncProgress(controller: MediaController) {
            syncProgress(controller)
        }
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

    private fun syncUiFromPlayer(controller: MediaController) {
        val hasQueue = controller.mediaItemCount > 0
        val metadata = controller.mediaMetadata
        binding.trackTitle.text = metadata.title?.toString().orEmpty()
        binding.trackArtist.text = metadata.artist?.toString().orEmpty()
        updatePlayPauseIcon(controller.isPlaying)
        syncProgress(controller)
        loadCoverForCurrentTrack(controller.currentMediaItem?.mediaId)
        updatePlayerChrome(hasQueue)
    }

    private fun updatePlayerChrome(hasQueue: Boolean) {
        binding.playingChip.isVisible = hasQueue
        binding.expandHint.isVisible = hasQueue
        binding.backgroundArt.isVisible = hasQueue
        binding.backgroundScrim.isVisible = hasQueue
        binding.playingChip.text = if (playingRandom) {
            getString(R.string.playing_random)
        } else {
            getString(R.string.playing)
        }
        binding.playerBarSeekRow.isVisible = hasQueue
        if (!hasQueue) {
            currentAccent = 0
            CoverUi.resetPlayerAccent(
                this,
                binding.playerBar,
                binding.seekBar,
                binding.playPauseButton,
                binding.playingChip,
            )
            playlistAdapter?.accentColor = 0
            binding.backgroundArt.setImageResource(R.drawable.ic_cover_placeholder)
        }
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
        val px = resources.getDimensionPixelSize(
            if (app.prefs.drivingMode) R.dimen.driving_cover_art_player_bar else R.dimen.cover_art_player_bar,
        )
        coverLoadJob = CoverArtLoader.load(
            lifecycleScope,
            app.subsonic,
            config,
            mediaId,
            SubsonicClient.COVER_SIZE_PLAYER,
            maxSidePx = px,
            isCurrent = { mediaId == coverMediaId },
            apply = { binding.coverArt.setImageBitmap(it) },
            applyBackground = { binding.backgroundArt.setImageBitmap(it) },
            applyAccent = { extracted ->
                val accent = CoverUi.resolveAccent(this, extracted)
                if (accent == currentAccent) return@load
                currentAccent = accent
                CoverUi.applyPlayerAccent(
                    this,
                    accent,
                    binding.playerBar,
                    binding.seekBar,
                    binding.playPauseButton,
                    binding.playingChip,
                )
                playlistAdapter?.accentColor = accent
            },
        )
    }

    private fun loadPlaylists(forceRefresh: Boolean = false) {
        val config = app.credentials.load() ?: return
        showLoading(R.string.loading_playlists, hideList = true)
        lifecycleScope.launch {
            runCatching { app.subsonic.getPlaylists(config, forceRefresh) }
                .onSuccess { list ->
                    if (list.isEmpty()) {
                        showEmpty(R.string.no_playlists)
                    } else {
                        showContent()
                        bindPlaylists(list, config)
                    }
                }
                .onFailure { showBrowseError(it) }
        }
    }

    private fun bindPlaylists(list: List<PlaylistSummary>, config: com.dean.navplayer.data.ServerConfig) {
        val adapter = PlaylistAdapter(
            list,
            lifecycleScope,
            app.subsonic,
            config,
            HeadUnitUi.playlistRowMinHeight(this, app.prefs.drivingMode),
            HeadUnitUi.playlistCoverSizePx(this, app.prefs.drivingMode),
            HeadUnitUi.playlistNameTextSp(app.prefs.drivingMode),
        ) { playlist ->
            playlistTracksJob?.cancel()
            playlistTracksJob = lifecycleScope.launch {
                showLoading(R.string.loading_tracks, hideList = false)
                runCatching { app.subsonic.getPlaylistTracks(config, playlist.id) }
                    .onSuccess { tracks ->
                        hideLoading()
                        setPlayingSource(playlistId = playlist.id, random = false)
                        playTracksProgressive(tracks, R.string.no_playlist_songs)
                    }
                    .onFailure {
                        hideLoading()
                        showErrorToast(it)
                    }
            }
        }.also {
            it.playingPlaylistId = playingPlaylistId
            it.accentColor = currentAccent
            playlistAdapter = it
        }
        binding.playlistList.adapter = adapter
    }

    private fun loadAndPlayRandom() {
        val config = app.credentials.load() ?: return
        randomLoadJob?.cancel()
        playbackAppendJob?.cancel()
        ContextCompat.startForegroundService(this, Intent(this, PlaybackService::class.java))
        randomLoadJob = lifecycleScope.launch {
            showLoading(R.string.loading_random, hideList = false)
            runCatching {
                app.subsonic.getRandomSongs(config, PlaybackService.INITIAL_PLAYBACK_BATCH)
            }.onSuccess { tracks ->
                hideLoading()
                if (tracks.isEmpty()) {
                    Toast.makeText(this@MainActivity, R.string.no_library_songs, Toast.LENGTH_SHORT).show()
                    return@launch
                }
                if (binding.emptyState.isVisible) {
                    showContent()
                }
                setPlayingSource(playlistId = null, random = true)
                val shuffled = tracks.shuffled()
                val generation = app.playbackQueue.beginQueue(shuffled, shuffled.size)
                startPlayback(generation, randomMode = true)
                prefetchMoreRandom(config, generation)
            }.onFailure {
                hideLoading()
                showBrowseError(it)
            }
        }
    }

    private fun prefetchMoreRandom(config: com.dean.navplayer.data.ServerConfig, generation: Long) {
        playbackAppendJob = lifecycleScope.launch {
            runCatching {
                app.subsonic.getRandomSongs(
                    config,
                    SubsonicClient.RANDOM_FOLLOWUP_BATCH_SIZE,
                )
            }.onSuccess { more ->
                if (more.isEmpty()) return@onSuccess
                app.playbackQueue.extendExpectedQueue(more.size)
                appendTracks(more.shuffled(), generation)
            }
        }
    }

    private fun setPlayingSource(playlistId: String?, random: Boolean) {
        playingPlaylistId = playlistId
        playingRandom = random
        playlistAdapter?.playingPlaylistId = playlistId
    }

    private fun playTracksProgressive(
        tracks: List<Track>,
        emptyMessage: Int = R.string.no_library_songs,
        randomMode: Boolean = false,
    ) {
        if (tracks.isEmpty()) {
            Toast.makeText(this, emptyMessage, Toast.LENGTH_SHORT).show()
            return
        }
        if (tracks.size <= PlaybackService.INITIAL_PLAYBACK_BATCH) {
            playTracks(tracks, randomMode)
            return
        }
        val initial = tracks.take(PlaybackService.INITIAL_PLAYBACK_BATCH)
        val remainder = tracks.drop(PlaybackService.INITIAL_PLAYBACK_BATCH)
        val generation = app.playbackQueue.beginQueue(initial, tracks.size)
        startPlayback(generation, randomMode)
        playbackAppendJob?.cancel()
        playbackAppendJob = lifecycleScope.launch {
            appendTracks(remainder, generation)
        }
    }

    private fun playTracks(tracks: List<Track>, randomMode: Boolean = false) {
        playbackAppendJob?.cancel()
        playbackAppendJob = null
        val generation = app.playbackQueue.beginQueue(tracks, tracks.size)
        startPlayback(generation, randomMode)
    }

    private fun startPlayback(generation: Long, randomMode: Boolean) {
        ContextCompat.startForegroundService(
            this,
            Intent(this, PlaybackService::class.java).apply {
                action = PlaybackService.ACTION_SET_QUEUE
                putExtra(PlaybackService.EXTRA_RANDOM_MODE, randomMode)
                putExtra(PlaybackService.EXTRA_QUEUE_GENERATION, generation)
            },
        )
    }

    private fun appendTracks(tracks: List<Track>, generation: Long) {
        if (tracks.isEmpty()) return
        app.playbackQueue.setAppend(tracks, generation)
        ContextCompat.startForegroundService(
            this,
            Intent(this, PlaybackService::class.java).apply {
                action = PlaybackService.ACTION_APPEND_QUEUE
                putExtra(PlaybackService.EXTRA_QUEUE_GENERATION, generation)
            },
        )
    }

    private fun showLoading(@StringRes message: Int, hideList: Boolean) {
        binding.loadingText.setText(message)
        binding.loadingOverlay.isVisible = true
        binding.emptyState.isVisible = false
        if (hideList) {
            binding.playlistList.isVisible = false
        }
    }

    private fun hideLoading() {
        binding.loadingOverlay.isVisible = false
        if (!binding.emptyState.isVisible) {
            binding.playlistList.isVisible = true
        }
    }

    private fun showContent() {
        binding.loadingOverlay.isVisible = false
        binding.emptyState.isVisible = false
        binding.playlistList.isVisible = true
    }

    private fun showEmpty(@StringRes message: Int) {
        binding.loadingOverlay.isVisible = false
        binding.playlistList.isVisible = false
        binding.emptyState.isVisible = true
        binding.emptyMessage.setText(message)
        binding.emptySubtext.setText(R.string.empty_browse_hint)
        binding.retryButton.isVisible = false
        binding.emptyRandomButton.isVisible = true
    }

    private fun showBrowseError(error: Throwable) {
        binding.loadingOverlay.isVisible = false
        binding.playlistList.isVisible = false
        binding.emptyState.isVisible = true
        binding.emptyMessage.text = error.message ?: getString(R.string.connection_failed)
        binding.emptySubtext.setText(R.string.empty_browse_hint)
        binding.retryButton.isVisible = true
        binding.emptyRandomButton.isVisible = true
    }

    private fun showErrorToast(error: Throwable) {
        Toast.makeText(
            this,
            error.message ?: getString(R.string.connection_failed),
            Toast.LENGTH_LONG,
        ).show()
    }

    private fun updateSettingsHint() {
        binding.settingsHint.isVisible = !app.prefs.settingsHintDismissed
    }

    private fun dismissSettingsHint() {
        if (!app.prefs.settingsHintDismissed) {
            app.prefs.settingsHintDismissed = true
            binding.settingsHint.isVisible = false
        }
    }
}
