package com.dean.navplayer.data

import com.google.gson.GsonBuilder
import java.math.BigInteger
import java.security.MessageDigest
import java.util.Locale
import java.util.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.Url

interface SubsonicApi {
    @GET
    suspend fun ping(
        @Url url: String,
        @Query("u") username: String,
        @Query("t") token: String,
        @Query("s") salt: String,
        @Query("v") version: String = API_VERSION,
        @Query("c") client: String = CLIENT_ID,
        @Query("f") format: String = "json",
    ): SubsonicResponseWrapper

    @GET
    suspend fun getPlaylists(
        @Url url: String,
        @Query("u") username: String,
        @Query("t") token: String,
        @Query("s") salt: String,
        @Query("v") version: String = API_VERSION,
        @Query("c") client: String = CLIENT_ID,
        @Query("f") format: String = "json",
    ): SubsonicResponseWrapper

    @GET
    suspend fun getPlaylist(
        @Url url: String,
        @Query("id") playlistId: String,
        @Query("u") username: String,
        @Query("t") token: String,
        @Query("s") salt: String,
        @Query("v") version: String = API_VERSION,
        @Query("c") client: String = CLIENT_ID,
        @Query("f") format: String = "json",
    ): SubsonicResponseWrapper

    @GET
    suspend fun getRandomSongs(
        @Url url: String,
        @Query("size") size: Int,
        @Query("u") username: String,
        @Query("t") token: String,
        @Query("s") salt: String,
        @Query("v") version: String = API_VERSION,
        @Query("c") client: String = CLIENT_ID,
        @Query("f") format: String = "json",
    ): SubsonicResponseWrapper

    companion object {
        const val API_VERSION = "1.16.1"
        const val CLIENT_ID = "NavPlayer"
    }
}

