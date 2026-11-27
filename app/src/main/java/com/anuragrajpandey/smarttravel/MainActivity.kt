package com.anuragrajpandey.smarttravel

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.NestedScrollView
import com.google.android.material.card.MaterialCardView
import org.json.JSONArray
import java.util.Locale
import kotlin.concurrent.thread

private const val BG = 0xFFF5F7FB.toInt()
private const val PANEL = 0xFFFFFFFF.toInt()
private const val FG = 0xFF172B4D.toInt()
private const val MUTED = 0xFF718096.toInt()
private const val ACCENT = 0xFF006CE4.toInt()
private const val BORDER = 0xFFE5EAF2.toInt()

class MainActivity : AppCompatActivity() {
    private lateinit var content: LinearLayout
    private val prefs by lazy { getSharedPreferences("flipdoor", MODE_PRIVATE) }
    private var exploreGeneration = 0
    private var currentTab = "Explore"
    private val backStack = ArrayDeque<String>()

    // Where the user is. Everything on Explore/Gems is built around this.
    private var meCity: CityLocation? = null
    private var meArea: String = ""
    // Loaded content is kept so switching tabs is instant.
    private var placesCache: List<MostVisitedPlace>? = null
    private var areasCache: List<NearbyArea>? = null
    private var foodCache: FoodBundle? = null
    private var gemsCache: List<MostVisitedPlace>? = null
    private var locationState = "pending" // pending | locating | denied | unavailable | ready


