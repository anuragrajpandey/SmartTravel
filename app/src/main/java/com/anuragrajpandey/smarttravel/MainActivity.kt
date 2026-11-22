package com.anuragrajpandey.smarttravel

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.card.MaterialCardView
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import org.json.JSONObject
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import kotlin.concurrent.thread

private const val BG=0xFF090A0C.toInt()
private const val PANEL=0xFF17191D.toInt()
private const val FG=0xFFF5F5F4.toInt()
private const val MUTED=0xFF969BA4.toInt()
private const val ACCENT=0xFFD9D9D5.toInt()
private const val BORDER=0xFF2B2F35.toInt()
data class Destination(val name:String,val region:String,val mood:String,val budget:String,val detail:String)

class MainActivity:AppCompatActivity(){
 private lateinit var content:LinearLayout
 private val prefs by lazy{getSharedPreferences("flipdoor",MODE_PRIVATE)}
 private var translator:Translator?=null
 private val places=listOf(
  Destination("Rishikesh","Uttarakhand","Adventure","₹8k–₹15k","River trails, cafés and Himalayan foothills."),
  Destination("Jaipur","Rajasthan","Culture","₹7k–₹14k","Historic forts, old-city lanes and regional food."),
  Destination("Munnar","Kerala","Nature","₹10k–₹18k","Tea gardens, misty roads and quiet viewpoints."),
  Destination("Pondicherry","Tamil Nadu","Coast","₹8k–₹16k","Seafront walks, heritage streets and cafés."),
  Destination("Ziro Valley","Arunachal Pradesh","Hidden gem","₹12k–₹20k","Green valleys and Apatani culture. Check permits."),
  Destination("Gokarna","Karnataka","Coast","₹7k–₹13k","Quiet beaches and cliffside trails.")
 )
 private val langs=listOf("English","Hindi","Spanish","French","German","Japanese","Korean","Arabic","Portuguese","Russian","Chinese")
 private val tags=listOf("en","hi","es","fr","de","ja","ko","ar","pt","ru","zh")
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);window.statusBarColor=BG;window.navigationBarColor=BG;show("Explore")}
 override fun onDestroy(){translator?.close();super.onDestroy()}
 private fun dp(v:Int)=(v*resources.displayMetrics.density).toInt()
 private fun shape(c:Int,r:Int=16)=GradientDrawable().apply{setColor(c);cornerRadius=dp(r).toFloat()}
 private fun txt(s:String,n:Int=14,c:Int=FG,b:Boolean=false)=TextView(this).apply{text=s;textSize=n.toFloat();setTextColor(c);if(b)setTypeface(typeface,Typeface.BOLD);setPadding(0,dp(3),0,dp(5))}
 private fun btn(s:String,primary:Boolean=false,click:()->Unit)=TextView(this).apply{text=s;gravity=Gravity.CENTER;textSize=14f;setTextColor(if(primary)BG else FG);setTypeface(typeface,Typeface.BOLD);background=shape(if(primary)ACCENT else 0xFF24272C.toInt(),14);setPadding(dp(14),dp(13),dp(14),dp(13));setOnClickListener{click()}}
 private fun heading(k:String,t:String,d:String){content.addView(txt("FLIPDOOR  /  "+k,10,MUTED,true));content.addView(txt(t,29,FG,true));content.addView(txt(d,14,MUTED))}
 private fun section(s:String){content.addView(txt(s,19,FG,true),LinearLayout.LayoutParams(-1,-2).apply{topMargin=dp(16);bottomMargin=dp(8)})}
 private fun tile(t:String,d:String,click:(()->Unit)?=null){
  val c=MaterialCardView(this).apply{radius=dp(18).toFloat();cardElevation=0f;setCardBackgroundColor(PANEL);strokeWidth=dp(1);strokeColor=BORDER;if(click!=null)setOnClickListener{click()}}
  val box=LinearLayout(this).apply{orientation=1;setPadding(dp(16),dp(14),dp(16),dp(12))}
  box.addView(txt(t,16,FG,true));box.addView(txt(d,13,MUTED));c.addView(box);content.addView(c,LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=dp(9)})
 }
 private fun show(tab:String){
  val root=LinearLayout(this).apply{orientation=1;setBackgroundColor(BG)}
  val scroll=ScrollView(this)
  content=LinearLayout(this).apply{orientation=1;setPadding(dp(20),dp(18),dp(20),dp(22))}
  scroll.addView(content);root.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
  val nav=LinearLayout(this).apply{gravity=Gravity.CENTER;background=shape(PANEL,18);setPadding(dp(4),dp(5),dp(4),dp(5))}
  listOf("Explore","Stay","Discover","Tools","Trips").forEach{n->
   val v=TextView(this).apply{text=n;textSize=11f;gravity=Gravity.CENTER;setTextColor(if(n==tab)BG else MUTED);background=shape(if(n==tab)ACCENT else Color.TRANSPARENT,12);setPadding(dp(7),dp(12),dp(7),dp(12));setOnClickListener{show(n)}}
   nav.addView(v,LinearLayout.LayoutParams(0,-2,1f))
  }
  root.addView(nav,LinearLayout.LayoutParams(-1,-2).apply{setMargins(dp(12),dp(5),dp(12),dp(8))});setContentView(root)
  when(tab){"Explore"->explore();"Stay"->stay();"Discover"->discover();"Tools"->tools();else->trips()}
 }
 private fun explore(){
  heading("YOUR TRAVEL COMPANION","Go beyond the usual.","Destinations, stays, food and useful tools in one place.")
  val q=EditText(this).apply{hint="Search destinations";setHintTextColor(MUTED);setTextColor(FG);background=shape(0xFF22252A.toInt());setPadding(dp(14),dp(10),dp(14),dp(10))}
  content.addView(q,LinearLayout.LayoutParams(-1,dp(50)).apply{topMargin=dp(14)})
  q.setOnEditorActionListener{_,_,_->val s=q.text.toString();if(s.isNotBlank())destination(places.firstOrNull{it.name.contains(s,true)}?:Destination(s,"Explore","Destination","Varies","Discover stays, food and experiences around "+s));true}
  section("Your travel toolkit")
  tile("Hotels & stays","Hotels, hostels, homestays"){show("Stay")}
  tile("Food guide","Regional dishes and local favourites"){food()}
  tile("Trip budget","Estimate and track trip costs"){budget()}
  tile("Translator","Download language packs for offline use"){translate()}
  tile("Hidden places","Discover quieter corners"){show("Discover")}
  tile("Live weather","Current conditions and 3-day forecast"){weather()}
  tile("Map preview","Mock map, API integration planned"){mapPreview()}
  section("Places to start")
  places.take(4).forEach{p->tile(p.name,p.region+" · "+p.mood+" · "+p.budget){destination(p)}}
 }
 private fun stay(){heading("STAYS","Find your kind of stay.","Explore stay styles. Live booking availability is not connected.");listOf("Boutique hotels","Hostels","Homestays","Resorts & retreats").forEach{tile(it,"Sample category · connect booking inventory for live prices")};section("Popular stay areas");listOf("Jaipur · Pink City","Rishikesh · Tapovan","Goa · Anjuna","Munnar · Chithirapuram").forEach{tile(it,"Area guide · sample content")}}
 private fun discover(){heading("DISCOVER","Take the quieter road.","Little-known places and slower experiences.");places.filter{it.mood=="Hidden gem"||it.name=="Gokarna"}.plus(Destination("Chopta","Uttarakhand","Mountain escape","₹6k–₹11k","Alpine meadows and the Tungnath trail.")).plus(Destination("Majuli","Assam","River island","₹8k–₹14k","Satras, river landscapes and local crafts.")).forEach{p->tile(p.name,p.region+" · "+p.budget+"\n"+p.detail){destination(p)}}}
 private fun destination(p:Destination){content.removeAllViews();heading("DESTINATION",p.name,p.region+" · "+p.mood);section("The feel");tile("Why go",p.detail);tile("Typical trip budget",p.budget);tile("Stay ideas","Hotels, hostels and homestays"){show("Stay")};tile("Local food","Regional food guide"){food()};tile("Weather","Check conditions before travelling"){weather()};content.addView(btn("Save to My Trips",true){prefs.edit().putString("saved",p.name).apply();Toast.makeText(this,"Saved "+p.name,Toast.LENGTH_SHORT).show()},LinearLayout.LayoutParams(-1,dp(50)))}
 private fun food(){content.removeAllViews();heading("FOOD","Taste the destination.","Regional favourites to try.");listOf("Rajasthan · Dal baati churma, pyaaz kachori","Uttarakhand · Kafuli, aloo ke gutke","Kerala · Appam, puttu and kadala curry","Tamil Nadu · Dosa, pongal and filter coffee","Karnataka · Bisi bele bath, neer dosa").forEach{tile(it,"Regional food guide · sample suggestions")}}
 private fun tools(){heading("TOOLS","Travel lighter.","Utilities for the road.");tile("Trip budget","Estimate trip costs"){budget()};tile("Translator","Downloadable offline language models"){translate()};tile("Live weather","Current conditions and forecast"){weather()};tile("Map preview","Mock map ready for later API integration"){mapPreview()}}
 private fun budget(){
  content.removeAllViews();heading("BUDGET PLANNER","Know your numbers.","Estimate trip costs. Your entries stay on this device.")
  val f=listOf("Days","Travellers","Stay per night (₹)","Food per person / day (₹)","Transport total (₹)","Activities & extras (₹)").map{hint->EditText(this).apply{this.hint=hint;setHintTextColor(MUTED);setTextColor(FG);background=shape(0xFF22252A.toInt());setPadding(dp(12),dp(8),dp(12),dp(8))}}
  f.forEach{content.addView(it,LinearLayout.LayoutParams(-1,dp(48)).apply{bottomMargin=dp(8)})}
  val out=txt("Your estimate will appear here.",16,FG,true);content.addView(out)
  content.addView(btn("Calculate budget",true){
   val v=f.map{it.text.toString().toDoubleOrNull()?:0.0};val days=v[0].coerceAtLeast(1.0);val people=v[1].coerceAtLeast(1.0);val total=days*v[2]+days*people*v[3]+v[4]+v[5]
   out.text="Estimated total: ₹"+ "%,.0f".format(Locale("en","IN"),total)+"\nPer traveller: ₹"+ "%,.0f".format(Locale("en","IN"),total/people);prefs.edit().putLong("budget",total.toLong()).apply()
  },LinearLayout.LayoutParams(-1,dp(50)))
 }
 private fun translate(){
  content.removeAllViews();heading("TRANSLATOR","Words open doors.","Download a language model once, then translate offline.")
  val a=Spinner(this);val b=Spinner(this);val ad=ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,langs);a.adapter=ad;b.adapter=ad;b.setSelection(1)
  section("From");content.addView(a);section("Translate to");content.addView(b)
  val src=EditText(this).apply{hint="Type a phrase";setHintTextColor(MUTED);setTextColor(FG);minLines=3;background=shape(0xFF22252A.toInt());setPadding(dp(12),dp(10),dp(12),dp(10))};content.addView(src)
  val status=txt("First use downloads the selected language model.",12,MUTED);content.addView(status)
  val out=txt("Translation appears here.",16,FG);content.addView(out)
  content.addView(btn("Download language & translate",true){
   val from=TranslateLanguage.fromLanguageTag(tags[a.selectedItemPosition]);val to=TranslateLanguage.fromLanguageTag(tags[b.selectedItemPosition]);val phrase=src.text.toString().trim()
   if(from==null||to==null||from==to||phrase.isBlank()){status.text="Choose different languages and enter a phrase.";return@btn}
   status.text="Downloading language pack…";translator?.close();translator=Translation.getClient(TranslatorOptions.Builder().setSourceLanguage(from).setTargetLanguage(to).build())
   translator?.downloadModelIfNeeded(DownloadConditions.Builder().build())?.addOnSuccessListener{status.text="Pack ready. Translation runs on-device.";translator?.translate(phrase)?.addOnSuccessListener{out.text=it}?.addOnFailureListener{status.text="Translation failed."}}?.addOnFailureListener{status.text="Download failed. Check internet."}
  },LinearLayout.LayoutParams(-1,dp(52)))
  content.addView(txt("After download, supported language pairs translate without internet. Model availability varies.",12,MUTED))
 }
 private fun weather(){
  content.removeAllViews();heading("LIVE WEATHER","Check the sky ahead.","Current conditions and 3-day forecast from Open-Meteo.")
  val city=EditText(this).apply{hint="City";setText("Mathura");setTextColor(FG);setHintTextColor(MUTED);background=shape(0xFF22252A.toInt());setPadding(dp(12),dp(10),dp(12),dp(10))};content.addView(city)
  val out=txt("Weather has not been loaded.",15,FG);content.addView(out)
  content.addView(btn("Get live weather",true){
   out.text="Loading…";thread{try{
    val g=JSONObject(URL("https://geocoding-api.open-meteo.com/v1/search?name="+URLEncoder.encode(city.text.toString(),"UTF-8")+"&count=1&language=en&format=json").readText())
    val loc=g.optJSONArray("results")?.optJSONObject(0)?:throw Exception()
    val d=JSONObject(URL("https://api.open-meteo.com/v1/forecast?latitude="+loc.getDouble("latitude")+"&longitude="+loc.getDouble("longitude")+"&current=temperature_2m,relative_humidity_2m,apparent_temperature,wind_speed_10m&daily=temperature_2m_max,temperature_2m_min&timezone=auto&forecast_days=3").readText())
    val n=d.getJSONObject("current");val day=d.getJSONObject("daily")
    val result=loc.getString("name")+"\n"+n.getDouble("temperature_2m")+"°C · Feels like "+n.getDouble("apparent_temperature")+"°C\nHumidity "+n.getInt("relative_humidity_2m")+"% · Wind "+n.getDouble("wind_speed_10m")+" km/h\n\nNext 3 days\n"+(0..2).joinToString("\n"){i->day.getJSONArray("time").getString(i)+" · "+day.getJSONArray("temperature_2m_min").getDouble(i)+"° / "+day.getJSONArray("temperature_2m_max").getDouble(i)+"°"}
    runOnUiThread{out.text=result}
   }catch(e:Exception){runOnUiThread{out.text="Could not load weather. Check the city and connection."}}}
  },LinearLayout.LayoutParams(-1,dp(50)))
  content.addView(txt("Weather data by Open-Meteo. Forecasts are estimates.",11,MUTED))
 }
 private fun mapPreview(){
  content.removeAllViews();heading("MAP PREVIEW","Your route, at a glance.","Mock interface only. Live tiles, GPS and directions are not connected.")
  val m=TextView(this).apply{text="  ·   ╲      ●\n──────╲────\n ●─────╲──●\n ·      ╲   ·\n────●──────";textSize=25f;gravity=Gravity.CENTER;setTextColor(ACCENT);background=shape(0xFF26302C.toInt(),22)}
  content.addView(m,LinearLayout.LayoutParams(-1,dp(270)).apply{topMargin=dp(12);bottomMargin=dp(12)})
  tile("Nearby","Hotels · Restaurants · Attractions");tile("Route planner","Multi-stop routing planned after maps API integration.")
 }
 private fun trips(){
  heading("YOUR SPACE","Trips & saved places.","Keep the places and estimates you want to revisit.")
  val saved=prefs.getString("saved",null);if(saved==null)tile("Nothing saved yet","Open a destination and tap Save to My Trips.")else tile(saved,"Saved locally on this phone.")
  val budget=prefs.getLong("budget",-1);if(budget>=0)tile("Last budget estimate","₹"+ "%,d".format(Locale("en","IN"),budget))
  tile("Travel checklist","Documents · Stay confirmation · Local transport · Emergency contacts")
 }
}
