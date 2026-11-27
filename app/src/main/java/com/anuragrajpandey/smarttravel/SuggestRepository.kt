package com.anuragrajpandey.smarttravel

import org.json.JSONObject
import java.net.URLEncoder
import java.util.Locale

data class Suggestion(
    val name: String, val district: String, val state: String, val kind: String,
    val lat: Double, val lon: Double
) {
    val detail: String get() = listOf(kind, district, state).filter { it.isNotBlank() }.distinct().joinToString(" · ")
    fun toCity() = CityLocation(name, state, "India", lat, lon)
}

/** Autocomplete for Indian places only: cities/towns/villages (Open-Meteo) + landmarks & POIs (Photon/OSM). */
object SuggestRepository {
    private const val INDIA_BBOX = "68.1,6.5,97.5,35.7"
    private val poiKeys = setOf("place", "tourism", "historic", "natural", "leisure", "waterway", "boundary")
    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    fun suggest(query: String, bias: LocationHelper.Saved? = null): List<Suggestion> {
        val q = query.trim()
        if (q.length < 2) return emptyList()
        val cities = Net.async { runCatching { openMeteo(q) }.getOrDefault(emptyList()) }
        val pois = Net.async { runCatching { photon(q, bias) }.getOrDefault(emptyList()) }
        val a = Net.await(cities, 5000, emptyList())
        val b = Net.await(pois, 5000, emptyList())
        val seen = HashSet<String>()
        val out = ArrayList<Suggestion>()
        // Places that start with what was typed come first, then the rest; cities before POIs on ties.
        for (s in (a + b).sortedBy { if (it.name.startsWith(q, true)) 0 else 1 }) {
            if (seen.add(s.name.lowercase(Locale.US) + "|" + s.state.lowercase(Locale.US))) out.add(s)
            if (out.size >= 8) break
        }
        return out
    }

    private fun openMeteo(q: String): List<Suggestion> {
        val j = JSONObject(CityDataRepository.get("https://geocoding-api.open-meteo.com/v1/search?name=${enc(q)}&count=8&language=en&countryCode=IN&format=json"))
        val r = j.optJSONArray("results") ?: return emptyList()
        return (0 until r.length()).mapNotNull { i ->
            val o = r.getJSONObject(i)
            if (o.optString("country_code", "IN") != "IN") return@mapNotNull null
            val code = o.optString("feature_code")
            val kind = when {
                code.startsWith("PPLA") || code == "PPLC" -> "City"
                code.startsWith("PPL") -> "Town / village"
                code.startsWith("ADM") -> "District / region"
                else -> "Place"
            }
            Suggestion(o.optString("name"), o.optString("admin2"), o.optString("admin1"), kind, o.getDouble("latitude"), o.getDouble("longitude"))
        }
    }

    private fun photon(q: String, bias: LocationHelper.Saved?): List<Suggestion> {
        var url = "https://photon.komoot.io/api/?q=${enc(q)}&limit=12&lang=en&bbox=$INDIA_BBOX"
        if (bias != null) url += "&lat=${bias.lat}&lon=${bias.lon}"
        val f = JSONObject(CityDataRepository.get(url)).optJSONArray("features") ?: return emptyList()
        return (0 until f.length()).mapNotNull { i ->
            val o = f.getJSONObject(i)
            val p = o.optJSONObject("properties") ?: return@mapNotNull null
            if (!p.optString("countrycode").equals("IN", true)) return@mapNotNull null
            if (p.optString("osm_key") !in poiKeys) return@mapNotNull null
            val name = p.optString("name").trim()
            if (name.isBlank()) return@mapNotNull null
            val c = o.optJSONObject("geometry")?.optJSONArray("coordinates") ?: return@mapNotNull null
            val kind = when (p.optString("osm_key")) {
                "place" -> "Area"
                "tourism", "historic" -> "Attraction"
                "natural", "waterway" -> "Nature"
                "leisure" -> "Park / leisure"
                else -> "Place"
            }
            Suggestion(name, p.optString("county").ifBlank { p.optString("city") }, p.optString("state"), kind, c.getDouble(1), c.getDouble(0))
        }
    }
}
