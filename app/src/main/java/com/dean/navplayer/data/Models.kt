package com.dean.navplayer.data

data class ServerConfig(
    val baseUrl: String,
    val username: String,
    val password: String,
)

data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val albumId: String?,
)

data class PlaylistSummary(
    val id: String,
    val name: String,
    val coverArtId: String,
)
