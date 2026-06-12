package com.dean.navplayer.data

import android.content.Context
import java.io.File
import java.math.BigInteger
import java.security.MessageDigest
import java.util.Locale

/** On-disk cache for cover art JPEG/PNG bytes (per server, keyed by id + requested size). */
class CoverArtDiskCache(
    private val rootDir: File,
    private val maxBytesPerServer: Long = MAX_BYTES_PER_SERVER,
) {
    constructor(context: Context) : this(File(context.cacheDir, "cover_art"))

    init {
        rootDir.mkdirs()
    }

    fun cachedFile(serverKey: String, coverArtId: String, size: Int): File? {
        val file = fileFor(serverKey, coverArtId, size)
        return file.takeIf { it.isFile }
    }

    fun write(serverKey: String, coverArtId: String, size: Int, bytes: ByteArray): File {
        serverDir(serverKey).mkdirs()
        evictIfNeeded(serverKey, bytes.size.toLong())
        val file = fileFor(serverKey, coverArtId, size)
        file.writeBytes(bytes)
        return file
    }

    fun clearAll() {
        rootDir.deleteRecursively()
        rootDir.mkdirs()
    }

    private fun serverDir(serverKey: String): File = File(rootDir, serverKey)

    private fun fileFor(serverKey: String, coverArtId: String, size: Int): File {
        val name = "${digest("$coverArtId:$size")}.img"
        return File(serverDir(serverKey), name)
    }

    private fun evictIfNeeded(serverKey: String, incomingBytes: Long) {
        val dir = serverDir(serverKey)
        val files = dir.listFiles()?.filter { it.isFile }?.sortedBy { it.lastModified() }.orEmpty()
        var total = files.sumOf { it.length() }
        val budget = maxBytesPerServer - incomingBytes
        if (budget <= 0) return
        for (file in files) {
            if (total <= budget) break
            total -= file.length()
            file.delete()
        }
    }

    private fun digest(value: String): String {
        val hash = MessageDigest.getInstance("MD5").digest(value.toByteArray())
        return BigInteger(1, hash).toString(16).padStart(32, '0').lowercase(Locale.US)
    }

    companion object {
        private const val MAX_BYTES_PER_SERVER = 150L * 1024 * 1024
    }
}
