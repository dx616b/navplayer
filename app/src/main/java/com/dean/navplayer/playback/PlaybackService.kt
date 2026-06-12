package com.dean.navplayer.playback

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
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

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val playerListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            trimPlayedMediaItems()
            maybePrefetchRandomBatch()
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
            .setBufferDurationsMs(
                MIN_BUFFER_MS,
                MAX_BUFFER_MS,
                BUFFER_FOR_PLAYBACK_MS,
                BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS,
            )
            .setPrioritizeTimeOverSizeThresholds(true)
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
        when (intent?.action) {
            ACTION_SET_QUEUE -> {
                randomMode = intent.getBooleanExtra(EXTRA_RANDOM_MODE, false)
                val tracks = (application as NavPlayerApp).playbackQueue.take().orEmpty()
                playQueue(tracks, replace = true)
            }
            ACTION_SEEK_TO_INDEX -> {
                val index = intent.getIntExtra(EXTRA_MEDIA_INDEX, -1)
                seekToQueueIndex(index)
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun seekToQueueIndex(index: Int) {
        val exo = player ?: return
        if (index !in 0 until exo.mediaItemCount) return
        exo.seekTo(index, 0L)
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
    ): List<MediaItem> = tracks.map { track ->
        MediaItem.Builder()
            .setUri(Uri.parse(client.streamUrl(config, track.id)))
            .setMediaId(track.id)
            .setMediaMetadata(
                androidx.media3.common.MediaMetadata.Builder()
                    .setTitle(track.title)
                    .setArtist(track.artist)
                    .build(),
            )
            .build()
    }

    companion object {
        const val ACTION_SET_QUEUE = "com.dean.navplayer.action.SET_QUEUE"
        const val ACTION_SEEK_TO_INDEX = "com.dean.navplayer.action.SEEK_TO_INDEX"
        const val EXTRA_RANDOM_MODE = "random_mode"
        const val EXTRA_MEDIA_INDEX = "media_index"

        private const val RANDOM_PREFETCH_REMAINING = 10
        private const val MAX_QUEUE_AHEAD = 35
        private const val RANDOM_PREFETCH_BATCH_SIZE = 50
        private const val KEEP_BEHIND_RANDOM = 15

        // Tuned for always-on cellular: higher pre-buffer, longer timeouts.
        private const val HTTP_TIMEOUT_MS = 30_000
        private const val MIN_BUFFER_MS = 60_000
        private const val MAX_BUFFER_MS = 180_000
        private const val BUFFER_FOR_PLAYBACK_MS = 15_000
        private const val BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS = 30_000
    }
}
