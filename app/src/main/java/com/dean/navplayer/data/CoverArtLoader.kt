package com.dean.navplayer.data

import android.graphics.Bitmap
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Shows cached art immediately; downloads and decodes in the background on a miss. */
object CoverArtLoader {
    fun load(
        scope: CoroutineScope,
        subsonic: SubsonicClient,
        config: ServerConfig,
        coverArtId: String,
        size: Int,
        maxSidePx: Int,
        lowMemory: Boolean = false,
        isCurrent: () -> Boolean,
        apply: (Bitmap) -> Unit,
    ): Job = scope.launch {
        cachedCoverArtFile(subsonic, config, coverArtId, size)?.let { file ->
            if (!isCurrent()) return@launch
            decode(file, maxSidePx, lowMemory)?.let(apply)
        }
        val file = withContext(Dispatchers.IO) {
            subsonic.prefetchCoverArt(config, coverArtId, size)
        } ?: return@launch
        if (!isCurrent()) return@launch
        decode(file, maxSidePx, lowMemory)?.let(apply)
    }

    suspend fun loadFile(
        subsonic: SubsonicClient,
        config: ServerConfig,
        coverArtId: String,
        size: Int,
    ): File? = cachedCoverArtFile(subsonic, config, coverArtId, size)
        ?: subsonic.prefetchCoverArt(config, coverArtId, size)

    private fun cachedCoverArtFile(
        subsonic: SubsonicClient,
        config: ServerConfig,
        coverArtId: String,
        size: Int,
    ): File? = subsonic.cachedCoverArtFile(config, coverArtId, size)

    private fun decode(file: File, maxSidePx: Int, lowMemory: Boolean): Bitmap? =
        CoverArtBitmap.decode(file, maxSidePx, lowMemory)
}
