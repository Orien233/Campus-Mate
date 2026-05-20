package com.example.campusmate.ui.statistics

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.campusmate.R
import com.example.campusmate.domain.statistics.HeatmapDay

/** Grid adapter where each cell represents one day of study duration. */
class HeatmapAdapter(
    private val onDayClick: (HeatmapDay) -> Unit
) : RecyclerView.Adapter<HeatmapAdapter.HeatmapViewHolder>() {
    private val days = mutableListOf<HeatmapDay>()

    fun submitList(items: List<HeatmapDay>) {
        days.clear()
        days.addAll(items)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HeatmapViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_heatmap_day, parent, false)
        return HeatmapViewHolder(view)
    }

    override fun onBindViewHolder(holder: HeatmapViewHolder, position: Int) {
        holder.bind(days[position])
    }

    override fun getItemCount(): Int = days.size

    inner class HeatmapViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val dayText: TextView = itemView.findViewById(R.id.heatmapDayText)

        fun bind(day: HeatmapDay) {
            dayText.text = day.date.takeLast(2)
            dayText.setTextColor(if (day.intensity >= 3) Color.WHITE else Color.parseColor("#1E2528"))
            itemView.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = itemView.resources.getDimension(R.dimen.space_xs)
                setColor(colorForIntensity(day.intensity))
                if (day.isToday) {
                    setStroke(2, Color.parseColor("#1B6B5F"))
                }
            }
            itemView.contentDescription = itemView.context.getString(
                R.string.statistics_heatmap_day_desc,
                day.date,
                day.durationSec / 60
            )
            itemView.setOnClickListener { onDayClick(day) }
        }
    }

    private fun colorForIntensity(intensity: Int): Int {
        return Color.parseColor(
            when (intensity) {
                1 -> "#CFE7DC"
                2 -> "#86C7A8"
                3 -> "#3C9A73"
                4 -> "#1B6B5F"
                else -> "#E8EDF0"
            }
        )
    }
}
