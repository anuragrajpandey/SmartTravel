package com.anuragrajpandey.smarttravel

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class CityLocation(val name:String,val region:String,val country:String,val latitude:Double,val longitude:Double)
data class WeatherSnapshot(val temperature:Double,val feelsLike:Double,val humidity:Int,val wind:Double,val description:String,val forecast:List<String>)
data class CityPlace(val name:String,val type:String,val detail:String,val latitude:Double,val longitude:Double,val website:String?)
data class WikiItem(val title:String,val description:String,val excerpt:String,val url:String)
data class CityGuide(val city:CityLocation,val weather:WeatherSnapshot?,val overview:WikiItem?,val hotels:List<CityPlace>,val restaurants:List<CityPlace>,val attractions:List<CityPlace>,val foods:List<WikiItem>)

object CityDataRepository {
    private fun get(url:String):String {
        val connection=URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout=12000
        connection.readTimeout=18000
        connection.setRequestProperty("User-Agent","FlipDoor/1.0 (Android travel discovery app)")
        return try {
            val code=connection.responseCode
            val stream=if(code in 200..299) connection.inputStream else connection.errorStream
            val body=stream.bufferedReader().use{it.readText()}
            if(code !in 200..299) throw IllegalStateException("Service returned HTTP $code")
            body
        } finally { connection.disconnect() }
    }

    private fun enc(value:String)=URLEncoder.encode(value,"UTF-8")

    fun searchCity(query:String):CityGuide {
        require(query.isNotBlank()) { "Enter a city name to search." }
        val geo=JSONObject(get("https://geocoding-api.open-meteo.com/v1/search?name=${enc(query)}&count=5&language=en&format=json"))
        val results=geo.optJSONArray("results") ?: throw IllegalStateException("No matching city found. Try adding its state or country.")
        if(results.length()==0) throw IllegalStateException("No matching city found. Try another spelling or add the country.")
        val item=results.getJSONObject(0)
        val city=CityLocation(item.optString("name",query),item.optString("admin1",""),item.optString("country",""),item.getDouble("latitude"),item.getDouble("longitude"))
        val weather=runCatching { loadWeather(city) }.getOrNull()
        val places=runCatching { loadPlaces(city) }.getOrDefault(emptyList())
        val overview=runCatching { wikiSearch("${city.name} ${city.country}",1).firstOrNull() }.getOrNull()
        val foods=runCatching { wikiSearch("${city.name} ${city.country} traditional food cuisine dishes",6) }.getOrDefault(emptyList())
        return CityGuide(
            city,weather,overview,
            places.filter{it.type=="Stay"}.take(8),
            places.filter{it.type=="Food & drink"}.take(10),
            places.filter{it.type=="Attraction"}.take(10),
            foods
        )
    }

    private fun loadWeather(city:CityLocation):WeatherSnapshot {
        val url="https://api.open-meteo.com/v1/forecast?latitude=${city.latitude}&longitude=${city.longitude}&current=temperature_2m,relative_humidity_2m,apparent_temperature,wind_speed_10m,weather_code&daily=temperature_2m_max,temperature_2m_min,precipitation_probability_max,precipitation_sum,weather_code&timezone=auto&past_days=7&forecast_days=8"
        val json=JSONObject(get(url))
        val current=json.getJSONObject("current")
        val daily=json.getJSONObject("daily")
        val forecast=(0 until daily.getJSONArray("time").length()).map { i ->
            val date=daily.getJSONArray("time").getString(i)
            val low=daily.getJSONArray("temperature_2m_min").getDouble(i).toInt()
            val high=daily.getJSONArray("temperature_2m_max").getDouble(i).toInt()
            val rain=daily.optJSONArray("precipitation_probability_max")?.optInt(i) ?: 0
            val rainMm=daily.optJSONArray("precipitation_sum")?.optDouble(i) ?: 0.0
            val code=daily.optJSONArray("weather_code")?.optInt(i) ?: -1
            date+"|"+low+"|"+high+"|"+rain+"|"+rainMm+"|"+weatherLabel(code)
        }
        WeatherSnapshot(current.getDouble("temperature_2m"),current.getDouble("apparent_temperature"),current.getInt("relative_humidity_2m"),current.getDouble("wind_speed_10m"),weatherLabel(current.optInt("weather_code")),forecast)
    }

