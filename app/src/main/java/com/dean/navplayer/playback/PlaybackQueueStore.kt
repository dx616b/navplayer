package com.dean.navplayer.playback

import com.dean.navplayer.data.Track

/** Holds the next queue in-process — avoids Intent size limits on large libraries. */
class PlaybackQueueStore {
    @Volatile
    private var pending: List<Track>? = null

    @Synchronized
    fun set(tracks: List<Track>) {
        pending = tracks.toList()
    }

    @Synchronized
    fun take(): List<Track>? = pending.also { pending = null }
}
