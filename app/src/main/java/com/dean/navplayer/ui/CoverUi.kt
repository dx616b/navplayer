package com.dean.navplayer.ui

import android.content.res.ColorStateList
import android.graphics.Outline
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import com.dean.navplayer.R

object CoverUi {
    fun roundCover(view: ImageView, cornerRadiusPx: Float, elevationPx: Float = 0f) {
        view.clipToOutline = true
        view.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(v: View, outline: Outline) {
                outline.setRoundRect(0, 0, v.width, v.height, cornerRadiusPx)
            }
        }
        view.elevation = elevationPx
    }

    fun resolveAccent(context: android.content.Context, extracted: Int): Int =
        if (extracted != 0) extracted else ContextCompat.getColor(context, R.color.primary)

    fun applySeekAccent(context: android.content.Context, seekBar: SeekBar, accent: Int) {
        seekBar.progressTintList = ColorStateList.valueOf(accent)
        seekBar.thumbTintList = ColorStateList.valueOf(controlColor(context))
    }

    fun applyPlayPauseWhite(context: android.content.Context, playPauseButton: ImageView) {
        playPauseButton.imageTintList = ColorStateList.valueOf(controlColor(context))
    }

    fun applyPlayerAccent(
        context: android.content.Context,
        accent: Int,
        playerBar: View,
        seekBar: SeekBar,
        playPauseButton: ImageView,
        playingChip: TextView,
    ) {
        val surface = ContextCompat.getColor(context, R.color.surface)
        playerBar.setBackgroundColor(ColorUtils.blendARGB(surface, accent, 0.14f))
        applySeekAccent(context, seekBar, accent)
        applyPlayPauseWhite(context, playPauseButton)
        playingChip.setTextColor(accent)
        playingChip.background = chipBackground(context, accent)
    }

    fun resetPlayerAccent(
        context: android.content.Context,
        playerBar: View,
        seekBar: SeekBar,
        playPauseButton: ImageView,
        playingChip: TextView,
    ) {
        val primary = ContextCompat.getColor(context, R.color.primary)
        playerBar.setBackgroundColor(ContextCompat.getColor(context, R.color.surface))
        applySeekAccent(context, seekBar, primary)
        applyPlayPauseWhite(context, playPauseButton)
        playingChip.setTextColor(primary)
        playingChip.setBackgroundResource(R.drawable.bg_playing_chip)
    }

    fun resetTransportAccent(context: android.content.Context, seekBar: SeekBar, playPauseButton: ImageView) {
        val primary = ContextCompat.getColor(context, R.color.primary)
        applySeekAccent(context, seekBar, primary)
        applyPlayPauseWhite(context, playPauseButton)
    }

    private fun controlColor(context: android.content.Context): Int =
        ContextCompat.getColor(context, R.color.on_surface)

    fun playingRowBackground(context: android.content.Context, accent: Int): Drawable {
        val radius = context.resources.getDimension(R.dimen.list_item_corner_radius)
        val fill = GradientDrawable().apply {
            cornerRadius = radius
            setColor(ColorUtils.setAlphaComponent(accent, 0x18))
            setStroke(context.resources.getDimensionPixelSize(R.dimen.stroke_hairline), accent)
        }
        return RippleDrawable(
            ColorStateList.valueOf(ColorUtils.setAlphaComponent(accent, 0x33)),
            fill,
            null,
        )
    }

    fun defaultRowBackground(context: android.content.Context): Drawable =
        requireNotNull(ContextCompat.getDrawable(context, R.drawable.bg_list_item))

    private fun chipBackground(context: android.content.Context, accent: Int): GradientDrawable =
        GradientDrawable().apply {
            cornerRadius = context.resources.getDimension(R.dimen.chip_corner_radius)
            setColor(ColorUtils.setAlphaComponent(accent, 0x33))
        }
}
