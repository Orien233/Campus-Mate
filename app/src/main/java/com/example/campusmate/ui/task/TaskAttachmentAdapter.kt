package com.example.campusmate.ui.task

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.campusmate.R
import com.example.campusmate.data.model.TaskAttachment
import com.google.android.material.button.MaterialButton

class TaskAttachmentAdapter(
    private val onOpen: (TaskAttachment) -> Unit,
    private val onDelete: (TaskAttachment) -> Unit
) : RecyclerView.Adapter<TaskAttachmentAdapter.Vh>() {
    private val items = mutableListOf<TaskAttachment>()

    fun submitList(newItems: List<TaskAttachment>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Vh {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_task_attachment, parent, false)
        return Vh(view)
    }

    override fun onBindViewHolder(holder: Vh, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class Vh(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val thumbnail: ImageView = itemView.findViewById(R.id.attachmentThumbnail)
        private val title: TextView = itemView.findViewById(R.id.attachmentTitle)
        private val openButton: MaterialButton = itemView.findViewById(R.id.attachmentOpenButton)
        private val deleteButton: MaterialButton = itemView.findViewById(R.id.attachmentDeleteButton)

        fun bind(item: TaskAttachment) {
            title.text = item.title ?: Uri.parse(item.uri).lastPathSegment ?: item.uri
            thumbnail.setImageURI(Uri.parse(item.uri))
            itemView.setOnClickListener { onOpen(item) }
            openButton.setOnClickListener { onOpen(item) }
            deleteButton.setOnClickListener { onDelete(item) }
        }
    }
}
