package com.example.campusmate.ui.buddy

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.campusmate.R
import com.example.campusmate.data.model.StudyBuddy
import com.example.campusmate.ui.profile.ProfileUiFormatter

/** RecyclerView adapter for saved study buddies. */
class BuddyAdapter(
    private val onBuddyClick: (StudyBuddy) -> Unit
) : RecyclerView.Adapter<BuddyAdapter.BuddyViewHolder>() {
    private val buddies = mutableListOf<StudyBuddy>()

    fun submitList(items: List<StudyBuddy>) {
        buddies.clear()
        buddies.addAll(items)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BuddyViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_buddy, parent, false)
        return BuddyViewHolder(view)
    }

    override fun onBindViewHolder(holder: BuddyViewHolder, position: Int) {
        holder.bind(buddies[position])
    }

    override fun getItemCount(): Int = buddies.size

    inner class BuddyViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val nameText: TextView = itemView.findViewById(R.id.buddyNameText)
        private val schoolText: TextView = itemView.findViewById(R.id.buddySchoolText)
        private val metaText: TextView = itemView.findViewById(R.id.buddyMetaText)

        fun bind(buddy: StudyBuddy) {
            nameText.text = buddy.nickname
            schoolText.text = ProfileUiFormatter.schoolLine(buddy.school, buddy.major, buddy.grade)
            metaText.text = itemView.context.getString(
                R.string.buddy_added_from,
                ProfileUiFormatter.sourceLabel(itemView.context, buddy.source)
            )
            itemView.setOnClickListener { onBuddyClick(buddy) }
        }
    }
}
