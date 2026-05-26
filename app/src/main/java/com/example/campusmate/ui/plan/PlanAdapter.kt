package com.example.campusmate.ui.plan

import android.graphics.Paint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageButton
import android.widget.PopupMenu
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.campusmate.R
import com.example.campusmate.data.model.StudyPlan

data class PlanListItem(
    val plan: StudyPlan,
    val courseName: String? = null
)

class PlanAdapter(
    private val onPlanClick: (StudyPlan) -> Unit,
    private val onToggleComplete: (StudyPlan) -> Unit,
    private val onDeleteClick: (StudyPlan) -> Unit
) : ListAdapter<PlanListItem, PlanAdapter.PlanViewHolder>(PlanDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PlanViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_plan, parent, false)
        return PlanViewHolder(view)
    }

    override fun onBindViewHolder(holder: PlanViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class PlanViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val checkbox: CheckBox = itemView.findViewById(R.id.planItemCheckbox)
        private val titleText: TextView = itemView.findViewById(R.id.planItemTitle)
        private val timeText: TextView = itemView.findViewById(R.id.planItemTime)
        private val durationText: TextView = itemView.findViewById(R.id.planItemDuration)
        private val moreButton: ImageButton = itemView.findViewById(R.id.planItemMoreButton)

        fun bind(item: PlanListItem) {
            val plan = item.plan
            val context = itemView.context

            titleText.text = plan.title
            timeText.text = if (plan.startTime != null && plan.endTime != null) {
                context.getString(R.string.plan_item_time_format, plan.startTime, plan.endTime)
            } else {
                plan.planDate
            }
            durationText.text = context.getString(R.string.plan_item_duration_format, plan.plannedMinutes)

            val isCompleted = plan.status == StudyPlan.STATUS_COMPLETED
            checkbox.isChecked = isCompleted

            if (isCompleted) {
                titleText.paintFlags = titleText.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                titleText.alpha = 0.6f
            } else {
                titleText.paintFlags = titleText.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
                titleText.alpha = 1.0f
            }

            checkbox.setOnClickListener {
                onToggleComplete(plan)
            }

            itemView.setOnClickListener {
                onPlanClick(plan)
            }

            moreButton.setOnClickListener { view ->
                showPopupMenu(view, plan)
            }
        }

        private fun showPopupMenu(view: View, plan: StudyPlan) {
            PopupMenu(view.context, view).apply {
                menuInflater.inflate(R.menu.menu_plan_item, menu)
                setOnMenuItemClickListener { menuItem ->
                    when (menuItem.itemId) {
                        R.id.action_mark_complete -> {
                            onToggleComplete(plan)
                            true
                        }
                        R.id.action_delete -> {
                            onDeleteClick(plan)
                            true
                        }
                        else -> false
                    }
                }
                show()
            }
        }
    }

    class PlanDiffCallback : DiffUtil.ItemCallback<PlanListItem>() {
        override fun areItemsTheSame(oldItem: PlanListItem, newItem: PlanListItem): Boolean {
            return oldItem.plan.id == newItem.plan.id
        }

        override fun areContentsTheSame(oldItem: PlanListItem, newItem: PlanListItem): Boolean {
            return oldItem.plan == newItem.plan
        }
    }
}
