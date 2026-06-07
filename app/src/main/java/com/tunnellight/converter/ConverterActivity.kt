package com.tunnellight.converter

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.MenuItem
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.tunnellight.converter.model.ConvUnit
import com.tunnellight.converter.model.UnitsRepository

/**
 * Shows every unit in a category, each with its own input field. Typing a value into any
 * field instantly converts and fills in all the others.
 */
class ConverterActivity : AppCompatActivity() {

    /** A single unit row: its model plus the field the user types into. */
    private data class UnitRow(val unit: ConvUnit, val field: EditText)

    private val rows = mutableListOf<UnitRow>()

    /** Guards against feedback loops: programmatic edits to other fields must not re-trigger. */
    private var isUpdating = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_converter)

        val categoryId = intent.getStringExtra(EXTRA_CATEGORY_ID)
        val category = categoryId?.let { UnitsRepository.categoryById(it) }
        if (category == null) {
            finish()
            return
        }

        setSupportActionBar(findViewById<Toolbar>(R.id.toolbar))
        title = category.name
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val container = findViewById<LinearLayout>(R.id.unitsContainer)
        // Keep the last unit row clear of the system navigation bar.
        val basePaddingBottom = container.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(container) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(bottom = basePaddingBottom + bars.bottom)
            insets
        }
        category.note?.let { addNote(container, it) }

        val inflater = LayoutInflater.from(this)

        category.units.forEach { unit ->
            val rowView = inflater.inflate(R.layout.item_unit_row, container, false)
            rowView.findViewById<TextView>(R.id.unitName).text = unit.name
            rowView.findViewById<TextView>(R.id.unitSymbol).text = unit.symbol

            val field = rowView.findViewById<EditText>(R.id.unitValue)
            val row = UnitRow(unit, field)
            field.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) = onUserInput(row)
            })

            rows.add(row)
            container.addView(rowView)
        }
    }

    /** Add a highlighted caveat banner above the unit rows. */
    private fun addNote(container: LinearLayout, note: String) {
        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()
        val banner = TextView(this).apply {
            text = getString(R.string.note_format, note)
            textSize = 13f
            setTextColor(ContextCompat.getColor(this@ConverterActivity, R.color.warning_text))
            setBackgroundResource(R.drawable.warning_bg)
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }
        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            leftMargin = dp(4); rightMargin = dp(4); topMargin = dp(8); bottomMargin = dp(4)
        }
        container.addView(banner, params)
    }

    /** Recompute every other field from the value the user just typed into [source]. */
    private fun onUserInput(source: UnitRow) {
        if (isUpdating) return

        val raw = source.field.text.toString().trim()
        val value = raw.toDoubleOrNull()

        isUpdating = true
        try {
            for (row in rows) {
                if (row === source) continue
                row.field.setText(
                    if (value == null) "" else UnitsRepository.format(source.unit.convert(value, row.unit))
                )
            }
        } finally {
            isUpdating = false
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    companion object {
        const val EXTRA_CATEGORY_ID = "category_id"
    }
}
