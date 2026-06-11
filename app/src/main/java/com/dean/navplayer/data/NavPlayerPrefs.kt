package com.dean.navplayer.data

import android.content.Context

class NavPlayerPrefs(context: Context) {
    private val prefs = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    var autoStartOnBoot: Boolean
        get() = prefs.getBoolean(KEY_AUTO_START, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_START, value).apply()

    var drivingMode: Boolean
        get() = prefs.getBoolean(KEY_DRIVING_MODE, false)
        set(value) = prefs.edit().putBoolean(KEY_DRIVING_MODE, value).apply()

    companion object {
        private const val FILE_NAME = "navplayer_prefs"
        private const val KEY_AUTO_START = "auto_start_on_boot"
        private const val KEY_DRIVING_MODE = "driving_mode"
    }
}
