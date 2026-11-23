package com.anuragrajpandey.smarttravel

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.card.MaterialCardView
import kotlin.concurrent.thread

class CityExplorerActivity:AppCompatActivity() {
    private val blue=Color.rgb(0,108,228)
    private val ink=Color.rgb(23,43,77)
    private val muted=Color.rgb(105,119,140)
    private val bg=Color.rgb(245,247,251)
    private lateinit var query:EditText
    private lateinit var progress:ProgressBar
    private lateinit var body:LinearLayout
    private lateinit var status:TextView

    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor=bg
        window.navigationBarColor=Color.WHITE
        buildScreen()
        val initial=intent.getStringExtra("city_query").orEmpty()
        query.setText(initial)
        if(initial.isNotBlank()) search(initial)
    }

    private fun dp(v:Int)=(v*resources.displayMetrics.density).toInt()
    private fun panel(color:Int=Color.WHITE,r:Int=18)=GradientDrawable().apply{setColor(color);cornerRadius=dp(r).toFloat()}
    private fun text(value:String,size:Int=14,color:Int=ink,bold:Boolean=false)=TextView(this).apply {
        text=value;textSize=size.toFloat();setTextColor(color)
        if(bold)setTypeface(typeface,Typeface.BOLD)
        setPadding(0,dp(3),0,dp(5))
    }
    private fun buildScreen() {
        val root=LinearLayout(this).apply{orientation=1;setBackgroundColor(bg)}
        val top=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;setPadding(dp(16),dp(12),dp(16),dp(8))}
        val back=TextView(this).apply{text="‹";textSize=34f;setTextColor(ink);gravity=Gravity.CENTER;setOnClickListener{finish()}}
        top.addView(back,LinearLayout.LayoutParams(dp(42),dp(44)))
        top.addView(text("City guide",20,ink,true),LinearLayout.LayoutParams(0,-2,1f))
        root.addView(top)
        val searchPanel=LinearLayout(this).apply{orientation=1;background=panel();setPadding(dp(14),dp(12),dp(14),dp(12))}
        query=EditText(this).apply{hint="Search a city, e.g. Mathura";setSingleLine(true);textSize=15f;setTextColor(ink);setHintTextColor(muted);background=panel(bg,12);setPadding(dp(13),dp(12),dp(13),dp(12));imeOptions=3}
        searchPanel.addView(query,LinearLayout.LayoutParams(-1,dp(50)))
        val go=TextView(this).apply{text="Explore destination";gravity=Gravity.CENTER;textSize=14f;setTypeface(typeface,Typeface.BOLD);setTextColor(Color.WHITE);background=panel(blue,12);setPadding(0,dp(13),0,dp(13));setOnClickListener{search(query.text.toString())}}
        searchPanel.addView(go,LinearLayout.LayoutParams(-1,dp(48)).apply{topMargin=dp(9)})
        root.addView(searchPanel,LinearLayout.LayoutParams(-1,-2).apply{setMargins(dp(16),dp(5),dp(16),dp(8))})
        progress=ProgressBar(this,null,android.R.attr.progressBarStyleLarge).apply{isIndeterminate=true;visibility=View.GONE}
        root.addView(progress,LinearLayout.LayoutParams(dp(48),dp(48)).apply{gravity=Gravity.CENTER_HORIZONTAL;topMargin=dp(8);bottomMargin=dp(8)})
        status=text("Search any city to build a live travel guide.",13,muted)
        status.setPadding(dp(18),dp(9),dp(18),dp(8))
        root.addView(status)
        val scroll=ScrollView(this)
        body=LinearLayout(this).apply{orientation=1;setPadding(dp(16),dp(4),dp(16),dp(28))}
        scroll.addView(body)
        root.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
        setContentView(root)
    }

    private fun search(value:String) {
        val city=value.trim()
        if(city.isBlank()){query.error="Enter a city name";return}
        progress.visibility=View.VISIBLE
        status.text="Finding $city and loading live travel information…"
        body.removeAllViews()
        body.addView(text("Please wait while FlipDoor searches weather, nearby places and local food information.",14,muted))
        thread {
            try {
                val guide=CityDataRepository.searchCity(city)
                runOnUiThread {
                    progress.visibility=View.GONE
                    status.text="Updated just now · Data from live online sources"
                    render(guide)
                }
            } catch(e:Exception) {
                runOnUiThread {
                    progress.visibility=View.GONE
                    status.text="We couldn't load this city."
                    body.removeAllViews()
                    card("Search error",e.message ?: "Please check your connection and try again.")
                    body.addView(button("Try again"){search(city)})
                }
            }
        }
    }

    private fun card(title:String,description:String) {
        val box=LinearLayout(this).apply{orientation=1;background=panel();setPadding(dp(15),dp(13),dp(15),dp(12))}
        box.addView(text(title,16,ink,true));box.addView(text(description,13,muted))
        body.addView(box,LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=dp(10)})
    }
    private fun button(label:String,action:()->Unit)=TextView(this).apply {
        text=label;gravity=Gravity.CENTER;textSize=14f;setTypeface(typeface,Typeface.BOLD);setTextColor(blue)
        background=panel(0xFFEAF3FF.toInt(),12);setPadding(dp(12),dp(12),dp(12),dp(12));setOnClickListener{action()}
    }
    private fun section(title:String,subtitle:String="") {
        body.addView(text(title,19,ink,true),LinearLayout.LayoutParams(-1,-2).apply{topMargin=dp(17);bottomMargin=dp(2)})
        if(subtitle.isNotBlank()) body.addView(text(subtitle,12,muted))
    }
    private fun render(g:CityGuide) {
        body.removeAllViews()
        val location=listOf(g.city.region,g.city.country).filter{it.isNotBlank()}.joinToString(" · ")
        card("✈  ${g.city.name}",location.ifBlank{"Destination guide"})
        g.overview?.let{card("About ${g.city.name}",it.excerpt.ifBlank{it.description}.ifBlank{"Read the destination overview online."});body.addView(button("Read destination guide"){open(it.url)})}
        section("Weather","Live conditions and a five-day forecast")
        val w=g.weather
        if(w==null) card("Weather unavailable","The weather service did not respond. Other city information is still available.")
        else {
            card("${w.temperature.toInt()}°C  ·  ${w.description}","Feels like ${w.feelsLike.toInt()}°C  ·  Humidity ${w.humidity}%  ·  Wind ${w.wind.toInt()} km/h")
            w.forecast.forEach{card("Forecast",it)}
        }
        section("Travel budget","Indicative planning ranges per person, per day. Not live prices or a verified city average.")
        card("Budget traveller","₹1,000–₹1,800 / day · basic stay, local food and public/local transport")
        card("Comfort traveller","₹2,000–₹3,800 / day · mid-range stay, meals and local experiences")
        card("Premium traveller","₹4,500–₹8,000+ / day · higher-end stays, dining and private transport")
        card("Make it your own","Use the Trip Budget tool to enter actual stay, food, transport and activity costs.")
        section("Places to stay","Listings mapped by OpenStreetMap contributors. Availability and prices are not verified.")
        if(g.hotels.isEmpty()) card("No mapped stays found","Try a nearby city or search again later.")
        g.hotels.forEach{placeCard(it)}
        section("Food & cafés","Nearby mapped restaurants and cafés")
        if(g.restaurants.isEmpty()) card("No mapped food places found","OpenStreetMap may have limited coverage here.")
        g.restaurants.forEach{placeCard(it)}
        section("Things to do","Nearby attractions and museums")
        if(g.attractions.isEmpty()) card("No mapped attractions found","Explore the city overview and local search results.")
        g.attractions.forEach{placeCard(it)}
        section("Local food to discover","Wikimedia search results. These are research links, not popularity rankings.")
        if(g.foods.isEmpty()) card("No food articles found","Try searching the city name with a regional cuisine term.")
        g.foods.forEach{item->
            card(item.title,item.description.ifBlank{item.excerpt})
            body.addView(button("Read about ${item.title}"){open(item.url)})
        }
        card("Data & attribution","Weather: Open-Meteo. Places: © OpenStreetMap contributors (ODbL). Destination and food research: Wikipedia contributors (CC BY-SA). Check details before travelling.")
    }
    private fun placeCard(place:CityPlace) {
        val detail=listOf(place.type,place.detail).filter{it.isNotBlank()}.joinToString(" · ")
        val box=LinearLayout(this).apply{orientation=1;background=panel();setPadding(dp(14),dp(12),dp(14),dp(10))}
        box.addView(text(place.name,15,ink,true));box.addView(text(detail,12,muted))
        val actions=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
        actions.addView(button("Directions"){open("https://www.openstreetmap.org/?mlat=${place.latitude}&mlon=${place.longitude}#map=17/${place.latitude}/${place.longitude}")})
        if(place.website!=null) actions.addView(button("Website"){open(place.website)})
        box.addView(actions)
        body.addView(box,LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=dp(9)})
    }
    private fun open(url:String) {
        runCatching{startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(url)))}
    }
}
