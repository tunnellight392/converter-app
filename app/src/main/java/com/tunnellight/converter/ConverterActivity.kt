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
import androidx.core.content.edit
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.tunnellight.converter.model.Category
import com.tunnellight.converter.model.CategoryParam
import com.tunnellight.converter.model.UnitsRepository

/**
 * Shows every unit in a category, each with its own input field. Typing a value into any
 * field instantly converts and fills in all the others.
 */
class ConverterActivity : AppCompatActivity() {

    /** A single unit row: its position in the category's unit list plus the field the user types into. */
    private class UnitRow(val index: Int, val field: EditText)

    private val rows = mutableListOf<UnitRow>()

    /**
     * The category being shown. For a category with a [CategoryParam] this is swapped for a
     * rebuilt variant whenever the user changes the parameter, so [UnitRow.index] is used to
     * look units up afresh rather than holding on to them.
     */
    private lateinit var category: Category

    /** Guards against feedback loops: programmatic edits to other fields must not re-trigger. */
    private var isUpdating = false

    /** Last field the user typed into, so conversions can be redriven when the parameter changes. */
    private var lastEdited: UnitRow? = null

    private var noteBanner: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_converter)

        val categoryId = intent.getStringExtra(EXTRA_CATEGORY_ID)
        val base = categoryId?.let { UnitsRepository.categoryById(it) }
        if (base == null) {
            finish()
            return
        }
        // Reopen with whatever parameter value was last used, not the built-in default.
        category = base.param?.let { it.variantFor(savedParamValue(base.id, it)) } ?: base

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
        category.note?.let { noteBanner = addNote(container, it) }

        val inflater = LayoutInflater.from(this)

        category.param?.let { addParamRow(container, inflater, it) }

        category.units.forEachIndexed { index, unit ->
            val rowView = inflater.inflate(R.layout.item_unit_row, container, false)
            rowView.findViewById<TextView>(R.id.unitName).text = unit.name
            rowView.findViewById<TextView>(R.id.unitSymbol).text = unit.symbol

            val row = UnitRow(index, rowView.findViewById(R.id.unitValue))
            row.field.onTextChanged { onUserInput(row) }

            rows.add(row)
            container.addView(rowView)
        }
    }

    /** Add a highlighted caveat banner above the unit rows. */
    private fun addNote(container: LinearLayout, note: String): TextView {
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
        return banner
    }

    /** Add the input for a category's adjustable parameter, e.g. hours worked per week. */
    private fun addParamRow(container: LinearLayout, inflater: LayoutInflater, param: CategoryParam) {
        val rowView = inflater.inflate(R.layout.item_param_row, container, false)
        val label = rowView.findViewById<TextView>(R.id.paramLabel)
        label.text = param.label
        rowView.findViewById<TextView>(R.id.paramCaption).text = param.caption

        val field = rowView.findViewById<EditText>(R.id.paramValue)
        // The label text comes from the model, so name the field for screen readers here.
        label.labelFor = field.id
        field.hint = UnitsRepository.format(param.defaultValue)
        field.setText(UnitsRepository.format(savedParamValue(category.id, param)))
        field.onTextChanged { onParamInput(param, field) }

        container.addView(rowView)
    }

    /** Rebuild the category around a new parameter value and refresh the note and conversions. */
    private fun onParamInput(param: CategoryParam, field: EditText) {
        val value = field.text.toString().trim().toDoubleOrNull()
        if (value == null || value !in param.range) {
            // Keep the last usable setting, but say why a nonsense entry had no effect.
            if (field.text.isNotEmpty()) {
                field.error = getString(
                    R.string.param_range_error,
                    UnitsRepository.format(param.range.start),
                    UnitsRepository.format(param.range.endInclusive)
                )
            }
            return
        }
        field.error = null

        category = param.variantFor(value)
        saveParamValue(category.id, value)
        category.note?.let { noteBanner?.text = getString(R.string.note_format, it) }
        lastEdited?.let { onUserInput(it) }
    }

    /** Recompute every other field from the value the user just typed into [source]. */
    private fun onUserInput(source: UnitRow) {
        if (isUpdating) return

        lastEdited = source
        val raw = source.field.text.toString().trim()
        val value = raw.toDoubleOrNull()
        val sourceUnit = category.units[source.index]

        isUpdating = true
        try {
            for (row in rows) {
                if (row === source) continue
                row.field.setText(
                    if (value == null) ""
                    else UnitsRepository.format(sourceUnit.convert(value, category.units[row.index]))
                )
            }
        } finally {
            isUpdating = false
        }
    }

    /** The parameter value last entered for [categoryId], or the parameter's default. */
    private fun savedParamValue(categoryId: String, param: CategoryParam): Double {
        val saved = paramPrefs().getString(categoryId, null)?.toDoubleOrNull()
        return if (saved != null && saved in param.range) saved else param.defaultValue
    }

    /** Persist the parameter so the category opens with it next time. */
    private fun saveParamValue(categoryId: String, value: Double) {
        paramPrefs().edit { putString(categoryId, value.toString()) }
    }

    private fun paramPrefs() = getSharedPreferences(PREFS_CATEGORY_PARAMS, MODE_PRIVATE)

    /** Run [block] whenever this field's text changes. */
    private fun EditText.onTextChanged(block: () -> Unit) {
        addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) = block()
        })
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
        private const val PREFS_CATEGORY_PARAMS = "category_params"
    }
}