    private fun weatherLabel(code:Int)=when(code) {
        0->"Clear sky";1,2->"Mostly clear";3->"Cloudy";45,48->"Fog";51,53,55->"Drizzle";61,63,65->"Rain";71,73,75->"Snow";80,81,82->"Rain showers";95,96,99->"Thunderstorm";else->"Current conditions"
    }

    private fun loadPlaces(city:CityLocation):List<CityPlace> {
        val lat=city.latitude
        val lon=city.longitude
        val query="[out:json][timeout:25];(nwr[\"tourism\"~\"hotel|hostel|guest_house\"](around:12000,$lat,$lon);nwr[\"amenity\"~\"restaurant|cafe\"](around:12000,$lat,$lon);nwr[\"tourism\"~\"attraction|museum\"](around:12000,$lat,$lon););out center;"
        val connection=URL("https://overpass-api.de/api/interpreter").openConnection() as HttpURLConnection
        connection.requestMethod="POST"
        connection.doOutput=true
        connection.connectTimeout=12000
        connection.readTimeout=30000
        connection.setRequestProperty("User-Agent","FlipDoor/1.0 (Android travel discovery app)")
        connection.setRequestProperty("Content-Type","application/x-www-form-urlencoded; charset=UTF-8")
        return try {
            connection.outputStream.use { it.write(("data="+enc(query)).toByteArray(Charsets.UTF_8)) }
            val code=connection.responseCode
            val stream=if(code in 200..299) connection.inputStream else connection.errorStream
            val body=stream.bufferedReader().use{it.readText()}
            if(code !in 200..299) throw IllegalStateException("Places service temporarily unavailable")
            val elements=JSONObject(body).optJSONArray("elements") ?: return emptyList()
            (0 until elements.length()).mapNotNull { i ->
                val e=elements.getJSONObject(i)
                val tags=e.optJSONObject("tags") ?: return@mapNotNull null
                val name=tags.optString("name","").trim()
                if(name.isBlank()) return@mapNotNull null
                val tourism=tags.optString("tourism")
                val amenity=tags.optString("amenity")
                val type=when {
                    tourism in listOf("hotel","hostel","guest_house")->"Stay"
                    amenity in listOf("restaurant","cafe")->"Food & drink"
                    tourism in listOf("attraction","museum")->"Attraction"
                    else->return@mapNotNull null
                }
                val center=e.optJSONObject("center")
                val pLat=e.optDouble("lat",center?.optDouble("lat") ?: city.latitude)
                val pLon=e.optDouble("lon",center?.optDouble("lon") ?: city.longitude)
                val detail=listOf(tags.optString("cuisine"),tags.optString("addr:street"),tags.optString("opening_hours")).filter{it.isNotBlank()}.joinToString(" · ").ifBlank { "OpenStreetMap listing" }
                CityPlace(name,type,detail,pLat,pLon,tags.optString("website").takeIf{it.startsWith("http")})
            }.distinctBy{it.name.lowercase()}.sortedBy{it.name.lowercase()}.take(100)
        } finally { connection.disconnect() }
    }

    private fun wikiSearch(query:String,limit:Int):List<WikiItem> {
        val json=JSONObject(get("https://en.wikipedia.org/w/rest.php/v1/search/page?q=${enc(query)}&limit=$limit"))
        val pages=json.optJSONArray("pages") ?: return emptyList()
        return (0 until pages.length()).mapNotNull { i ->
            val page=pages.getJSONObject(i)
            val title=page.optString("title").takeIf{it.isNotBlank()} ?: return@mapNotNull null
            val description=page.optString("description","")
            val excerpt=page.optString("excerpt","").replace(Regex("<[^>]*>")," ").replace("&quot;","\"").replace("&#39;","'").replace("&amp;","&").trim()
            WikiItem(title,description,excerpt,"https://en.wikipedia.org/wiki/"+enc(title).replace("+","_"))
        }
    }
}
