package com.anuragrajpandey.smarttravel

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Search box with live, India-only suggestions shown right under the field.
 * [onPick] fires when a suggestion is tapped; [onSubmit] when the keyboard's search key is pressed.
 */
class SuggestField(
    private val activity: Activity,
    hint: String,
    private val onPick: (Suggestion) -> Unit,
    private val onSubmit: (String) -> Unit
) {
    val input: EditText
    val view: LinearLayout
    private val list: LinearLayout
    private val handler = Handler(Looper.getMainLooper())
    private var pending: Runnable? = null
    private var gen = 0
    private var silent = false

    private fun dp(v: Int) = Ui.dp(activity, v)

    init {
        input = EditText(activity).apply {
            this.hint = hint
            setSingleLine(true)
            textSize = 15f
            setTextColor(Ui.FG)
            setHintTextColor(Ui.MUTED)
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            background = Ui.shape(activity, Color.WHITE, 14)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            setOnEditorActionListener { _, id, _ ->
                if (id == EditorInfo.IME_ACTION_SEARCH) { hideList(); hideKeyboard(); onSubmit(text.toString().trim()); true } else false
            }
        }
        list = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            visibility = android.view.View.GONE
            background = Ui.card(activity).background
            setPadding(0, dp(4), 0, dp(4))
        }
        view = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            addView(input, LinearLayout.LayoutParams(-1, dp(50)))
            addView(list, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })
        }
        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (silent) return
                pending?.let { handler.removeCallbacks(it) }
                val q = s?.toString()?.trim().orEmpty()
                if (q.length < 2) { gen++; hideList(); return }
                val r = Runnable { search(q) }
                pending = r
                handler.postDelayed(r, 260)
            }
        })
    }

    fun text() = input.text.toString().trim()

    fun setText(s: String) { silent = true; input.setText(s); input.setSelection(s.length); silent = false; hideList() }

    fun hideList() { list.removeAllViews(); list.visibility = android.view.View.GONE }

    private fun hideKeyboard() {
        (activity.getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(input.windowToken, 0)
    }

    private fun search(q: String) {
        val g = ++gen
        val bias = LocationHelper.saved(activity)
        Net.pool.execute {
            val items = runCatching { SuggestRepository.suggest(q, bias) }.getOrDefault(emptyList())
            activity.runOnUiThread { if (!activity.isFinishing && g == gen) show(items) }
        }
    }

    private fun show(items: List<Suggestion>) {
        list.removeAllViews()
        if (items.isEmpty()) { list.visibility = android.view.View.GONE; return }
        items.forEach { s ->
            val row = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = dp(54)
                setPadding(dp(14), dp(6), dp(14), dp(6))
                background = Ui.ripple(activity, Color.TRANSPARENT, 10)
                isClickable = true
                setOnClickListener { setText(s.name); hideKeyboard(); onPick(s) }
            }
            row.addView(Ui.iconView(activity, R.drawable.ic_place, Ui.ACCENT), Ui.iconLp(activity, 20).apply { rightMargin = dp(12) })
            val col = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
            col.addView(TextView(activity).apply { text = s.name; textSize = 15f; setTextColor(Ui.FG); setTypeface(typeface, Typeface.BOLD); maxLines = 1 })
            col.addView(TextView(activity).apply { text = s.detail; textSize = 12f; setTextColor(Ui.MUTED); maxLines = 1 })
            row.addView(col, LinearLayout.LayoutParams(0, -2, 1f))
            list.addView(row)
        }
        list.visibility = android.view.View.VISIBLE
    }
}
