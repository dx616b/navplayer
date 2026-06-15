package com.dean.navplayer.ui

import android.app.Activity
import android.util.TypedValue
import android.view.ViewGroup
import android.view.WindowManager
import androidx.recyclerview.widget.GridLayoutManager
import com.dean.navplayer.R
import com.dean.navplayer.databinding.ActivityMainBinding
import com.dean.navplayer.databinding.ActivityNowPlayingBinding

object HeadUnitUi {
    fun apply(activity: Activity) {
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(activity.window, true)
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
        val cover = activity.dimen(if (enabled) R.dimen.driving_cover_art_player_bar else R.dimen.cover_art_player_bar)
        val randomMin = activity.dimen(if (enabled) R.dimen.driving_random_min_height else R.dimen.touch_target_min)
        val seekMin = activity.dimen(if (enabled) R.dimen.driving_seekbar_min_height else R.dimen.seekbar_min_height)
        val seekPad = activity.dimen(if (enabled) R.dimen.driving_seekbar_padding_vertical else R.dimen.seekbar_padding_vertical)

        binding.preButton.setSizePx(transport, transport)
        binding.playPauseButton.setSizePx(play, play)
        binding.nextButton.setSizePx(transport, transport)
        binding.coverArt.setSizePx(cover, cover)
        binding.playerBarTopRow.minimumHeight = barMin
        binding.randomButton.minHeight = randomMin
        binding.seekBar.applySeekSize(seekMin, seekPad)

        binding.trackTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, playerTitleTextSp(enabled))
        binding.trackArtist.setTextSize(TypedValue.COMPLEX_UNIT_SP, playerArtistTextSp(enabled))
        binding.randomButton.setTextSize(TypedValue.COMPLEX_UNIT_SP, if (enabled) 17f else 15f)
        binding.drivingModeBadge.visibility = if (enabled) android.view.View.VISIBLE else android.view.View.GONE

        (binding.playlistList.layoutManager as? GridLayoutManager)?.spanCount = if (enabled) 1 else 2
    }

    fun applyDrivingMode(activity: Activity, binding: ActivityNowPlayingBinding, enabled: Boolean) {
        val transport = activity.dimen(if (enabled) R.dimen.driving_touch_transport else R.dimen.touch_target_transport)
        val play = activity.dimen(if (enabled) R.dimen.driving_touch_play else R.dimen.touch_target_play)
        val cover = activity.dimen(if (enabled) R.dimen.driving_cover_art_now_playing else R.dimen.cover_art_now_playing)
        val close = activity.dimen(if (enabled) R.dimen.driving_close_button else R.dimen.touch_target_min)
        val seekMin = activity.dimen(if (enabled) R.dimen.driving_seekbar_min_height else R.dimen.seekbar_min_height)
        val seekPad = activity.dimen(if (enabled) R.dimen.driving_seekbar_padding_vertical else R.dimen.seekbar_padding_vertical)

        binding.preButton.setSizePx(transport, transport)
        binding.playPauseButton.setSizePx(play, play)
        binding.nextButton.setSizePx(transport, transport)
        binding.coverArt.setSizePx(cover, cover)
        binding.closeButton.setSizePx(close, close)
        binding.seekBar.applySeekSize(seekMin, seekPad)

        binding.trackTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, if (enabled) 22f else 20f)
        binding.trackArtist.setTextSize(TypedValue.COMPLEX_UNIT_SP, if (enabled) 17f else 15f)
    }

    fun playlistRowMinHeight(activity: Activity, drivingMode: Boolean): Int =
        activity.dimen(if (drivingMode) R.dimen.driving_playlist_row_min_height else R.dimen.playlist_row_min_height)

    fun playlistCoverSizePx(activity: Activity, drivingMode: Boolean): Int =
        activity.dimen(if (drivingMode) R.dimen.driving_cover_art_playlist else R.dimen.cover_art_playlist)

    fun playlistNameTextSp(drivingMode: Boolean): Float = if (drivingMode) 18f else 15f

    fun playerTitleTextSp(drivingMode: Boolean): Float = if (drivingMode) 17f else 14f

    fun playerArtistTextSp(drivingMode: Boolean): Float = if (drivingMode) 14f else 12f

    fun queueRowMinHeight(activity: Activity, drivingMode: Boolean): Int =
        activity.dimen(if (drivingMode) R.dimen.driving_queue_row_min_height else R.dimen.queue_row_min_height)

    private fun Activity.dimen(id: Int): Int = resources.getDimensionPixelSize(id)

    private fun android.view.View.setSizePx(width: Int, height: Int) {
        val lp = layoutParams ?: ViewGroup.LayoutParams(width, height)
        lp.width = width
        lp.height = height
        layoutParams = lp
    }

    private fun android.widget.SeekBar.applySeekSize(minHeightPx: Int, verticalPaddingPx: Int) {
        minimumHeight = minHeightPx
        setPadding(paddingLeft, verticalPaddingPx, paddingRight, verticalPaddingPx)
    }
}
