package com.tunnellight.converter

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.tunnellight.converter.model.Category
import com.tunnellight.converter.model.CategoryParam
import com.tunnellight.converter.model.Keyboard
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

    /** Decimal places results are shown to; null formats adaptively. */
    private var decimals: Int? = null

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
        decimals = savedDecimals()

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
            // A category that formats its own values brings its own keyboard and empty-field
            // hint with it — a time keypad and "00:00" for Clock, not a decimal pad and "0".
            category.valueFormat?.let { format ->
                row.field.inputType = format.keyboard.inputType()
                row.field.hint = format.format(0.0)
            }
            row.field.onTextChanged { onUserInput(row) }
            // The field itself keeps the usual text-selection menu, so copying the whole row
            // is offered from everywhere else on the card.
            rowView.setOnLongClickListener { copyValue(unit.name, row.field) }

            rows.add(row)
            container.addView(rowView)
        }

        fillInitialValue()
        showCopyHintOnce()
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
        val value = parseValue(raw)
        val sourceUnit = category.units[source.index]

        isUpdating = true
        try {
            for (row in rows) {
                if (row === source) continue
                row.field.setText(
                    if (value == null) ""
                    else formatValue(sourceUnit.convert(value, category.units[row.index]))
                )
            }
        } finally {
            isUpdating = false
        }
    }

    /** Read a field the way this category writes values — Clock's "14:30", say, rather than 14.5. */
    private fun parseValue(text: String): Double? =
        category.valueFormat?.parse(text) ?: text.toDoubleOrNull()

    /** Render a converted value the way this category writes values. */
    private fun formatValue(value: Double): String =
        category.valueFormat?.format(value) ?: UnitsRepository.format(value, decimals)

    /** The Android input type a category's chosen keyboard maps to. */
    private fun Keyboard.inputType(): Int = when (this) {
        Keyboard.NUMBER -> InputType.TYPE_CLASS_NUMBER or
            InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED
        Keyboard.TIME -> InputType.TYPE_CLASS_DATETIME or InputType.TYPE_DATETIME_VARIATION_TIME
    }

    /** Put a row's value on the clipboard. Returns false when there is nothing to copy. */
    private fun copyValue(unitName: String, field: EditText): Boolean {
        val value = field.text.toString().trim()
        if (value.isEmpty()) return false

        getSystemService(ClipboardManager::class.java)
            .setPrimaryClip(ClipData.newPlainText(unitName, value))
        // Android 13 and up shows its own copy confirmation, so a toast would be a duplicate.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Toast.makeText(this, getString(R.string.copied_toast, value), Toast.LENGTH_SHORT).show()
        }
        return true
    }

    /** Point out the long-press-to-copy gesture the first time a converter is opened. */
    private fun showCopyHintOnce() {
        if (statePrefs().getBoolean(KEY_COPY_HINT_SHOWN, false)) return
        Toast.makeText(this, R.string.copy_hint, Toast.LENGTH_LONG).show()
        statePrefs().edit { putBoolean(KEY_COPY_HINT_SHOWN, true) }
    }

    /**
     * Fill in the value the screen opens on: the one the category names, where it has one (Clock
     * opens on the current time), and otherwise the value last typed here, so that coming back
     * resumes where the user left off.
     */
    private fun fillInitialValue() {
        // Setting the text drives the watcher, which fills in every other row.
        category.openingValue?.let {
            rows.firstOrNull()?.field?.setText(formatValue(it()))
            return
        }
        val value = statePrefs().getString("$KEY_VALUE_PREFIX${category.id}", null)
        if (value.isNullOrEmpty()) return
        val row = rows.getOrNull(statePrefs().getInt("$KEY_UNIT_PREFIX${category.id}", -1)) ?: return
        row.field.setText(value)
    }

    override fun onPause() {
        super.onPause()
        // A category that opens on a value of its own (Clock, on the current time) would only
        // ever restore a stale one, so there is nothing here worth saving.
        if (category.openingValue != null) return
        val row = lastEdited ?: return
        statePrefs().edit {
            putInt("$KEY_UNIT_PREFIX${category.id}", row.index)
            putString("$KEY_VALUE_PREFIX${category.id}", row.field.text.toString())
        }
    }

    /** Let the user override how many decimal places this category's results are shown to. */
    private fun showDecimalsDialog() {
        val labels = DECIMAL_CHOICES.map {
            if (it == null) getString(R.string.decimals_auto) else getString(R.string.decimals_places, it)
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.menu_decimals)
            .setSingleChoiceItems(labels.toTypedArray(), DECIMAL_CHOICES.indexOf(decimals)) { dialog, which ->
                decimals = DECIMAL_CHOICES[which]
                // "auto" is stored as a non-numeric marker so it stays distinct from the
                // category's own default, which applies only while nothing has been chosen.
                statePrefs().edit {
                    putString("$KEY_DECIMALS_PREFIX${category.id}", decimals?.toString() ?: AUTO)
                }
                lastEdited?.let { onUserInput(it) }
                dialog.dismiss()
            }
            .show()
    }

    /** The chosen decimal places for this category, falling back to the category's own default. */
    private fun savedDecimals(): Int? {
        val saved = statePrefs().getString("$KEY_DECIMALS_PREFIX${category.id}", null)
            ?: return category.decimals
        return saved.toIntOrNull()
    }

    /** The parameter value last entered for [categoryId], or the parameter's default. */
    private fun savedParamValue(categoryId: String, param: CategoryParam): Double {
        val saved = statePrefs().getString("$KEY_PARAM_PREFIX$categoryId", null)?.toDoubleOrNull()
        return if (saved != null && saved in param.range) saved else param.defaultValue
    }

    /** Persist the parameter so the category opens with it next time. */
    private fun saveParamValue(categoryId: String, value: Double) {
        statePrefs().edit { putString("$KEY_PARAM_PREFIX$categoryId", value.toString()) }
    }

    private fun statePrefs() = getSharedPreferences(PREFS_STATE, MODE_PRIVATE)

    /** Run [block] whenever this field's text changes. */
    private fun EditText.onTextChanged(block: () -> Unit) {
        addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) = block()
        })
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.converter_menu, menu)
        // Decimal places say nothing about a category that formats its own values; Clock offers
        // a way back to the current moment instead.
        menu.findItem(R.id.action_decimals).isVisible = category.valueFormat == null
        menu.findItem(R.id.action_now).isVisible = category.openingValue != null
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                finish()
                return true
            }
            R.id.action_now -> {
                fillInitialValue()
                return true
            }
            R.id.action_decimals -> {
                showDecimalsDialog()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    companion object {
        const val EXTRA_CATEGORY_ID = "category_id"

        private const val PREFS_STATE = "converter_state"
        private const val KEY_PARAM_PREFIX = "param:"
        private const val KEY_VALUE_PREFIX = "value:"
        private const val KEY_UNIT_PREFIX = "unit:"
        private const val KEY_DECIMALS_PREFIX = "decimals:"
        private const val KEY_COPY_HINT_SHOWN = "copy_hint_shown"

        /** Decimal-place options offered in the menu; null means format adaptively. */
        private val DECIMAL_CHOICES = listOf(null, 0, 1, 2, 3, 4, 6)
        private const val AUTO = "auto"
    }
}
