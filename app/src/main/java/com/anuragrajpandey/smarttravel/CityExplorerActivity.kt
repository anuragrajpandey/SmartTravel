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
    private lateinit var suggest: SuggestField
    private lateinit var progress: ProgressBar
    private lateinit var body: LinearLayout
    private lateinit var status: TextView
    private var searchGeneration = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildScreen()
        val initial = intent.getStringExtra("city_query").orEmpty()
        suggest.setText(initial)
        if (intent.hasExtra("lat") && intent.hasExtra("lon")) {
            // Opened from "Areas near you": use the exact coordinates instead of re-geocoding the name.
            val preset = CityLocation(
                initial.ifBlank { "This area" }, intent.getStringExtra("region").orEmpty(),
                intent.getStringExtra("country").orEmpty(), intent.getDoubleExtra("lat", 0.0), intent.getDoubleExtra("lon", 0.0)
            )
            search(initial, preset)
        } else if (initial.isNotBlank()) search(initial)
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
        root.addView(Ui.topBar(this, "Area guide"))

        val searchPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = panel()
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }
        suggest = SuggestField(this, "Search any place in India", { search(it.name, it.toCity()) }, { search(it) })
        searchPanel.addView(suggest.view, LinearLayout.LayoutParams(-1, -2))
        val go = Ui.button(this, "Explore destination & photos", R.drawable.ic_search, Color.WHITE, blue, 12) { search(suggest.text()) }
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
        Ui.edgeToEdge(this, root)
    }

    private fun search(value: String, preset: CityLocation? = null) {
        val city = value.trim()
        if (city.isBlank() && preset == null) {
            suggest.input.error = "Enter a place in India"
            return
        }
        val gen = ++searchGeneration
        progress.visibility = View.VISIBLE
        status.text = "Finding $city, fetching live photos and most visited places…"
        body.removeAllViews()
        body.addView(text("Please wait while FlipDoor loads photos, landmarks, weather, stays and local food.", 14, muted))
        thread {
            try {
                val guide = if (preset != null) CityDataRepository.searchCity(preset) else CityDataRepository.searchCity(city)
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
                    body.addView(button("Try again") { search(city, preset) })
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
        card(g.city.name, location.ifBlank { "Destination guide" })

        val planCityBtn = Ui.button(this, "Plan a trip to ${g.city.name}", R.drawable.ic_flight, ink, gold, 12) { openTripPlanner(g.city.name) }
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
            section("Hidden gems in ${g.city.name}", "Lesser-known viewpoints, nature & offbeat spots · Tap to explore")
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
            body.addView(Ui.button(this, "Full details about ${g.city.name}", R.drawable.ic_explore, blue, 0xFFEAF3FF.toInt(), 12) {
                ArticleActivity.open(this, g.city.name, ref = it.url)
            })
        }

        section("Weather", "Live conditions, hourly and 15-day view")
        val w = g.weather
        val sky = WeatherStyle.sky(0, true)
        val wCard = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR, sky).apply { cornerRadius = dp(22).toFloat() }
            setPadding(dp(18), dp(16), dp(18), dp(16))
            isClickable = true
            setOnClickListener { WeatherActivity.open(this@CityExplorerActivity, g.city.latitude, g.city.longitude, g.city.name) }
        }
        val wText = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        wText.addView(text(if (w == null) "Weather" else "${w.temperature.toInt()}°", 40, Color.WHITE, true))
        wText.addView(text(w?.description ?: "Tap to load the forecast", 14, Color.WHITE))
        if (w != null) wText.addView(text("Feels like ${w.feelsLike.toInt()}° · Humidity ${w.humidity}% · Wind ${w.wind.toInt()} km/h", 12, 0xCCFFFFFF.toInt()))
        wCard.addView(wText, LinearLayout.LayoutParams(0, -2, 1f))
        wCard.addView(Ui.iconView(this, R.drawable.ic_w_partly), LinearLayout.LayoutParams(dp(64), dp(64)))
        body.addView(wCard, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(6) })
        body.addView(Ui.button(this, "Open full weather", R.drawable.ic_arrow_forward, blue, 0xFFEAF3FF.toInt(), 12) {
            WeatherActivity.open(this, g.city.latitude, g.city.longitude, g.city.name)
        })

        section("Travel budget", "Indicative planning ranges per person, per day. Not live prices or a verified city average.")
        card("Budget traveller", "₹1,000–₹1,800 / day · basic stay, local food and public/local transport")
        card("Comfort traveller", "₹2,000–₹3,800 / day · mid-range stay, meals and local experiences")
        card("Premium traveller", "₹4,500–₹8,000+ / day · higher-end stays, dining and private transport")
        card("Make it your own", "Use the Trip Planner to enter actual stay, food, transport and activity costs.")

        section("Places to stay", "Listings mapped by OpenStreetMap contributors. Availability and prices are not verified.")
        body.addView(Ui.button(this, "Best hotels & prices in ${g.city.name}", R.drawable.ic_hotel, Color.WHITE, blue, 12) {
            HotelsActivity.open(this, g.city.latitude, g.city.longitude, g.city.name, g.city.country)
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6); bottomMargin = dp(10) })
        if (g.hotels.isEmpty()) card("No mapped stays found", "Try a nearby city or search again later.")
        g.hotels.forEach { placeCard(it) }

        section("Food & cafés", "Nearby mapped restaurants and cafés")
        if (g.restaurants.isEmpty()) card("No mapped food places found", "OpenStreetMap may have limited coverage here.")
        g.restaurants.forEach { placeCard(it) }

        section("Things to do", "Nearby attractions, monuments and museums")
        if (g.attractions.isEmpty()) card("No mapped attractions found", "Explore the city overview and most visited slides above.")
        g.attractions.forEach { placeCard(it) }

        val foodRegion = g.regionFoods.region.ifBlank { g.city.region.ifBlank { g.city.country } }
        section("Famous food of ${foodRegion.ifBlank { g.city.name }}", "Traditional dishes this region is known for · tap any dish for details")
        if (g.regionFoods.items.isNotEmpty()) {
            g.regionFoods.items.forEach { foodCard(it) }
        } else if (g.foods.isNotEmpty()) {
            g.foods.forEach { item ->
                card(item.title, item.description.ifBlank { item.excerpt })
                body.addView(Ui.button(this, "Details: ${item.title}", R.drawable.ic_restaurant, blue, 0xFFEAF3FF.toInt(), 12) {
                    ArticleActivity.open(this, item.title, ref = item.url)
                })
            }
        } else card("No food information found", "Try searching the larger nearby city or state.")
        card("Data & attribution", "Weather: Open-Meteo. Places and hotels: © OpenStreetMap contributors (ODbL). Maps: Google Maps. Photos, dishes and guides: Wikipedia / Wikimedia Commons contributors (CC BY-SA). Hotel prices are estimates unless a live price is opened. Check details before travelling.")
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
        actions.addView(Ui.button(this, "Map & directions", R.drawable.ic_directions, blue, 0xFFEAF3FF.toInt(), 12) {
            MapUi.openMap(this, place.latitude, place.longitude, place.name)
        }, LinearLayout.LayoutParams(0, -2, 1f).apply { rightMargin = dp(6) })
        if (place.website != null) {
            actions.addView(Ui.button(this, "Website", R.drawable.ic_public, blue, 0xFFEAF3FF.toInt(), 12) {
                open(place.website, place.name)
            }, LinearLayout.LayoutParams(0, -2, 1f))
        }
        box.addView(actions)
        body.addView(box, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(9) })
    }

    private fun foodCard(item: FoodItem) {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = panel()
            setPadding(dp(12), dp(12), dp(12), dp(12))
            isClickable = true
            setOnClickListener { ArticleActivity.open(this@CityExplorerActivity, item.name, ref = item.wikiTitle, image = item.imageUrl) }
        }
        val img = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = panel(0xFFFFF2E6.toInt(), 14)
            clipToOutline = true
        }
        if (item.imageUrl != null) ImageLoader.load(item.imageUrl, img) else img.setImageResource(R.drawable.ic_restaurant)
        if (item.imageUrl == null) img.setPadding(dp(22), dp(22), dp(22), dp(22))
        box.addView(img, LinearLayout.LayoutParams(dp(84), dp(84)).apply { rightMargin = dp(12) })
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(text(item.name, 16, ink, true))
        col.addView(text(item.description, 12, muted).apply { maxLines = 3; ellipsize = android.text.TextUtils.TruncateAt.END })
        box.addView(col, LinearLayout.LayoutParams(0, -2, 1f))
        body.addView(box, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(9) })
    }

    private fun open(url: String, title: String = "") {
        WebPageActivity.open(this, url, title)
    }
}
