package com.anuragrajpandey.smarttravel

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.concurrent.thread
import kotlin.math.roundToInt

/** Full-screen weather, styled like a native weather app: live sky gradient, hourly strip, daily ranges, detail tiles. */
class WeatherActivity : AppCompatActivity() {
    private val white = Color.WHITE
    private val soft = 0xCCFFFFFF.toInt()
    private val faint = 0x99FFFFFF.toInt()

    private lateinit var root: LinearLayout
    private lateinit var body: LinearLayout
    private var lat = 0.0
    private var lon = 0.0
    private var placeName = ""
    private var gen = 0
    private val isoDay = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val dayName = SimpleDateFormat("EEE", Locale.US)
    private val longDay = SimpleDateFormat("EEE, d MMM", Locale.US)

    private fun dp(v: Int) = Ui.dp(this, v)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val saved = LocationHelper.saved(this)
        lat = intent.getDoubleExtra("lat", saved?.lat ?: Double.NaN)
        lon = intent.getDoubleExtra("lon", saved?.lon ?: Double.NaN)
        placeName = intent.getStringExtra("name") ?: saved?.name.orEmpty()

        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, WeatherStyle.sky(0, true))
        }
        val search = Ui.iconButton(this, R.drawable.ic_search, white) { promptCity() }
        val locate = Ui.iconButton(this, R.drawable.ic_my_location, white) { useMyLocation() }
        root.addView(Ui.topBar(this, "Weather", white, listOf(search, locate)))
        val scroll = ScrollView(this).apply { isVerticalScrollBarEnabled = false }
        body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(4), dp(16), dp(28))
        }
        scroll.addView(body)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        Ui.edgeToEdge(this, root, lightBars = false)
        if (lat.isNaN() || lon.isNaN()) promptCityFirst() else load()
    }

    private fun promptCityFirst() {
        body.removeAllViews()
        body.addView(msg("Search a city or tap the location button to see the weather."))
        promptCity()
    }

    private fun msg(s: String) = TextView(this).apply {
        text = s; textSize = 15f; setTextColor(white); gravity = Gravity.CENTER
        setPadding(dp(16), dp(40), dp(16), dp(16))
    }

    private fun useMyLocation() {
        val s = LocationHelper.saved(this)
        if (s == null) { Toast.makeText(this, "Location not available yet. Allow location on the home screen.", Toast.LENGTH_LONG).show(); return }
        lat = s.lat; lon = s.lon; placeName = s.name
        load()
    }

    private fun promptCity() {
        var dialog: AlertDialog? = null
        val field = SuggestField(this, "Search a place in India", { sug ->
            dialog?.dismiss()
            lat = sug.lat; lon = sug.lon; placeName = sug.name
            load()
        }, { q ->
            dialog?.dismiss()
            if (q.isNotBlank()) searchTyped(q)
        })
        val wrap = LinearLayout(this).apply { setPadding(dp(16), dp(8), dp(16), dp(4)); addView(field.view, LinearLayout.LayoutParams(-1, -2)) }
        dialog = AlertDialog.Builder(this)
            .setTitle("Weather for another place")
            .setView(wrap)
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun searchTyped(q: String) {
        body.removeAllViews()
        body.addView(ProgressBar(this), LinearLayout.LayoutParams(-1, dp(80)).apply { topMargin = dp(80) })
        Net.pool.execute {
            try {
                val c = CityDataRepository.geocodeCity(q)
                runOnUiThread { lat = c.latitude; lon = c.longitude; placeName = c.name; load() }
            } catch (e: Exception) {
                runOnUiThread { body.removeAllViews(); body.addView(msg(e.message ?: "Place not found")) }
            }
        }
    }

    private fun load() {
        val g = ++gen
        body.removeAllViews()
        body.addView(ProgressBar(this), LinearLayout.LayoutParams(-1, dp(80)).apply { topMargin = dp(120) })
        thread {
            try {
                val r = WeatherRepository.fetch(lat, lon)
                runOnUiThread { if (!isFinishing && !isDestroyed && g == gen) render(r) }
            } catch (e: Exception) {
                runOnUiThread {
                    if (isFinishing || isDestroyed || g != gen) return@runOnUiThread
                    body.removeAllViews()
                    body.addView(msg("Couldn't load the weather. Check your connection."))
                    body.addView(Ui.button(this, "Try again", null, Ui.ACCENT, white) { load() })
                }
            }
        }
    }

    // ---------------- rendering ----------------
    private fun glass(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = GradientDrawable().apply {
            setColor(0x33FFFFFF); cornerRadius = dp(22).toFloat(); setStroke(dp(1), 0x26FFFFFF)
        }
        setPadding(dp(14), dp(12), dp(14), dp(12))
    }

    private fun cardTitle(icon: Int, title: String) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(Ui.iconView(this@WeatherActivity, icon, faint), Ui.iconLp(this@WeatherActivity, 16).apply { rightMargin = dp(6) })
        addView(TextView(this@WeatherActivity).apply {
            text = title.uppercase(Locale.US); textSize = 11.5f; setTextColor(faint)
            setTypeface(typeface, Typeface.BOLD); letterSpacing = 0.05f
        })
        setPadding(0, 0, 0, dp(8))
    }

    private fun hour12(iso: String): String {
        val h = iso.substring(11, 13).toInt()
        return "${if (h % 12 == 0) 12 else h % 12} ${if (h < 12) "AM" else "PM"}"
    }

    private fun clock(iso: String) = if (iso.length >= 16) {
        val h = iso.substring(11, 13).toInt()
        "${if (h % 12 == 0) 12 else h % 12}:${iso.substring(14, 16)} ${if (h < 12) "AM" else "PM"}"
    } else "--"

    private fun t(v: Double) = "${v.roundToInt()}°"

    private fun render(r: WeatherReport) {
        root.background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, WeatherStyle.sky(r.code, r.isDay))
        body.removeAllViews()
        val today = r.todayPoint

        // Hero
        body.addView(TextView(this).apply {
            text = placeName.ifBlank { "Current location" }; textSize = 30f; setTextColor(white)
            gravity = Gravity.CENTER; setPadding(0, dp(6), 0, 0)
        })
        body.addView(Ui.iconView(this, WeatherStyle.icon(r.code, r.isDay)), LinearLayout.LayoutParams(dp(92), dp(92)).apply {
            gravity = Gravity.CENTER_HORIZONTAL; topMargin = dp(6)
        })
        body.addView(TextView(this).apply {
            text = t(r.temp); textSize = 88f; setTextColor(white); gravity = Gravity.CENTER
            typeface = Typeface.create("sans-serif-thin", Typeface.NORMAL); includeFontPadding = false
            setShadowLayer(8f, 0f, 2f, 0x33000000)
        })
        body.addView(TextView(this).apply {
            text = WeatherStyle.label(r.code); textSize = 21f; setTextColor(soft); gravity = Gravity.CENTER
        })
        if (today != null) body.addView(TextView(this).apply {
            text = "H:${t(today.max)}   L:${t(today.min)}"; textSize = 16f; setTextColor(soft); gravity = Gravity.CENTER
            setPadding(0, dp(2), 0, dp(18))
        })

        // Hourly
        val hourly = glass()
        hourly.addView(cardTitle(R.drawable.ic_sun, "Hourly forecast"))
        val hs = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val hr = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        r.hourly.forEachIndexed { i, h ->
            val col = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
                setPadding(dp(10), dp(4), dp(10), dp(4))
            }
            col.addView(TextView(this).apply { text = if (i == 0) "Now" else hour12(h.time); textSize = 13f; setTextColor(soft); setTypeface(typeface, if (i == 0) Typeface.BOLD else Typeface.NORMAL) })
            col.addView(Ui.iconView(this, WeatherStyle.icon(h.code, h.isDay)), LinearLayout.LayoutParams(dp(34), dp(34)).apply { topMargin = dp(6); bottomMargin = dp(2) })
            col.addView(TextView(this).apply { text = if (h.rainProb >= 20) "${h.rainProb}%" else " "; textSize = 11.5f; setTextColor(0xFF9AD4FF.toInt()) })
            col.addView(TextView(this).apply { text = t(h.temp); textSize = 17f; setTextColor(white); setTypeface(typeface, Typeface.BOLD) })
            hr.addView(col)
        }
        hs.addView(hr)
        hourly.addView(hs)
        body.addView(hourly, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })

        // Daily (tabs: next 7 days / past 7 days)
        val next = r.daily.filter { it.date >= r.today }
        val past = r.daily.filter { it.date < r.today }
        val daily = glass()
        daily.addView(cardTitle(R.drawable.ic_explore, "Daily forecast"))
        val tabs = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, 0, 0, dp(6)) }
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val tabViews = mutableListOf<TextView>()
        fun showDays(idx: Int) {
            tabViews.forEachIndexed { i, tv ->
                tv.setTextColor(if (i == idx) Color.BLACK else white)
                tv.background = Ui.shape(this, if (i == idx) 0xDDFFFFFF.toInt() else 0x22FFFFFF, 18)
            }
            list.removeAllViews()
            val items = if (idx == 0) next else past
            val gMin = items.minOfOrNull { it.min } ?: 0.0
            val gMax = items.maxOfOrNull { it.max } ?: 1.0
            items.forEach { list.addView(dayRow(it, r.today, gMin, gMax, idx == 1)) }
            if (items.isEmpty()) list.addView(msg("No data"))
        }
        listOf("Next 7 days", "Past 7 days").forEachIndexed { i, label ->
            val tv = TextView(this).apply {
                text = label; textSize = 13f; setTypeface(typeface, Typeface.BOLD)
                gravity = Gravity.CENTER; minHeight = dp(38); setPadding(dp(16), 0, dp(16), 0)
                setOnClickListener { showDays(i) }
            }
            tabViews.add(tv)
            tabs.addView(tv, LinearLayout.LayoutParams(-2, -2).apply { rightMargin = dp(8) })
        }
        daily.addView(tabs)
        daily.addView(list)
        showDays(0)
        body.addView(daily, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })

        // Detail tiles
        val tiles = listOf(
            Tile(R.drawable.ic_sun, "Feels like", t(r.feelsLike), when {
                r.feelsLike > r.temp + 1 -> "Humidity makes it feel warmer"
                r.feelsLike < r.temp - 1 -> "Wind makes it feel cooler"
                else -> "Similar to the actual temperature"
            }),
            Tile(R.drawable.ic_water_drop, "Humidity", "${r.humidity}%", when {
                r.humidity < 30 -> "Dry air"; r.humidity <= 60 -> "Comfortable"; else -> "Humid"
            }),
            Tile(R.drawable.ic_air, "Wind", "${r.windKmh.roundToInt()} km/h", "From ${WeatherStyle.compass(r.windDir)} · gusts ${r.gustKmh.roundToInt()} km/h"),
            Tile(R.drawable.ic_sun, "UV index", (today?.uv ?: 0.0).roundToInt().toString(), WeatherStyle.uvLabel(today?.uv ?: 0.0)),
            Tile(R.drawable.ic_visibility, "Visibility", "${r.visibilityKm.roundToInt()} km", if (r.visibilityKm >= 10) "Clear view" else if (r.visibilityKm >= 4) "Slightly reduced" else "Poor visibility"),
            Tile(R.drawable.ic_explore, "Pressure", "${r.pressure.roundToInt()} hPa", "Sea level · cloud cover ${r.cloud}%"),
            Tile(R.drawable.ic_sun, "Sunrise", clock(today?.sunrise.orEmpty()), "Sunset ${clock(today?.sunset.orEmpty())}"),
            Tile(R.drawable.ic_w_rain, "Rain today", "${today?.rainProb ?: 0}%", "${String.format(Locale.US, "%.1f", today?.rainMm ?: 0.0)} mm expected")
        )
        tiles.chunked(2).forEach { pair ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            pair.forEachIndexed { i, tile ->
                row.addView(tileView(tile), LinearLayout.LayoutParams(0, -1, 1f).apply { if (i == 0) rightMargin = dp(6) else leftMargin = dp(6) })
            }
            body.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
        }
        body.addView(TextView(this).apply {
            text = "Weather data by Open-Meteo. Past days are model-based estimates, not station readings. Forecasts can change."
            textSize = 11.5f; setTextColor(faint); gravity = Gravity.CENTER; setPadding(dp(10), dp(6), dp(10), 0)
        })
    }

    private data class Tile(val icon: Int, val title: String, val value: String, val sub: String)

    private fun tileView(t: Tile): View = glass().apply {
        addView(cardTitle(t.icon, t.title))
        addView(TextView(this@WeatherActivity).apply { text = t.value; textSize = 28f; setTextColor(white) })
        addView(TextView(this@WeatherActivity).apply { text = t.sub; textSize = 12.5f; setTextColor(soft); setPadding(0, dp(4), 0, 0) })
        minimumHeight = dp(132)
    }

    private fun dayRow(d: DayPoint, today: String, gMin: Double, gMax: Double, dim: Boolean): View {
        val name = when (d.date) {
            today -> "Today"
            else -> runCatching { dayName.format(isoDay.parse(d.date)!!) }.getOrDefault(d.date)
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            alpha = if (dim) 0.8f else 1f
            minimumHeight = dp(52)
            addView(TextView(context).apply { text = name; textSize = 16f; setTextColor(white); setTypeface(typeface, if (d.date == today) Typeface.BOLD else Typeface.NORMAL) }, LinearLayout.LayoutParams(dp(60), -2))
            addView(Ui.iconView(context, WeatherStyle.icon(d.code, true)), LinearLayout.LayoutParams(dp(30), dp(30)))
            addView(TextView(context).apply {
                text = if (d.rainProb >= 20) "${d.rainProb}%" else ""; textSize = 12f; setTextColor(0xFF9AD4FF.toInt()); gravity = Gravity.CENTER
            }, LinearLayout.LayoutParams(dp(42), -2))
            addView(TextView(context).apply { text = t(d.min); textSize = 16f; setTextColor(soft); gravity = Gravity.END }, LinearLayout.LayoutParams(dp(38), -2))
            addView(RangeBarView(context, d.min, d.max, gMin, gMax), LinearLayout.LayoutParams(0, dp(6), 1f).apply { leftMargin = dp(8); rightMargin = dp(8) })
            addView(TextView(context).apply { text = t(d.max); textSize = 16f; setTextColor(white); setTypeface(typeface, Typeface.BOLD) }, LinearLayout.LayoutParams(dp(38), -2))
        }
    }

    private class RangeBarView(
        ctx: Context, private val lo: Double, private val hi: Double,
        private val gMin: Double, private val gMax: Double
    ) : View(ctx) {
        private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x33FFFFFF }
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        override fun onDraw(canvas: Canvas) {
            val w = width.toFloat()
            val h = height.toFloat()
            canvas.drawRoundRect(0f, 0f, w, h, h / 2, h / 2, track)
            val span = (gMax - gMin).coerceAtLeast(1.0)
            val x1 = (((lo - gMin) / span) * w).toFloat().coerceIn(0f, w - h)
            val x2 = (((hi - gMin) / span) * w).toFloat().coerceIn(x1 + h, w)
            fill.shader = LinearGradient(x1, 0f, x2, 0f, 0xFF7FD6FF.toInt(), 0xFFFFB347.toInt(), Shader.TileMode.CLAMP)
            canvas.drawRoundRect(x1, 0f, x2, h, h / 2, h / 2, fill)
        }
    }

    companion object {
        fun open(ctx: Context, lat: Double, lon: Double, name: String) {
            ctx.startActivity(Intent(ctx, WeatherActivity::class.java).putExtra("lat", lat).putExtra("lon", lon).putExtra("name", name))
        }
    }
}
