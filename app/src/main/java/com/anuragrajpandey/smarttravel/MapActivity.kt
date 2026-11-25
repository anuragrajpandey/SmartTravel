package com.anuragrajpandey.smarttravel

import android.graphics.Color
import android.os.Bundle
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity

/** Full-screen in-app Google map with quick "nearby" filters and a route-from-me mode. */
class MapActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val lat = intent.getDoubleExtra("lat", 0.0)
        val lon = intent.getDoubleExtra("lon", 0.0)
        val title = intent.getStringExtra("title").orEmpty().ifBlank { "Map" }
        val initialQuery = intent.getStringExtra("query")
        val me = LocationHelper.saved(this)

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Ui.BG) }
        root.addView(Ui.topBar(this, title))

        val web = MapUi.createWebView(this)
        fun show(mode: String, q: String?) = MapUi.load(web, MapUi.embedUrl(lat, lon, mode, q, me?.lat, me?.lon))

        data class Filter(val label: String, val icon: Int, val mode: String, val query: String?)
        val filters = buildList {
            add(Filter("Place", R.drawable.ic_place, "place", null))
            add(Filter("Hotels", R.drawable.ic_hotel, "search", "hotels"))
            add(Filter("Restaurants", R.drawable.ic_restaurant, "search", "restaurants"))
            add(Filter("Attractions", R.drawable.ic_explore, "search", "tourist attractions"))
            add(Filter("Hospitals", R.drawable.ic_favorite, "search", "hospital"))
            if (me != null) add(Filter("Route from me", R.drawable.ic_directions, "route", null))
        }
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(Ui.dp(this@MapActivity, 12), 0, Ui.dp(this@MapActivity, 12), Ui.dp(this@MapActivity, 8)) }
        val chips = mutableListOf<LinearLayout>()
        var active = if (initialQuery != null) filters.indexOfFirst { it.query == initialQuery }.coerceAtLeast(0) else 0
        fun restyle() = chips.forEachIndexed { i, c -> Ui.restyleChip(c, i == active, filters[i].icon, filters[i].label) }
        filters.forEachIndexed { i, f ->
            val c = Ui.chip(this, f.label, i == active, f.icon) {
                active = i; restyle(); show(f.mode, f.query)
            }
            chips.add(c)
            row.addView(c, LinearLayout.LayoutParams(-2, -2).apply { rightMargin = Ui.dp(this@MapActivity, 8) })
        }
        root.addView(HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(row) })
        root.addView(web, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        Ui.edgeToEdge(this, root)
        filters[active].let { show(it.mode, it.query) }
    }
}
