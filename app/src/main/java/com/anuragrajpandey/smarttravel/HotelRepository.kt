package com.anuragrajpandey.smarttravel

import java.util.Locale

data class Hotel(
    val name: String, val kind: String, val stars: Int?, val lat: Double, val lon: Double,
    val distanceKm: Double, val website: String?, val phone: String?, val address: String,
    val amenities: List<String>, val listedPrice: String?, val currency: String,
    val priceLow: Int, val priceHigh: Int, val score: Double
) {
    val priceMid get() = (priceLow + priceHigh) / 2
    fun priceText(): String {
        fun f(v: Int) = currency + String.format(Locale.US, "%,d", v)
        return "${f(priceLow)} – ${f(priceHigh)}"
    }
}

/**
 * Nearby hotels from OpenStreetMap (open data, fetched live through Overpass).
 * OSM rarely stores room rates, so when a hotel has no listed price we show a clearly-labelled
 * estimated nightly range based on its star rating and type. Real, live rates are one tap away
 * in the in-app "Live price" view.
 */
object HotelRepository {
    private val inrBase = mapOf(1 to 1000, 2 to 1800, 3 to 3200, 4 to 6500, 5 to 13500)
    private val usdBase = mapOf(1 to 40, 2 to 60, 3 to 95, 4 to 160, 5 to 300)
    private val inrKind = mapOf("hotel" to 2800, "resort" to 6500, "motel" to 1800, "guest_house" to 1500, "hostel" to 600, "apartment" to 3000, "chalet" to 3500)
    private val usdKind = mapOf("hotel" to 90, "resort" to 170, "motel" to 70, "guest_house" to 65, "hostel" to 28, "apartment" to 100, "chalet" to 120)

    fun search(lat: Double, lon: Double, inIndia: Boolean, radiusM: Int = 6000): List<Hotel> {
        val q = "[out:json][timeout:25];nwr[\"tourism\"~\"^(hotel|hostel|guest_house|motel|apartment|resort|chalet)$\"][\"name\"]" +
            "(around:$radiusM,$lat,$lon);out center 200;"
        val els = GeoRepository.overpass(q).optJSONArray("elements") ?: return emptyList()
        val currency = if (inIndia) "₹" else "$"
        val out = ArrayList<Hotel>()
        for (i in 0 until els.length()) {
            val e = els.getJSONObject(i)
            val tags = e.optJSONObject("tags") ?: continue
            val name = tags.optString("name").trim()
            if (name.isBlank()) continue
            val c = e.optJSONObject("center")
            val hLat = if (e.has("lat")) e.getDouble("lat") else c?.optDouble("lat") ?: continue
            val hLon = if (e.has("lon")) e.getDouble("lon") else c?.optDouble("lon") ?: continue
            val kind = tags.optString("tourism")
            val stars = Regex("\\d").find(tags.optString("stars"))?.value?.toIntOrNull()?.coerceIn(1, 5)
            val website = (tags.optString("website").ifBlank { tags.optString("contact:website") }).takeIf { it.startsWith("http") }
            val phone = tags.optString("phone").ifBlank { tags.optString("contact:phone") }.takeIf { it.isNotBlank() }
            val address = listOf(
                listOf(tags.optString("addr:housenumber"), tags.optString("addr:street")).filter { it.isNotBlank() }.joinToString(" "),
                tags.optString("addr:city")
            ).filter { it.isNotBlank() }.joinToString(", ")
            val amenities = buildList {
                if (tags.optString("internet_access") in listOf("wlan", "yes", "wifi")) add("Wi-Fi")
                if (tags.optString("swimming_pool") == "yes" || tags.optString("pool") == "yes") add("Pool")
                if (tags.optString("air_conditioning") == "yes") add("AC")
                if (tags.optString("breakfast") == "yes") add("Breakfast")
                if (tags.optString("wheelchair") == "yes") add("Accessible")
            }
            val listed = tags.optString("charge").ifBlank { tags.optString("fee") }
                .takeIf { s -> s.any { it.isDigit() } && s.length < 40 }

            val base = if (stars != null) (if (inIndia) inrBase else usdBase).getValue(stars)
            else (if (inIndia) inrKind else usdKind)[kind] ?: (if (inIndia) 2800 else 90)
            val step = if (inIndia) 100 else 5
            val low = ((base * 0.8) / step).toInt() * step
            val high = ((base * 1.35) / step).toInt() * step

            val dist = GeoRepository.haversineKm(lat, lon, hLat, hLon)
            val score = (stars ?: 0) * 20.0 +
                (if (kind == "hotel" || kind == "resort") 6 else 0) +
                (if (website != null) 8 else 0) + (if (phone != null) 5 else 0) +
                amenities.size * 3 + (if (address.isNotBlank()) 2 else 0) - dist * 1.2
            out.add(Hotel(name, kind, stars, hLat, hLon, dist, website, phone, address, amenities, listed, currency, low, high, score))
        }
        return out.distinctBy { it.name.lowercase(Locale.US) }
    }

    fun kindLabel(kind: String) = when (kind) {
        "guest_house" -> "Guest house"
        "hotel" -> "Hotel"
        "hostel" -> "Hostel"
        "motel" -> "Motel"
        "apartment" -> "Apartment"
        "resort" -> "Resort"
        "chalet" -> "Chalet"
        else -> "Stay"
    }
}
