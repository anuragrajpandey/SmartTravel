package com.anuragrajpandey.smarttravel

import android.app.DatePickerDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.NestedScrollView
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/** Minimal, internet-driven trip planner: pick where & when, get weather, stays, itinerary, food and a budget. */
class TripPlannerActivity : AppCompatActivity() {
    private val prefs by lazy { getSharedPreferences("flipdoor", MODE_PRIVATE) }
    private val iso = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val shown = SimpleDateFormat("d MMM yyyy", Locale.US)

    private lateinit var body: LinearLayout
    private lateinit var formBox: LinearLayout
    private lateinit var resultBox: LinearLayout
    private lateinit var savedBox: LinearLayout
    private lateinit var fromField: SuggestField
    private lateinit var toField: SuggestField
    private lateinit var startBtn: TextView
    private lateinit var endBtn: TextView
    private lateinit var countView: TextView
    private val tierChips = mutableListOf<LinearLayout>()

    private var origin: CityLocation? = null
    private var dest: CityLocation? = null
    private var start: Calendar = Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH, 1); clearTime() }
    private var end: Calendar = Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH, 3); clearTime() }
    private var travellers = 2
    private var tier = 1
    private var plan: TripPlan? = null
    private var gen = 0

    private fun Calendar.clearTime() { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
    private fun dp(v: Int) = Ui.dp(this, v)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Ui.BG) }
        root.addView(Ui.topBar(this, "Trip planner"))
        val scroll = NestedScrollView(this)
        body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(4), dp(16), dp(28)) }
        scroll.addView(body)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        Ui.edgeToEdge(this, root)

        formBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        resultBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        savedBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        body.addView(formBox)
        body.addView(resultBox)
        body.addView(savedBox)
        buildForm()
        showSaved()
        // Opened from a place page ("Plan a trip to X")
        intent.getStringExtra("destination")?.takeIf { it.isNotBlank() }?.let { toField.setText(it) }
    }

    // ---------------------------------------------------------------- form
    private fun label(s: String) = Ui.text(this, s, 12f, Ui.MUTED, true).apply { setPadding(dp(2), dp(12), 0, dp(4)) }

    private fun buildForm() {
        formBox.removeAllViews()
        formBox.addView(Ui.text(this, "Plan your trip", 26f, Ui.FG, true))
        formBox.addView(Ui.text(this, "Live weather, hotels, places and a realistic budget for anywhere in India.", 13.5f, Ui.MUTED))

        formBox.addView(label("FROM"))
        fromField = SuggestField(this, "Starting city (optional)", { origin = it.toCity() }, { })
        formBox.addView(fromField.view)
        val saved = LocationHelper.saved(this)
        if (origin == null && saved != null && saved.countryCode == "IN") {
            origin = CityLocation(saved.name, "", "India", saved.lat, saved.lon)
            fromField.setText(saved.name)
        }

        formBox.addView(label("TO"))
        toField = SuggestField(this, "Where in India?", { dest = it.toCity() }, { })
        formBox.addView(toField.view)

        formBox.addView(label("DATES"))
        val dates = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        startBtn = dateButton(true)
        endBtn = dateButton(false)
        dates.addView(startBtn, LinearLayout.LayoutParams(0, dp(52), 1f).apply { rightMargin = dp(6) })
        dates.addView(endBtn, LinearLayout.LayoutParams(0, dp(52), 1f).apply { leftMargin = dp(6) })
        formBox.addView(dates)

        formBox.addView(label("TRAVELLERS"))
        val stepper = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = Ui.shape(this@TripPlannerActivity, Color.WHITE, 14)
            setPadding(dp(6), dp(4), dp(6), dp(4))
        }
        countView = TextView(this).apply {
            textSize = 18f; setTextColor(Ui.FG); gravity = Gravity.CENTER; setTypeface(typeface, Typeface.BOLD)
            text = "$travellers traveller${if (travellers > 1) "s" else ""}"
        }
        fun step(sym: String, delta: Int) = TextView(this).apply {
            text = sym; textSize = 24f; gravity = Gravity.CENTER; setTextColor(Ui.ACCENT)
            background = Ui.ripple(this@TripPlannerActivity, Ui.ACCENT_SOFT, 12)
            isClickable = true
            setOnClickListener {
                travellers = (travellers + delta).coerceIn(1, 12)
                countView.text = "$travellers traveller${if (travellers > 1) "s" else ""}"
                if (plan != null) renderPlan()
            }
        }
        stepper.addView(step("−", -1), LinearLayout.LayoutParams(dp(48), dp(44)))
        stepper.addView(countView, LinearLayout.LayoutParams(0, -2, 1f))
        stepper.addView(step("+", 1), LinearLayout.LayoutParams(dp(48), dp(44)))
        formBox.addView(stepper, LinearLayout.LayoutParams(-1, dp(54)))

        formBox.addView(label("STYLE"))
        val tiers = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        tierChips.clear()
        TripRepository.tierNames.forEachIndexed { i, n ->
            val chip = Ui.chip(this, n, i == tier) {
                tier = i
                tierChips.forEachIndexed { j, c -> Ui.restyleChip(c, j == tier, null, TripRepository.tierNames[j]) }
                if (plan != null) renderPlan()
            }
            tierChips.add(chip)
            tiers.addView(chip, LinearLayout.LayoutParams(0, -2, 1f).apply { rightMargin = dp(8) })
        }
        formBox.addView(tiers)

        formBox.addView(Ui.button(this, "Plan my trip", R.drawable.ic_flight) { generate() },
            LinearLayout.LayoutParams(-1, dp(54)).apply { topMargin = dp(18) })
    }

    private fun dateButton(isStart: Boolean): TextView = TextView(this).apply {
        gravity = Gravity.CENTER
        textSize = 14f
        setTextColor(Ui.FG)
        setTypeface(typeface, Typeface.BOLD)
        background = Ui.ripple(this@TripPlannerActivity, Color.WHITE, 14)
        isClickable = true
        text = dateText(isStart)
        setOnClickListener { pickDate(isStart) }
    }

    private fun dateText(isStart: Boolean) = (if (isStart) "From  " else "To  ") + shown.format((if (isStart) start else end).time)

    private fun pickDate(isStart: Boolean) {
        val c = if (isStart) start else end
        val dlg = DatePickerDialog(this, { _, y, m, d ->
            val sel = Calendar.getInstance().apply { set(y, m, d); clearTime() }
            if (isStart) {
                start = sel
                if (end.before(start)) end = (start.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, 2) }
            } else end = sel
            startBtn.text = dateText(true)
            endBtn.text = dateText(false)
        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH))
        dlg.datePicker.minDate = (if (isStart) Calendar.getInstance().apply { clearTime() } else start).timeInMillis
        dlg.show()
    }

    // ---------------------------------------------------------------- generate
    private fun generate() {
        val toText = toField.text()
        if (toText.isBlank()) { Toast.makeText(this, "Enter where you want to go.", Toast.LENGTH_SHORT).show(); return }
        val nights = ((end.timeInMillis - start.timeInMillis) / 86_400_000L).toInt()
        if (nights < 0) { Toast.makeText(this, "End date must be after the start date.", Toast.LENGTH_SHORT).show(); return }
        if (nights > 14) { Toast.makeText(this, "Trips are limited to 14 nights.", Toast.LENGTH_SHORT).show(); return }

        val g = ++gen
        resultBox.removeAllViews()
        val loading = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            setPadding(0, dp(30), 0, dp(10))
        }
        loading.addView(ProgressBar(this), LinearLayout.LayoutParams(dp(40), dp(40)))
        loading.addView(Ui.text(this, "Checking weather, hotels, places and food…", 13f, Ui.MUTED).apply { gravity = Gravity.CENTER })
        resultBox.addView(loading)

        val fromText = fromField.text()
        val fromPick = origin
        val toPick = dest
        Net.pool.execute {
            try {
                val d = resolve(toText, toPick)
                val o = if (fromText.isBlank()) null else runCatching { resolve(fromText, fromPick) }.getOrNull()
                val input = TripInput("Trip to ${d.name}", o, d, iso.format(start.time), iso.format(end.time), travellers)
                val result = TripRepository.generate(input)
                runOnUiThread {
                    if (isFinishing || isDestroyed || g != gen) return@runOnUiThread
                    dest = d; origin = o; plan = result
                    renderPlan()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    if (isFinishing || isDestroyed || g != gen) return@runOnUiThread
                    resultBox.removeAllViews()
                    resultBox.addView(Ui.text(this, e.message ?: "Couldn't build the plan. Check your connection.", 14f, Ui.MUTED).apply { setPadding(dp(4), dp(20), dp(4), dp(8)) })
                }
            }
        }
    }

    private fun resolve(text: String, picked: CityLocation?): CityLocation =
        if (picked != null && picked.name.equals(text, true)) picked else CityDataRepository.geocodeCity(text)

    // ---------------------------------------------------------------- results
    private fun renderPlan() {
        val p = plan ?: return
        resultBox.removeAllViews()
        val lines = TripRepository.budget(p, tier)
        val total = lines.sumOf { it.amount }

        // Summary
        val hero = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(0xFF006CE4.toInt(), 0xFF4C8DFF.toInt())).apply { cornerRadius = dp(24).toFloat() }
            setPadding(dp(20), dp(18), dp(20), dp(18))
        }
        hero.addView(Ui.text(this, "${(p.input.origin?.name?.plus(" → ")) ?: ""}${p.input.dest.name}", 14f, 0xCCFFFFFF.toInt(), true))
        hero.addView(Ui.text(this, TripRepository.money(total), 36f, Color.WHITE, true))
        val per = total / p.input.travellers
        hero.addView(Ui.text(this, "${TripRepository.money(per)} per person  ·  ${TripRepository.tierNames[tier]}", 13.5f, 0xDDFFFFFF.toInt()))
        hero.addView(Ui.text(this, "${p.dayCount} days, ${p.nights} night${if (p.nights == 1) "" else "s"}  ·  ${p.input.travellers} traveller${if (p.input.travellers > 1) "s" else ""}" +
            (p.distanceKm?.let { "  ·  ${it.toInt()} km away" } ?: ""), 12.5f, 0xDDFFFFFF.toInt()))
        resultBox.addView(hero, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(22) })

        // Weather
        heading("Weather on your dates")
        val withWeather = p.days.filter { it.weather != null }
        if (withWeather.isEmpty()) {
            resultBox.addView(Ui.text(this, "Forecasts are available for the next 16 days. Open Weather for current conditions.", 12.5f, Ui.MUTED))
        } else {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            p.days.forEach { d ->
                val w = d.weather ?: return@forEach
                val col = Ui.card(this, Color.WHITE, 16).apply { gravity = Gravity.CENTER_HORIZONTAL; setPadding(dp(12), dp(10), dp(12), dp(10)) }
                col.addView(Ui.text(this, d.label.substringBefore(","), 12f, Ui.MUTED, true))
                col.addView(Ui.iconView(this, WeatherStyle.icon(w.code, true)), Ui.iconLp(this, 34))
                col.addView(Ui.text(this, "${w.max.toInt()}° / ${w.min.toInt()}°", 13f, Ui.FG, true))
                if (w.rainProb >= 20) col.addView(Ui.text(this, "${w.rainProb}% rain", 11f, Ui.ACCENT))
                row.addView(col, LinearLayout.LayoutParams(-2, -2).apply { rightMargin = dp(8) })
            }
            resultBox.addView(HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(row) })
        }

        // Budget
        heading("Budget breakdown")
        val bcard = Ui.card(this)
        val maxLine = lines.maxOf { it.amount }.coerceAtLeast(1.0)
        lines.forEach { l ->
            val r = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(6), 0, dp(6)) }
            val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            top.addView(Ui.text(this, l.label, 14.5f, Ui.FG, true).apply { setPadding(0, 0, 0, 0) }, LinearLayout.LayoutParams(0, -2, 1f))
            top.addView(Ui.text(this, TripRepository.money(l.amount), 14.5f, Ui.FG, true).apply { setPadding(0, 0, 0, 0) })
            r.addView(top)
            val bar = View(this).apply { background = Ui.shape(this@TripPlannerActivity, Ui.ACCENT, 3) }
            r.addView(FrameLayout(this).apply {
                background = Ui.shape(this@TripPlannerActivity, Ui.ACCENT_SOFT, 3)
                addView(bar, FrameLayout.LayoutParams(0, dp(6)).also { lp -> bar.post { lp.width = (width * (l.amount / maxLine)).toInt().coerceAtLeast(dp(4)); bar.layoutParams = lp } })
            }, LinearLayout.LayoutParams(-1, dp(6)).apply { topMargin = dp(5) })
            r.addView(Ui.text(this, l.note, 11.5f, Ui.MUTED).apply { setPadding(0, dp(3), 0, 0) })
            bcard.addView(r)
        }
        bcard.addView(Ui.text(this, "Estimates for planning, based on live place and hotel data. Actual prices vary by season and availability.", 11f, Ui.MUTED))
        resultBox.addView(bcard)

        // Stay
        heading("Where to stay")
        val stays = TripRepository.hotelsForTier(p, tier)
        if (stays.isEmpty()) {
            resultBox.addView(Ui.text(this, "No mapped hotels were found here. Search live prices instead.", 12.5f, Ui.MUTED))
            resultBox.addView(Ui.button(this, "Find hotels in ${p.input.dest.name}", R.drawable.ic_hotel, Ui.ACCENT, Ui.ACCENT_SOFT, 14) {
                HotelsActivity.open(this, p.input.dest.latitude, p.input.dest.longitude, p.input.dest.name, "India")
            })
        }
        stays.forEach { h ->
            val c = Ui.card(this)
            c.addView(Ui.text(this, h.name, 15.5f, Ui.FG, true))
            val sub = (h.stars?.let { "$it★ · " } ?: "") + HotelRepository.kindLabel(h.kind) + " · " + Ui.dist(h.distanceKm) + " from centre"
            c.addView(Ui.text(this, sub, 12f, Ui.MUTED))
            c.addView(Ui.text(this, "${h.priceText()} / night (estimate)", 14.5f, Ui.ACCENT, true))
            val acts = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            acts.addView(Ui.button(this, "Live price", R.drawable.ic_wallet, Color.WHITE, Ui.ACCENT, 12) {
                WebPageActivity.open(this, "https://www.google.com/travel/search?q=${java.net.URLEncoder.encode("${h.name} ${p.input.dest.name} hotel", "UTF-8")}&hl=en", h.name)
            }, LinearLayout.LayoutParams(0, -2, 1f).apply { rightMargin = dp(6) })
            acts.addView(Ui.button(this, "Map", R.drawable.ic_map, Ui.ACCENT, Ui.ACCENT_SOFT, 12) {
                MapUi.openMap(this, h.lat, h.lon, h.name)
            }, LinearLayout.LayoutParams(0, -2, 1f))
            c.addView(acts)
            resultBox.addView(c, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
        }

        // Itinerary
        heading("Day by day")
        p.days.forEachIndexed { i, d ->
            val c = Ui.card(this)
            val head = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            head.addView(Ui.text(this, "Day ${i + 1}  ·  ${d.label}", 15.5f, Ui.FG, true).apply { setPadding(0, 0, 0, 0) }, LinearLayout.LayoutParams(0, -2, 1f))
            d.weather?.let { w ->
                head.addView(Ui.iconView(this, WeatherStyle.icon(w.code, true)), Ui.iconLp(this, 24).apply { rightMargin = dp(6) })
                head.addView(Ui.text(this, "${w.max.toInt()}°", 13f, Ui.MUTED, true).apply { setPadding(0, 0, 0, 0) })
            }
            c.addView(head)
            val tag = when { i == 0 && p.dayCount > 1 -> "Arrive and settle in"; i == p.dayCount - 1 && p.dayCount > 1 -> "Last day · check-out"; else -> "" }
            if (tag.isNotBlank()) c.addView(Ui.text(this, tag, 12f, Ui.ACCENT, true))
            if (d.stops.isEmpty()) c.addView(Ui.text(this, "Free day to explore on your own.", 13f, Ui.MUTED))
            d.stops.forEach { s -> c.addView(stopRow(s)) }
            d.dish?.let { dish ->
                c.addView(Ui.iconText(this, R.drawable.ic_restaurant, "Try today: ${dish.name}", 13f, Color.parseColor("#B45F06"), 18, true).apply {
                    setPadding(0, dp(10), 0, dp(2))
                    setOnClickListener { ArticleActivity.open(this@TripPlannerActivity, dish.name, ref = dish.wikiTitle, image = dish.imageUrl) }
                })
            }
            resultBox.addView(c, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
        }

        // Food
        if (p.foods.isNotEmpty()) {
            heading("Must-try food")
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            p.foods.take(8).forEach { row.addView(FoodUi.tallCard(this, it), LinearLayout.LayoutParams(dp(150), -2).apply { rightMargin = dp(10) }) }
            resultBox.addView(HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(row) })
        }

        // Actions
        val acts = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        acts.addView(Ui.button(this, "Save trip", R.drawable.ic_favorite, Color.WHITE, Ui.ACCENT, 14) { saveTrip(p, total) },
            LinearLayout.LayoutParams(0, dp(52), 1f).apply { rightMargin = dp(6) })
        acts.addView(Ui.button(this, "Share", R.drawable.ic_arrow_forward, Ui.ACCENT, Ui.ACCENT_SOFT, 14) { share(p, total) },
            LinearLayout.LayoutParams(0, dp(52), 1f).apply { leftMargin = dp(6) })
        resultBox.addView(acts, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(18) })
    }

    private fun heading(s: String) {
        resultBox.addView(Ui.text(this, s, 18f, Ui.FG, true), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(20); bottomMargin = dp(6) })
    }

    private fun stopRow(s: MostVisitedPlace): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(64)
            setPadding(0, dp(6), 0, dp(6))
            background = Ui.ripple(this@TripPlannerActivity, Color.TRANSPARENT, 10)
            isClickable = true
            setOnClickListener { startActivity(Intent(this@TripPlannerActivity, PlaceDetailActivity::class.java).putExtra("place", s)) }
        }
        val thumb = FrameLayout(this).apply { background = Ui.shape(this@TripPlannerActivity, Ui.ACCENT_SOFT, 12); clipToOutline = true }
        if (s.imageUrl != null) {
            val iv = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
            thumb.addView(iv, FrameLayout.LayoutParams(-1, -1))
            ImageLoader.load(s.imageUrl, iv)
        } else thumb.addView(Ui.iconView(this, R.drawable.ic_place, Ui.ACCENT), FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER))
        row.addView(thumb, LinearLayout.LayoutParams(dp(56), dp(56)).apply { rightMargin = dp(12) })
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(Ui.text(this, s.name, 14.5f, Ui.FG, true).apply { maxLines = 1; setPadding(0, 0, 0, 0) })
        col.addView(Ui.text(this, s.category, 12f, Ui.MUTED).apply { setPadding(0, dp(1), 0, 0) })
        row.addView(col, LinearLayout.LayoutParams(0, -2, 1f))
        return row
    }

    // ---------------------------------------------------------------- save / share / saved list
    private fun saveTrip(p: TripPlan, total: Double) {
        val trips = runCatching { JSONArray(prefs.getString("planned_trips", "[]")) }.getOrDefault(JSONArray())
        val i = p.input
        trips.put(
            JSONObject().put("name", i.name).put("destination", i.dest.name)
                .put("start", shown.format(start.time)).put("end", shown.format(end.time))
                .put("days", p.dayCount).put("travellers", i.travellers).put("budget", total)
                .put("startIso", i.startIso).put("endIso", i.endIso).put("tier", tier)
                .put("origin", i.origin?.name ?: "")
        )
        prefs.edit().putString("planned_trips", trips.toString()).apply()
        Toast.makeText(this, "Trip saved on this device.", Toast.LENGTH_SHORT).show()
        showSaved()
    }

    private fun share(p: TripPlan, total: Double) {
        val sb = StringBuilder()
        sb.append("${p.input.name}\n${p.days.first().label} – ${p.days.last().label}  ·  ${p.input.travellers} traveller(s)\n")
        sb.append("Estimated total ${TripRepository.money(total)} (${TripRepository.tierNames[tier]})\n\n")
        p.days.forEachIndexed { i, d ->
            sb.append("Day ${i + 1} (${d.label}): ").append(if (d.stops.isEmpty()) "free day" else d.stops.joinToString(", ") { it.name }).append('\n')
        }
        sb.append("\nPlanned with FlipDoor")
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, sb.toString()), "Share trip"))
    }

    private fun showSaved() {
        savedBox.removeAllViews()
        val saved = runCatching { JSONArray(prefs.getString("planned_trips", "[]")) }.getOrDefault(JSONArray())
        if (saved.length() == 0) return
        savedBox.addView(Ui.text(this, "Saved trips", 18f, Ui.FG, true), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(28); bottomMargin = dp(6) })
        for (idx in saved.length() - 1 downTo 0) {
            val t = saved.getJSONObject(idx)
            val card = Ui.card(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                isClickable = true
                setOnClickListener { reopen(t) }
            }
            val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            col.addView(Ui.text(this, t.optString("name"), 15.5f, Ui.FG, true).apply { setPadding(0, 0, 0, 0) })
            col.addView(Ui.text(this, t.optString("destination") + " · " + t.optString("start") + " – " + t.optString("end"), 12f, Ui.MUTED))
            col.addView(Ui.text(this, "${t.optInt("days")} days · ${t.optInt("travellers")} travellers · ${TripRepository.money(t.optDouble("budget"))}", 12.5f, Ui.ACCENT, true).apply { setPadding(0, 0, 0, 0) })
            card.addView(col, LinearLayout.LayoutParams(0, -2, 1f))
            card.addView(Ui.iconButton(this, R.drawable.ic_close, Ui.MUTED, null, 44, 18) {
                val arr = JSONArray()
                for (j in 0 until saved.length()) if (j != idx) arr.put(saved.getJSONObject(j))
                prefs.edit().putString("planned_trips", arr.toString()).apply()
                showSaved()
            })
            savedBox.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
        }
    }

    /** Re-plans a saved trip with fresh live data. */
    private fun reopen(t: JSONObject) {
        toField.setText(t.optString("destination"))
        dest = null
        t.optString("origin").takeIf { it.isNotBlank() }?.let { fromField.setText(it) }
        origin = null
        runCatching {
            if (t.has("startIso")) {
                start = Calendar.getInstance().apply { time = iso.parse(t.getString("startIso"))!!; clearTime() }
                end = Calendar.getInstance().apply { time = iso.parse(t.getString("endIso"))!!; clearTime() }
                if (start.before(Calendar.getInstance().apply { clearTime() })) {
                    val len = ((end.timeInMillis - start.timeInMillis) / 86_400_000L).toInt()
                    start = Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH, 1); clearTime() }
                    end = (start.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, len) }
                }
            }
        }
        startBtn.text = dateText(true); endBtn.text = dateText(false)
        travellers = t.optInt("travellers", 2).coerceIn(1, 12)
        countView.text = "$travellers traveller${if (travellers > 1) "s" else ""}"
        tier = t.optInt("tier", 1).coerceIn(0, 2)
        tierChips.forEachIndexed { j, c -> Ui.restyleChip(c, j == tier, null, TripRepository.tierNames[j]) }
        generate()
    }
}
