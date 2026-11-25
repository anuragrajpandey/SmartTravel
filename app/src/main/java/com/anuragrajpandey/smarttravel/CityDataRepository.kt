package com.anuragrajpandey.smarttravel

import org.json.JSONArray
import org.json.JSONObject
import java.io.Serializable
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.CountDownLatch

data class CityLocation(val name: String, val region: String, val country: String, val latitude: Double, val longitude: Double)
data class WeatherSnapshot(val temperature: Double, val feelsLike: Double, val humidity: Int, val wind: Double, val description: String, val forecast: List<String>)
data class CityPlace(val name: String, val type: String, val detail: String, val latitude: Double, val longitude: Double, val website: String?)
data class WikiItem(val title: String, val description: String, val excerpt: String, val url: String)
data class MostVisitedPlace(
    val name: String,
    val location: String,
    val category: String,
    val summary: String,
    val imageUrl: String?,
    val galleryUrls: List<String>,
    val wikiUrl: String?,
    val latitude: Double?,
    val longitude: Double?,
    val cityQuery: String
) : Serializable

data class CityGuide(
    val city: CityLocation,
    val weather: WeatherSnapshot?,
    val overview: WikiItem?,
    val hotels: List<CityPlace>,
    val restaurants: List<CityPlace>,
    val attractions: List<CityPlace>,
    val foods: List<WikiItem>,
    val mostVisited: List<MostVisitedPlace> = emptyList(),
    val photos: List<String> = emptyList(),
    val hiddenGems: List<MostVisitedPlace> = emptyList(),
    val regionFoods: FoodBundle = FoodBundle("", emptyList())
)

object CityDataRepository {
    private data class FeaturedSeed(
        val wikiTitle: String,
        val displayName: String,
        val location: String,
        val category: String,
        val summary: String,
        val cityQuery: String
    )

    private val featuredSeeds = listOf(
        FeaturedSeed("Taj_Mahal", "Taj Mahal", "Agra, Uttar Pradesh", "World Wonder", "Ivory-white marble mausoleum on the Yamuna riverbank, famous at sunrise.", "Agra"),
        FeaturedSeed("Hawa_Mahal", "Hawa Mahal", "Jaipur, Rajasthan", "Royal Heritage", "Iconic pink sandstone Palace of Winds in the heart of Jaipur's old city.", "Jaipur"),
        FeaturedSeed("Rishikesh", "Rishikesh", "Uttarakhand", "River & Adventure", "Himalayan foothills, suspension bridges, river rafting and quiet cafés.", "Rishikesh"),
        FeaturedSeed("Munnar", "Munnar", "Kerala", "Misty Hills", "Rolling emerald tea plantations, winding roads and cool viewpoints.", "Munnar"),
        FeaturedSeed("Basilica_of_Bom_Jesus", "Old Goa & Beaches", "Goa", "Coast & Heritage", "Historic Portuguese architecture, palm-lined shores and laid-back cafés.", "Goa"),
        FeaturedSeed("Harmandir_Sahib", "Golden Temple", "Amritsar, Punjab", "Sacred Landmark", "Shimmering gold sanctum surrounded by the tranquil Amrit Sarovar.", "Amritsar"),
        FeaturedSeed("Leh", "Leh & Ladakh", "Ladakh", "Mountain Escape", "High-altitude monasteries, turquoise lakes and dramatic Himalayan passes.", "Leh"),
        FeaturedSeed("Varanasi", "Varanasi Ghats", "Uttar Pradesh", "Cultural Soul", "Ancient riverside steps, evening Ganga aarti and narrow heritage lanes.", "Varanasi")
    )

