package com.dean.navplayer.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Connects a screen to [PlaybackService] via Media3 [MediaController].
 * Shared by main and now-playing screens (same pattern as NaviPK's PlayerManager).
 */
class MediaControllerConnector(
    private val context: Context,
    private val serviceClass: Class<*>,
    private val scope: LifecycleCoroutineScope,
    private val callbacks: Callbacks,
) {
    interface Callbacks {
        fun onControllerReady(controller: MediaController)
        fun onControllerReleased()
        fun onIsPlayingChanged(isPlaying: Boolean)
        fun onMediaItemTransition(controller: MediaController)
        fun onPlaybackStateChanged(controller: MediaController)
        fun onPlayerError(error: PlaybackException)
        fun onSyncProgress(controller: MediaController)
    }

    var controller: MediaController? = null
        private set

    var userSeeking = false
        set(value) {
            field = value
            if (!value) {
                controller?.let(callbacks::onSyncProgress)
            }
        }

    private var progressJob: Job? = null
    private var connectFuture: ListenableFuture<MediaController>? = null

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            callbacks.onIsPlayingChanged(isPlaying)
        }

        override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
            controller?.let(callbacks::onMediaItemTransition)
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            controller?.let(callbacks::onPlaybackStateChanged)
        }

        override fun onPlayerError(error: PlaybackException) {
            callbacks.onPlayerError(error)
        }
    }

    fun connect() {
        if (controller != null || connectFuture != null) return
        context.startService(Intent(context, serviceClass))
        val token = SessionToken(context, ComponentName(context, serviceClass))
        val future = MediaController.Builder(context, token).buildAsync()
        connectFuture = future
        future.addListener(
            {
                connectFuture = null
                runCatching { future.get(10, TimeUnit.SECONDS) }
                    .onSuccess { c ->
                        controller = c
                        c.addListener(playerListener)
                        callbacks.onControllerReady(c)
                        startProgressLoop()
                    }
            },
            MoreExecutors.directExecutor(),
        )
    }

    fun release() {
        progressJob?.cancel()
        progressJob = null
        controller?.removeListener(playerListener)
        controller?.release()
        controller = null
        connectFuture = null
        callbacks.onControllerReleased()
    }

    private fun startProgressLoop() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive) {
                val c = controller
                if (c != null && !userSeeking) {
                    callbacks.onSyncProgress(c)
                }
                delay(if (c?.isPlaying == true) 500 else 1_000)
            }
        }
    }

    companion object {
        fun formatMs(ms: Long): String {
            val totalSec = (ms / 1000).toInt()
            val min = totalSec / 60
            val sec = totalSec % 60
            return "%d:%02d".format(min, sec)
        }
    }
}
