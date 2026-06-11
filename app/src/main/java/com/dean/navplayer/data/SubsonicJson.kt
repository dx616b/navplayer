package com.dean.navplayer.data

import com.google.gson.annotations.SerializedName

data class SubsonicResponseWrapper(
    @SerializedName("subsonic-response") val response: SubsonicResponse,
)

data class SubsonicResponse(
    val status: String,
    val version: String? = null,
    val error: SubsonicError? = null,
    val playlists: PlaylistsWrapper? = null,
    val playlist: PlaylistDetailWrapper? = null,
    val randomSongs: RandomSongsWrapper? = null,
)

data class SubsonicError(
    val code: Int,
    val message: String,
)

data class PlaylistsWrapper(val playlist: List<PlaylistJson>?)

data class PlaylistJson(
    val id: String,
    val name: String,
    val coverArt: String? = null,
)

data class PlaylistDetailWrapper(val entry: List<PlaylistEntryJson>?)

data class PlaylistEntryJson(
    val id: String,
    val title: String,
    val artist: String?,
    @SerializedName("albumId") val albumId: String?,
    @SerializedName("isDir") val isDir: Boolean = false,
)

data class SongJson(
    val id: String,
    val title: String,
    val artist: String?,
    @SerializedName("albumId") val albumId: String?,
)

data class RandomSongsWrapper(val song: List<SongJson>?)
