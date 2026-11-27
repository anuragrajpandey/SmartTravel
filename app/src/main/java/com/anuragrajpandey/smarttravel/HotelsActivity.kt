package com.anuragrajpandey.smarttravel

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.net.URLEncoder
import kotlin.concurrent.thread

/** Best hotels around the user (or any searched city) with distance, stars, amenities and pricing. */
class HotelsActivity : AppCompatActivity() {
    private lateinit var body: LinearLayout
    private lateinit var suggest: SuggestField
    private var lat = Double.NaN
    private var lon = Double.NaN
    private var label = ""
    private var country = ""
    private var countryCode = ""
    private var hotels: List<Hotel> = emptyList()
    private var sort = 0
    private var gen = 0

    private fun dp(v: Int) = Ui.dp(this, v)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val saved = LocationHelper.saved(this)
        lat = intent.getDoubleExtra("lat", saved?.lat ?: Double.NaN)
        lon = intent.getDoubleExtra("lon", saved?.lon ?: Double.NaN)
        label = intent.getStringExtra("label") ?: saved?.name.orEmpty()
        country = intent.getStringExtra("country") ?: saved?.country.orEmpty()
        countryCode = saved?.countryCode.orEmpty().takeIf { !intent.hasExtra("lat") } ?: ""

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Ui.BG) }
        root.addView(Ui.topBar(this, "Hotels nearby"))

        val searchRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(2), dp(16), dp(8))
        }
        suggest = SuggestField(this, "Search hotels in another place in India", { pickPlace(it) }, { searchCity(it) })
        searchRow.addView(suggest.view, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(searchRow)

        val scroll = ScrollView(this).apply { isVerticalScrollBarEnabled = false }
        body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(4), dp(16), dp(28)) }
        scroll.addView(body)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        Ui.edgeToEdge(this, root)

        if (lat.isNaN() || lon.isNaN()) {
            body.addView(note("Search a city above, or allow location on the home screen to see hotels around you."))
        } else load()
    }

    private fun note(s: String) = Ui.text(this, s, 14f, Ui.MUTED).apply { setPadding(dp(4), dp(24), dp(4), dp(8)) }

    private fun pickPlace(sug: Suggestion) {
        lat = sug.lat; lon = sug.lon; label = sug.name; country = "India"; countryCode = "IN"
        load()
    }

    private fun searchCity(q: String) {
        if (q.isBlank()) { suggest.input.error = "Enter a place in India"; return }
        body.removeAllViews()
        body.addView(ProgressBar(this), LinearLayout.LayoutParams(-1, dp(70)).apply { topMargin = dp(60) })
        Net.pool.execute {
            try {
                val c = CityDataRepository.geocodeCity(q)
                runOnUiThread { lat = c.latitude; lon = c.longitude; label = c.name; country = "India"; countryCode = "IN"; load() }
            } catch (e: Exception) {
                runOnUiThread { body.removeAllViews(); body.addView(note(e.message ?: "Place not found")) }
            }
        }
    }

    private fun load() {
        val g = ++gen
        body.removeAllViews()
        body.addView(ProgressBar(this), LinearLayout.LayoutParams(-1, dp(70)).apply { topMargin = dp(60) })
        body.addView(Ui.text(this, "Finding the best stays around ${label.ifBlank { "you" }}…", 13f, Ui.MUTED).apply { gravity = Gravity.CENTER })
        thread {
            try {
                if (countryCode.isBlank() && country.isBlank()) {
                    val rr = GeoRepository.reverseGeocode(lat, lon)
                    countryCode = rr.countryCode; country = rr.city.country
                    if (label.isBlank()) label = rr.city.name
                }
                val inIndia = countryCode == "IN" || country.equals("India", true)
                val list = HotelRepository.search(lat, lon, inIndia)
                runOnUiThread { if (!isFinishing && !isDestroyed && g == gen) { hotels = list; render() } }
            } catch (e: Exception) {
                runOnUiThread {
                    if (isFinishing || isDestroyed || g != gen) return@runOnUiThread
                    body.removeAllViews()
                    body.addView(note("Couldn't load hotels right now (${e.message ?: "network error"}). The open map service can be busy — try again."))
                    body.addView(Ui.button(this, "Try again", R.drawable.ic_search) { load() })
                }
            }
        }
    }

    private fun sorted(): List<Hotel> = when (sort) {
        1 -> hotels.sortedBy { it.priceMid }
        2 -> hotels.sortedBy { it.distanceKm }
        else -> hotels.sortedByDescending { it.score }
    }

    private fun render() {
        body.removeAllViews()
        val where = label.ifBlank { "your location" }
        body.addView(Ui.text(this, "${hotels.size} stays near $where", 20f, Ui.FG, true))
        body.addView(Ui.text(this, "Real listings from OpenStreetMap within 6 km, ranked by rating, details and distance.", 12.5f, Ui.MUTED))

        body.addView(MapUi.inlineMap(this, lat, lon, "search", "hotels", 220) { MapUi.openMap(this, lat, lon, "Hotels near $where", "hotels") },
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8); bottomMargin = dp(12) })

        body.addView(Ui.button(this, "See live prices in $where", R.drawable.ic_wallet) {
            WebPageActivity.open(this, "https://www.google.com/travel/search?q=${enc("hotels in $where")}&hl=en", "Live hotel prices")
        }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })

        val priceNote = Ui.card(this, 0xFFFFF8E5.toInt(), 14)
        priceNote.addView(Ui.text(this, "About prices", 13f, Ui.FG, true))
        priceNote.addView(Ui.text(this, "OpenStreetMap lists hotels but rarely room rates. Ranges below are estimates for planning based on star rating and type. Tap “Live price” on any hotel for today's actual rates, shown inside the app.", 12f, Ui.MUTED))
        body.addView(priceNote, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })

        val chipsRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val chips = mutableListOf<LinearLayout>()
        val labels = listOf("Best match", "Lowest price", "Nearest")
        val listBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        fun fill() {
            listBox.removeAllViews()
            if (hotels.isEmpty()) {
                listBox.addView(note("No hotels are mapped here yet. Try a nearby larger city, or use the live price button above."))
            } else sorted().take(40).forEach { listBox.addView(hotelCard(it)) }
        }
        labels.forEachIndexed { i, l ->
            val c = Ui.chip(this, l, i == sort, if (i == 0) R.drawable.ic_star else if (i == 1) R.drawable.ic_wallet else R.drawable.ic_place) {
                sort = i
                chips.forEachIndexed { j, cc -> Ui.restyleChip(cc, j == sort, if (j == 0) R.drawable.ic_star else if (j == 1) R.drawable.ic_wallet else R.drawable.ic_place, labels[j]) }
                fill()
            }
            chips.add(c)
            chipsRow.addView(c, LinearLayout.LayoutParams(-2, -2).apply { rightMargin = dp(8) })
        }
        body.addView(HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(chipsRow) },
            LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
        body.addView(listBox)
        fill()
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun hotelCard(h: Hotel): View {
        val card = Ui.card(this)
        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        top.addView(Ui.text(this, h.name, 16.5f, Ui.FG, true).apply { maxLines = 2 }, LinearLayout.LayoutParams(0, -2, 1f))
        card.addView(top)

        val meta = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        if (h.stars != null) repeat(h.stars) {
            meta.addView(Ui.iconView(this, R.drawable.ic_star, Ui.GOLD), Ui.iconLp(this, 15).apply { rightMargin = dp(1) })
        }
        meta.addView(Ui.text(this, (if (h.stars != null) "  " else "") + HotelRepository.kindLabel(h.kind) + "  ·  " + Ui.dist(h.distanceKm) + " away", 12.5f, Ui.MUTED))
        card.addView(meta)
        if (h.address.isNotBlank()) card.addView(Ui.text(this, h.address, 12f, Ui.MUTED))
        if (h.amenities.isNotEmpty()) card.addView(Ui.text(this, h.amenities.joinToString("  ·  "), 12f, 0xFF2E7D32.toInt(), true))

        val priceRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(6), 0, dp(8)) }
        if (h.listedPrice != null) {
            priceRow.addView(Ui.text(this, "Listed: ${h.listedPrice}", 16f, Ui.ACCENT, true))
        } else {
            priceRow.addView(Ui.text(this, h.priceText(), 16.5f, Ui.ACCENT, true))
            priceRow.addView(Ui.text(this, " / night", 12.5f, Ui.MUTED))
            priceRow.addView(TextView(this).apply {
                text = "ESTIMATE"; textSize = 9.5f; setTextColor(0xFF8A5A00.toInt())
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                background = Ui.shape(this@HotelsActivity, 0xFFFFE9B0.toInt(), 8)
                setPadding(dp(7), dp(3), dp(7), dp(3))
            }, LinearLayout.LayoutParams(-2, -2).apply { leftMargin = dp(8) })
        }
        card.addView(priceRow)

        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val where = label.ifBlank { "" }
        actions.addView(Ui.button(this, "Live price", R.drawable.ic_wallet, Color.WHITE, Ui.ACCENT, 12) {
            WebPageActivity.open(this, "https://www.google.com/travel/search?q=${enc("${h.name} $where hotel")}&hl=en", h.name)
        }, LinearLayout.LayoutParams(0, -2, 1.3f).apply { rightMargin = dp(6) })
        actions.addView(Ui.button(this, "Map", R.drawable.ic_map, Ui.ACCENT, Ui.ACCENT_SOFT, 12) {
            MapUi.openMap(this, h.lat, h.lon, h.name)
        }, LinearLayout.LayoutParams(0, -2, 1f).apply { rightMargin = dp(6) })
        if (h.website != null) actions.addView(Ui.button(this, "Site", R.drawable.ic_public, Ui.ACCENT, Ui.ACCENT_SOFT, 12) {
            WebPageActivity.open(this, h.website, h.name)
        }, LinearLayout.LayoutParams(0, -2, 1f).apply { rightMargin = dp(6) })
        if (h.phone != null) actions.addView(Ui.iconButton(this, R.drawable.ic_phone, Ui.ACCENT, Ui.ACCENT_SOFT, 48, 22) {
            runCatching { startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + h.phone.filter { c -> c.isDigit() || c == '+' }))) }
        })
        card.addView(actions)
        card.layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) }
        return card
    }

    companion object {
        fun open(ctx: Context, lat: Double? = null, lon: Double? = null, label: String = "", country: String = "") {
            val i = Intent(ctx, HotelsActivity::class.java)
            if (lat != null && lon != null) i.putExtra("lat", lat).putExtra("lon", lon).putExtra("label", label).putExtra("country", country)
            ctx.startActivity(i)
        }
    }
}
