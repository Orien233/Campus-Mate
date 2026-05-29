package com.example.campusmate.ui.common

import android.view.View
import android.widget.TextView
import com.example.campusmate.R

/** Small helper for repeated expandable cards in XML fragments. */
object CollapsibleSection {
    fun bind(
        root: View,
        headerId: Int,
        contentId: Int,
        indicatorId: Int,
        expandedByDefault: Boolean = false,
        onChanged: ((Boolean) -> Unit)? = null
    ) {
        val header = root.findViewById<View>(headerId)
        val content = root.findViewById<View>(contentId)
        val indicator = root.findViewById<TextView>(indicatorId)
        var expanded = expandedByDefault

        fun render() {
            content.visibility = if (expanded) View.VISIBLE else View.GONE
            indicator.setText(if (expanded) R.string.ui_collapse else R.string.ui_expand)
            onChanged?.invoke(expanded)
        }

        header.setOnClickListener {
            expanded = !expanded
            render()
        }
        render()
    }
}
