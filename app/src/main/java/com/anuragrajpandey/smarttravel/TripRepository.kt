package com.anuragrajpandey.smarttravel

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.roundToInt

data class TripInput(
    val name: String, val origin: CityLocation?, val dest: CityLocation,
    val startIso: String, val endIso: String, val travellers: Int
)

data class DayPlan(val date: String, val label: String, val weather: DayPoint?, val stops: List<MostVisitedPlace>, val dish: FoodItem?)
data class BudgetLine(val label: String, val amount: Double, val note: String)

data class TripPlan(
    val input: TripInput, val nights: Int, val days: List<DayPlan>, val hotels: List<Hotel>,
    val foods: List<FoodItem>, val distanceKm: Double?, val forecastCovered: Boolean
) {
    val dayCount get() = nights + 1
}

/** Builds a trip plan from live internet data: weather, attractions, hotels, food and distance. */
object TripRepository {
    val tierNames = listOf("Budget", "Comfort", "Luxury")
    private val iso = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val pretty = SimpleDateFormat("EEE, d MMM", Locale.US)

    fun nightsBetween(startIso: String, endIso: String): Int {
        val a = iso.parse(startIso)!!.time
        val b = iso.parse(endIso)!!.time
        return ((b - a) / 86_400_000L).toInt()
    }

    fun generate(input: TripInput): TripPlan {
        val nights = nightsBetween(input.startIso, input.endIso).coerceAtLeast(0)
        val dayCount = nights + 1
        val d = input.dest

        val weatherF = Net.async { runCatching { WeatherRepository.fetch(d.latitude, d.longitude, 16, 0) }.getOrNull() }
        val visitedF = Net.async { runCatching { CityDataRepository.mostVisitedFor(d) }.getOrDefault(emptyList()) }
        val gemsF = Net.async { runCatching { CityDataRepository.hiddenGemsFor(d) }.getOrDefault(emptyList()) }
        val hotelsF = Net.async { runCatching { HotelRepository.search(d.latitude, d.longitude, true, 8000) }.getOrDefault(emptyList()) }
        val foodF = Net.async { runCatching { GeoRepository.regionFoods(d.region, "India") }.getOrDefault(FoodBundle("", emptyList())) }

        val weather = Net.await(weatherF, 20000, null)
        val visited = Net.await(visitedF, 30000, emptyList())
        val hotels = Net.await(hotelsF, 30000, emptyList())
        val foods = Net.await(foodF, 20000, FoodBundle("", emptyList())).items
        val needed = dayCount * 3
        val gems = if (visited.size < needed) Net.await(gemsF, 25000, emptyList()) else { gemsF.cancel(true); emptyList() }

        val pool = (visited + gems).distinctBy { it.name.lowercase(Locale.US) }.take(needed)
        val ordered = routeOrder(pool)
        val chunks = splitEvenly(ordered, dayCount)

        val byDate = weather?.daily?.associateBy { it.date } ?: emptyMap()
        val cal = Calendar.getInstance().apply { time = iso.parse(input.startIso)!! }
        val days = (0 until dayCount).map { i ->
            val date = iso.format(cal.time)
            val plan = DayPlan(date, pretty.format(cal.time), byDate[date], chunks.getOrElse(i) { emptyList() }, foods.getOrNull(i % maxOf(foods.size, 1)))
            cal.add(Calendar.DAY_OF_MONTH, 1)
            plan
        }
        val dist = input.origin?.let { GeoRepository.haversineKm(it.latitude, it.longitude, d.latitude, d.longitude) }
        return TripPlan(input, nights, days, hotels, foods, dist, byDate.isNotEmpty() && days.any { byDate.containsKey(it.date) })
    }

