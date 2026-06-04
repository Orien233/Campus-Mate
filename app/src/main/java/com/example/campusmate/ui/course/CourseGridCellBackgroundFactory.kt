package com.example.campusmate.ui.course

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import androidx.core.graphics.ColorUtils

object CourseGridCellBackgroundFactory {
    fun create(context: Context, color: String?): GradientDrawable {
        val base = try {
            Color.parseColor(color ?: DEFAULT_COLOR)
        } catch (_: IllegalArgumentException) {
            Color.parseColor(DEFAULT_COLOR)
        }
        return GradientDrawable().apply {
            cornerRadius = context.resources.getDimension(com.example.campusmate.R.dimen.space_s)
            setColor(ColorUtils.setAlphaComponent(base, 220))
            setStroke(2, ColorUtils.setAlphaComponent(base, 255))
        }
    }

    private const val DEFAULT_COLOR = "#1B6B5F"
}
