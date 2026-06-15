package com.dean.navplayer.playback

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.dean.navplayer.NavPlayerApp
import com.dean.navplayer.data.SubsonicClient
import com.dean.navplayer.data.Track
import com.dean.navplayer.ui.NowPlayingActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {
    private var player: ExoPlayer? = null
    private var mediaSession: MediaSession? = null
    private var randomMode = false
    private var isPrefetchingRandom = false
    private var consecutivePlayErrors = 0
    private var activeQueueGeneration: Long = 0L

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val playerListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            consecutivePlayErrors = 0
            trimPlayedMediaItems()
            maybePrefetchRandomBatch()
        }

        override fun onPlayerError(error: PlaybackException) {
            skipAfterPlayError()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) {
                maybePrefetchRandomBatch()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        setMediaNotificationProvider(DefaultMediaNotificationProvider(this))

        val httpFactory = DefaultHttpDataSource.Factory()
            .setConnectTimeoutMs(HTTP_TIMEOUT_MS)
            .setReadTimeoutMs(HTTP_TIMEOUT_MS)
            .setAllowCrossProtocolRedirects(true)
        val dataSourceFactory = DefaultDataSource.Factory(this, httpFactory)
        val loadControl = DefaultLoadControl.Builder()
            // Head units stream over cellular/LTE — tuned for one track at a time, not whole queue.
            .setBufferDurationsMs(
                STREAMING_MIN_BUFFER_MS,
                STREAMING_MAX_BUFFER_MS,
                STREAMING_BUFFER_FOR_PLAYBACK_MS,
                STREAMING_BUFFER_AFTER_REBUFFER_MS,
            )
            .setPrioritizeTimeOverSizeThresholds(false)
            .setBackBuffer(BACK_BUFFER_MS, false)
            .build()

        val exo = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .setLoadControl(loadControl)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true,
            )
            .build()
        exo.addListener(playerListener)
        player = exo
        mediaSession = MediaSession.Builder(this, exo)
            .setSessionActivity(
                PendingIntent.getActivity(
                    this,
                    0,
                    Intent(this, NowPlayingActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val app = application as NavPlayerApp
        when (intent?.action) {
            ACTION_SET_QUEUE -> {
                randomMode = intent.getBooleanExtra(EXTRA_RANDOM_MODE, false)
                val generation = intent.getLongExtra(EXTRA_QUEUE_GENERATION, -1L)
                val tracks = app.playbackQueue.takeStart(generation).orEmpty()
                if (tracks.isEmpty()) {
                    return super.onStartCommand(intent, flags, startId)
                }
                activeQueueGeneration = generation
                playQueue(tracks, replace = true)
            }
            ACTION_APPEND_QUEUE -> {
                val generation = intent.getLongExtra(EXTRA_QUEUE_GENERATION, -1L)
                if (generation != activeQueueGeneration) {
                    return super.onStartCommand(intent, flags, startId)
                }
                val tracks = app.playbackQueue.takeAppend(generation).orEmpty()
                if (tracks.isNotEmpty()) {
                    playQueue(tracks, replace = false)
                }
                app.playbackQueue.onAppendComplete(generation)
            }
            ACTION_SEEK_TO_INDEX -> {
                val index = intent.getIntExtra(EXTRA_MEDIA_INDEX, -1)
                seekToQueueIndex(index)
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun skipAfterPlayError() {
        val exo = player ?: return
        consecutivePlayErrors++
        if (consecutivePlayErrors > MAX_CONSECUTIVE_PLAY_ERRORS) {
            exo.stop()
            return
        }
        if (exo.hasNextMediaItem()) {
            exo.seekToNextMediaItem()
            exo.play()
        } else if (randomMode) {
            maybePrefetchRandomBatch()
        } else {
            exo.stop()
        }
    }

    private fun seekToQueueIndex(index: Int) {
        val exo = player ?: return
        if (index !in 0 until exo.mediaItemCount) return
        consecutivePlayErrors = 0
        exo.seekTo(index, 0L)
        if (exo.playbackState == Player.STATE_IDLE || exo.playbackState == Player.STATE_ENDED) {
            exo.prepare()
        }
        exo.play()
    }

    override fun onDestroy() {
        player?.removeListener(playerListener)
        mediaSession?.release()
        player?.release()
        mediaSession = null
        player = null
        super.onDestroy()
    }

    private fun playQueue(tracks: List<Track>, replace: Boolean) {
        if (tracks.isEmpty()) return
        val exo = player ?: return
        val app = application as NavPlayerApp
        val config = app.credentials.load() ?: return
        val items = tracksToMediaItems(app.subsonic, config, tracks)
        if (replace) {
            consecutivePlayErrors = 0
            exo.setMediaItems(items, 0, 0L)
            exo.prepare()
            exo.play()
        } else {
            exo.addMediaItems(items)
        }
    }

    private fun trimPlayedMediaItems() {
        if (!randomMode) return
        val exo = player ?: return
        val index = exo.currentMediaItemIndex
        val trimEnd = index - KEEP_BEHIND_RANDOM
        if (trimEnd > 0) {
            exo.removeMediaItems(0, trimEnd)
        }
    }

    private fun maybePrefetchRandomBatch() {
        if (!randomMode || isPrefetchingRandom) return
        val exo = player ?: return
        if (exo.mediaItemCount == 0) return

        val remaining = exo.mediaItemCount - exo.currentMediaItemIndex - 1
        if (remaining > RANDOM_PREFETCH_REMAINING) return
        if (remaining >= MAX_QUEUE_AHEAD) return

        isPrefetchingRandom = true
        serviceScope.launch {
            runCatching {
                val app = application as NavPlayerApp
                val config = app.credentials.load()
                    ?: throw IllegalStateException("Not logged in")
                withContext(Dispatchers.IO) {
                    app.subsonic.getRandomSongs(config, RANDOM_PREFETCH_BATCH_SIZE)
                }
            }.onSuccess { tracks ->
                if (tracks.isNotEmpty()) {
                    val space = MAX_QUEUE_AHEAD - remaining
                    playQueue(tracks.shuffled().take(space), replace = false)
                }
            }
            isPrefetchingRandom = false
        }
    }

    private fun tracksToMediaItems(
        client: com.dean.navplayer.data.SubsonicClient,
        config: com.dean.navplayer.data.ServerConfig,
        tracks: List<Track>,
    ): List<MediaItem> {
        val streamUrls = client.streamUrls(config, tracks.map { it.id })
        return tracks.zip(streamUrls) { track, url ->
            MediaItem.Builder()
                .setUri(Uri.parse(url))
                .setMediaId(track.id)
                .setMediaMetadata(
                    androidx.media3.common.MediaMetadata.Builder()
                        .setTitle(track.title)
                        .setArtist(track.artist)
                        .build(),
                )
                .build()
        }
    }

    companion object {
        const val ACTION_SET_QUEUE = "com.dean.navplayer.action.SET_QUEUE"
        const val ACTION_APPEND_QUEUE = "com.dean.navplayer.action.APPEND_QUEUE"
        const val ACTION_SEEK_TO_INDEX = "com.dean.navplayer.action.SEEK_TO_INDEX"
        const val EXTRA_RANDOM_MODE = "random_mode"
        const val EXTRA_MEDIA_INDEX = "media_index"
        const val EXTRA_QUEUE_GENERATION = "queue_generation"

        /** Tracks queued immediately; the rest append in the background. */
        const val INITIAL_PLAYBACK_BATCH = 20

        private const val RANDOM_PREFETCH_REMAINING = 10
        private const val MAX_QUEUE_AHEAD = 35
        private const val RANDOM_PREFETCH_BATCH_SIZE = 50
        private const val KEEP_BEHIND_RANDOM = 15
        private const val MAX_CONSECUTIVE_PLAY_ERRORS = 5

        // Cellular head-unit streaming: ~20s lookahead on current track, ~1s to start,
        // longer recovery after rebuffer. Queue URIs only — not the full playlist.
        private const val HTTP_TIMEOUT_MS = 30_000
        private const val STREAMING_MIN_BUFFER_MS = 20_000
        private const val STREAMING_MAX_BUFFER_MS = 60_000
        private const val STREAMING_BUFFER_FOR_PLAYBACK_MS = 1_000
        private const val STREAMING_BUFFER_AFTER_REBUFFER_MS = 5_000
        private const val BACK_BUFFER_MS = 0
    }
}
