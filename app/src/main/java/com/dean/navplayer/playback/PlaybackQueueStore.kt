package com.dean.navplayer.playback

import com.dean.navplayer.data.Track

/** Holds the next queue in-process — avoids Intent size limits on large libraries. */
class PlaybackQueueStore {
    private data class AppendRequest(val generation: Long, val tracks: List<Track>)

    private val pendingStarts = mutableMapOf<Long, List<Track>>()

    @Volatile
    private var pendingAppend: AppendRequest? = null

    @Volatile
    private var queueGeneration: Long = 0L

    /** Full playlist/random size when loading progressively. */
    @Volatile
    var expectedQueueSize: Int = 0
        private set

    @Volatile
    var appendInFlight: Boolean = false
        private set

    @Synchronized
    fun beginQueue(initialTracks: List<Track>, totalSize: Int): Long {
        queueGeneration++
        val generation = queueGeneration
        pendingStarts.clear()
        pendingAppend = null
        pendingStarts[generation] = initialTracks.toList()
        expectedQueueSize = totalSize
        appendInFlight = totalSize > initialTracks.size
        return generation
    }

    @Synchronized
    fun setAppend(tracks: List<Track>, generation: Long) {
        if (generation != queueGeneration) return
        pendingAppend = AppendRequest(generation, tracks.toList())
        appendInFlight = true
    }

    @Synchronized
    fun takeStart(requestedGeneration: Long): List<Track>? =
        pendingStarts.remove(requestedGeneration)

    @Synchronized
    fun takeAppend(requestedGeneration: Long): List<Track>? {
        val request = pendingAppend ?: return null
        if (request.generation != requestedGeneration || requestedGeneration != queueGeneration) {
            return null
        }
        pendingAppend = null
        return request.tracks
    }

    @Synchronized
    fun onAppendComplete(generation: Long) {
        if (generation == queueGeneration) {
            appendInFlight = false
        }
    }

    @Synchronized
    fun extendExpectedQueue(additionalCount: Int) {
        expectedQueueSize += additionalCount
        appendInFlight = true
    }

    fun isQueueLoading(currentPlayerItemCount: Int): Boolean =
        appendInFlight || expectedQueueSize > currentPlayerItemCount
}
