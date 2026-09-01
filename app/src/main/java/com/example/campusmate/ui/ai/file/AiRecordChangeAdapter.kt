package com.example.campusmate.ui.ai.file

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.campusmate.R
import com.example.campusmate.domain.ai.command.AiCourseSnapshot
import com.example.campusmate.domain.ai.command.AiPlanSnapshot
import com.example.campusmate.domain.ai.command.AiRecordChange
import com.example.campusmate.domain.ai.command.AiRecordOperation
import com.example.campusmate.domain.ai.command.AiRecordSnapshot
import com.example.campusmate.domain.ai.command.AiRecordType
import com.example.campusmate.domain.ai.command.AiTaskSnapshot
import com.example.campusmate.util.DateTimeUtils
import com.google.android.material.card.MaterialCardView

data class AiRecordChangeItem(val change: AiRecordChange, var selected: Boolean)

class AiRecordChangeAdapter(
    private val onSelectionChanged: () -> Unit
) : RecyclerView.Adapter<AiRecordChangeAdapter.Holder>() {
    private val items = mutableListOf<AiRecordChangeItem>()

    fun submit(changes: List<AiRecordChange>) {
        items.clear()
        items += changes.map { AiRecordChangeItem(it, it.defaultSelected) }
        notifyDataSetChanged()
    }

    fun selectedChanges(): List<AiRecordChange> = items.filter { it.selected }.map { it.change }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        return Holder(
            LayoutInflater.from(parent.context)
                .inflate(R.layout.item_ai_record_change, parent, false)
        )
    }

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(items[position])
    override fun getItemCount(): Int = items.size

    inner class Holder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val card = itemView as MaterialCardView
        private val check: CheckBox = itemView.findViewById(R.id.recordChangeCheck)
        private val title: TextView = itemView.findViewById(R.id.recordChangeTitle)
        private val meta: TextView = itemView.findViewById(R.id.recordChangeMeta)
        private val detail: TextView = itemView.findViewById(R.id.recordChangeDetail)
        private val evidence: TextView = itemView.findViewById(R.id.recordChangeEvidence)
        private val warning: TextView = itemView.findViewById(R.id.recordChangeWarning)

        fun bind(item: AiRecordChangeItem) {
            val change = item.change
            check.setOnCheckedChangeListener(null)
            check.isChecked = item.selected
            title.text = change.displayName.ifBlank { itemView.context.getString(R.string.ai_record_unnamed) }
            meta.text = itemView.context.getString(
                R.string.ai_record_change_meta,
                operationLabel(change.operation),
                typeLabel(change.recordType)
            )
            detail.text = describe(change)
            evidence.text = itemView.context.getString(R.string.ai_record_evidence, change.evidenceQuote)
            warning.visibility = if (change.warnings.isEmpty()) View.GONE else View.VISIBLE
            warning.text = change.warnings.joinToString("\n")
            applyState(item.selected)
            check.setOnCheckedChangeListener { _, selected ->
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    items[position].selected = selected
                    applyState(selected)
                    onSelectionChanged()
                }
            }
            itemView.setOnClickListener { check.isChecked = !check.isChecked }
        }

        private fun describe(change: AiRecordChange): String {
            if (change.operation == AiRecordOperation.DELETE) {
                return itemView.context.getString(R.string.ai_record_delete_detail)
            }
            val after = change.after ?: return ""
            val beforeText = change.before?.let(::snapshotText)
            val afterText = snapshotText(after)
            return if (beforeText == null) {
                afterText
            } else {
                itemView.context.getString(R.string.ai_record_before_after, beforeText, afterText)
            }
        }

        private fun snapshotText(value: AiRecordSnapshot): String = when (value) {
            is AiCourseSnapshot -> itemView.context.getString(
                R.string.ai_record_course_detail,
                value.weekday, value.startSection, value.endSection,
                value.startWeek, value.endWeek,
                listOfNotNull(value.teacher, value.classroom).joinToString(" · ")
            )
            is AiTaskSnapshot -> listOfNotNull(
                value.description,
                value.dueAt?.let(DateTimeUtils::formatDateTime),
                itemView.context.getString(R.string.ai_record_task_status_value, value.status)
            ).joinToString(" · ")
            is AiPlanSnapshot -> listOfNotNull(
                value.planDate,
                if (value.startTime != null && value.endTime != null) "${value.startTime}-${value.endTime}" else null,
                itemView.context.getString(R.string.ai_record_minutes_value, value.plannedMinutes),
                itemView.context.getString(R.string.ai_record_plan_status_value, value.status)
            ).joinToString(" · ")
        }

        private fun operationLabel(value: AiRecordOperation) = itemView.context.getString(
            when (value) {
                AiRecordOperation.CREATE -> R.string.ai_record_operation_create
                AiRecordOperation.UPDATE -> R.string.ai_record_operation_update
                AiRecordOperation.DELETE -> R.string.ai_record_operation_delete
            }
        )

        private fun typeLabel(value: AiRecordType) = itemView.context.getString(
            when (value) {
                AiRecordType.COURSE -> R.string.ai_record_type_course
                AiRecordType.TASK -> R.string.ai_record_type_task
                AiRecordType.PLAN -> R.string.ai_record_type_plan
            }
        )

        private fun applyState(selected: Boolean) {
            card.setCardBackgroundColor(
                itemView.context.getColor(if (selected) R.color.campus_primary_light else R.color.campus_surface)
            )
            listOf(title, meta, detail, evidence, warning).forEach { it.alpha = if (selected) 1f else 0.72f }
        }
    }
}
