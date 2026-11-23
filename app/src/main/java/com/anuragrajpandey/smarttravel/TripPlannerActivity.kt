package com.anuragrajpandey.smarttravel

import android.app.DatePickerDialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class TripPlannerActivity:AppCompatActivity() {
    private val blue=Color.rgb(0,108,228)
    private val ink=Color.rgb(23,43,77)
    private val muted=Color.rgb(105,119,140)
    private val bg=Color.rgb(245,247,251)
    private val prefs by lazy{getSharedPreferences("flipdoor",MODE_PRIVATE)}
    private lateinit var content:LinearLayout
    private lateinit var destination:EditText
    private lateinit var tripName:EditText
    private lateinit var travellers:EditText
    private lateinit var stay:EditText
    private lateinit var food:EditText
    private lateinit var transport:EditText
    private lateinit var activities:EditText
    private lateinit var startButton:TextView
    private lateinit var endButton:TextView
    private var start=Calendar.getInstance()
    private var end=Calendar.getInstance().apply{add(Calendar.DAY_OF_MONTH,3)}
    private var estimate=0.0

    private fun dp(v:Int)=(v*resources.displayMetrics.density).toInt()
    private fun shape(color:Int,r:Int=14)=GradientDrawable().apply{setColor(color);cornerRadius=dp(r).toFloat()}
    private fun label(value:String,size:Int=14,color:Int=ink,bold:Boolean=false)=TextView(this).apply{text=value;textSize=size.toFloat();setTextColor(color);if(bold)setTypeface(typeface,Typeface.BOLD);setPadding(0,dp(3),0,dp(5))}
    private fun field(hintText:String,numeric:Boolean=false)=EditText(this).apply{
        hint=hintText;textSize=14f;setTextColor(ink);setHintTextColor(muted);background=shape(Color.WHITE);setPadding(dp(13),dp(10),dp(13),dp(10))
        if(numeric) inputType=android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
    }
    private fun button(textValue:String,primary:Boolean=false,action:()->Unit)=TextView(this).apply{
        text=textValue;gravity=Gravity.CENTER;textSize=14f;setTypeface(typeface,Typeface.BOLD);setTextColor(if(primary)Color.WHITE else blue)
        background=shape(if(primary)blue else 0xFFEAF3FF.toInt(),13);setPadding(dp(12),dp(13),dp(12),dp(13));setOnClickListener{action()}
    }
    override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);window.statusBarColor=bg;window.navigationBarColor=Color.WHITE;build()}
    private fun build(){
        val root=LinearLayout(this).apply{orientation=1;setBackgroundColor(bg)}
        val top=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;setPadding(dp(16),dp(12),dp(16),dp(8))}
        top.addView(label("‹",34),LinearLayout.LayoutParams(dp(40),dp(44)).apply{})
        top.getChildAt(0).setOnClickListener{finish()}
        top.addView(label("Trip planner",20,ink,true))
        root.addView(top)
        val scroll=ScrollView(this)
        content=LinearLayout(this).apply{orientation=1;setPadding(dp(17),dp(8),dp(17),dp(26))}
        scroll.addView(content);root.addView(scroll)
        setContentView(root)
        content.addView(label("PLAN A TRIP",10,blue,true))
        content.addView(label("Build your next getaway.",27,ink,true))
        content.addView(label("Plan dates, travellers and a realistic trip budget in one place.",14,muted))
        section("Trip details")
        tripName=field("Trip name, e.g. Family holiday");addField("Name your trip",tripName)
        destination=field("Where are you going?");addField("Destination",destination)
        travellers=field("Number of travellers",true).apply{setText("2")};addField("Travellers",travellers)
        section("Travel dates")
        startButton=button(dateLabel(start),false){pickDate(true)}
        endButton=button(dateLabel(end),false){pickDate(false)}
        val dates=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
        dates.addView(startButton,LinearLayout.LayoutParams(0,-2,1f).apply{rightMargin=dp(5)})
        dates.addView(endButton,LinearLayout.LayoutParams(0,-2,1f).apply{leftMargin=dp(5)})
        content.addView(dates)
        section("Budget breakdown", "Enter your expected costs in ₹. Stay is per room per night; food is per person per day.")
        stay=field("Stay cost per night (₹)",true);addField("Accommodation / night",stay)
        food=field("Food per person / day (₹)",true);addField("Food / day",food)
        transport=field("Total transport cost (₹)",true);addField("Transport total",transport)
        activities=field("Activities and extras (₹)",true);addField("Activities & extras",activities)
        val result=label("Your trip estimate will appear here.",16,ink,true)
        content.addView(result)
        content.addView(button("Calculate trip budget",true){
            val days=tripDays()
            val people=travellers.text.toString().toIntOrNull()?.coerceAtLeast(1)?:1
            if(days<1){Toast.makeText(this,"End date must be after start date.",Toast.LENGTH_SHORT).show();return@button}
            val room=stay.text.toString().toDoubleOrNull()?:0.0
            val meals=food.text.toString().toDoubleOrNull()?:0.0
            val travel=transport.text.toString().toDoubleOrNull()?:0.0
            val extras=activities.text.toString().toDoubleOrNull()?:0.0
            estimate=days*room+days*people*meals+travel+extras
            result.text="Estimated trip total: ₹"+money(estimate)+"\nPer traveller: ₹"+money(estimate/people)+"\nDuration: $days days · $people traveller(s)"
        },LinearLayout.LayoutParams(-1,-2).apply{topMargin=dp(10)})
        content.addView(button("Save this trip",true){
            val place=destination.text.toString().trim()
            if(place.isBlank()){destination.error="Enter a destination";return@button}
            if(tripDays()<1){Toast.makeText(this,"Please correct the trip dates.",Toast.LENGTH_SHORT).show();return@button}
            if(estimate<=0.0){Toast.makeText(this,"Calculate the budget before saving.",Toast.LENGTH_SHORT).show();return@button}
            saveTrip(place)
            Toast.makeText(this,"Trip saved on this device.",Toast.LENGTH_SHORT).show()
            showSaved()
        },LinearLayout.LayoutParams(-1,-2).apply{topMargin=dp(9)})
        section("Your saved plans")
        showSaved()
    }
    private fun addField(caption:String,view:EditText){
        content.addView(label(caption,12,muted,true),LinearLayout.LayoutParams(-1,-2).apply{topMargin=dp(7)})
        content.addView(view,LinearLayout.LayoutParams(-1,dp(49)).apply{bottomMargin=dp(3)})
    }
    private fun section(title:String,sub:String=""){
        content.addView(label(title,18,ink,true),LinearLayout.LayoutParams(-1,-2).apply{topMargin=dp(19);bottomMargin=dp(3)})
        if(sub.isNotBlank())content.addView(label(sub,12,muted))
    }
    private fun dateLabel(c:Calendar)=SimpleDateFormat("EEE, dd MMM yyyy",Locale.getDefault()).format(c.time)
    private fun pickDate(isStart:Boolean){
        val c=if(isStart)start else end
        DatePickerDialog(this,{_,y,m,d->
            val selected=Calendar.getInstance().apply{set(y,m,d,0,0,0);set(Calendar.MILLISECOND,0)}
            if(isStart)start=selected else end=selected
            startButton.text=dateLabel(start);endButton.text=dateLabel(end)
        },c.get(Calendar.YEAR),c.get(Calendar.MONTH),c.get(Calendar.DAY_OF_MONTH)).show()
    }
    private fun tripDays()=((end.timeInMillis-start.timeInMillis)/86400000L).toInt().coerceAtLeast(0)
    private fun money(v:Double)="%,.0f".format(Locale("en","IN"),v)
    private fun saveTrip(place:String){
        val trips=runCatching{JSONArray(prefs.getString("planned_trips","[]"))}.getOrDefault(JSONArray())
        val people=travellers.text.toString().toIntOrNull()?.coerceAtLeast(1)?:1
        val obj=JSONObject().put("name",tripName.text.toString().ifBlank{"Trip to $place"}).put("destination",place)
            .put("start",dateLabel(start)).put("end",dateLabel(end)).put("days",tripDays()).put("travellers",people)
            .put("budget",estimate).put("stay",stay.text.toString()).put("food",food.text.toString())
            .put("transport",transport.text.toString()).put("activities",activities.text.toString())
        trips.put(obj);prefs.edit().putString("planned_trips",trips.toString()).apply()
    }
    private fun showSaved(){
        val saved=runCatching{JSONArray(prefs.getString("planned_trips","[]"))}.getOrDefault(JSONArray())
        if(saved.length()==0){content.addView(label("No trips saved yet. Create a plan above to get started.",13,muted));return}
        for(i in saved.length()-1 downTo 0){
            val t=saved.getJSONObject(i)
            val card=LinearLayout(this).apply{orientation=1;background=shape(Color.WHITE,16);setPadding(dp(14),dp(12),dp(14),dp(10))}
            card.addView(label(t.optString("name"),16,ink,true))
            card.addView(label(t.optString("destination")+" · "+t.optString("start")+" to "+t.optString("end"),12,muted))
            card.addView(label(t.optInt("days").toString()+" days · "+t.optInt("travellers")+" travellers · ₹"+money(t.optDouble("budget")),13,blue,true))
            content.addView(card,LinearLayout.LayoutParams(-1,-2).apply{topMargin=dp(8)})
        }
    }
}
