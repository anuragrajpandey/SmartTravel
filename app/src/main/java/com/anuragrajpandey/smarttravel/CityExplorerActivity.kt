package com.anuragrajpandey.smarttravel

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.NestedScrollView
import com.google.android.material.card.MaterialCardView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread

class CityExplorerActivity : AppCompatActivity() {
    private val blue = Color.rgb(0, 108, 228)
    private val gold = Color.rgb(255, 183, 0)
    private val ink = Color.rgb(23, 43, 77)
    private val muted = Color.rgb(105, 119, 140)
    private val bg = Color.rgb(245, 247, 251)
    private lateinit var query: EditText
    private lateinit var progress: ProgressBar
    private lateinit var body: LinearLayout
    private lateinit var status: TextView
    private var searchGeneration = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = Color.WHITE
        buildScreen()
        val initial = intent.getStringExtra("city_query").orEmpty()
        query.setText(initial)
        if (initial.isNotBlank()) search(initial)
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun panel(color: Int = Color.WHITE, r: Int = 18) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(r).toFloat()
    }

    private fun text(value: String, size: Int = 14, color: Int = ink, bold: Boolean = false) = TextView(this).apply {
        text = value
        textSize = size.toFloat()
        setTextColor(color)
        if (bold) setTypeface(typeface, Typeface.BOLD)
        setPadding(0, dp(3), 0, dp(5))
    }

    private fun buildScreen() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
        }
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(8))
        }
        val back = TextView(this).apply {
            text = "‹"
            textSize = 34f
            setTextColor(ink)
            gravity = Gravity.CENTER
            setOnClickListener { finish() }
        }
        top.addView(back, LinearLayout.LayoutParams(dp(42), dp(44)))
        top.addView(text("City guide", 20, ink, true), LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(top)

        val searchPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = panel()
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }
        query = EditText(this).apply {
            hint = "Search any city or place, e.g. Manali, Paris"
            setSingleLine(true)
            textSize = 15f
            setTextColor(ink)
            setHintTextColor(muted)
            background = panel(bg, 12)
            setPadding(dp(13), dp(12), dp(13), dp(12))
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                    search(text.toString())
                    true
                } else {
                    false
                }
            }
        }
        searchPanel.addView(query, LinearLayout.LayoutParams(-1, dp(50)))
        val go = TextView(this).apply {
            text = "Explore destination & photos"
            gravity = Gravity.CENTER
            textSize = 14f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
            background = panel(blue, 12)
            setPadding(0, dp(13), 0, dp(13))
            setOnClickListener { search(query.text.toString()) }
        }
        searchPanel.addView(go, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(9) })
        root.addView(searchPanel, LinearLayout.LayoutParams(-1, -2).apply {
            setMargins(dp(16), dp(5), dp(16), dp(8))
        })

        progress = ProgressBar(this, null, android.R.attr.progressBarStyleLarge).apply {
            isIndeterminate = true
            visibility = View.GONE
        }
        root.addView(progress, LinearLayout.LayoutParams(dp(48), dp(48)).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            topMargin = dp(8)
            bottomMargin = dp(8)
        })
        status = text("Search any location to auto-fetch photos, most visited places & weather.", 13, muted)
        status.setPadding(dp(18), dp(9), dp(18), dp(8))
        root.addView(status)

        val scroll = NestedScrollView(this)
        body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(4), dp(16), dp(28))
        }
        scroll.addView(body)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }

    private fun search(value: String) {
        val city = value.trim()
        if (city.isBlank()) {
            query.error = "Enter a city name"
            return
        }
        val gen = ++searchGeneration
        progress.visibility = View.VISIBLE
        status.text = "Finding $city, fetching live photos and most visited places…"
        body.removeAllViews()
        body.addView(text("Please wait while FlipDoor loads photos, landmarks, weather, stays and local food.", 14, muted))
        thread {
            try {
                val guide = CityDataRepository.searchCity(city)
                runOnUiThread {
                    if (isFinishing || isDestroyed || gen != searchGeneration) return@runOnUiThread
                    progress.visibility = View.GONE
                    status.text = "Updated just now · Live photos & data from online sources"
                    render(guide)
                }
            } catch (e: Exception) {
                runOnUiThread {
                    if (isFinishing || isDestroyed || gen != searchGeneration) return@runOnUiThread
                    progress.visibility = View.GONE
                    status.text = "We couldn't load this location."
                    body.removeAllViews()
                    card("Search error", e.message ?: "Please check your connection and try again.")
                    body.addView(button("Try again") { search(city) })
                }
            }
        }
    }

    private fun card(title: String, description: String) {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = panel()
            setPadding(dp(15), dp(13), dp(15), dp(12))
        }
        box.addView(text(title, 16, ink, true))
        box.addView(text(description, 13, muted))
        body.addView(box, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
    }

    private fun button(label: String, action: () -> Unit) = TextView(this).apply {
        text = label
        gravity = Gravity.CENTER
        textSize = 14f
        setTypeface(typeface, Typeface.BOLD)
        setTextColor(blue)
        background = panel(0xFFEAF3FF.toInt(), 12)
        setPadding(dp(12), dp(12), dp(12), dp(12))
        setOnClickListener { action() }
    }

    private fun section(title: String, subtitle: String = "") {
        body.addView(text(title, 19, ink, true), LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(17)
            bottomMargin = dp(2)
        })
        if (subtitle.isNotBlank()) body.addView(text(subtitle, 12, muted))
    }

    private fun openPlaceDetail(place: MostVisitedPlace) {
        startActivity(Intent(this, PlaceDetailActivity::class.java).putExtra("place", place))
    }

    private fun openTripPlanner(destinationName: String) {
        startActivity(
            Intent(this, TripPlannerActivity::class.java)
                .putExtra("destination", destinationName)
                .putExtra("trip_name", "Trip to $destinationName")
        )
    }

    private fun render(g: CityGuide) {
        body.removeAllViews()
        val location = listOf(g.city.region, g.city.country).filter { it.isNotBlank() }.joinToString(" · ")
        card("✈  ${g.city.name}", location.ifBlank { "Destination guide" })

        val planCityBtn = TextView(this).apply {
            text = "✈  Plan a trip to ${g.city.name}"
            gravity = Gravity.CENTER
            textSize = 14f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(ink)
            background = panel(gold, 12)
            setPadding(dp(14), dp(13), dp(14), dp(13))
            setOnClickListener { openTripPlanner(g.city.name) }
        }
        body.addView(planCityBtn, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })

        if (g.mostVisited.isNotEmpty()) {
            section("Most visited places in ${g.city.name}", "Swipe big slides · Tap any photo to view details & plan")
            val carousel = PlacesCarouselView(
                context = this,
                onPlaceClick = { openPlaceDetail(it) },
                onPlanClick = { openTripPlanner(it.name) }
            )
            carousel.submitPlaces(g.mostVisited)
            body.addView(carousel.container, LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = dp(8)
                bottomMargin = dp(6)
            })
        }

        if (g.photos.isNotEmpty()) {
            section("Photos of ${g.city.name}", "Tap any photo to open full view & plan")
            val photoScroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            val cityPlaceObj = MostVisitedPlace(
                name = g.city.name,
                location = location.ifBlank { g.city.name },
                category = "Photo Gallery",
                summary = g.overview?.excerpt?.ifBlank { g.overview.description }.orEmpty()
                    .ifBlank { "Photos and highlights from ${g.city.name}." },
                imageUrl = g.photos.firstOrNull(),
                galleryUrls = g.photos,
                wikiUrl = g.overview?.url,
                latitude = g.city.latitude,
                longitude = g.city.longitude,
                cityQuery = g.city.name
            )
            g.photos.forEachIndexed { idx, url ->
                val photoCard = MaterialCardView(this).apply {
                    radius = dp(14).toFloat()
                    cardElevation = dp(2).toFloat()
                    setCardBackgroundColor(0xFFE5EAF2.toInt())
                    setOnClickListener {
                        val reordered = listOf(url) + g.photos.filterIndexed { i, _ -> i != idx }
                        openPlaceDetail(cityPlaceObj.copy(imageUrl = url, galleryUrls = reordered))
                    }
                }
                val img = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
                ImageLoader.load(url, img)
                photoCard.addView(img, LinearLayout.LayoutParams(dp(144), dp(98)))
                row.addView(photoCard, LinearLayout.LayoutParams(-2, -2).apply { rightMargin = dp(10) })
            }
            photoScroll.addView(row)
            body.addView(photoScroll, LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = dp(6)
                bottomMargin = dp(4)
            })
        }

        if (g.hiddenGems.isNotEmpty()) {
            section("💎 Hidden gems in ${g.city.name}", "Lesser-known viewpoints, nature & offbeat spots · Tap to explore")
            val gemScroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
            val gemRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            g.hiddenGems.forEachIndexed { idx, gem ->
                gemRow.addView(
                    HiddenGemCardsHelper.createCard(
                        context = this,
                        place = gem,
                        index = idx,
                        fullWidth = false,
                        onPlaceClick = { openPlaceDetail(it) },
                        onPlanClick = { openTripPlanner(it.name) }
                    )
                )
            }
            gemScroll.addView(gemRow)
            body.addView(gemScroll, LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = dp(6)
                bottomMargin = dp(6)
            })
        }

        g.overview?.let {
            card("About ${g.city.name}", it.excerpt.ifBlank { it.description }.ifBlank { "Read the destination overview online." })
            body.addView(button("Read destination guide") { open(it.url) })
        }

        section("Weather calendar", "Seven days of recent weather and seven days ahead")
        val w = g.weather
        if (w == null) {
            card("Weather unavailable", "The weather service did not respond. Other city information is still available.")
        } else {
            card("${w.temperature.toInt()}°C  ·  ${w.description}", "Feels like ${w.feelsLike.toInt()}°C  ·  Humidity ${w.humidity}%  ·  Wind ${w.wind.toInt()} km/h")
            val iso = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val today = iso.format(Date())
            val rows = w.forecast.mapNotNull { raw ->
                val p = raw.split("|")
                if (p.size < 6) null else Pair(p[0], p)
            }
            val past = rows.filter { it.first < today }
            val current = rows.filter { it.first == today }
            val future = rows.filter { it.first > today }
            fun weatherRows(label: String, items: List<Pair<String, List<String>>>) {
                if (items.isEmpty()) return
                section(label)
                items.forEach { (date, p) ->
                    val day = runCatching { SimpleDateFormat("EEE, dd MMM", Locale.US).format(iso.parse(date)!!) }.getOrDefault(date)
                    val details = p[5] + "  ·  Rain " + p[3] + "% (" + p[4] + " mm)"
                    card(day, p[1] + "° to " + p[2] + "°  ·  " + details)
                }
            }
            weatherRows("Past 7 days", past)
            weatherRows("Today", current)
            weatherRows("Next 7 days", future)
            card("Weather data", "Past days are model-based historical estimates, not readings from a local weather station. Forecasts can change.")
        }

        section("Travel budget", "Indicative planning ranges per person, per day. Not live prices or a verified city average.")
        card("Budget traveller", "₹1,000–₹1,800 / day · basic stay, local food and public/local transport")
        card("Comfort traveller", "₹2,000–₹3,800 / day · mid-range stay, meals and local experiences")
        card("Premium traveller", "₹4,500–₹8,000+ / day · higher-end stays, dining and private transport")
        card("Make it your own", "Use the Trip Planner to enter actual stay, food, transport and activity costs.")

        section("Places to stay", "Listings mapped by OpenStreetMap contributors. Availability and prices are not verified.")
        if (g.hotels.isEmpty()) card("No mapped stays found", "Try a nearby city or search again later.")
        g.hotels.forEach { placeCard(it) }

        section("Food & cafés", "Nearby mapped restaurants and cafés")
        if (g.restaurants.isEmpty()) card("No mapped food places found", "OpenStreetMap may have limited coverage here.")
        g.restaurants.forEach { placeCard(it) }

        section("Things to do", "Nearby attractions, monuments and museums")
        if (g.attractions.isEmpty()) card("No mapped attractions found", "Explore the city overview and most visited slides above.")
        g.attractions.forEach { placeCard(it) }

        section("Local food to discover", "Wikimedia search results. These are research links, not popularity rankings.")
        if (g.foods.isEmpty()) card("No food articles found", "Try searching the city name with a regional cuisine term.")
        g.foods.forEach { item ->
            card(item.title, item.description.ifBlank { item.excerpt })
            body.addView(button("Read about ${item.title}") { open(item.url) })
        }
        card("Data & attribution", "Weather: Open-Meteo. Places: © OpenStreetMap contributors (ODbL). Destination photos & guides: Wikipedia / Wikimedia Commons contributors (CC BY-SA). Check details before travelling.")
    }

    private fun placeCard(place: CityPlace) {
        val detail = listOf(place.type, place.detail).filter { it.isNotBlank() }.joinToString(" · ")
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = panel()
            setPadding(dp(14), dp(12), dp(14), dp(10))
        }
        box.addView(text(place.name, 15, ink, true))
        box.addView(text(detail, 12, muted))
        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        actions.addView(button("Directions") {
            open("https://www.openstreetmap.org/?mlat=${place.latitude}&mlon=${place.longitude}#map=17/${place.latitude}/${place.longitude}")
        })
        if (place.website != null) {
            actions.addView(button("Website") { open(place.website) }.apply {
                setPadding(dp(12), dp(12), dp(12), dp(12))
            })
        }
        box.addView(actions)
        body.addView(box, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(9) })
    }

    private fun open(url: String) {
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }
}
