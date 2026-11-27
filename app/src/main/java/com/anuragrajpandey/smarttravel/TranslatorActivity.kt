package com.anuragrajpandey.smarttravel

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.NestedScrollView
import java.util.Locale

/** Clean online translator: live results as you type, language swap, copy and speak. No downloads. */
class TranslatorActivity : AppCompatActivity() {
    private var from = TranslateRepository.AUTO
    private var to = "hi"
    private lateinit var fromBtn: TextView
    private lateinit var toBtn: TextView
    private lateinit var input: EditText
    private lateinit var output: TextView
    private lateinit var meta: TextView
    private lateinit var spinner: ProgressBar
    private lateinit var count: TextView
    private val handler = Handler(Looper.getMainLooper())
    private var pending: Runnable? = null
    private var gen = 0
    private var lastDetected: String? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    private fun dp(v: Int) = Ui.dp(this, v)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(applicationContext) { ttsReady = it == TextToSpeech.SUCCESS }

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Ui.BG) }
        root.addView(Ui.topBar(this, "Translate"))
        val scroll = NestedScrollView(this)
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(6), dp(16), dp(24)) }
        scroll.addView(body)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        // Language bar: [From] ⇄ [To]
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = Ui.card(this@TranslatorActivity).background
            setPadding(dp(6), dp(6), dp(6), dp(6))
        }
        fromBtn = langButton { pick(true) }
        toBtn = langButton { pick(false) }
        bar.addView(fromBtn, LinearLayout.LayoutParams(0, dp(48), 1f))
        bar.addView(Ui.iconButton(this, R.drawable.ic_swap, Ui.ACCENT, Ui.ACCENT_SOFT, 44, 22) { swap() })
        bar.addView(toBtn, LinearLayout.LayoutParams(0, dp(48), 1f))
        body.addView(bar)

        // Input
        val inCard = Ui.card(this)
        input = EditText(this).apply {
            hint = "Type or paste text"
            textSize = 20f
            setTextColor(Ui.FG)
            setHintTextColor(0xFFA0AEC0.toInt())
            background = null
            minLines = 4
            gravity = Gravity.TOP
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            setPadding(0, dp(4), 0, dp(4))
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: Editable?) { count.text = "${s?.length ?: 0}/3000"; schedule() }
            })
        }
        inCard.addView(input)
        val inRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        count = Ui.text(this, "0/3000", 12f, Ui.MUTED)
        inRow.addView(count, LinearLayout.LayoutParams(0, -2, 1f))
        inRow.addView(Ui.iconButton(this, R.drawable.ic_close, Ui.MUTED, null, 44, 20) { input.setText(""); showOutput("", null) })
        inCard.addView(inRow)
        body.addView(inCard, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })

        // Output
        val outCard = Ui.card(this, Ui.ACCENT_SOFT)
        meta = Ui.text(this, "", 12f, Ui.ACCENT, true)
        outCard.addView(meta)
        spinner = ProgressBar(this, null, android.R.attr.progressBarStyleSmall).apply { visibility = android.view.View.GONE }
        outCard.addView(spinner, LinearLayout.LayoutParams(dp(20), dp(20)))
        output = TextView(this).apply {
            textSize = 20f
            setTextColor(Ui.FG)
            setTextIsSelectable(true)
            minLines = 3
            hint = "Translation appears here"
            setHintTextColor(0xFF8FA6C8.toInt())
        }
        outCard.addView(output)
        val outRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.END }
        outRow.addView(Ui.iconButton(this, R.drawable.ic_volume, Ui.ACCENT, Color.WHITE, 44, 20) { speak() })
        outRow.addView(Ui.button(this, "Copy", null, Ui.ACCENT, Color.WHITE, 22) { copy() },
            LinearLayout.LayoutParams(-2, dp(44)).apply { leftMargin = dp(8) })
        outCard.addView(outRow)
        body.addView(outCard, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })

        // Travel phrases
        body.addView(Ui.text(this, "Travel phrases", 15f, Ui.FG, true), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(20) })
        val phrases = listOf(
            "Where is the railway station?", "How much does this cost?", "Thank you very much", "I need help",
            "Please take me to this address", "Vegetarian food, please", "Is this place open now?", "Where is the nearest hospital?"
        )
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        phrases.forEach { p ->
            row.addView(Ui.chip(this, p, false) { from = "en"; refreshLabels(); input.setText(p) },
                LinearLayout.LayoutParams(-2, -2).apply { rightMargin = dp(8) })
        }
        body.addView(HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(row) })
        body.addView(Ui.text(this, "Translations are fetched online as you type. Machine translation can be imperfect; double-check anything important.", 11.5f, Ui.MUTED),
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(18) })

        setContentView(root)
        Ui.edgeToEdge(this, root)
        refreshLabels()
    }

    private fun langButton(click: () -> Unit) = TextView(this).apply {
        textSize = 15f
        setTextColor(Ui.FG)
        gravity = Gravity.CENTER
        setTypeface(typeface, android.graphics.Typeface.BOLD)
        background = Ui.ripple(this@TranslatorActivity, Color.TRANSPARENT, 12)
        isClickable = true
        setOnClickListener { click() }
    }

    private fun refreshLabels() {
        fromBtn.text = if (from == TranslateRepository.AUTO) "Detect language" else TranslateRepository.nameOf(from)
        toBtn.text = TranslateRepository.nameOf(to)
    }

    private fun pick(isFrom: Boolean) {
        val opts = TranslateRepository.languages.let { if (isFrom) listOf(Lang("Detect language", TranslateRepository.AUTO)) + it else it }
        AlertDialog.Builder(this)
            .setTitle(if (isFrom) "Translate from" else "Translate to")
            .setItems(opts.map { it.name }.toTypedArray()) { _, i ->
                if (isFrom) from = opts[i].code else to = opts[i].code
                refreshLabels()
                schedule(0)
            }
            .show()
    }

    private fun swap() {
        val oldFrom = if (from == TranslateRepository.AUTO) (lastDetected ?: "en") else from
        val oldTo = to
        from = oldTo
        to = oldFrom
        val text = output.text.toString()
        refreshLabels()
        // Move the translation into the input so the user can keep going the other way.
        if (text.isNotBlank()) input.setText(text) else schedule(0)
    }

    private fun schedule(delay: Long = 600) {
        pending?.let { handler.removeCallbacks(it) }
        val q = input.text.toString().trim()
        if (q.isEmpty()) { gen++; showOutput("", null); return }
        val r = Runnable { run(q) }
        pending = r
        handler.postDelayed(r, delay)
    }

    private fun run(q: String) {
        val g = ++gen
        spinner.visibility = android.view.View.VISIBLE
        val f = from
        val t = to
        Net.pool.execute {
            val res = runCatching { TranslateRepository.translate(q.take(3000), f, t) }
            runOnUiThread {
                if (isFinishing || g != gen) return@runOnUiThread
                spinner.visibility = android.view.View.GONE
                res.onSuccess { showOutput(it.text, it.detected) }
                    .onFailure { output.text = ""; meta.text = "Couldn't translate. Check your internet connection." }
            }
        }
    }

    private fun showOutput(text: String, detected: String?) {
        output.text = text
        lastDetected = detected
        meta.text = when {
            text.isBlank() -> ""
            from == TranslateRepository.AUTO && detected != null -> "Detected: ${TranslateRepository.nameOf(detected)}  →  ${TranslateRepository.nameOf(to)}"
            else -> TranslateRepository.nameOf(to)
        }
    }

    private fun copy() {
        val t = output.text.toString()
        if (t.isBlank()) return
        (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("translation", t))
        Toast.makeText(this, "Copied", Toast.LENGTH_SHORT).show()
    }

    private fun speak() {
        val t = output.text.toString()
        if (t.isBlank()) return
        val engine = tts
        if (engine == null || !ttsReady) { Toast.makeText(this, "Speech isn't available on this phone.", Toast.LENGTH_SHORT).show(); return }
        val code = TranslateRepository.languages.firstOrNull { it.code == to }?.tts ?: to
        val avail = engine.setLanguage(Locale.forLanguageTag(code))
        if (avail == TextToSpeech.LANG_MISSING_DATA || avail == TextToSpeech.LANG_NOT_SUPPORTED) {
            Toast.makeText(this, "This phone has no ${TranslateRepository.nameOf(to)} voice installed.", Toast.LENGTH_LONG).show()
            return
        }
        engine.speak(t, TextToSpeech.QUEUE_FLUSH, null, "flipdoor")
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}
