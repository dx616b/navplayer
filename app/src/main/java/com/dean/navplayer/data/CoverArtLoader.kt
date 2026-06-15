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
        applyBackground: ((Bitmap) -> Unit)? = null,
        applyAccent: ((Int) -> Unit)? = null,
    ): Job = scope.launch {
        var file = cachedCoverArtFile(subsonic, config, coverArtId, size)
        if (file == null) {
            file = withContext(Dispatchers.IO) {
                subsonic.prefetchCoverArt(config, coverArtId, size)
            } ?: return@launch
        }
        if (!isCurrent()) return@launch
        val decoded = withContext(Dispatchers.Default) {
            val cover = decode(file, maxSidePx, lowMemory) ?: return@withContext null
            val background = if (applyBackground != null) {
                CoverArtBitmap.blurFromDecoded(cover)
            } else {
                null
            }
            val accent = if (applyAccent != null) CoverAccent.fromBitmap(cover) else 0
            Triple(cover, background, accent)
        } ?: return@launch
        if (!isCurrent()) return@launch
        apply(decoded.first)
        decoded.second?.let { applyBackground?.invoke(it) }
        if (applyAccent != null) applyAccent.invoke(decoded.third)
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
