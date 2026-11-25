package com.anuragrajpandey.smarttravel

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.IntentCompat
import androidx.core.widget.NestedScrollView
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.card.MaterialCardView
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import kotlin.concurrent.thread

class PlaceDetailActivity : AppCompatActivity() {
    private val blue = 0xFF006CE4.toInt()
    private val gold = 0xFFFFB700.toInt()
    private val ink = 0xFF172B4D.toInt()
    private val muted = 0xFF718096.toInt()
    private val bg = 0xFFF5F7FB.toInt()
    private val prefs by lazy { getSharedPreferences("flipdoor", MODE_PRIVATE) }

    private lateinit var place: MostVisitedPlace
    private lateinit var galleryPager: ViewPager2
    private lateinit var dotsRow: LinearLayout
    private lateinit var photoCountBadge: TextView
    private var galleryUrls: MutableList<String> = mutableListOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val passed = IntentCompat.getSerializableExtra(intent, "place", MostVisitedPlace::class.java)
        if (passed == null) {
            finish()
            return
        }
        place = passed
        galleryUrls = (listOfNotNull(place.imageUrl) + place.galleryUrls).distinct().toMutableList()
        buildUi()
        fetchMorePhotos()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun shape(color: Int, r: Int = 16) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(r).toFloat()
    }

    private fun label(value: String, size: Int = 14, color: Int = ink, bold: Boolean = false) =
        TextView(this).apply {
            text = value
            textSize = size.toFloat()
            setTextColor(color)
            if (bold) setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(2), 0, dp(4))
        }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
        }

        // Top bar: back arrow, title, save toggle (vector icons, 48dp touch targets)
        val saveTop = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(40)
            background = Ui.ripple(this@PlaceDetailActivity, 0xFFEAF3FF.toInt(), 20)
            setPadding(dp(12), dp(8), dp(14), dp(8))
        }
        fun paintSave(saved: Boolean) {
            saveTop.removeAllViews()
            saveTop.addView(
                Ui.iconView(this, if (saved) R.drawable.ic_favorite else R.drawable.ic_favorite_border, blue),
                Ui.iconLp(this, 20).apply { rightMargin = dp(6) }
            )
            saveTop.addView(TextView(this).apply {
                text = if (saved) "Saved" else "Save"
                textSize = 13.5f
                setTextColor(blue)
                setTypeface(typeface, Typeface.BOLD)
            })
        }
        paintSave(isPlaceSaved(place.name))
        saveTop.setOnClickListener { savePlace(place); paintSave(true) }
        root.addView(Ui.topBar(this, place.name, ink, listOf(saveTop)))

        val scroll = NestedScrollView(this)
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(4), dp(16), dp(28))
        }
        scroll.addView(body)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        // Big photo gallery slider
        val sliderCard = MaterialCardView(this).apply {
            radius = dp(22).toFloat()
            cardElevation = dp(3).toFloat()
            setCardBackgroundColor(0xFF0B3C5D.toInt())
        }
        val sliderFrame = FrameLayout(this)
        galleryPager = ViewPager2(this).apply { offscreenPageLimit = 2 }
        galleryPager.adapter = GalleryAdapter(galleryUrls)
        sliderFrame.addView(galleryPager, FrameLayout.LayoutParams(-1, dp(290)))

        photoCountBadge = TextView(this).apply {
            text = if (galleryUrls.isEmpty()) "Photo preview" else "1 / ${galleryUrls.size}"
            textSize = 11.5f
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
            background = GradientDrawable().apply { setColor(0x88000000.toInt()); cornerRadius = dp(10).toFloat() }
            setPadding(dp(10), dp(5), dp(10), dp(5))
        }
        sliderFrame.addView(photoCountBadge, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.END).apply {
            topMargin = dp(14); rightMargin = dp(14)
        })
        val categoryBadge = TextView(this).apply {
            text = place.category.uppercase(Locale.getDefault())
            textSize = 10.5f
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
            background = GradientDrawable().apply { setColor(0x88000000.toInt()); cornerRadius = dp(10).toFloat() }
            setPadding(dp(10), dp(5), dp(10), dp(5))
        }
        sliderFrame.addView(categoryBadge, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.START).apply {
            topMargin = dp(14); leftMargin = dp(14)
        })
        sliderCard.addView(sliderFrame)
        body.addView(sliderCard, LinearLayout.LayoutParams(-1, dp(290)))

        dotsRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        body.addView(dotsRow, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10); bottomMargin = dp(8) })
        galleryPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) { updateGalleryDots(position) }
        })
        updateGalleryDots(0)

        // Title & location
        body.addView(label(place.name, 26, ink, true).apply { setPadding(0, dp(6), 0, dp(2)) })
        body.addView(Ui.iconText(this, R.drawable.ic_place, place.location, 14f, blue, 18, true))

        // Quick summary
        val infoCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = shape(Color.WHITE, 18)
            setPadding(dp(16), dp(14), dp(16), dp(14))
        }
        infoCard.addView(label("About this place", 16, ink, true))
        infoCard.addView(label(place.summary, 14, muted))
        body.addView(infoCard, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12); bottomMargin = dp(14) })

        // Actions
        body.addView(Ui.button(this, "Plan a trip to ${place.name}", R.drawable.ic_flight, ink, gold, 14) {
            startActivity(
                Intent(this, TripPlannerActivity::class.java)
                    .putExtra("destination", place.name)
                    .putExtra("trip_name", "Trip to ${place.name}")
            )
        }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })

        val plat = place.latitude
        val plon = place.longitude
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(Ui.button(this, "City guide & weather", R.drawable.ic_public, Color.WHITE, blue, 14) {
            startActivity(Intent(this, CityExplorerActivity::class.java).putExtra("city_query", place.cityQuery))
        }, LinearLayout.LayoutParams(0, -2, 1f).apply { rightMargin = dp(6) })
        if (plat != null && plon != null) {
            row.addView(Ui.button(this, "Hotels nearby", R.drawable.ic_hotel, blue, 0xFFEAF3FF.toInt(), 14) {
                HotelsActivity.open(this, plat, plon, place.name)
            }, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(6) })
        }
        body.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(14) })

        // Google Map, in the app
        if (plat != null && plon != null) {
            body.addView(label("Location", 18, ink, true))
            body.addView(
                MapUi.inlineMap(this, plat, plon, "place", null, 230) {
                    MapUi.openMap(this, plat, plon, place.name)
                },
                LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(14) }
            )
        }

        // Full information, shown here instead of sending people to Wikipedia
        body.addView(label("In depth", 18, ink, true).apply { setPadding(0, dp(4), 0, dp(6)) })
        articleBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        articleBox.addView(ProgressBar(this), LinearLayout.LayoutParams(-1, dp(56)))
        body.addView(articleBox)

        setContentView(root)
        Ui.edgeToEdge(this, root)
        loadArticle()
    }

    private lateinit var articleBox: LinearLayout

    private fun loadArticle() {
        thread {
            try {
                val wikiRef = place.wikiUrl?.takeIf { GeoRepository.titleFromUrl(it) != null } ?: place.name
                val article = GeoRepository.fetchArticle(wikiRef, "${place.name} ${place.cityQuery}")
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    articleBox.removeAllViews()
                    ArticleUi.render(this, articleBox, article, showTitle = false)
                }
            } catch (e: Exception) {
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    articleBox.removeAllViews()
                    articleBox.addView(label("No extra write-up was found for this place. The summary above is what's available.", 13, muted))
                    val site = place.wikiUrl?.takeIf { it.startsWith("http") && GeoRepository.titleFromUrl(it) == null }
                    if (site != null) articleBox.addView(Ui.button(this, "Open website", R.drawable.ic_public, blue, 0xFFEAF3FF.toInt(), 14) {
                        WebPageActivity.open(this, site, place.name)
                    })
                }
            }
        }
    }

    private fun fetchMorePhotos() {
        thread {
            val extra = runCatching { CityDataRepository.fetchPlaceGallery(place.name, place.cityQuery) }
                .getOrDefault(emptyList())
            if (extra.isEmpty()) return@thread
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                val added = extra.filter { it !in galleryUrls }
                if (added.isNotEmpty()) {
                    galleryUrls.addAll(added)
                    galleryPager.adapter?.notifyDataSetChanged()
                    updateGalleryDots(galleryPager.currentItem)
                }
            }
        }
    }

    private fun updateGalleryDots(activeIndex: Int) {
        dotsRow.removeAllViews()
        val count = galleryUrls.size
        photoCountBadge.text = if (count == 0) "Photo preview" else "${activeIndex + 1} / $count"
        if (count <= 1) return
        for (i in 0 until count) {
            val active = i == activeIndex
            val dot = View(this).apply {
                background = GradientDrawable().apply {
                    setColor(if (active) blue else 0xFFCBD5E1.toInt())
                    cornerRadius = dp(4).toFloat()
                }
            }
            dotsRow.addView(dot, LinearLayout.LayoutParams(dp(if (active) 20 else 7), dp(7)).apply {
                setMargins(dp(3), 0, dp(3), 0)
            })
        }
    }

    private fun isPlaceSaved(name: String): Boolean {
        val arr = runCatching { JSONArray(prefs.getString("saved_places", "[]")) }.getOrDefault(JSONArray())
        for (i in 0 until arr.length()) {
            if (arr.optJSONObject(i)?.optString("name").equals(name, ignoreCase = true)) return true
        }
        return prefs.getString("saved", null).equals(name, ignoreCase = true)
    }

    private fun savePlace(p: MostVisitedPlace) {
        prefs.edit().putString("saved", p.name).apply()
        val arr = runCatching { JSONArray(prefs.getString("saved_places", "[]")) }.getOrDefault(JSONArray())
        var exists = false
        for (i in 0 until arr.length()) {
            if (arr.optJSONObject(i)?.optString("name").equals(p.name, ignoreCase = true)) {
                exists = true
                break
            }
        }
        if (!exists) {
            val obj = JSONObject()
                .put("name", p.name)
                .put("location", p.location)
                .put("category", p.category)
                .put("summary", p.summary)
                .put("imageUrl", p.imageUrl ?: "")
                .put("wikiUrl", p.wikiUrl ?: "")
                .put("cityQuery", p.cityQuery)
            arr.put(obj)
            prefs.edit().putString("saved_places", arr.toString()).apply()
        }
        Toast.makeText(this, "Saved ${p.name} to My Trips", Toast.LENGTH_SHORT).show()
    }

    private inner class GalleryAdapter(private val urls: List<String>) :
        RecyclerView.Adapter<GalleryAdapter.PhotoVH>() {
        inner class PhotoVH(val container: FrameLayout) : RecyclerView.ViewHolder(container)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhotoVH {
            val frame = FrameLayout(this@PlaceDetailActivity).apply {
                layoutParams = ViewGroup.LayoutParams(-1, -1)
            }
            return PhotoVH(frame)
        }

        override fun getItemCount(): Int = maxOf(urls.size, 1)

        override fun onBindViewHolder(holder: PhotoVH, position: Int) {
            holder.container.removeAllViews()
            val grad = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(0xFF0B3C5D.toInt(), 0xFF328CC1.toInt())
            )
            holder.container.addView(View(this@PlaceDetailActivity).apply { background = grad }, FrameLayout.LayoutParams(-1, -1))
            if (urls.isNotEmpty()) {
                val img = ImageView(this@PlaceDetailActivity).apply {
                    scaleType = ImageView.ScaleType.CENTER_CROP
                }
                holder.container.addView(img, FrameLayout.LayoutParams(-1, -1))
                ImageLoader.load(urls[position], img)
            } else {
                val emptyText = TextView(this@PlaceDetailActivity).apply {
                    text = place.name
                    textSize = 22f
                    setTextColor(Color.WHITE)
                    setTypeface(typeface, Typeface.BOLD)
                    gravity = Gravity.CENTER
                }
                holder.container.addView(emptyText, FrameLayout.LayoutParams(-1, -1))
            }
        }
    }
}
