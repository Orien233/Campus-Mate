package com.example.campusmate.ui.statistics

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.campusmate.R
import com.example.campusmate.data.model.StudyRecord
import com.example.campusmate.util.DateTimeUtils

/** Adapter for study records shown in a selected-day detail dialog. */
class StudyRecordAdapter : RecyclerView.Adapter<StudyRecordAdapter.StudyRecordViewHolder>() {
    private val records = mutableListOf<StudyRecord>()

    fun submitList(items: List<StudyRecord>) {
        records.clear()
        records.addAll(items)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StudyRecordViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_study_record, parent, false)
        return StudyRecordViewHolder(view)
    }

    override fun onBindViewHolder(holder: StudyRecordViewHolder, position: Int) {
        holder.bind(records[position])
    }

    override fun getItemCount(): Int = records.size

    class StudyRecordViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val titleText: TextView = itemView.findViewById(R.id.studyRecordTitleText)
        private val durationText: TextView = itemView.findViewById(R.id.studyRecordDurationText)
        private val timeText: TextView = itemView.findViewById(R.id.studyRecordTimeText)
        private val noteText: TextView = itemView.findViewById(R.id.studyRecordNoteText)

        fun bind(record: StudyRecord) {
            val context = itemView.context
            titleText.text = record.title?.takeIf { it.isNotBlank() } ?: context.getString(R.string.statistics_record_default_title)
            durationText.text = context.getString(R.string.duration_minutes, record.durationSec / 60)
            timeText.text = record.startAt?.let { DateTimeUtils.formatDateTime(it) } ?: record.recordDate
            noteText.text = record.note?.takeIf { it.isNotBlank() } ?: context.getString(R.string.statistics_record_no_note)
        }
    }
}
