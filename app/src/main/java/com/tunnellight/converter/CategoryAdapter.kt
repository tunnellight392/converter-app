package com.tunnellight.converter

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.graphics.toColorInt
import androidx.recyclerview.widget.RecyclerView
import com.tunnellight.converter.model.Category
import java.util.Collections

/** Renders each [Category] as a clickable tile in the home grid, draggable to reorder. */
class CategoryAdapter(
    private val categories: MutableList<Category>,
    private val onClick: (Category) -> Unit,
    private val onOrderChanged: (List<Category>) -> Unit
) : RecyclerView.Adapter<CategoryAdapter.CategoryViewHolder>() {

    class CategoryViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val colorDot: View = view.findViewById(R.id.colorDot)
        val emoji: TextView = view.findViewById(R.id.emoji)
        val name: TextView = view.findViewById(R.id.categoryName)
        val unitCount: TextView = view.findViewById(R.id.unitCount)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CategoryViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_category, parent, false)
        return CategoryViewHolder(view)
    }

    override fun onBindViewHolder(holder: CategoryViewHolder, position: Int) {
        val category = categories[position]
        holder.emoji.text = category.emoji
        holder.name.text = category.name
        holder.unitCount.text = holder.itemView.context
            .getString(R.string.units_count, category.units.size)
        holder.colorDot.backgroundTintList =
            ColorStateList.valueOf(category.colorHex.toColorInt())
        holder.itemView.setOnClickListener { onClick(category) }
    }

    override fun getItemCount(): Int = categories.size

    /** Swap two tiles during a drag. Called by the ItemTouchHelper callback for each step. */
    fun moveItem(from: Int, to: Int) {
        Collections.swap(categories, from, to)
        notifyItemMoved(from, to)
    }

    /** Called once when a drag gesture finishes, so the new order can be persisted. */
    fun commitOrder() {
        onOrderChanged(categories.toList())
    }
}
