package com.tunnellight.converter

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.graphics.toColorInt
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.tunnellight.converter.model.Category
import com.tunnellight.converter.model.CategoryMatch
import java.util.Collections

/** Renders each [Category] as a clickable tile in the home grid, draggable to reorder. */
class CategoryAdapter(
    private val matches: MutableList<CategoryMatch>,
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
        val match = matches[position]
        val category = match.category
        val context = holder.itemView.context
        holder.emoji.text = category.emoji
        holder.name.text = category.name
        // When a search found this tile through one of its units, name that unit instead of
        // the unit count — it is why the tile is on screen.
        holder.unitCount.text = match.matchedUnit?.let { unit ->
            if (unit.name == unit.symbol) unit.name
            else context.getString(R.string.unit_match, unit.name, unit.symbol)
        } ?: context.getString(R.string.units_count, category.units.size)
        holder.colorDot.backgroundTintList =
            ColorStateList.valueOf(category.colorHex.toColorInt())
        holder.itemView.setOnClickListener { onClick(category) }
    }

    override fun getItemCount(): Int = matches.size

    /** Replace the visible tiles, e.g. when a search query narrows the grid. */
    fun submit(newMatches: List<CategoryMatch>) {
        val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = matches.size
            override fun getNewListSize() = newMatches.size
            override fun areItemsTheSame(oldPos: Int, newPos: Int) =
                matches[oldPos].category.id == newMatches[newPos].category.id
            override fun areContentsTheSame(oldPos: Int, newPos: Int) =
                matches[oldPos] == newMatches[newPos]
        })
        matches.clear()
        matches.addAll(newMatches)
        diff.dispatchUpdatesTo(this)
    }

    /** Swap two tiles during a drag. Called by the ItemTouchHelper callback for each step. */
    fun moveItem(from: Int, to: Int) {
        Collections.swap(matches, from, to)
        notifyItemMoved(from, to)
    }

    /** Called once when a drag gesture finishes, so the new order can be persisted. */
    fun commitOrder() {
        onOrderChanged(matches.map { it.category })
    }
}
