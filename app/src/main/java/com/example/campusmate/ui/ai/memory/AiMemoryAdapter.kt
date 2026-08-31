package com.example.campusmate.ui.ai.memory

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.widget.PopupMenu
import androidx.recyclerview.widget.RecyclerView
import com.example.campusmate.R
import com.example.campusmate.data.model.AiMemory
import com.example.campusmate.util.DateTimeUtils

/** One scrolling surface for the disclosure, growth summary and user memories. */
internal class AiMemoryAdapter(
    private val bindHeader: (View) -> Unit,
    private val onEdit: (AiMemory) -> Unit,
    private val onToggleEnabled: (AiMemory) -> Unit,
    private val onTogglePinned: (AiMemory) -> Unit,
    private val onDelete: (AiMemory) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private var memories: List<AiMemory> = emptyList()
    var busy: Boolean = false

    fun submitList(items: List<AiMemory>) {
        memories = items
        notifyDataSetChanged()
    }

    fun refreshHeader() = notifyItemChanged(0)

    override fun getItemCount(): Int = memories.size + 1

    override fun getItemViewType(position: Int): Int = if (position == 0) HEADER else MEMORY

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == HEADER) {
            HeaderHolder(inflater.inflate(R.layout.item_ai_memory_header, parent, false))
        } else {
            MemoryHolder(inflater.inflate(R.layout.item_ai_memory, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is HeaderHolder) bindHeader(holder.itemView)
        else (holder as MemoryHolder).bind(memories[position - 1])
    }

    private class HeaderHolder(view: View) : RecyclerView.ViewHolder(view)

    private inner class MemoryHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val category: TextView = view.findViewById(R.id.aiMemoryCategoryText)
        private val content: TextView = view.findViewById(R.id.aiMemoryContentText)
        private val status: TextView = view.findViewById(R.id.aiMemoryStatusText)
        private val meta: TextView = view.findViewById(R.id.aiMemoryMetaText)
        private val more: ImageButton = view.findViewById(R.id.aiMemoryMoreButton)

        fun bind(memory: AiMemory) {
            val context = itemView.context
            category.text = AiMemoryUiFormatter.category(context, memory.category)
            content.text = memory.content
            val expired = !memory.isPinned && memory.expiresAt?.let { it <= System.currentTimeMillis() } == true
            status.text = buildList {
                add(context.getString(when {
                    !memory.isEnabled -> R.string.ai_memory_status_disabled
                    expired -> R.string.ai_memory_status_expired
                    else -> R.string.ai_memory_status_enabled
                }))
                if (memory.isPinned) add(context.getString(R.string.ai_memory_status_pinned))
            }.joinToString(" · ")
            meta.text = listOf(
                context.getString(R.string.ai_memory_updated, DateTimeUtils.formatDateTime(memory.updatedAt)),
                memory.expiresAt?.let {
                    context.getString(R.string.ai_memory_expires, DateTimeUtils.formatDate(it))
                } ?: context.getString(R.string.ai_memory_retention_forever)
            ).joinToString("\n")
            itemView.setOnClickListener { if (!busy) onEdit(memory) }
            more.setOnClickListener {
                if (busy) return@setOnClickListener
                PopupMenu(context, more).apply {
                    menu.add(0, EDIT, 0, R.string.ai_memory_edit)
                    menu.add(0, ENABLE, 1, if (memory.isEnabled) R.string.ai_memory_disable else R.string.ai_memory_enable)
                    menu.add(0, PIN, 2, if (memory.isPinned) R.string.ai_memory_unpin else R.string.ai_memory_pin)
                    menu.add(0, DELETE, 3, R.string.action_delete)
                    setOnMenuItemClickListener { item ->
                        if (!busy) when (item.itemId) {
                            EDIT -> onEdit(memory)
                            ENABLE -> onToggleEnabled(memory)
                            PIN -> onTogglePinned(memory)
                            DELETE -> onDelete(memory)
                        }
                        true
                    }
                    show()
                }
            }
        }
    }

    private companion object {
        const val HEADER = 0
        const val MEMORY = 1
        const val EDIT = 10
        const val ENABLE = 11
        const val PIN = 12
        const val DELETE = 13
    }
}