    private val hiddenGemSeeds = listOf(
        FeaturedSeed("Ziro", "Ziro Valley", "Arunachal Pradesh", "Hidden Gem · Northeast", "Emerald pine hills, terraced rice fields and Apatani tribal heritage.", "Ziro"),
        FeaturedSeed("Majuli", "Majuli Island", "Assam", "Hidden Gem · Northeast", "World's largest river island on the Brahmaputra, famed for satras and crafts.", "Jorhat"),
        FeaturedSeed("Mawlynnong", "Mawlynnong", "Meghalaya", "Hidden Gem · Northeast", "Living root bridges, bamboo walkways and misty Khasi hills.", "Shillong"),
        FeaturedSeed("Dzükou_Valley", "Dzükou Valley", "Nagaland & Manipur", "Hidden Gem · Northeast", "Rolling bamboo-green ridges and seasonal valley lilies above the clouds.", "Kohima"),
        FeaturedSeed("Chopta", "Chopta Meadows", "Uttarakhand", "Hidden Gem · Himalayas", "Quiet alpine meadows and the scenic trek up to Tungnath temple.", "Chopta"),
        FeaturedSeed("Spiti", "Spiti Valley", "Himachal Pradesh", "Hidden Gem · Himalayas", "High-altitude cold desert, cliffside monasteries and star-filled skies.", "Kaza"),
        FeaturedSeed("Great_Himalayan_National_Park", "Tirthan Valley", "Himachal Pradesh", "Hidden Gem · Himalayas", "Glacial river banks, cedar forests and peaceful mountain hamlets.", "Banjar"),
        FeaturedSeed("Gurez", "Gurez Valley", "Jammu & Kashmir", "Hidden Gem · Himalayas", "Untouched alpine valley along the Kishanganga river with wooden log homes.", "Gurez"),
        FeaturedSeed("Gokarna,_Karnataka", "Gokarna", "Karnataka", "Hidden Gem · Coast", "Quiet crescent coves, cliffside beach trails and laid-back temple lanes.", "Gokarna"),
        FeaturedSeed("Varkala", "Varkala Cliff", "Kerala", "Hidden Gem · Coast", "Dramatic red laterite cliffs overlooking the Arabian Sea and sunset cafés.", "Varkala"),
        FeaturedSeed("Dhanushkodi", "Dhanushkodi", "Tamil Nadu", "Hidden Gem · Coast", "Surreal ghost-town ruins on a narrow sandspit where two seas meet.", "Rameswaram"),
        FeaturedSeed("Shaheed_Dweep", "Neil Island", "Andaman & Nicobar", "Hidden Gem · Coast", "Coral reefs, natural rock bridges and uncrowded turquoise lagoons.", "Port Blair"),
        FeaturedSeed("Bundi", "Bundi", "Rajasthan", "Hidden Gem · Heritage", "Blue-washed old lanes, ornate stepwells and a quiet hilltop palace.", "Bundi"),
        FeaturedSeed("Orchha", "Orchha", "Madhya Pradesh", "Hidden Gem · Heritage", "Riverside cenotaphs, medieval palaces and calm Betwa river views.", "Orchha"),
        FeaturedSeed("Gandikota", "Gandikota Canyon", "Andhra Pradesh", "Hidden Gem · Heritage", "Red gorge carved by the Pennar river with an ancient hilltop fort.", "Gandikota"),
        FeaturedSeed("Chettinad", "Chettinad", "Tamil Nadu", "Hidden Gem · Heritage", "Palatial heritage mansions, hand-painted tiles and famed local cuisine.", "Karaikudi"),
        FeaturedSeed("Hallstatt", "Hallstatt", "Austria", "Hidden Gem · Global", "Fairy-tale lakeside alpine village tucked beneath steep Dachstein peaks.", "Hallstatt"),
        FeaturedSeed("Giethoorn", "Giethoorn", "Netherlands", "Hidden Gem · Global", "Car-free canal village of thatched cottages and wooden footbridges.", "Giethoorn"),
        FeaturedSeed("Chefchaouen", "Chefchaouen", "Morocco", "Hidden Gem · Global", "Blue-painted mountain medina nestled in the Rif mountains.", "Chefchaouen"),
        FeaturedSeed("Sintra", "Sintra", "Portugal", "Hidden Gem · Global", "Misty forested hills filled with pastel palaces and hidden gardens.", "Sintra")
    )

