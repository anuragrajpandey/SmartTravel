package com.anuragrajpandey.smarttravel

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.card.MaterialCardView
import com.google.android.material.bottomnavigation.BottomNavigationView

data class Destination(
    val name: String, val place: String, val tag: String,
    val budget: String, val description: String, val emoji: String
)

class MainActivity : AppCompatActivity() {
    private val destinations = listOf(
        Destination("Rishikesh", "Uttarakhand", "Adventure", "₹8k–₹15k", "River views, cafés and outdoor adventures with a relaxed Himalayan feel.", "🏔️"),
        Destination("Jaipur", "Rajasthan", "Culture", "₹7k–₹14k", "Historic forts, local markets, architecture and rich Rajasthani food.", "🏰"),
        Destination("Munnar", "Kerala", "Nature", "₹10k–₹18k", "Tea gardens, misty hills and quiet viewpoints for a slower escape.", "🌿"),
        Destination("Pondicherry", "Tamil Nadu", "Beach", "₹8k–₹16k", "A calm coastal break with cafés, heritage streets and seaside walks.", "🌊")
    )
    private lateinit var content: LinearLayout
    private var selectedTab = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.WHITE
        window.navigationBarColor = Color.WHITE
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        showHome()
    }

    private fun baseLayout(): LinearLayout {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
        }
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(12), dp(20), dp(12))
        }
        root.addView(ScrollView(this).apply {
            isFillViewport = true
            addView(content, ViewGroup.LayoutParams(-1, -1))
        }, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(bottomNav(), LinearLayout.LayoutParams(-1, dp(72)))
        return root
    }

    private fun bottomNav(): BottomNavigationView = BottomNavigationView(this).apply {
        menu.add(0, 1, 0, "Explore").setIcon(android.R.drawable.ic_menu_search)
        menu.add(0, 2, 1, "Trips").setIcon(android.R.drawable.ic_menu_myplaces)
        menu.add(0, 3, 2, "Profile").setIcon(android.R.drawable.ic_menu_manage)
        selectedItemId = selectedTab + 1
        setOnItemSelectedListener { item ->
            selectedTab = item.itemId - 1
            when (item.itemId) {
                1 -> showHome()
                2 -> showTrips()
                3 -> showProfile()
            }
            true
        }
    }

    private fun showHome() {
        setContentView(baseLayout())
        content.removeAllViews()
        text("Good evening, Anurag", 14, Color.GRAY)
        text("Where will you go next?", 28, Color.rgb(17,17,17), true, 8)

        val search = EditText(this).apply {
            hint = "Search destinations"
            setSingleLine()
            setPadding(dp(16), 0, dp(16), 0)
            background = rounded(Color.rgb(247,247,247), 18)
        }
        content.addView(search, LinearLayout.LayoutParams(-1, dp(52)).apply { bottomMargin = dp(18) })

        text("Explore by mood", 19, Color.rgb(17,17,17), true)
        val chips = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf("Nature", "Adventure", "Culture", "Beach").forEachIndexed { i, label ->
            val b = Button(this).apply {
                text = label
                textSize = 12f
                isAllCaps = false
                setTextColor(if (i == 0) Color.WHITE else Color.DKGRAY)
                setBackgroundColor(if (i == 0) Color.rgb(17,17,17) else Color.rgb(245,245,245))
                setPadding(dp(8), 0, dp(8), 0)
            }
            chips.addView(b, LinearLayout.LayoutParams(dp(92), dp(40)).apply {
                rightMargin = dp(8); topMargin = dp(10); bottomMargin = dp(18)
            })
        }
        content.addView(chips)

        text("Recommended for you", 20, Color.rgb(17,17,17), true, 2)
        destinations.take(3).forEach { addDestinationCard(it) }

        val safety = MaterialCardView(this).apply {
            radius = dp(20).toFloat()
            setCardBackgroundColor(Color.rgb(248,248,248))
            strokeWidth = 0
        }
        val safetyBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(16))
        }
        textTo(safetyBox, "Travel safety", 17, Color.rgb(17,17,17), true)
        textTo(safetyBox, "Quick access to emergency numbers and practical safety tips.", 13, Color.GRAY, false)
        safetyBox.addView(Button(this).apply {
            text = "View safety guide"
            isAllCaps = false
            setOnClickListener { showSafety() }
        }, LinearLayout.LayoutParams(-1, dp(44)).apply { topMargin = dp(8) })
        safety.addView(safetyBox)
        content.addView(safety, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14) })
    }

    private fun addDestinationCard(d: Destination) {
        val card = MaterialCardView(this).apply {
            radius = dp(22).toFloat()
            setCardBackgroundColor(Color.WHITE)
            strokeColor = Color.rgb(235,235,235)
            strokeWidth = dp(1)
            setOnClickListener { showDestination(d) }
        }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(16))
        }
        textTo(box, "${d.emoji}  ${d.name}", 19, Color.rgb(17,17,17), true)
        textTo(box, "${d.place}  •  ${d.tag}", 12, Color.GRAY, false)
        textTo(box, d.description, 13, Color.DKGRAY, false)
        textTo(box, "Typical budget  ${d.budget}", 12, Color.rgb(80,80,80), true)
        card.addView(box)
        content.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
    }

    private fun showDestination(d: Destination) {
        content.removeAllViews()
        backButton()
        text("${d.emoji}  ${d.name}", 28, Color.rgb(17,17,17), true, 12)
        text("${d.place}  •  ${d.tag}", 14, Color.GRAY, false, 16)
        text(d.description, 16, Color.DKGRAY, false, 22)
        infoCard("Budget", d.budget)
        infoCard("Best for", "${d.tag} lovers")
        infoCard("Starter plan", "Explore 2–3 key places, try local food and keep one flexible block.")
        content.addView(Button(this).apply {
            text = "Add to my trip"
            isAllCaps = false
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.rgb(17,17,17))
            setOnClickListener { Toast.makeText(this@MainActivity, "Added to My Trip", Toast.LENGTH_SHORT).show() }
        }, LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(10) })
    }

    private fun showTrips() {
        setContentView(baseLayout())
        content.removeAllViews()
        text("My Trips", 30, Color.rgb(17,17,17), true, 6)
        text("Simple planning for now. Your itinerary module can grow here.", 14, Color.GRAY, false, 20)
        infoCard("Weekend escape", "Rishikesh • 3 days • Adventure")
        infoCard("Saved idea", "Munnar • 4 days • Nature")
    }

    private fun showProfile() {
        setContentView(baseLayout())
        content.removeAllViews()
        text("Profile", 30, Color.rgb(17,17,17), true, 8)
        infoCard("Travel preference", "Nature • Culture • Mid-range")
        infoCard("Planning style", "Short trips with flexible time")
        text("SmartTravel", 13, Color.GRAY, false, 28)
        text("Version 0.1 • Android MVP", 12, Color.LTGRAY, false, 2)
    }

    private fun showSafety() {
        content.removeAllViews()
        backButton()
        text("Travel Safety", 28, Color.rgb(17,17,17), true, 14)
        infoCard("Emergency", "India: 112 for integrated emergency response. Verify local numbers before travelling.")
        infoCard("Before you leave", "Keep ID copies, share your itinerary, check local conditions and save your accommodation details.")
        infoCard("On the move", "Stay aware of your surroundings and use official transport or trusted providers where possible.")
    }

    private fun backButton() {
        content.addView(Button(this).apply {
            text = "‹  Back"
            isAllCaps = false
            setOnClickListener { showHome() }
        }, LinearLayout.LayoutParams(dp(100), dp(44)))
    }

    private fun infoCard(title: String, body: String) {
        val card = MaterialCardView(this).apply {
            radius = dp(20).toFloat()
            setCardBackgroundColor(Color.rgb(248,248,248))
            strokeWidth = 0
        }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(16))
        }
        textTo(box, title, 16, Color.rgb(17,17,17), true)
        textTo(box, body, 14, Color.DKGRAY, false)
        card.addView(box)
        content.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
    }

    private fun text(value: String, size: Int, color: Int, bold: Boolean = false, top: Int = 0) {
        textTo(content, value, size, color, bold)
        (content.getChildAt(content.childCount - 1).layoutParams as LinearLayout.LayoutParams).topMargin = dp(top)
    }

    private fun textTo(parent: LinearLayout, value: String, size: Int, color: Int, bold: Boolean) {
        val t = TextView(this).apply {
            text = value
            textSize = size.toFloat()
            setTextColor(color)
            setPadding(0, dp(2), 0, dp(6))
            if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        parent.addView(t, LinearLayout.LayoutParams(-1, -2))
    }

    private fun rounded(color: Int, radius: Int) =
        android.graphics.drawable.GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
        }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