class SubsonicClient(
    private val credentialsStore: CredentialsStore,
    private val coverArtCache: CoverArtDiskCache,
) {
    private val http = OkHttpClient.Builder().build()
    private val coverDownloadSemaphore = Semaphore(COVER_DOWNLOAD_CONCURRENCY)
    @Volatile
    private var playlistsCacheKey: String? = null
    @Volatile
    private var playlistsCache: List<PlaylistSummary>? = null
    @Volatile
    private var playlistsCacheAtMs: Long = 0L
    private val gson = GsonBuilder().withSubsonicAdapters().create()
    private val api: SubsonicApi = Retrofit.Builder()
        .baseUrl("https://placeholder.invalid/")
        .client(http)
        .addConverterFactory(GsonConverterFactory.create(gson))
        .build()
        .create(SubsonicApi::class.java)

    fun normalizeBaseUrl(raw: String): String {
        var url = raw.trim().removeSuffix("/")
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "https://$url"
        }
        if (!url.endsWith("/rest")) {
            url = "$url/rest"
        }
        return url
    }

    suspend fun testConnection(config: ServerConfig): Result<Unit> = runCatching {
        ping(config)
    }

    suspend fun ping(config: ServerConfig) {
        val auth = auth(config.password)
        val body = api.ping(endpoint(config.baseUrl, "ping.view"), config.username, auth.token, auth.salt)
        ensureOk(body.response)
    }

    suspend fun getPlaylists(
        config: ServerConfig,
        forceRefresh: Boolean = false,
    ): List<PlaylistSummary> {
        val cacheKey = playlistCacheKey(config)
        if (!forceRefresh) {
            val cached = playlistsCache
            if (cached != null &&
                playlistsCacheKey == cacheKey &&
                System.currentTimeMillis() - playlistsCacheAtMs < PLAYLISTS_CACHE_TTL_MS
            ) {
                return cached
            }
        }

        val auth = auth(config.password)
        val body = api.getPlaylists(
            endpoint(config.baseUrl, "getPlaylists.view"),
            config.username,
            auth.token,
            auth.salt,
        )
        ensureOk(body.response)
        val playlists = body.response.playlists?.playlist.orEmpty().map {
            PlaylistSummary(it.id, it.name, it.coverArt ?: it.id)
        }
        playlistsCacheKey = cacheKey
        playlistsCache = playlists
        playlistsCacheAtMs = System.currentTimeMillis()
        return playlists
    }

    fun invalidatePlaylistsCache() {
        playlistsCacheKey = null
        playlistsCache = null
        playlistsCacheAtMs = 0L
    }

    suspend fun getPlaylistTracks(config: ServerConfig, playlistId: String): List<Track> =
        withContext(Dispatchers.IO) {
            val auth = auth(config.password)
            val body = api.getPlaylist(
                endpoint(config.baseUrl, "getPlaylist.view"),
                playlistId,
                config.username,
                auth.token,
                auth.salt,
            )
            ensureOk(body.response)
            body.response.playlist?.entry.orEmpty()
                .filter { !it.isDir }
                .map { Track(it.id, it.title, it.artist.orEmpty(), it.albumId) }
        }

    suspend fun getRandomSongs(config: ServerConfig, size: Int = RANDOM_BATCH_SIZE): List<Track> =
        withContext(Dispatchers.IO) {
            val auth = auth(config.password)
            val body = api.getRandomSongs(
                endpoint(config.baseUrl, "getRandomSongs.view"),
                size,
                config.username,
                auth.token,
                auth.salt,
            )
            ensureOk(body.response)
            body.response.randomSongs?.song.orEmpty().map { song ->
                Track(song.id, song.title, song.artist.orEmpty(), song.albumId)
            }
        }

    fun streamUrl(config: ServerConfig, songId: String): String =
        streamUrl(config, songId, auth(config.password))

    fun streamUrls(config: ServerConfig, songIds: List<String>): List<String> {
        if (songIds.isEmpty()) return emptyList()
        val auth = auth(config.password)
        return songIds.map { streamUrl(config, it, auth) }
    }

    fun coverArtUrl(config: ServerConfig, coverArtId: String, size: Int = COVER_SIZE_THUMB): String {
        val auth = auth(config.password)
        return coverArtUrl(config, coverArtId, size, auth)
    }

    private fun streamUrl(config: ServerConfig, songId: String, auth: AuthParams): String =
        endpoint(config.baseUrl, "stream.view").toHttpUrl().newBuilder()
            .addQueryParameter("id", songId)
            .addQueryParameter("u", config.username)
            .addQueryParameter("t", auth.token)
            .addQueryParameter("s", auth.salt)
            .addQueryParameter("v", SubsonicApi.API_VERSION)
            .addQueryParameter("c", SubsonicApi.CLIENT_ID)
            .build()
            .toString()

    private fun coverArtUrl(
        config: ServerConfig,
        coverArtId: String,
        size: Int,
        auth: AuthParams,
    ): String = endpoint(config.baseUrl, "getCoverArt.view").toHttpUrl().newBuilder()
        .addQueryParameter("id", coverArtId)
        .addQueryParameter("size", size.toString())
        .addQueryParameter("u", config.username)
        .addQueryParameter("t", auth.token)
        .addQueryParameter("s", auth.salt)
        .addQueryParameter("v", SubsonicApi.API_VERSION)
        .addQueryParameter("c", SubsonicApi.CLIENT_ID)
        .build()
        .toString()

    /** Disk hit only — safe to call on the main thread. */
    fun cachedCoverArtFile(
        config: ServerConfig,
        coverArtId: String,
        size: Int = COVER_SIZE_THUMB,
    ): java.io.File? = coverArtCache.cachedFile(serverCacheKey(config.baseUrl), coverArtId, size)

    /** Downloads on miss and writes to disk. Does not block playback. */
    suspend fun prefetchCoverArt(
        config: ServerConfig,
        coverArtId: String,
        size: Int = COVER_SIZE_THUMB,
    ): java.io.File? = withContext(Dispatchers.IO) {
        val serverKey = serverCacheKey(config.baseUrl)
        coverArtCache.cachedFile(serverKey, coverArtId, size)?.let { return@withContext it }

        coverDownloadSemaphore.withPermit {
            coverArtCache.cachedFile(serverKey, coverArtId, size)?.let { return@withPermit it }

            val request = Request.Builder().url(coverArtUrl(config, coverArtId, size)).build()
            val bytes = http.newCall(request).execute().use { response ->
                if (response.isSuccessful) response.body?.bytes() else null
            } ?: return@withPermit null
            coverArtCache.write(serverKey, coverArtId, size, bytes)
        }
    }

    private fun serverCacheKey(baseUrl: String): String = md5Hex(baseUrl)

    fun currentConfig(): ServerConfig? = credentialsStore.load()

    private fun endpoint(baseUrl: String, method: String): String = "$baseUrl/$method"

    private fun ensureOk(response: SubsonicResponse) {
        if (response.status != "ok") {
            val message = response.error?.message ?: "Unknown Subsonic error"
            throw IllegalStateException(message)
        }
    }

    private fun auth(password: String): AuthParams {
        val salt = randomSalt()
        val token = md5Hex(password + salt)
        return AuthParams(salt, token)
    }

    private fun randomSalt(): String {
        val bytes = ByteArray(6)
        Random().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun md5Hex(value: String): String {
        val digest = MessageDigest.getInstance("MD5").digest(value.toByteArray())
        return BigInteger(1, digest).toString(16).padStart(32, '0').lowercase(Locale.US)
    }

    private data class AuthParams(val salt: String, val token: String)

    private fun playlistCacheKey(config: ServerConfig): String =
        "${config.baseUrl}|${config.username}"

    companion object {
        const val RANDOM_BATCH_SIZE = 100
        const val RANDOM_FOLLOWUP_BATCH_SIZE = 80
        const val COVER_SIZE_THUMB = 256
        const val COVER_SIZE_PLAYER = 320
        const val COVER_SIZE_LARGE = 800
        private const val PLAYLISTS_CACHE_TTL_MS = 5 * 60 * 1000L
        private const val COVER_DOWNLOAD_CONCURRENCY = 4
    }
}
