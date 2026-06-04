package com.example.campusmate.ui.course

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import androidx.core.graphics.ColorUtils
import com.example.campusmate.R

object CourseGridCellBackgroundFactory {
    fun create(context: Context, color: String?): GradientDrawable {
        val baseColor = runCatching {
            Color.parseColor(color ?: DEFAULT_COLOR)
        }.getOrElse {
            Color.parseColor(DEFAULT_COLOR)
        }
        return GradientDrawable().apply {
            cornerRadius = context.resources.getDimension(R.dimen.space_s)
            setColor(ColorUtils.setAlphaComponent(baseColor, 220))
            setStroke(2, ColorUtils.setAlphaComponent(baseColor, 255))
        }
    }

    private const val DEFAULT_COLOR = "#1B6B5F"
}
