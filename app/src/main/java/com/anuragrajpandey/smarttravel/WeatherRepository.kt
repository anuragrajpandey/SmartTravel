package com.anuragrajpandey.smarttravel

import org.json.JSONObject

data class HourPoint(val time: String, val temp: Double, val code: Int, val rainProb: Int, val isDay: Boolean)
data class DayPoint(
    val date: String, val min: Double, val max: Double, val code: Int, val rainProb: Int,
    val rainMm: Double, val sunrise: String, val sunset: String, val uv: Double
)
data class WeatherReport(
    val temp: Double, val feelsLike: Double, val humidity: Int, val windKmh: Double, val windDir: Int,
    val gustKmh: Double, val pressure: Double, val cloud: Int, val visibilityKm: Double,
    val isDay: Boolean, val code: Int, val localTime: String,
    val hourly: List<HourPoint>, val daily: List<DayPoint>
) {
    val today: String get() = localTime.take(10)
    val todayPoint: DayPoint? get() = daily.firstOrNull { it.date == today }
}

object WeatherRepository {
    fun fetch(lat: Double, lon: Double): WeatherReport {
        val url = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
            "&current=temperature_2m,relative_humidity_2m,apparent_temperature,is_day,weather_code,cloud_cover,pressure_msl,wind_speed_10m,wind_direction_10m,wind_gusts_10m" +
            "&hourly=temperature_2m,weather_code,precipitation_probability,is_day,visibility" +
            "&daily=weather_code,temperature_2m_max,temperature_2m_min,sunrise,sunset,uv_index_max,precipitation_probability_max,precipitation_sum" +
            "&timezone=auto&past_days=7&forecast_days=8"
        val json = JSONObject(CityDataRepository.get(url))
        val cur = json.getJSONObject("current")
        val h = json.getJSONObject("hourly")
        val d = json.getJSONObject("daily")
        val now = cur.getString("time")

        val hTimes = h.getJSONArray("time")
        var start = 0
        for (i in 0 until hTimes.length()) {
            if (hTimes.getString(i).take(13) >= now.take(13)) { start = i; break }
        }
        val hourly = (start until minOf(start + 24, hTimes.length())).map { i ->
            HourPoint(
                hTimes.getString(i), h.getJSONArray("temperature_2m").optDouble(i),
                h.getJSONArray("weather_code").optInt(i), h.optJSONArray("precipitation_probability")?.optInt(i) ?: 0,
                (h.optJSONArray("is_day")?.optInt(i) ?: 1) == 1
            )
        }
        val vis = (h.optJSONArray("visibility")?.optDouble(start) ?: 10000.0) / 1000.0

        val dTimes = d.getJSONArray("time")
        val daily = (0 until dTimes.length()).map { i ->
            DayPoint(
                dTimes.getString(i), d.getJSONArray("temperature_2m_min").optDouble(i), d.getJSONArray("temperature_2m_max").optDouble(i),
                d.getJSONArray("weather_code").optInt(i), d.optJSONArray("precipitation_probability_max")?.optInt(i) ?: 0,
                d.optJSONArray("precipitation_sum")?.optDouble(i) ?: 0.0,
                d.optJSONArray("sunrise")?.optString(i).orEmpty(), d.optJSONArray("sunset")?.optString(i).orEmpty(),
                d.optJSONArray("uv_index_max")?.optDouble(i) ?: 0.0
            )
        }
        return WeatherReport(
            cur.getDouble("temperature_2m"), cur.getDouble("apparent_temperature"), cur.getInt("relative_humidity_2m"),
            cur.getDouble("wind_speed_10m"), cur.optInt("wind_direction_10m"), cur.optDouble("wind_gusts_10m"),
            cur.optDouble("pressure_msl"), cur.optInt("cloud_cover"), vis,
            cur.optInt("is_day", 1) == 1, cur.optInt("weather_code"), now, hourly, daily
        )
    }
}

/** WMO weather-code → label, icon and sky gradient. */
object WeatherStyle {
    fun label(code: Int) = when (code) {
        0 -> "Clear sky"
        1 -> "Mainly clear"
        2 -> "Partly cloudy"
        3 -> "Overcast"
        45, 48 -> "Fog"
        51, 53, 55 -> "Drizzle"
        56, 57 -> "Freezing drizzle"
        61 -> "Light rain"
        63 -> "Rain"
        65 -> "Heavy rain"
        66, 67 -> "Freezing rain"
        71 -> "Light snow"
        73 -> "Snow"
        75 -> "Heavy snow"
        77 -> "Snow grains"
        80 -> "Light showers"
        81 -> "Showers"
        82 -> "Violent showers"
        85, 86 -> "Snow showers"
        95 -> "Thunderstorm"
        96, 99 -> "Thunderstorm with hail"
        else -> "Unknown"
    }

    fun icon(code: Int, isDay: Boolean) = when (code) {
        0, 1 -> if (isDay) R.drawable.ic_w_sun else R.drawable.ic_w_moon
        2 -> if (isDay) R.drawable.ic_w_partly else R.drawable.ic_w_night_cloud
        3 -> R.drawable.ic_w_cloud
        45, 48 -> R.drawable.ic_w_fog
        51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82 -> R.drawable.ic_w_rain
        71, 73, 75, 77, 85, 86 -> R.drawable.ic_w_snow
        95, 96, 99 -> R.drawable.ic_w_storm
        else -> R.drawable.ic_w_cloud
    }

    /** Top → bottom sky colours. */
    fun sky(code: Int, isDay: Boolean): IntArray = when {
        code >= 95 -> intArrayOf(0xFF1B2030.toInt(), 0xFF4B5569.toInt())
        code in 71..77 || code in 85..86 -> if (isDay) intArrayOf(0xFF7C93AB.toInt(), 0xFFC9D6E3.toInt()) else intArrayOf(0xFF2A3547.toInt(), 0xFF5B6B80.toInt())
        code in 51..67 || code in 80..82 -> if (isDay) intArrayOf(0xFF35465A.toInt(), 0xFF6F89A3.toInt()) else intArrayOf(0xFF161E2A.toInt(), 0xFF3A4A5E.toInt())
        code == 45 || code == 48 -> if (isDay) intArrayOf(0xFF8A97A5.toInt(), 0xFFBAC4CE.toInt()) else intArrayOf(0xFF2A323B.toInt(), 0xFF59636E.toInt())
        code == 3 -> if (isDay) intArrayOf(0xFF5E7390.toInt(), 0xFF9DB0C6.toInt()) else intArrayOf(0xFF1D2733.toInt(), 0xFF3B4A5C.toInt())
        code == 2 -> if (isDay) intArrayOf(0xFF2F6FC9.toInt(), 0xFF7FB3E6.toInt()) else intArrayOf(0xFF0F1E36.toInt(), 0xFF2E4466.toInt())
        else -> if (isDay) intArrayOf(0xFF1E6FE0.toInt(), 0xFF62C2F5.toInt()) else intArrayOf(0xFF081425.toInt(), 0xFF22406B.toInt())
    }

    fun compass(deg: Int): String {
        val dirs = arrayOf("N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE", "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW")
        return dirs[(((deg % 360) + 360) % 360 / 22.5 + 0.5).toInt() % 16]
    }

    fun uvLabel(uv: Double) = when {
        uv < 3 -> "Low"
        uv < 6 -> "Moderate"
        uv < 8 -> "High"
        uv < 11 -> "Very high"
        else -> "Extreme"
    }
}