    /** Nearest-neighbour ordering so each day's stops are close together. */
    private fun routeOrder(places: List<MostVisitedPlace>): List<MostVisitedPlace> {
        if (places.size < 3) return places
        val withGeo = places.filter { it.latitude != null && it.longitude != null }
        val without = places.filter { it.latitude == null || it.longitude == null }
        if (withGeo.size < 3) return places
        val left = withGeo.toMutableList()
        val out = ArrayList<MostVisitedPlace>()
        var cur = left.removeAt(0)
        out.add(cur)
        while (left.isNotEmpty()) {
            val c = cur
            val next = left.minByOrNull { GeoRepository.haversineKm(c.latitude!!, c.longitude!!, it.latitude!!, it.longitude!!) }!!
            left.remove(next)
            out.add(next)
            cur = next
        }
        return out + without
    }

    private fun <T> splitEvenly(items: List<T>, parts: Int): List<List<T>> {
        if (items.isEmpty() || parts <= 0) return List(parts) { emptyList() }
        val size = ceil(items.size / parts.toDouble()).toInt().coerceAtLeast(1)
        return items.chunked(size).let { c -> c + List(maxOf(0, parts - c.size)) { emptyList() } }
    }

    // ---------- costs (₹) ----------
    fun nightlyPrice(plan: TripPlan, tier: Int): Int {
        val prices = plan.hotels.filter { it.listedPrice == null }.map { it.priceMid }.sorted()
        if (prices.isEmpty()) return intArrayOf(1500, 3500, 9000)[tier]
        val p = doubleArrayOf(0.22, 0.6, 0.94)[tier]
        return prices[((prices.size - 1) * p).roundToInt()]
    }

    /** Three hotels whose price is closest to the chosen tier. */
    fun hotelsForTier(plan: TripPlan, tier: Int): List<Hotel> {
        val target = nightlyPrice(plan, tier)
        return plan.hotels.sortedBy { Math.abs(it.priceMid - target) - it.score * 8 }.take(3)
    }

    fun travelCost(distanceKm: Double?, tier: Int, people: Int): Pair<String, Double> {
        if (distanceKm == null) return "Add a starting point to include travel" to 0.0
        val road = distanceKm * 1.25
        val mult = doubleArrayOf(0.85, 1.0, 1.7)[tier]
        val (mode, oneWay) = when {
            road < 60 -> "Local (${road.roundToInt()} km)" to road * 9
            road <= 350 -> "Bus / train · ${road.roundToInt()} km" to road * 1.5
            road <= 900 -> "Train · ${road.roundToInt()} km" to road * 1.4
            else -> "Flight · ${distanceKm.roundToInt()} km" to (2600 + distanceKm * 2.6)
        }
        return "$mode, round trip" to oneWay * mult * 2 * people
    }

    fun budget(plan: TripPlan, tier: Int): List<BudgetLine> {
        val people = plan.input.travellers
        val rooms = ceil(people / 2.0).toInt()
        val nightly = nightlyPrice(plan, tier)
        val travel = travelCost(plan.distanceKm, tier, people)
        val food = intArrayOf(400, 900, 2200)[tier]
        val local = intArrayOf(350, 800, 2200)[tier] * ceil(people / 4.0).toInt()
        val fun_ = intArrayOf(150, 350, 900)[tier]
        val lines = listOf(
            BudgetLine("Travel", travel.second, travel.first),
            BudgetLine("Stay", nightly.toDouble() * rooms * plan.nights, "₹${"%,d".format(Locale("en", "IN"), nightly)}/night · $rooms room(s) · ${plan.nights} night(s)"),
            BudgetLine("Food", food.toDouble() * people * plan.dayCount, "₹$food per person per day"),
            BudgetLine("Local transport", local.toDouble() * plan.dayCount, "Cabs / autos in the city"),
            BudgetLine("Entry & activities", fun_.toDouble() * people * plan.dayCount, "₹$fun_ per person per day")
        )
        val sub = lines.sumOf { it.amount }
        return lines + BudgetLine("Buffer (8%)", sub * 0.08, "For surprises and tips")
    }

    fun money(v: Double) = "₹" + "%,.0f".format(Locale("en", "IN"), v)
}
