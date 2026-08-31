package com.example.campusmate.ui.ai.memory

import android.content.Context
import com.example.campusmate.R
import com.example.campusmate.data.model.AiMemory

internal object AiMemoryUiFormatter {
    val categories = listOf(
        AiMemory.CATEGORY_GOAL,
        AiMemory.CATEGORY_PREFERENCE,
        AiMemory.CATEGORY_HABIT,
        AiMemory.CATEGORY_CONSTRAINT,
        AiMemory.CATEGORY_OTHER
    )

    fun category(context: Context, value: Int): String = context.getString(
        when (value) {
            AiMemory.CATEGORY_GOAL -> R.string.ai_memory_category_goal
            AiMemory.CATEGORY_PREFERENCE -> R.string.ai_memory_category_preference
            AiMemory.CATEGORY_HABIT -> R.string.ai_memory_category_habit
            AiMemory.CATEGORY_CONSTRAINT -> R.string.ai_memory_category_constraint
            else -> R.string.ai_memory_category_other
        }
    )
}