    internal fun get(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 12000
        connection.readTimeout = 18000
        connection.setRequestProperty("User-Agent", "FlipDoor/1.0 (Android travel discovery app)")
        return try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream.bufferedReader().use { it.readText() }
            if (code !in 200..299) throw IllegalStateException("Service returned HTTP $code")
            body
        } finally {
            connection.disconnect()
        }
    }

    private fun enc(value: String) = URLEncoder.encode(value, "UTF-8")

    fun geocodeCity(query: String): CityLocation {
        require(query.isNotBlank()) { "Enter a city name to search." }
        val geo = JSONObject(get("https://geocoding-api.open-meteo.com/v1/search?name=${enc(query.trim())}&count=5&language=en&format=json"))
        val results = geo.optJSONArray("results")
            ?: throw IllegalStateException("No matching location found. Try adding its state or country.")
        if (results.length() == 0) {
            throw IllegalStateException("No matching location found. Try another spelling or add the country.")
        }
        val item = results.getJSONObject(0)
        return CityLocation(
            item.optString("name", query.trim()),
            item.optString("admin1", ""),
            item.optString("country", ""),
            item.getDouble("latitude"),
            item.getDouble("longitude")
        )
    }

    fun searchCity(query: String): CityGuide = searchCity(geocodeCity(query))

    fun searchCity(city: CityLocation): CityGuide {
        var foodBundle = FoodBundle("", emptyList())
        var weather: WeatherSnapshot? = null
        var places: List<CityPlace> = emptyList()
        var overview: WikiItem? = null
        var foods: List<WikiItem> = emptyList()
        var mostVisited: List<MostVisitedPlace> = emptyList()
        var photos: List<String> = emptyList()

        val latch = CountDownLatch(6)
        Thread {
            foodBundle = runCatching { GeoRepository.regionFoods(city.region, city.country) }.getOrDefault(FoodBundle("", emptyList()))
            latch.countDown()
        }.start()
        Thread {
            weather = runCatching { loadWeather(city) }.getOrNull()
            latch.countDown()
        }.start()
        Thread {
            places = runCatching { loadPlaces(city) }.getOrDefault(emptyList())
            latch.countDown()
        }.start()
        Thread {
            overview = runCatching { wikiSearch("${city.name} ${city.country}", 1).firstOrNull() }.getOrNull()
            latch.countDown()
        }.start()
        Thread {
            foods = runCatching { wikiSearch("${city.name} ${city.region} ${city.country} famous traditional food cuisine dishes regional specialties", 8) }.getOrDefault(emptyList())
            latch.countDown()
        }.start()
        Thread {
            val fetched = runCatching { loadMostVisitedAndPhotos(city) }.getOrNull()
            if (fetched != null) {
                mostVisited = fetched.first
                photos = fetched.second
            }
            latch.countDown()
        }.start()
        latch.await()

        val attractions = places.filter { it.type == "Attraction" }.take(10)
        val finalMostVisited = if (mostVisited.isNotEmpty()) {
            mostVisited
        } else {
            buildFallbackMostVisited(city, attractions, photos)
        }
        val hiddenGems = runCatching {
            deriveCityHiddenGems(city, finalMostVisited, places, photos)
        }.getOrDefault(emptyList())

        return CityGuide(
            city,
            weather,
            overview,
            places.filter { it.type == "Stay" }.take(8),
            places.filter { it.type == "Food & drink" }.take(10),
            attractions,
            foods,
            finalMostVisited,
            photos,
            hiddenGems,
            foodBundle
        )
    }

    fun fetchFeaturedMostVisited(): List<MostVisitedPlace> = fetchWikiBatchBySeeds(featuredSeeds)

    fun fetchAllHiddenGems(): List<MostVisitedPlace> = fetchWikiBatchBySeeds(hiddenGemSeeds)

    fun fetchHiddenGemsForLocation(query: String): Pair<CityLocation, List<MostVisitedPlace>> {
        val city = geocodeCity(query)
        val (mostVisited, photos) = loadMostVisitedAndPhotos(city)
        val osmPlaces = runCatching { loadPlaces(city) }.getOrDefault(emptyList())
        val gems = deriveCityHiddenGems(city, mostVisited, osmPlaces, photos)
        return city to gems
    }

    private fun fetchWikiBatchBySeeds(seeds: List<FeaturedSeed>): List<MostVisitedPlace> {
        val defaults = seeds.map { seed ->
            MostVisitedPlace(
                name = seed.displayName,
                location = seed.location,
                category = seed.category,
                summary = seed.summary,
                imageUrl = null,
                galleryUrls = emptyList(),
                wikiUrl = "https://en.wikipedia.org/wiki/${seed.wikiTitle}",
                latitude = null,
                longitude = null,
                cityQuery = seed.cityQuery
            )
        }
        return runCatching {
            val titlesParam = seeds.joinToString("|") { it.wikiTitle.replace("_", " ") }
            val url = "https://en.wikipedia.org/w/api.php?action=query&titles=${enc(titlesParam)}" +
                "&prop=pageimages%7Cextracts%7Cdescription%7Ccoordinates%7Cinfo" +
                "&piprop=thumbnail&pithumbsize=900&pilimit=30&exintro=1&explaintext=1&exsentences=2&exlimit=20&colimit=30&inprop=url&redirects=1&format=json&formatversion=2"
            val root = JSONObject(get(url))
            val queryObj = root.optJSONObject("query") ?: return@runCatching defaults
            val redirectMap = mutableMapOf<String, String>()
            val normalizedMap = mutableMapOf<String, String>()
            queryObj.optJSONArray("normalized")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    normalizedMap[o.optString("from")] = o.optString("to")
                }
            }
            queryObj.optJSONArray("redirects")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    redirectMap[o.optString("from")] = o.optString("to")
                }
            }
            val pagesByTitle = mutableMapOf<String, JSONObject>()
            queryObj.optJSONArray("pages")?.let { pages ->
                for (i in 0 until pages.length()) {
                    val p = pages.getJSONObject(i)
                    pagesByTitle[p.optString("title")] = p
                }
            }

            seeds.mapIndexed { idx, seed ->
                val raw = seed.wikiTitle.replace("_", " ")
                val norm = normalizedMap[raw] ?: raw
                val finalTitle = redirectMap[norm] ?: norm
                val page = pagesByTitle[finalTitle] ?: pagesByTitle[raw]
                val thumb = page?.optJSONObject("thumbnail")?.optString("source")?.takeIf { isUsefulImageUrl(it) }
                val extract = page?.optString("extract")?.trim()?.takeIf { it.isNotBlank() }
                val fullUrl = page?.optString("fullurl")?.takeIf { it.startsWith("http") }
                val coords = page?.optJSONArray("coordinates")?.optJSONObject(0)
                val lat = coords?.optDouble("lat")?.takeIf { !it.isNaN() }
                val lon = coords?.optDouble("lon")?.takeIf { !it.isNaN() }
                defaults[idx].copy(
                    summary = extract ?: defaults[idx].summary,
                    imageUrl = thumb,
                    galleryUrls = listOfNotNull(thumb),
                    wikiUrl = fullUrl ?: defaults[idx].wikiUrl,
                    latitude = lat,
                    longitude = lon
                )
            }
        }.getOrDefault(defaults)
    }

    private fun deriveCityHiddenGems(
        city: CityLocation,
        mostVisited: List<MostVisitedPlace>,
        osmPlaces: List<CityPlace>,
        cityPhotos: List<String>
    ): List<MostVisitedPlace> {
        val locationLabel = listOf(city.region, city.country).filter { it.isNotBlank() }.joinToString(", ").ifBlank { city.name }
        val existingNames = mostVisited.map { it.name.lowercase(Locale.US).trim() }.toMutableSet()
        existingNames.add(city.name.lowercase(Locale.US).trim())

        val offbeatQuery = "${city.name} ${city.country} hidden gems offbeat viewpoints waterfalls caves villages".trim()
        val offbeatUrl = "https://en.wikipedia.org/w/api.php?action=query&generator=search&gsrsearch=${enc(offbeatQuery)}&gsrlimit=15" +
            "&prop=pageimages%7Cextracts%7Cdescription%7Ccoordinates%7Cinfo" +
            "&piprop=thumbnail&pithumbsize=900&pilimit=15&exintro=1&explaintext=1&exsentences=3&exlimit=15&colimit=15&inprop=url&format=json&formatversion=2"
        val geoExtraUrl = "https://en.wikipedia.org/w/api.php?action=query&generator=geosearch" +
            "&ggscoord=${city.latitude}%7C${city.longitude}&ggsradius=10000&ggslimit=40" +
            "&prop=pageimages%7Cextracts%7Cdescription%7Ccoordinates%7Cinfo" +
            "&piprop=thumbnail&pithumbsize=900&pilimit=40&exintro=1&explaintext=1&exsentences=2&exlimit=20&colimit=40&inprop=url&format=json&formatversion=2"

        val candidates = mutableListOf<MostVisitedPlace>()
        runCatching { parseWikiPlacePages(JSONObject(get(offbeatUrl)), locationLabel, city.name) }
            .getOrDefault(emptyList())
            .forEach { candidates.add(it) }
        runCatching { parseWikiPlacePages(JSONObject(get(geoExtraUrl)), locationLabel, city.name) }
            .getOrDefault(emptyList())
            .forEach { candidates.add(it) }

        val deduped = LinkedHashMap<String, MostVisitedPlace>()
        for (item in candidates) {
            val key = item.name.lowercase(Locale.US).trim()
            if (key in existingNames) continue
            val prev = deduped[key]
            if (prev == null || (prev.imageUrl == null && item.imageUrl != null)) {
                deduped[key] = item.copy(category = "Hidden Gem · ${item.category}")
            }
        }

        // Add offbeat OSM attractions not already covered
        val offbeatOsm = osmPlaces.filter { it.type == "Attraction" || it.type == "Hidden Gem" }
        for (attr in offbeatOsm) {
            val key = attr.name.lowercase(Locale.US).trim()
            if (key in existingNames || deduped.containsKey(key)) continue
            deduped[key] = MostVisitedPlace(
                name = attr.name,
                location = locationLabel,
                category = "Hidden Gem · Local Spot",
                summary = attr.detail,
                imageUrl = null,
                galleryUrls = emptyList(),
                wikiUrl = attr.website,
                latitude = attr.latitude,
                longitude = attr.longitude,
                cityQuery = city.name
            )
        }

        val ranked = deduped.values
            .sortedByDescending { (if (it.imageUrl != null) 100 else 0) + (if (it.summary.length > 40) 20 else 0) }
            .take(8)
            .mapIndexed { idx, p ->
                val img = p.imageUrl ?: cityPhotos.getOrNull(idx % maxOf(cityPhotos.size, 1))
                p.copy(imageUrl = img, galleryUrls = (listOfNotNull(img) + cityPhotos.take(4)).distinct())
            }
        return ranked
    }

    fun fetchMostVisitedForLocation(query: String): Pair<CityLocation, List<MostVisitedPlace>> {
        val city = geocodeCity(query)
        val (places, photos) = loadMostVisitedAndPhotos(city)
        val result = if (places.isNotEmpty()) {
            places
        } else {
            val osmAttractions = runCatching { loadPlaces(city).filter { it.type == "Attraction" } }.getOrDefault(emptyList())
            buildFallbackMostVisited(city, osmAttractions, photos)
        }
        return city to result
    }

    /** Most-visited places around an exact point (used for the user's current location). */
    fun mostVisitedFor(city: CityLocation): List<MostVisitedPlace> {
        val (places, photos) = loadMostVisitedAndPhotos(city)
        if (places.isNotEmpty()) return places
        val osm = runCatching { loadPlaces(city).filter { it.type == "Attraction" } }.getOrDefault(emptyList())
        return buildFallbackMostVisited(city, osm, photos)
    }

    /** Offbeat spots around an exact point. */
    fun hiddenGemsFor(city: CityLocation): List<MostVisitedPlace> {
        val (visited, photos) = loadMostVisitedAndPhotos(city)
        val osm = runCatching { loadPlaces(city) }.getOrDefault(emptyList())
        return deriveCityHiddenGems(city, visited, osm, photos)
    }

    fun fetchPlaceGallery(placeName: String, locationHint: String): List<String> {
        val combined = mutableListOf<String>()
        val queries = listOf(
            "$placeName $locationHint".trim(),
            placeName.trim()
        ).distinct().filter { it.isNotBlank() }
        for (q in queries) {
            val found = runCatching { searchCommonsPhotos(q, 8) }.getOrDefault(emptyList())
            found.forEach { url -> if (url !in combined) combined.add(url) }
            if (combined.size >= 6) break
        }
        return combined.take(8)
    }

    private fun loadMostVisitedAndPhotos(city: CityLocation): Pair<List<MostVisitedPlace>, List<String>> {
        val locationLabel = listOf(city.region, city.country).filter { it.isNotBlank() }.joinToString(", ")
        var geoPlaces: List<MostVisitedPlace> = emptyList()
        var searchPlaces: List<MostVisitedPlace> = emptyList()
        var commonsPhotos: List<String> = emptyList()

        val latch = CountDownLatch(3)
        Thread {
            geoPlaces = runCatching { fetchWikiGeoPlaces(city, locationLabel) }.getOrDefault(emptyList())
            latch.countDown()
        }.start()
        Thread {
            searchPlaces = runCatching { fetchWikiSearchPlaces(city, locationLabel) }.getOrDefault(emptyList())
            latch.countDown()
        }.start()
        Thread {
            val primary = runCatching { searchCommonsPhotos("${city.name} ${city.country}", 10) }.getOrDefault(emptyList())
            val secondary = if (primary.size < 4) {
                runCatching { searchCommonsPhotos(city.name, 8) }.getOrDefault(emptyList())
            } else {
                emptyList()
            }
            commonsPhotos = (primary + secondary).distinct().take(10)
            latch.countDown()
        }.start()
        latch.await()

        val merged = LinkedHashMap<String, MostVisitedPlace>()
        fun addAll(items: List<MostVisitedPlace>) {
            for (p in items) {
                val key = p.name.lowercase(Locale.US).trim()
                val existing = merged[key]
                if (existing == null) {
                    merged[key] = p
                } else if (existing.imageUrl == null && p.imageUrl != null) {
                    merged[key] = p
                }
            }
        }
        addAll(geoPlaces)
        addAll(searchPlaces)

        val sorted = merged.values
            .filter { !it.name.equals(city.name, ignoreCase = true) || merged.size <= 3 }
            .sortedByDescending { scorePlace(it, city.name) }
            .take(10)
            .mapIndexed { idx, place ->
                val primaryImg = place.imageUrl ?: commonsPhotos.getOrNull(idx % maxOf(commonsPhotos.size, 1))
                val gallery = (listOfNotNull(primaryImg) + commonsPhotos.take(4)).distinct()
                place.copy(imageUrl = primaryImg, galleryUrls = gallery)
            }

        val allPhotos = (sorted.mapNotNull { it.imageUrl } + commonsPhotos).distinct().take(12)
        return sorted to allPhotos
    }

    private fun scorePlace(place: MostVisitedPlace, cityName: String): Int {
        var score = 0
        if (place.imageUrl != null) score += 100
        if (place.summary.length > 40) score += 20
        if (!place.name.equals(cityName, ignoreCase = true)) score += 15
        if (place.category != "Most Visited") score += 10
        return score
    }

    private fun fetchWikiGeoPlaces(city: CityLocation, locationLabel: String): List<MostVisitedPlace> {
        val url = "https://en.wikipedia.org/w/api.php?action=query&generator=geosearch" +
            "&ggscoord=${city.latitude}%7C${city.longitude}&ggsradius=10000&ggslimit=25" +
            "&prop=pageimages%7Cextracts%7Cdescription%7Ccoordinates%7Cinfo" +
            "&piprop=thumbnail&pithumbsize=900&pilimit=25&exintro=1&explaintext=1&exsentences=3&exlimit=20&colimit=25&inprop=url&format=json&formatversion=2"
        return parseWikiPlacePages(JSONObject(get(url)), locationLabel, city.name)
    }

    private fun fetchWikiSearchPlaces(city: CityLocation, locationLabel: String): List<MostVisitedPlace> {
        val q = "${city.name} ${city.country} tourist attractions landmarks".trim()
        val url = "https://en.wikipedia.org/w/api.php?action=query&generator=search&gsrsearch=${enc(q)}&gsrlimit=15" +
            "&prop=pageimages%7Cextracts%7Cdescription%7Ccoordinates%7Cinfo" +
            "&piprop=thumbnail&pithumbsize=900&pilimit=15&exintro=1&explaintext=1&exsentences=3&exlimit=20&colimit=15&inprop=url&format=json&formatversion=2"
        val first = parseWikiPlacePages(JSONObject(get(url)), locationLabel, city.name)
        if (first.count { it.imageUrl != null } >= 4) return first
        val fallbackUrl = "https://en.wikipedia.org/w/api.php?action=query&generator=search&gsrsearch=${enc("${city.name} ${city.region}".trim())}&gsrlimit=12" +
            "&prop=pageimages%7Cextracts%7Cdescription%7Ccoordinates%7Cinfo" +
            "&piprop=thumbnail&pithumbsize=900&pilimit=12&exintro=1&explaintext=1&exsentences=3&exlimit=20&colimit=12&inprop=url&format=json&formatversion=2"
        val second = runCatching { parseWikiPlacePages(JSONObject(get(fallbackUrl)), locationLabel, city.name) }.getOrDefault(emptyList())
        return first + second
    }

    private fun parseWikiPlacePages(root: JSONObject, locationLabel: String, cityName: String): List<MostVisitedPlace> {
        val pages = root.optJSONObject("query")?.optJSONArray("pages") ?: return emptyList()
        val result = mutableListOf<MostVisitedPlace>()
        for (i in 0 until pages.length()) {
            val p = pages.getJSONObject(i)
            val title = p.optString("title").trim()
            val desc = p.optString("description", "").trim()
            if (title.isBlank() || !isUsefulWikiPlace(title, desc)) continue
            val thumb = p.optJSONObject("thumbnail")?.optString("source")?.takeIf { isUsefulImageUrl(it, title) }
            val extract = p.optString("extract", "").trim()
            val summary = when {
                extract.isNotBlank() -> extract
                desc.isNotBlank() -> desc
                else -> "Notable attraction around $cityName."
            }
            val wikiUrl = p.optString("fullurl").takeIf { it.startsWith("http") }
                ?: ("https://en.wikipedia.org/wiki/" + enc(title).replace("+", "_"))
            val coords = p.optJSONArray("coordinates")?.optJSONObject(0)
            val lat = coords?.optDouble("lat")?.takeIf { !it.isNaN() }
            val lon = coords?.optDouble("lon")?.takeIf { !it.isNaN() }
            result.add(
                MostVisitedPlace(
                    name = title,
                    location = locationLabel.ifBlank { cityName },
                    category = categorizePlace(title, desc),
                    summary = summary,
                    imageUrl = thumb,
                    galleryUrls = listOfNotNull(thumb),
                    wikiUrl = wikiUrl,
                    latitude = lat,
                    longitude = lon,
                    cityQuery = cityName
                )
            )
        }
        return result
    }

    private fun searchCommonsPhotos(query: String, limit: Int): List<String> {
        val url = "https://commons.wikimedia.org/w/api.php?action=query&generator=search&gsrnamespace=6" +
            "&gsrsearch=${enc(query)}&gsrlimit=$limit&prop=imageinfo&iiprop=url%7Cmime&iiurlwidth=900&format=json&formatversion=2"
        val pages = JSONObject(get(url)).optJSONObject("query")?.optJSONArray("pages") ?: return emptyList()
        val photos = mutableListOf<String>()
        for (i in 0 until pages.length()) {
            val p = pages.getJSONObject(i)
            val title = p.optString("title", "")
            val info = p.optJSONArray("imageinfo")?.optJSONObject(0) ?: continue
            val mime = info.optString("mime", "").lowercase(Locale.US)
            if (mime !in listOf("image/jpeg", "image/png", "image/webp")) continue
            val thumb = info.optString("thumburl").ifBlank { info.optString("url") }
            if (isUsefulImageUrl(thumb, title)) {
                photos.add(thumb)
            }
        }
        return photos.distinct()
    }

    private fun buildFallbackMostVisited(city: CityLocation, attractions: List<CityPlace>, photos: List<String>): List<MostVisitedPlace> {
        val loc = listOf(city.region, city.country).filter { it.isNotBlank() }.joinToString(", ").ifBlank { city.name }
        if (attractions.isNotEmpty()) {
            return attractions.take(8).mapIndexed { i, attr ->
                val img = photos.getOrNull(i % maxOf(photos.size, 1))
                MostVisitedPlace(
                    name = attr.name,
                    location = loc,
                    category = "Local Attraction",
                    summary = attr.detail,
                    imageUrl = img,
                    galleryUrls = (listOfNotNull(img) + photos.take(4)).distinct(),
                    wikiUrl = attr.website,
                    latitude = attr.latitude,
                    longitude = attr.longitude,
                    cityQuery = city.name
                )
            }
        }
        if (photos.isNotEmpty()) {
            return photos.take(5).mapIndexed { i, photo ->
                MostVisitedPlace(
                    name = if (i == 0) city.name else "${city.name} Highlight ${i + 1}",
                    location = loc,
                    category = "Destination View",
                    summary = "Scenic view of ${city.name}. Tap to inspect photos and plan your trip.",
                    imageUrl = photo,
                    galleryUrls = photos.take(6),
                    wikiUrl = "https://en.wikipedia.org/wiki/" + enc(city.name).replace("+", "_"),
                    latitude = city.latitude,
                    longitude = city.longitude,
                    cityQuery = city.name
                )
            }
        }
        return listOf(
            MostVisitedPlace(
                name = city.name,
                location = loc,
                category = "Destination",
                summary = "Explore ${city.name}, view weather and plan your itinerary.",
                imageUrl = null,
                galleryUrls = emptyList(),
                wikiUrl = "https://en.wikipedia.org/wiki/" + enc(city.name).replace("+", "_"),
                latitude = city.latitude,
                longitude = city.longitude,
                cityQuery = city.name
            )
        )
    }

    private fun isUsefulImageUrl(url: String?, titleHint: String = ""): Boolean {
        if (url.isNullOrBlank() || !url.startsWith("http")) return false
        val combined = (url + " " + titleHint).lowercase(Locale.US)
        val blocked = listOf(
            ".svg", ".pdf", ".tif", ".tiff", ".ogg", ".ogv", ".webm",
            "flag_of", "flag of", "locator", "blank_map", "location_map",
            "coat_of_arms", "coat of arms", "emblem", "diagram", "chart",
            "signature", "wikimedia-logo", "red_pog", "blue_pog"
        )
        return blocked.none { combined.contains(it) }
    }

    private fun isUsefulWikiPlace(title: String, desc: String): Boolean {
        val t = title.lowercase(Locale.US)
        val d = desc.lowercase(Locale.US)
        val skipPrefixes = listOf("list of ", "outline of ", "history of ", "demographics of ", "timeline of ", "index of ", "category:", "template:")
        if (skipPrefixes.any { t.startsWith(it) }) return false
        val skipWords = listOf("constituency", "lok sabha", "vidhan sabha", "legislative assembly", "census", "municipal corporation", "mayoral", "election")
        return skipWords.none { t.contains(it) || d.contains(it) }
    }

    private fun categorizePlace(title: String, desc: String): String {
        val text = "$title $desc".lowercase(Locale.US)
        return when {
            listOf("temple", "mandir", "shrine", "mosque", "masjid", "church", "cathedral", "basilica", "monastery", "gurdwara", "pagoda", "ghat").any { text.contains(it) } -> "Sacred & Heritage"
            listOf("fort", "palace", "mahal", "castle", "monument", "memorial", "gate", "tower", "tomb", "ruin", "heritage").any { text.contains(it) } -> "Landmark"
            listOf("museum", "gallery", "planetarium", "library", "art").any { text.contains(it) } -> "Museum & Culture"
            listOf("park", "garden", "lake", "waterfall", "beach", "mountain", "hill", "valley", "forest", "peak", "island", "river", "sanctuary", "wildlife").any { text.contains(it) } -> "Nature & Views"
            listOf("market", "bazaar", "square", "bridge", "street").any { text.contains(it) } -> "Local Highlight"
            else -> "Most Visited"
        }
    }

    private fun loadWeather(city: CityLocation): WeatherSnapshot {
        val url = "https://api.open-meteo.com/v1/forecast?latitude=${city.latitude}&longitude=${city.longitude}&current=temperature_2m,relative_humidity_2m,apparent_temperature,wind_speed_10m,weather_code&daily=temperature_2m_max,temperature_2m_min,precipitation_probability_max,precipitation_sum,weather_code&timezone=auto&past_days=7&forecast_days=8"
        val json = JSONObject(get(url))
        val current = json.getJSONObject("current")
        val daily = json.getJSONObject("daily")
        val forecast = (0 until daily.getJSONArray("time").length()).map { i ->
            val date = daily.getJSONArray("time").getString(i)
            val low = daily.getJSONArray("temperature_2m_min").getDouble(i).toInt()
            val high = daily.getJSONArray("temperature_2m_max").getDouble(i).toInt()
            val rain = daily.optJSONArray("precipitation_probability_max")?.optInt(i) ?: 0
            val rainMm = daily.optJSONArray("precipitation_sum")?.optDouble(i) ?: 0.0
            val code = daily.optJSONArray("weather_code")?.optInt(i) ?: -1
            "$date|$low|$high|$rain|$rainMm|${weatherLabel(code)}"
        }
        return WeatherSnapshot(
            current.getDouble("temperature_2m"),
            current.getDouble("apparent_temperature"),
            current.getInt("relative_humidity_2m"),
            current.getDouble("wind_speed_10m"),
            weatherLabel(current.optInt("weather_code")),
            forecast
        )
    }

    private fun weatherLabel(code: Int) = when (code) {
        0 -> "Clear sky"
        1, 2 -> "Mostly clear"
        3 -> "Cloudy"
        45, 48 -> "Fog"
        51, 53, 55 -> "Drizzle"
        61, 63, 65 -> "Rain"
        71, 73, 75 -> "Snow"
        80, 81, 82 -> "Rain showers"
        95, 96, 99 -> "Thunderstorm"
        else -> "Current conditions"
    }

    private fun loadPlaces(city: CityLocation): List<CityPlace> {
        val lat = city.latitude
        val lon = city.longitude
        val query = "[out:json][timeout:25];(" +
            "nwr[\"tourism\"~\"hotel|hostel|guest_house\"](around:12000,$lat,$lon);" +
            "nwr[\"amenity\"~\"restaurant|cafe\"](around:12000,$lat,$lon);" +
            "nwr[\"tourism\"~\"attraction|museum|viewpoint|theme_park|zoo|camp_site\"](around:12000,$lat,$lon);" +
            "nwr[\"historic\"~\"monument|fort|palace|castle|memorial|ruins|archaeological_site\"](around:12000,$lat,$lon);" +
            "nwr[\"natural\"~\"waterfall|peak|cave_entrance|spring|beach\"](around:12000,$lat,$lon);" +
            ");out center;"
        val connection = URL("https://overpass-api.de/api/interpreter").openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.connectTimeout = 12000
        connection.readTimeout = 30000
        connection.setRequestProperty("User-Agent", "FlipDoor/1.0 (Android travel discovery app)")
        connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
        return try {
            connection.outputStream.use { it.write(("data=" + enc(query)).toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream.bufferedReader().use { it.readText() }
            if (code !in 200..299) throw IllegalStateException("Places service temporarily unavailable")
            val elements = JSONObject(body).optJSONArray("elements") ?: return emptyList()
            (0 until elements.length()).mapNotNull { i ->
                val e = elements.getJSONObject(i)
                val tags = e.optJSONObject("tags") ?: return@mapNotNull null
                val name = tags.optString("name", "").trim()
                if (name.isBlank()) return@mapNotNull null
                val tourism = tags.optString("tourism")
                val amenity = tags.optString("amenity")
                val historic = tags.optString("historic")
                val naturalTag = tags.optString("natural")
                val type = when {
                    tourism in listOf("hotel", "hostel", "guest_house") -> "Stay"
                    amenity in listOf("restaurant", "cafe") -> "Food & drink"
                    tourism in listOf("viewpoint", "camp_site") || historic in listOf("ruins", "archaeological_site") || naturalTag.isNotBlank() -> "Hidden Gem"
                    tourism in listOf("attraction", "museum", "theme_park", "zoo") || historic.isNotBlank() -> "Attraction"
                    else -> return@mapNotNull null
                }
                val center = e.optJSONObject("center")
                val pLat = e.optDouble("lat", center?.optDouble("lat") ?: city.latitude)
                val pLon = e.optDouble("lon", center?.optDouble("lon") ?: city.longitude)
                val detail = listOf(tags.optString("cuisine"), tags.optString("addr:street"), tags.optString("opening_hours"))
                    .filter { it.isNotBlank() }
                    .joinToString(" · ")
                    .ifBlank { "OpenStreetMap listing" }
                CityPlace(name, type, detail, pLat, pLon, tags.optString("website").takeIf { it.startsWith("http") })
            }.distinctBy { it.name.lowercase() }.sortedBy { it.name.lowercase() }.take(100)
        } finally {
            connection.disconnect()
        }
    }

    private fun wikiSearch(query: String, limit: Int): List<WikiItem> {
        val json = JSONObject(get("https://en.wikipedia.org/w/rest.php/v1/search/page?q=${enc(query)}&limit=$limit"))
        val pages = json.optJSONArray("pages") ?: return emptyList()
        return (0 until pages.length()).mapNotNull { i ->
            val page = pages.getJSONObject(i)
            val title = page.optString("title").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val description = page.optString("description", "")
            val excerpt = page.optString("excerpt", "")
                .replace(Regex("<[^>]*>"), " ")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&amp;", "&")
                .trim()
            WikiItem(title, description, excerpt, "https://en.wikipedia.org/wiki/" + enc(title).replace("+", "_"))
        }
    }
}
