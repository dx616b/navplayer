package com.dean.navplayer

import android.app.Application
import com.dean.navplayer.data.CredentialsStore
import com.dean.navplayer.data.NavPlayerPrefs
import com.dean.navplayer.data.SubsonicClient
import com.dean.navplayer.playback.PlaybackQueueStore

class NavPlayerApp : Application() {
    lateinit var credentials: CredentialsStore
        private set

    lateinit var subsonic: SubsonicClient
        private set

    lateinit var prefs: NavPlayerPrefs
        private set

    lateinit var playbackQueue: PlaybackQueueStore
        private set

    override fun onCreate() {
        super.onCreate()
        credentials = CredentialsStore(this)
        subsonic = SubsonicClient(credentials)
        prefs = NavPlayerPrefs(this)
        playbackQueue = PlaybackQueueStore()
    }
}
