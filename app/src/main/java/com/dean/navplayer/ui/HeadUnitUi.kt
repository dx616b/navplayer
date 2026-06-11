package com.dean.navplayer.ui

import android.app.Activity
import android.view.ViewGroup
import android.view.WindowManager
import androidx.core.view.WindowCompat
import androidx.recyclerview.widget.GridLayoutManager
import com.dean.navplayer.R
import com.dean.navplayer.databinding.ActivityMainBinding
import com.dean.navplayer.databinding.ActivityNowPlayingBinding

object HeadUnitUi {
    fun apply(activity: Activity) {
        WindowCompat.setDecorFitsSystemWindows(activity.window, true)
    }

    fun setKeepScreenOn(activity: Activity, keepOn: Boolean) {
        if (keepOn) {
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    fun applyDrivingMode(activity: Activity, binding: ActivityMainBinding, enabled: Boolean) {
        val transport = activity.dimen(if (enabled) R.dimen.driving_touch_transport else R.dimen.touch_target_transport)
        val play = activity.dimen(if (enabled) R.dimen.driving_touch_play else R.dimen.touch_target_play)
        val barMin = activity.dimen(if (enabled) R.dimen.driving_player_bar_min_height else R.dimen.player_bar_min_height)

        binding.preButton.setSizePx(transport, transport)
        binding.playPauseButton.setSizePx(play, play)
        binding.nextButton.setSizePx(transport, transport)
        binding.playerBar.minimumHeight = barMin

        (binding.playlistList.layoutManager as? GridLayoutManager)?.spanCount = if (enabled) 1 else 2
    }

    fun applyDrivingMode(activity: Activity, binding: ActivityNowPlayingBinding, enabled: Boolean) {
        val transport = activity.dimen(if (enabled) R.dimen.driving_touch_transport else R.dimen.touch_target_transport)
        val play = activity.dimen(if (enabled) R.dimen.driving_touch_play else R.dimen.touch_target_play)
        binding.preButton.setSizePx(transport, transport)
        binding.playPauseButton.setSizePx(play, play)
        binding.nextButton.setSizePx(transport, transport)
    }

    fun playlistRowMinHeight(activity: Activity, drivingMode: Boolean): Int =
        activity.dimen(if (drivingMode) R.dimen.driving_playlist_row_min_height else R.dimen.playlist_row_min_height)

    private fun Activity.dimen(id: Int): Int = resources.getDimensionPixelSize(id)

    private fun android.view.View.setSizePx(width: Int, height: Int) {
        val lp = layoutParams ?: ViewGroup.LayoutParams(width, height)
        lp.width = width
        lp.height = height
        layoutParams = lp
    }
}
