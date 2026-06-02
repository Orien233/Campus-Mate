package com.example.campusmate.ui.plan

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.campusmate.R
import com.example.campusmate.data.model.StudyPlan

class LlmPlanPreviewAdapter(
    private val onItemClick: ((StudyPlan) -> Unit)? = null
) : ListAdapter<StudyPlan, LlmPlanPreviewAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_llm_plan_preview, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val titleText: TextView = itemView.findViewById(R.id.planPreviewTitle)
        private val timeText: TextView = itemView.findViewById(R.id.planPreviewTime)
        private val durationText: TextView = itemView.findViewById(R.id.planPreviewDuration)
        private val typeText: TextView = itemView.findViewById(R.id.planPreviewType)

        fun bind(plan: StudyPlan) {
            titleText.text = plan.title

            val timeStr = if (plan.startTime != null && plan.endTime != null) {
                "${plan.startTime} - ${plan.endTime}"
            } else {
                itemView.context.getString(R.string.plan_no_specific_time)
            }
            timeText.text = timeStr

            durationText.text = itemView.context.getString(R.string.plan_item_duration_format, plan.plannedMinutes)

            val sourceName = when (plan.sourceType) {
                StudyPlan.SOURCE_LLM -> itemView.context.getString(R.string.plan_source_llm)
                else -> itemView.context.getString(R.string.plan_source_auto)
            }
            typeText.text = sourceName

            itemView.setOnClickListener { onItemClick?.invoke(plan) }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<StudyPlan>() {
        override fun areItemsTheSame(oldItem: StudyPlan, newItem: StudyPlan): Boolean {
            return oldItem.title == newItem.title && oldItem.planDate == newItem.planDate
        }

        override fun areContentsTheSame(oldItem: StudyPlan, newItem: StudyPlan): Boolean {
            return oldItem == newItem
        }
    }
}
