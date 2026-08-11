package com.tunnellight.converter

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.appcompat.widget.Toolbar
import androidx.core.content.edit
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.tunnellight.converter.model.Category
import com.tunnellight.converter.model.UnitsRepository

/** Home screen: a tiled grid of every conversion category, draggable to reorder and searchable. */
class MainActivity : AppCompatActivity() {

    private lateinit var adapter: CategoryAdapter
    private lateinit var emptyState: TextView

    /** Every category in the user's saved order — the grid shows a filtered view of this. */
    private var categories: List<Category> = emptyList()

    /** Current search text. Reordering is only meaningful, and only allowed, while it is blank. */
    private var query: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(findViewById<Toolbar>(R.id.toolbar))

        val recycler = findViewById<RecyclerView>(R.id.categoriesRecycler)
        // Keep the last row clear of the system navigation bar.
        val basePaddingBottom = recycler.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(recycler) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(bottom = basePaddingBottom + bars.bottom)
            insets
        }

        categories = orderedCategories()
        emptyState = findViewById(R.id.emptyState)
        adapter = CategoryAdapter(
            UnitsRepository.search("", categories).toMutableList(),
            onClick = { category ->
                startActivity(
                    Intent(this, ConverterActivity::class.java)
                        .putExtra(ConverterActivity.EXTRA_CATEGORY_ID, category.id)
                )
            },
            onOrderChanged = { newOrder ->
                categories = newOrder
                saveOrder(newOrder)
            }
        )
        recycler.layoutManager = GridLayoutManager(this, 2)
        recycler.adapter = adapter

        attachDragToReorder(recycler, adapter)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        val searchView = menu.findItem(R.id.action_search).actionView as SearchView
        searchView.queryHint = getString(R.string.search_hint)
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                searchView.clearFocus()
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                applyQuery(newText.orEmpty())
                return true
            }
        })
        return true
    }

    /** Narrow the grid to the categories and units matching [query], or show everything again. */
    private fun applyQuery(query: String) {
        this.query = query
        val matches = UnitsRepository.search(query, categories)
        adapter.submit(matches)
        emptyState.text = getString(R.string.search_no_matches, query.trim())
        emptyState.visibility = if (matches.isEmpty()) View.VISIBLE else View.GONE
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_feedback) {
            startActivity(Intent(this, FeedbackActivity::class.java))
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    /** Wire up long-press-and-drag reordering of the category tiles. */
    private fun attachDragToReorder(recycler: RecyclerView, adapter: CategoryAdapter) {
        val dragDirs = ItemTouchHelper.UP or ItemTouchHelper.DOWN or
            ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
        val callback = object : ItemTouchHelper.SimpleCallback(dragDirs, 0) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                adapter.moveItem(viewHolder.bindingAdapterPosition, target.bindingAdapterPosition)
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}

            // A drag within search results would reorder a partial list, so only allow it
            // while the full grid is on screen.
            override fun isLongPressDragEnabled() = query.isBlank()

            override fun onSelectedChanged(
                viewHolder: RecyclerView.ViewHolder?,
                actionState: Int
            ) {
                super.onSelectedChanged(viewHolder, actionState)
                // Lift the tile while it is being dragged for clear visual feedback.
                if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
                    viewHolder?.itemView?.isPressed = true
                }
            }

            override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(recyclerView, viewHolder)
                viewHolder.itemView.isPressed = false
                adapter.commitOrder()
            }
        }
        ItemTouchHelper(callback).attachToRecyclerView(recycler)
    }

    /** Apply any saved reordering, appending categories the saved order doesn't mention. */
    private fun orderedCategories(): List<Category> {
        val all = UnitsRepository.categories
        val saved = orderPrefs().getString(KEY_ORDER, null) ?: return all
        val byId = all.associateBy { it.id }
        val ordered = saved.split('\n').mapNotNull { byId[it] }
        if (ordered.size == all.size) return ordered
        // Fall back gracefully if the category set changed since the order was saved.
        return ordered + all.filter { it !in ordered }
    }

    /** Persist the current tile order so it is restored on next launch. */
    private fun saveOrder(categories: List<Category>) {
        orderPrefs().edit {
            putString(KEY_ORDER, categories.joinToString("\n") { it.id })
        }
    }

    private fun orderPrefs() = getSharedPreferences(PREFS_CATEGORY_ORDER, MODE_PRIVATE)

    companion object {
        private const val PREFS_CATEGORY_ORDER = "category_order"
        private const val KEY_ORDER = "order"
    }
}