    private val permLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        if (res.values.any { it }) resolveLocation() else { locationState = "denied"; refreshLocationTabs() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Back goes to the previous screen, then to Explore, and only then leaves the app.
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    backStack.isNotEmpty() -> show(backStack.removeLast())
                    currentTab != "Explore" -> show("Explore")
                    else -> { isEnabled = false; onBackPressedDispatcher.onBackPressed() }
                }
            }
        })
        show("Explore")
        if (!prefs.getBoolean("onboarding_complete", false)) showFirstRunSetup()
    }

    override fun onResume() {
        super.onResume()
        // Returning from system settings after granting permission / enabling GPS.
        if ((locationState == "denied" || locationState == "unavailable") && LocationHelper.hasPermission(this)) resolveLocation()
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    private fun showFirstRunSetup() {
        val input = EditText(this).apply { hint = "Your name"; singleLine = true }
        val wrap = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(22), dp(8), dp(22), dp(2)); addView(txt("Make every journey feel personal.", 14, MUTED)); addView(input) }
        AlertDialog.Builder(this).setTitle("Welcome to FlipDoor").setMessage("What should we call you?").setView(wrap).setCancelable(false)
            .setPositiveButton("Continue") { _, _ ->
                prefs.edit().putString("user_name", input.text.toString().trim().ifBlank { "Traveller" }).apply()
                AlertDialog.Builder(this).setTitle("Use your location?")
                    .setMessage("FlipDoor can find nearby places once to personalise your first Explore screen. You can skip this and use search-based suggestions instead.")
                    .setNegativeButton("Not now") { _, _ -> prefs.edit().putBoolean("onboarding_complete", true).apply(); show("Explore") }
                    .setPositiveButton("Use my location") { _, _ ->
                        prefs.edit().putBoolean("onboarding_complete", true).apply()
                        if (LocationHelper.hasPermission(this)) resolveLocation() else permLauncher.launch(LocationHelper.PERMISSIONS)
                    }.show()
            }.show()
    }
    private fun resolveLocation() {
        locationState = "locating"
        refreshLocationTabs()
        LocationHelper.getLocation(this) { loc ->
            if (loc == null) {
                locationState = "unavailable"
                refreshLocationTabs()
                return@getLocation
            }
            thread {
                val rr = GeoRepository.reverseGeocode(loc.latitude, loc.longitude)
                LocationHelper.save(this, loc.latitude, loc.longitude, rr.city.name, rr.city.country, rr.countryCode)
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    meCity = rr.city
                    placesCache = null; areasCache = null; foodCache = null; gemsCache = null
                    meArea = rr.area
                    locationState = "ready"
                    refreshLocationTabs()
                }
            }
        }
    }

    private fun refreshLocationTabs() {
        if (currentTab == "Explore" || currentTab == "Gems" || currentTab == "Food") show(currentTab)
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun shape(c: Int, r: Int = 16) = GradientDrawable().apply {
        setColor(c)
        cornerRadius = dp(r).toFloat()
    }

    private fun gradient(a: Int, b: Int, r: Int = 22) =
        GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(a, b)).apply {
            cornerRadius = dp(r).toFloat()
        }

    private fun txt(s: String, n: Int = 14, c: Int = FG, b: Boolean = false) = TextView(this).apply {
        text = s
        textSize = n.toFloat()
        setTextColor(c)
        if (b) setTypeface(typeface, Typeface.BOLD)
        setPadding(0, dp(3), 0, dp(5))
    }

    private fun btn(s: String, primary: Boolean = false, click: () -> Unit) =
        Ui.button(this, s, null, if (primary) Color.WHITE else ACCENT, if (primary) ACCENT else 0xFFEAF3FF.toInt(), 14, click)

    private fun heading(k: String, t: String, d: String) {
        content.addView(txt(k.uppercase(Locale.getDefault()), 11, ACCENT, true))
        content.addView(txt(t, 28, FG, true))
        content.addView(txt(d, 14, MUTED))
    }

    private fun section(s: String, sub: String = "") {
        content.addView(txt(s, 19, FG, true), LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(20)
            bottomMargin = if (sub.isEmpty()) dp(10) else 0
        })
        if (sub.isNotEmpty()) content.addView(txt(sub, 12, MUTED), LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
    }

    private fun featureCard(parent: LinearLayout, icon: Int, title: String, sub: String, color: Int, click: () -> Unit) {
        val c = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            background = Ui.ripple(this@MainActivity, color, 18)
            setPadding(dp(14), dp(14), dp(14), dp(14))
            isClickable = true
            setOnClickListener { click() }
        }
        c.addView(Ui.iconView(this, icon, ACCENT), Ui.iconLp(this, 32).apply { bottomMargin = dp(6) })
        c.addView(txt(title, 15, FG, true))
        c.addView(txt(sub, 12, MUTED))
        parent.addView(c, LinearLayout.LayoutParams(0, dp(122), 1f).apply { setMargins(dp(4), dp(4), dp(4), dp(4)) })
    }

    private fun tile(t: String, d: String, icon: Int? = null, click: (() -> Unit)? = null) {
        val c = MaterialCardView(this).apply {
            radius = dp(18).toFloat()
            cardElevation = dp(1).toFloat()
            setCardBackgroundColor(PANEL)
            strokeWidth = dp(1)
            strokeColor = BORDER
            if (click != null) setOnClickListener { click() }
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(13), dp(16), dp(11))
        }
        if (icon != null) {
            val badge = FrameLayout2.badge(this, icon)
            row.addView(badge, LinearLayout.LayoutParams(dp(44), dp(44)).apply { rightMargin = dp(14) })
        }
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        box.addView(txt(t, 16, FG, true))
        box.addView(txt(d, 13, MUTED))
        row.addView(box, LinearLayout.LayoutParams(0, -2, 1f))
        if (click != null) row.addView(Ui.iconView(this, R.drawable.ic_arrow_forward, MUTED), Ui.iconLp(this, 20))
        c.addView(row)
        content.addView(c, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
    }

    /** Opens a sub-screen that Back returns from. */
    private fun go(screen: String) {
        backStack.addLast(currentTab)
        show(screen)
    }

    private fun show(tab: String) {
        exploreGeneration++
        currentTab = tab
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(BG)
        }
        val scroll = NestedScrollView(this)
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(12), dp(20), dp(22))
        }
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        val nav = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            background = shape(Color.WHITE, 22)
            elevation = dp(8).toFloat()
            setPadding(dp(6), dp(6), dp(6), dp(6))
        }
        listOf("Explore", "Hotels", "Gems", "Tools", "Trips").forEach { n ->
            val icon = when (n) {
                "Explore" -> R.drawable.ic_home
                "Hotels" -> R.drawable.ic_hotel
                "Gems" -> R.drawable.ic_explore
                "Tools" -> R.drawable.ic_build
                else -> R.drawable.ic_favorite
            }
            val selected = n == when (tab) { "Food" -> "Explore"; "Budget" -> "Tools"; else -> tab }
            val v = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                minimumHeight = dp(60)
                background = Ui.ripple(this@MainActivity, if (selected) 0xFFEAF3FF.toInt() else Color.TRANSPARENT, 16)
                setPadding(dp(4), dp(8), dp(4), dp(8))
                isClickable = true
                setOnClickListener { if (n == "Hotels") openHotels() else { backStack.clear(); show(n) } }
            }
            v.addView(Ui.iconView(this, icon, if (selected) ACCENT else MUTED), Ui.iconLp(this, 26))
            v.addView(TextView(this).apply {
                text = n
                textSize = 11.5f
                gravity = Gravity.CENTER
                setTextColor(if (selected) ACCENT else MUTED)
                setTypeface(typeface, if (selected) Typeface.BOLD else Typeface.NORMAL)
                setPadding(0, dp(3), 0, 0)
            })
            nav.addView(v, LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(dp(2), 0, dp(2), 0) })
        }
        root.addView(nav, LinearLayout.LayoutParams(-1, -2).apply {
            setMargins(dp(12), dp(5), dp(12), dp(8))
        })
        setContentView(root)
        // Keep everything below the notch / status bar and above the gesture bar.
        Ui.edgeToEdge(this, root)
        root.alpha = 0f
        root.translationY = dp(8).toFloat()
        root.animate().alpha(1f).translationY(0f).setDuration(220).start()
        when (tab) {
            "Explore" -> explore()
            "Gems" -> discover()
            "Tools" -> tools()
            "Food" -> food()
            "Budget" -> budget()
            else -> trips()
        }
    }

    private fun openCityGuide(cityQuery: String) {
        startActivity(Intent(this, CityExplorerActivity::class.java).putExtra("city_query", cityQuery))
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

    private fun openTranslator() = startActivity(Intent(this, TranslatorActivity::class.java))

    private fun openArea(sug: Suggestion) {
        startActivity(
            Intent(this, CityExplorerActivity::class.java)
                .putExtra("city_query", sug.name).putExtra("lat", sug.lat).putExtra("lon", sug.lon)
                .putExtra("region", sug.state).putExtra("country", "India")
        )
    }

    private fun openHotels() {
        val c = meCity
        if (c != null) HotelsActivity.open(this, c.latitude, c.longitude, c.name, c.country) else HotelsActivity.open(this)
    }

    private fun openMap() {
        val c = meCity
        if (c == null) {
            Toast.makeText(this, "Allow location to open the map around you.", Toast.LENGTH_SHORT).show()
            return
        }
        MapUi.openMap(this, c.latitude, c.longitude, "Around ${c.name}", "tourist attractions")
    }

    private fun weather() {
        val c = meCity
        if (c != null) WeatherActivity.open(this, c.latitude, c.longitude, c.name)
        else startActivity(Intent(this, WeatherActivity::class.java))
    }

    private fun locationLabel(): String {
        val c = meCity ?: return ""
        return listOf(meArea.ifBlank { c.name }, c.region).filter { it.isNotBlank() }.distinct().joinToString(", ")
    }

    private fun loadingRow(text: String): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(0, dp(4), 0, dp(8))
        addView(ProgressBar(this@MainActivity, null, android.R.attr.progressBarStyleSmall), LinearLayout.LayoutParams(dp(18), dp(18)).apply { rightMargin = dp(8) })
        addView(txt(text, 12, ACCENT, true))
    }

    /** Shown instead of local content until we know where the user is. */
    private fun locationCard() {
        val card = Ui.card(this, 0xFFEAF3FF.toInt(), 20)
        val (title, msg) = when (locationState) {
            "locating" -> "Finding your location…" to "Hang on a moment while we find places around you."
            "unavailable" -> "Turn on location" to "We couldn't get a fix. Switch on location services (GPS) and try again."
            "denied" -> "Location permission needed" to "FlipDoor shows areas, places, hotels and food around you. Allow location to continue."
            else -> "Allow location" to "FlipDoor shows areas, places, hotels and food around you."
        }
        card.addView(Ui.iconText(this, R.drawable.ic_my_location, title, 16f, FG, 22, true, 10))
        card.addView(txt(msg, 13, MUTED))
        if (locationState == "locating") {
            card.addView(ProgressBar(this), LinearLayout.LayoutParams(dp(32), dp(32)))
        } else {
            card.addView(Ui.button(this, if (locationState == "unavailable") "Try again" else "Allow location", R.drawable.ic_my_location) {
                if (LocationHelper.hasPermission(this)) resolveLocation() else permLauncher.launch(LocationHelper.PERMISSIONS)
            }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })
            if (locationState == "denied" || locationState == "unavailable") {
                card.addView(Ui.button(this, "Open settings", null, ACCENT, Color.WHITE) {
                    startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
                }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
            }
        }
        content.addView(card, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14) })
    }

    private fun searchHero() {
        val hero = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = gradient(0xFF006CE4.toInt(), 0xFF4C8DFF.toInt(), 24)
            setPadding(dp(18), dp(16), dp(18), dp(16))
        }
        hero.addView(txt("Planning somewhere else?", 18, Color.WHITE, true))
        val field = SuggestField(this, "Search any place in India", { openArea(it) }, { q -> if (q.isNotEmpty()) openCityGuide(q) })
        hero.addView(field.view, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
        hero.addView(Ui.button(this, "Search", R.drawable.ic_search, FG, Ui.GOLD, 12) {
            val q = field.text()
            if (q.isNotEmpty()) openCityGuide(q) else field.input.error = "Enter a place in India"
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        content.addView(hero, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14) })
    }

    private fun featureGrid() {
        section("Plan your trip")
        val row1 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        content.addView(row1)
        featureCard(row1, R.drawable.ic_hotel, "Hotels", "Best stays & prices", 0xFFEAF3FF.toInt()) { openHotels() }
        featureCard(row1, R.drawable.ic_restaurant, "Local food", "Famous in your state", 0xFFFFF2E6.toInt()) { go("Food") }
        val row2 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        content.addView(row2)
        featureCard(row2, R.drawable.ic_luggage, "Trip planner", "Dates & full budget", 0xFFEAF8F0.toInt()) {
            startActivity(Intent(this, TripPlannerActivity::class.java))
        }
        featureCard(row2, R.drawable.ic_public, "Translator", "Online · 27 languages", 0xFFF2EDFF.toInt()) { openTranslator() }
        section("Travel essentials")
        tile("Weather", "Live conditions, hourly and 15-day forecast", R.drawable.ic_sun) { weather() }
        tile("Map", "Google Maps around you, right here in the app", R.drawable.ic_map) { openMap() }
    }

    private fun explore() {
        content.addView(txt("FLIPDOOR", 13, ACCENT, true))
        content.addView(txt("Explore around you", 28, FG, true))
        val c = meCity
        if (c == null) {
            content.addView(txt("Areas, places, hotels and food near where you are.", 14, MUTED))
            locationCard()
            searchHero()
            featureGrid()
            return
        }
        content.addView(Ui.iconText(this, R.drawable.ic_place, locationLabel(), 14f, MUTED, 16, true))
        searchHero()
        val gen = exploreGeneration

        // 1. Places around the user
        section("Places around ${c.name}", "Landmarks within about 10 km · tap a photo for full details")
        val loading = loadingRow("Finding places near you…")
        content.addView(loading)
        val carousel = PlacesCarouselView(this, { openPlaceDetail(it) }, { openTripPlanner(it.name) })
        content.addView(carousel.container, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(6) })
        thread {
            val items = placesCache ?: runCatching { CityDataRepository.mostVisitedFor(c) }.getOrDefault(emptyList()).also { if (it.isNotEmpty()) placesCache = it }
            runOnUiThread {
                if (isFinishing || isDestroyed || gen != exploreGeneration) return@runOnUiThread
                loading.visibility = View.GONE
                if (items.isEmpty()) content.addView(txt("No landmarks were found near you. Try the area list below.", 13, MUTED))
                else carousel.submitPlaces(items)
            }
        }

        // 2. Nearby areas
        val areasHolder = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(areasHolder)
        areasHolder.addView(loadingRow("Finding areas near you…"))
        thread {
            val areas = areasCache ?: runCatching { GeoRepository.nearbyAreas(c.latitude, c.longitude) }.getOrDefault(emptyList()).also { if (it.isNotEmpty()) areasCache = it }
            runOnUiThread {
                if (isFinishing || isDestroyed || gen != exploreGeneration) return@runOnUiThread
                areasHolder.removeAllViews()
                if (areas.isEmpty()) return@runOnUiThread
                areasHolder.addView(txt("Areas near you", 19, FG, true), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(20) })
                areasHolder.addView(txt("Open an area for its places, weather, hotels and famous food", 12, MUTED), LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
                areas.take(10).forEach { a -> areasHolder.addView(areaTile(a, c), LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) }) }
            }
        }

        // 3. Famous food of the user's state
        val foodHolder = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(foodHolder)
        thread {
            val bundle = foodCache ?: runCatching { GeoRepository.regionFoods(c.region, c.country) }.getOrDefault(FoodBundle("", emptyList())).also { if (it.items.isNotEmpty()) foodCache = it }
            runOnUiThread {
                if (isFinishing || isDestroyed || gen != exploreGeneration || bundle.items.isEmpty()) return@runOnUiThread
                foodHolder.addView(txt("Famous food in ${bundle.region}", 19, FG, true), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(20) })
                foodHolder.addView(txt("Dishes this region is known for · tap for details", 12, MUTED), LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
                val hs = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
                val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                bundle.items.forEach { row.addView(FoodUi.tallCard(this, it), LinearLayout.LayoutParams(dp(150), -2).apply { rightMargin = dp(10) }) }
                hs.addView(row)
                foodHolder.addView(hs)
            }
        }

        // 4. Hidden gems nearby
        val gemHolder = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(gemHolder)
        thread {
            val gems = gemsCache ?: runCatching { CityDataRepository.hiddenGemsFor(c) }.getOrDefault(emptyList()).also { if (it.isNotEmpty()) gemsCache = it }
            runOnUiThread {
                if (isFinishing || isDestroyed || gen != exploreGeneration || gems.isEmpty()) return@runOnUiThread
                gemHolder.addView(txt("Hidden gems nearby", 19, FG, true), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(20) })
                gemHolder.addView(txt("Offbeat spots close to you · tap to explore", 12, MUTED), LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
                val hs = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
                val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                gems.take(10).forEachIndexed { i, g ->
                    row.addView(HiddenGemCardsHelper.createCard(this, g, i, false, { openPlaceDetail(it) }, { openTripPlanner(it.name) }))
                }
                hs.addView(row)
                gemHolder.addView(hs)
            }
        }

        featureGrid()
    }

    private fun areaTile(a: NearbyArea, me: CityLocation): View {
        val card = MaterialCardView(this).apply {
            radius = dp(16).toFloat()
            cardElevation = dp(1).toFloat()
            setCardBackgroundColor(PANEL)
            strokeWidth = dp(1)
            strokeColor = BORDER
            setOnClickListener {
                startActivity(
                    Intent(this@MainActivity, CityExplorerActivity::class.java)
                        .putExtra("city_query", a.name).putExtra("lat", a.lat).putExtra("lon", a.lon)
                        .putExtra("region", me.region).putExtra("country", me.country)
                )
            }
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }
        row.addView(FrameLayout2.badge(this, R.drawable.ic_place), LinearLayout.LayoutParams(dp(42), dp(42)).apply { rightMargin = dp(12) })
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(txt(a.name, 16, FG, true).apply { setPadding(0, 0, 0, 0) })
        col.addView(txt(a.kind.replaceFirstChar { it.uppercase() } + "  ·  " + Ui.dist(a.distanceKm), 12, MUTED).apply { setPadding(0, dp(1), 0, 0) })
        row.addView(col, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(Ui.iconView(this, R.drawable.ic_arrow_forward, MUTED), Ui.iconLp(this, 20))
        card.addView(row)
        return card
    }

    private fun discover() {
        heading("HIDDEN GEMS", "Take the quieter road.", "Offbeat viewpoints, trails and secret spots around you.")
        val c = meCity
        if (c == null) {
            locationCard()
            return
        }
        content.addView(Ui.iconText(this, R.drawable.ic_place, locationLabel(), 13f, MUTED, 16, true))
        val gen = exploreGeneration
        val loading = loadingRow("Searching hidden gems around ${c.name}…")
        content.addView(loading, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(list)
        thread {
            val gems = gemsCache ?: runCatching { CityDataRepository.hiddenGemsFor(c) }.getOrDefault(emptyList()).also { if (it.isNotEmpty()) gemsCache = it }
            runOnUiThread {
                if (isFinishing || isDestroyed || gen != exploreGeneration) return@runOnUiThread
                loading.visibility = View.GONE
                if (gems.isEmpty()) list.addView(txt("No hidden gems were found close to you yet.", 14, MUTED))
                gems.forEachIndexed { i, g ->
                    list.addView(HiddenGemCardsHelper.createCard(this, g, i, true, { openPlaceDetail(it) }, { openTripPlanner(it.name) }))
                }
            }
        }
    }

    private fun food() {
        content.removeAllViews()
        heading("FOOD", "Taste your region.", "Famous dishes from where you are.")
        val c = meCity
        if (c == null) {
            locationCard()
            return
        }
        val gen = exploreGeneration
        val loading = loadingRow("Finding famous food in ${c.region.ifBlank { c.name }}…")
        content.addView(loading)
        thread {
            val bundle = foodCache ?: runCatching { GeoRepository.regionFoods(c.region, c.country) }.getOrDefault(FoodBundle("", emptyList())).also { if (it.items.isNotEmpty()) foodCache = it }
            runOnUiThread {
                if (isFinishing || isDestroyed || gen != exploreGeneration) return@runOnUiThread
                loading.visibility = View.GONE
                if (bundle.items.isEmpty()) content.addView(txt("We couldn't find food information for this region.", 14, MUTED))
                else {
                    section("Famous in ${bundle.region}")
                    bundle.items.forEach { FoodUi.addRow(this, content, it) }
                }
            }
        }
    }

    private fun tools() {
        heading("TOOLS", "Travel lighter.", "Utilities for the road.")
        tile("Weather", "Live conditions and forecast", R.drawable.ic_sun) { weather() }
        tile("Hotels nearby", "Best stays with pricing", R.drawable.ic_hotel) { openHotels() }
        tile("Map", "Google Maps around you, inside the app", R.drawable.ic_map) { openMap() }
        tile("Trip planner", "Plan dates, travellers and a full trip budget", R.drawable.ic_luggage) {
            startActivity(Intent(this, TripPlannerActivity::class.java))
        }
        tile("Trip budget", "Quick cost calculator", R.drawable.ic_wallet) { go("Budget") }
        tile("Translator", "Instant online translation, no downloads", R.drawable.ic_public) { openTranslator() }
    }

    private fun budget() {
        content.removeAllViews()
        heading("BUDGET PLANNER", "Know your numbers.", "Estimate trip costs. Your entries stay on this device.")
        val f = listOf("Days", "Travellers", "Stay per night (₹)", "Food per person / day (₹)", "Transport total (₹)", "Activities & extras (₹)").map { hint ->
            EditText(this).apply {
                this.hint = hint
                setHintTextColor(MUTED)
                setTextColor(FG)
                background = shape(0xFFF1F4F9.toInt())
                setPadding(dp(12), dp(8), dp(12), dp(8))
            }
        }
        f.forEach { content.addView(it, LinearLayout.LayoutParams(-1, dp(48)).apply { bottomMargin = dp(8) }) }
        val out = txt("Your estimate will appear here.", 16, FG, true)
        content.addView(out)
        content.addView(btn("Calculate budget", true) {
            val v = f.map { it.text.toString().toDoubleOrNull() ?: 0.0 }
            val days = v[0].coerceAtLeast(1.0)
            val people = v[1].coerceAtLeast(1.0)
            val total = days * v[2] + days * people * v[3] + v[4] + v[5]
            out.text = "Estimated total: ₹" + "%,.0f".format(Locale("en", "IN"), total) +
                "\nPer traveller: ₹" + "%,.0f".format(Locale("en", "IN"), total / people)
            prefs.edit().putLong("budget", total.toLong()).apply()
        }, LinearLayout.LayoutParams(-1, dp(50)))
    }

    private fun trips() {
        heading("YOUR SPACE", "Trips & saved places.", "Plan journeys, track budgets and keep destinations together.")
        content.addView(btn("＋  Create a trip plan", true) {
            startActivity(Intent(this, TripPlannerActivity::class.java))
        }, LinearLayout.LayoutParams(-1, dp(50)).apply { bottomMargin = dp(10) })

        val savedPlaces = runCatching { JSONArray(prefs.getString("saved_places", "[]")) }.getOrDefault(JSONArray())
        val saved = prefs.getString("saved", null)
        if (savedPlaces.length() > 0) {
            section("Saved places")
            for (i in savedPlaces.length() - 1 downTo 0) {
                val sp = savedPlaces.getJSONObject(i)
                val name = sp.optString("name")
                val loc = sp.optString("location")
                val cat = sp.optString("category", "Saved")
                val cityQ = sp.optString("cityQuery", name)
                val img = sp.optString("imageUrl").takeIf { it.isNotBlank() }
                val wiki = sp.optString("wikiUrl").takeIf { it.isNotBlank() }
                val placeObj = MostVisitedPlace(
                    name = name,
                    location = loc.ifBlank { cityQ },
                    category = cat,
                    summary = sp.optString("summary", "Saved destination"),
                    imageUrl = img,
                    galleryUrls = listOfNotNull(img),
                    wikiUrl = wiki,
                    latitude = null,
                    longitude = null,
                    cityQuery = cityQ
                )
                tile(name, "$loc · Tap to view & plan") { openPlaceDetail(placeObj) }
            }
        } else if (saved == null) {
            tile("Nothing saved yet", "Open a destination or slide and tap Save to My Trips.")
        } else {
            tile(saved, "Saved locally on this phone. Tap to explore.") { openCityGuide(saved) }
        }

        val plans = runCatching { JSONArray(prefs.getString("planned_trips", "[]")) }.getOrDefault(JSONArray())
        section("Your trip plans")
        if (plans.length() == 0) {
            tile("No planned trips yet", "Create a plan to save dates, travellers and a budget.")
        } else {
            for (i in plans.length() - 1 downTo 0) {
                val p = plans.getJSONObject(i)
                val dest = p.optString("destination")
                tile(
                    p.optString("name"),
                    dest + " · " + p.optString("days") + " days · ₹" + "%,.0f".format(Locale("en", "IN"), p.optDouble("budget"))
                ) {
                    if (dest.isNotBlank()) openCityGuide(dest)
                }
            }
        }
        val budget = prefs.getLong("budget", -1)
        if (budget >= 0) tile("Last quick budget estimate", "₹" + "%,d".format(Locale("en", "IN"), budget))
        tile("Travel checklist", "Documents · Stay confirmation · Local transport · Emergency contacts")
    }
}
