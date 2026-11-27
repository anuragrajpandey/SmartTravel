package com.anuragrajpandey.smarttravel

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class ReverseResult(val city: CityLocation, val countryCode: String, val area: String)
data class NearbyArea(val name: String, val kind: String, val distanceKm: Double, val lat: Double, val lon: Double)
data class FoodItem(val name: String, val description: String, val imageUrl: String?, val wikiTitle: String)
data class FoodBundle(val region: String, val items: List<FoodItem>)
data class ArticleSection(val heading: String?, val body: String)
data class Article(val title: String, val description: String, val imageUrl: String?, val url: String, val sections: List<ArticleSection>)

/** Location-based lookups built on open data: Nominatim, Overpass (OSM) and Wikipedia. */
object GeoRepository {
    private const val UA = "FlipDoor/1.1 (Android travel discovery app)"
    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
        return r * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

    // ---------- Reverse geocoding (Nominatim) ----------
    fun reverseGeocode(lat: Double, lon: Double): ReverseResult {
        val fallback = ReverseResult(CityLocation("Your location", "", "", lat, lon), "", "")
        return runCatching {
            val j = JSONObject(CityDataRepository.get(
                "https://nominatim.openstreetmap.org/reverse?lat=$lat&lon=$lon&format=jsonv2&zoom=12&addressdetails=1&accept-language=en"
            ))
            val a = j.optJSONObject("address") ?: return fallback
            fun s(k: String) = a.optString(k).trim()
            val name = listOf("city", "town", "village", "suburb", "city_district", "county", "state_district")
                .map { s(it) }.firstOrNull { it.isNotBlank() } ?: s("state")
            val area = listOf("suburb", "neighbourhood", "city_district", "village", "town")
                .map { s(it) }.firstOrNull { it.isNotBlank() } ?: ""
            ReverseResult(
                CityLocation(name.ifBlank { "Your location" }, s("state"), s("country"), lat, lon),
                s("country_code").uppercase(Locale.US), area
            )
        }.getOrDefault(fallback)
    }

    // ---------- Overpass ----------
    // Public Overpass servers are often busy, so all mirrors are queried in parallel and the first good answer wins.
    private val overpassHosts = listOf(
        "https://overpass-api.de/api/interpreter",
        "https://overpass.kumi.systems/api/interpreter",
        "https://overpass.private.coffee/api/interpreter",
        "https://maps.mail.ru/osm/tools/overpass/api/interpreter"
    )

    private fun overpassOnce(host: String, query: String): JSONObject {
        val key = "OP|$host|$query"
        Net.cached(key, 30L * 60_000)?.let { return JSONObject(it) }
        val c = URL(host).openConnection() as HttpURLConnection
        c.requestMethod = "POST"
        c.doOutput = true
        c.connectTimeout = 7000
        c.readTimeout = 16000
        c.setRequestProperty("User-Agent", UA)
        c.setRequestProperty("Accept", "application/json")
        c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
        try {
            c.outputStream.use { it.write(("data=" + enc(query)).toByteArray(Charsets.UTF_8)) }
            if (c.responseCode !in 200..299) throw IllegalStateException("HTTP ${c.responseCode}")
            val body = c.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)               // throws on HTML error pages
            if (json.has("remark") && json.optJSONArray("elements")?.length() == 0) throw IllegalStateException("Server busy")
            Net.put(key, body)
            return json
        } finally {
            c.disconnect()
        }
    }

    fun overpass(query: String): JSONObject {
        val done = java.util.concurrent.ArrayBlockingQueue<Result<JSONObject>>(overpassHosts.size)
        overpassHosts.forEach { host -> Net.pool.execute { done.offer(runCatching { overpassOnce(host, query) }) } }
        var last: Throwable? = null
        repeat(overpassHosts.size) {
            val r = done.poll(18, java.util.concurrent.TimeUnit.SECONDS) ?: return@repeat
            r.onSuccess { return it }
            last = r.exceptionOrNull()
        }
        throw IllegalStateException("Map data service is busy (${last?.message ?: "timeout"})")
    }

    // ---------- Nearby areas ----------
    fun nearbyAreas(lat: Double, lon: Double, radiusM: Int = 30000): List<NearbyArea> {
        val q = "[out:json][timeout:20];node[\"place\"~\"^(city|town|village|suburb)$\"][\"name\"](around:$radiusM,$lat,$lon);out 150;"
        val els = overpass(q).optJSONArray("elements") ?: return emptyList()
        val out = ArrayList<NearbyArea>()
        for (i in 0 until els.length()) {
            val e = els.getJSONObject(i)
            val tags = e.optJSONObject("tags") ?: continue
            val name = tags.optString("name_en").ifBlank { tags.optString("name") }.trim()
            if (name.isBlank() || !e.has("lat")) continue
            val d = haversineKm(lat, lon, e.getDouble("lat"), e.getDouble("lon"))
            out.add(NearbyArea(name, tags.optString("place"), d, e.getDouble("lat"), e.getDouble("lon")))
        }
        return out.distinctBy { it.name.lowercase(Locale.US) }.sortedBy { it.distanceKm }.take(14)
    }

    // ---------- Famous food by region ----------
    // Entries are "Wikipedia title|Display name"; a plain entry uses the same text for both.
    private val indiaDishes: Map<String, List<String>> = mapOf(
        "andhra pradesh" to listOf("Pesarattu", "Pulihora", "Gongura", "Bobbatlu", "Pootharekulu"),
        "arunachal pradesh" to listOf("Thukpa", "Momo (food)|Momos", "Apong", "Pika Pila"),
        "assam" to listOf("Masor tenga", "Pitha", "Khar (food)|Khar", "Aloo pitika"),
        "bihar" to listOf("Litti chokha", "Sattu", "Khaja", "Thekua", "Balushahi"),
        "chhattisgarh" to listOf("Chila (food)|Chila", "Bafauri", "Faraa", "Dehrori"),
        "goa" to listOf("Vindaloo", "Bebinca", "Sorpotel", "Xacuti", "Feni (liquor)|Feni", "Goan fish curry"),
        "gujarat" to listOf("Dhokla", "Khakhra", "Thepla", "Undhiyu", "Fafda", "Handvo"),
        "haryana" to listOf("Lassi", "Churma", "Kadhi", "Bajra"),
        "himachal pradesh" to listOf("Siddu", "Dham (food)|Himachali Dham", "Madra", "Babru"),
        "jharkhand" to listOf("Dhuska", "Litti chokha", "Thekua", "Pitha"),
        "karnataka" to listOf("Bisi bele bath", "Mysore pak", "Masala dosa", "Neer dosa", "Ragi mudde", "Dharwad pedha"),
        "kerala" to listOf("Appam", "Puttu", "Sadhya", "Malabar parotta", "Avial", "Payasam", "Fish moilee"),
        "madhya pradesh" to listOf("Poha", "Dal bafla", "Bhutte ka kees", "Mawa bati", "Jalebi"),
        "maharashtra" to listOf("Vada pav", "Misal pav", "Puran poli", "Pav bhaji", "Modak", "Sabudana khichdi"),
        "manipur" to listOf("Eromba", "Singju", "Chak-hao", "Kangshoi"),
        "meghalaya" to listOf("Jadoh", "Dohneiiong", "Tungrymbai"),
        "mizoram" to listOf("Bai", "Sawhchiar", "Vawksa rep"),
        "nagaland" to listOf("Axone", "Galho", "Naga cuisine"),
        "odisha" to listOf("Pakhala", "Dalma", "Chhena poda", "Rasabali", "Chhena gaja"),
        "punjab" to listOf("Butter chicken", "Sarson da saag", "Makki di roti", "Amritsari kulcha", "Lassi", "Chole bhature"),
        "rajasthan" to listOf("Dal baati churma", "Pyaaz kachori", "Laal maas", "Ghevar", "Gatte ki sabzi", "Mirchi vada"),
        "sikkim" to listOf("Momo (food)|Momos", "Thukpa", "Gundruk", "Sel roti", "Phagshapa"),
        "tamil nadu" to listOf("Dosa", "Idli", "Pongal (dish)|Pongal", "Chettinad chicken", "Filter coffee", "Sambar (dish)|Sambar"),
        "telangana" to listOf("Hyderabadi biryani", "Haleem", "Double ka meetha", "Irani chai", "Mirchi ka salan"),
        "tripura" to listOf("Mui Borok", "Wahan Mosdeng"),
        "uttar pradesh" to listOf("Petha", "Tunday Kababi", "Peda", "Bedmi puri", "Malaiyo", "Chaat", "Awadhi cuisine"),
        "uttarakhand" to listOf("Kafuli", "Aloo ke gutke", "Bal mithai", "Singodi", "Jhangora"),
        "west bengal" to listOf("Rosogolla", "Sandesh (confectionery)|Sandesh", "Mishti doi", "Kathi roll", "Shorshe ilish", "Puchka"),
        "delhi" to listOf("Chole bhature", "Butter chicken", "Daulat ki chaat", "Nihari", "Kulfi", "Paratha"),
        "jammu and kashmir" to listOf("Rogan josh", "Wazwan", "Gushtaba", "Dum aloo", "Noon chai", "Kahwah"),
        "ladakh" to listOf("Thukpa", "Skyu", "Momo (food)|Momos", "Butter tea", "Khambir"),
        "chandigarh" to listOf("Chole bhature", "Amritsari kulcha", "Lassi", "Butter chicken", "Paratha"),
        "puducherry" to listOf("Dosa", "Idli", "Sambar (dish)|Sambar", "Filter coffee", "Crêpe")
    )

    private val regionAliases = mapOf(
        "nct" to "delhi", "national capital territory" to "delhi", "orissa" to "odisha",
        "uttaranchal" to "uttarakhand", "pondicherry" to "puducherry", "jammu & kashmir" to "jammu and kashmir",
        "jammu and kashmir" to "jammu and kashmir", "telengana" to "telangana"
    )

    private fun indiaKey(region: String): String? {
        val r = region.lowercase(Locale.US).replace("&", "and").trim()
        if (r.isBlank()) return null
        regionAliases.entries.firstOrNull { r.contains(it.key) }?.let { return it.value }
        return indiaDishes.keys.firstOrNull { r.contains(it) }
    }

    private val foodWords = listOf(
        "dish", "food", "cuisine", "soup", "bread", "rice", "curry", "sweet", "snack", "beverage", "drink",
        "pastry", "stew", "sauce", "salad", "noodle", "dessert", "cake", "dumpling", "sausage", "cheese", "pie", "tea", "coffee"
    )

    fun regionFoods(region: String, country: String): FoodBundle {
        val label = region.ifBlank { country }
        if (label.isBlank()) return FoodBundle("", emptyList())
        val key = if (country.equals("India", true) || country.isBlank()) indiaKey(region) else null
        if (key != null) {
            // "Wikipedia title|Display name" or a plain name used for both.
            val titled = indiaDishes.getValue(key).map {
                val p = it.split("|")
                if (p.size == 2) Pair(p[1], p[0]) else Pair(p[0], p[0])   // (display, wiki title)
            }
            val pages = runCatching { wikiBatch(titled.map { it.second }) }.getOrDefault(emptyMap())
            val pretty = key.split(" ").joinToString(" ") { w -> w.replaceFirstChar { c -> c.uppercase() } }
            val items = titled.map { (display, title) ->
                val page = pages[title]
                val extract = page?.optString("extract")?.trim().orEmpty()
                val ok = extract.isNotBlank() && !extract.contains("may refer to", true)
                FoodItem(display, if (ok) extract else "A famous local favourite of $pretty.", if (ok) thumb(page) else null, title)
            }
            return FoodBundle(pretty, items)
        }
        // Outside the curated list: discover dishes from Wikipedia.
        val items = runCatching { dynamicFoods(region, country) }.getOrDefault(emptyList())
        return FoodBundle(label, items)
    }

    private fun thumb(page: JSONObject?): String? {
        val u = page?.optJSONObject("thumbnail")?.optString("source")
        return u?.takeIf { it.startsWith("http") && !it.lowercase(Locale.US).contains(".svg") }
    }

    private fun dynamicFoods(region: String, country: String): List<FoodItem> {
        val seen = LinkedHashMap<String, FoodItem>()
        for (q in listOf("$region $country traditional dish", "$country traditional dish cuisine")) {
            if (seen.size >= 8) break
            val url = "https://en.wikipedia.org/w/api.php?action=query&generator=search&gsrsearch=${enc(q.trim())}&gsrlimit=20" +
                "&prop=pageimages%7Cextracts%7Cdescription&piprop=thumbnail&pithumbsize=700&pilimit=20&exintro=1&explaintext=1&exsentences=2&exlimit=20&format=json&formatversion=2"
            val pages = JSONObject(CityDataRepository.get(url)).optJSONObject("query")?.optJSONArray("pages") ?: continue
            for (i in 0 until pages.length()) {
                val p = pages.getJSONObject(i)
                val title = p.optString("title")
                val blob = (p.optString("description") + " " + p.optString("extract")).lowercase(Locale.US)
                if (title.startsWith("List of", true)) continue
                if (foodWords.none { blob.contains(it) }) continue
                seen.putIfAbsent(title.lowercase(Locale.US), FoodItem(title, p.optString("extract").trim().ifBlank { p.optString("description") }, thumb(p), title))
            }
        }
        return seen.values.take(8)
    }

    /** Batch-resolve Wikipedia titles (following redirects) to page objects keyed by the requested title. */
    private fun wikiBatch(titles: List<String>): Map<String, JSONObject> {
        val url = "https://en.wikipedia.org/w/api.php?action=query&titles=${enc(titles.joinToString("|"))}&redirects=1" +
            "&prop=pageimages%7Cextracts%7Cdescription&piprop=thumbnail&pithumbsize=700&pilimit=50&exintro=1&explaintext=1&exsentences=2&exlimit=20&format=json&formatversion=2"
        val q = JSONObject(CityDataRepository.get(url)).optJSONObject("query") ?: return emptyMap()
        val norm = HashMap<String, String>()
        val redir = HashMap<String, String>()
        q.optJSONArray("normalized")?.let { for (i in 0 until it.length()) it.getJSONObject(i).let { o -> norm[o.optString("from")] = o.optString("to") } }
        q.optJSONArray("redirects")?.let { for (i in 0 until it.length()) it.getJSONObject(i).let { o -> redir[o.optString("from")] = o.optString("to") } }
        val byTitle = HashMap<String, JSONObject>()
        q.optJSONArray("pages")?.let { for (i in 0 until it.length()) it.getJSONObject(i).let { p -> if (!p.optBoolean("missing")) byTitle[p.optString("title")] = p } }
        val out = LinkedHashMap<String, JSONObject>()
        for (t in titles) {
            val n = norm[t] ?: t
            val f = redir[n] ?: n
            byTitle[f]?.let { out[t] = it }
        }
        return out
    }

    /** Exact-title Wikipedia lead images for several place names in one request. Only returns images of the matching article. */
    fun wikiImages(names: List<String>): Map<String, String> {
        if (names.isEmpty()) return emptyMap()
        val pages = wikiBatch(names)
        val out = HashMap<String, String>()
        for ((name, page) in pages) {
            val extract = page.optString("extract")
            if (extract.contains("may refer to", true)) continue
            if (!ImageMatch.matches(page.optString("title"), name)) continue
            thumb(page)?.let { out[name] = it }
        }
        return out
    }

    // ---------- Full article text (shown in-app instead of sending people to Wikipedia) ----------
    fun titleFromUrl(url: String?): String? {
        if (url.isNullOrBlank() || !url.contains("wikipedia.org/wiki/")) return null
        return runCatching { URLDecoder.decode(url.substringAfter("/wiki/").substringBefore("#").substringBefore("?"), "UTF-8").replace('_', ' ') }.getOrNull()
    }

    private val skipHeadings = setOf(
        "see also", "references", "external links", "further reading", "notes", "bibliography",
        "sources", "citations", "footnotes", "gallery", "explanatory notes", "general references"
    )

    fun fetchArticle(titleOrUrl: String, searchFallback: String? = null): Article {
        var title = titleFromUrl(titleOrUrl) ?: titleOrUrl
        var page = loadPage(title)
        if ((page == null || page.optBoolean("missing")) && !searchFallback.isNullOrBlank()) {
            val hits = org.json.JSONArray(CityDataRepository.get("https://en.wikipedia.org/w/api.php?action=opensearch&search=${enc(searchFallback)}&limit=1&format=json")).optJSONArray(1)
            title = hits?.optString(0).orEmpty()
            page = if (title.isNotBlank()) loadPage(title) else null
        }
        if (page == null || page.optBoolean("missing")) throw IllegalStateException("No detailed article is available for this place yet.")
        val text = page.optString("extract")
        val sections = ArrayList<ArticleSection>()
        var heading: String? = null
        val buf = StringBuilder()
        fun flush() {
            val h = heading
            val body = buf.toString().replace(Regex("\n{3,}"), "\n\n").trim()
            if (body.isNotBlank() && (h == null || h.lowercase(Locale.US) !in skipHeadings)) {
                sections.add(ArticleSection(h, if (body.length > 3000) body.take(3000).trimEnd() + "…" else body))
            }
            buf.setLength(0)
        }
        val headingRe = Regex("^(={2,6})\\s*(.+?)\\s*\\1$")
        for (line in text.lines()) {
            val m = headingRe.matchEntire(line.trim())
            if (m != null) {
                if (m.groupValues[1].length == 2) { flush(); heading = m.groupValues[2] }
                else buf.append("\n").append(m.groupValues[2]).append("\n")
            } else buf.append(line).append("\n")
        }
        flush()
        return Article(
            page.optString("title", title),
            page.optString("description"),
            page.optJSONObject("thumbnail")?.optString("source")?.takeIf { it.startsWith("http") },
            page.optString("fullurl"),
            sections.take(14)
        )
    }

    private fun loadPage(title: String): JSONObject? {
        val url = "https://en.wikipedia.org/w/api.php?action=query&titles=${enc(title)}&redirects=1" +
            "&prop=extracts%7Cpageimages%7Cdescription%7Cinfo&explaintext=1&exsectionformat=wiki&piprop=thumbnail&pithumbsize=1000&inprop=url&format=json&formatversion=2"
        return JSONObject(CityDataRepository.get(url)).optJSONObject("query")?.optJSONArray("pages")?.optJSONObject(0)
    }
}

/** Decides whether an image file/article title really refers to a given place. */
object ImageMatch {
    private val generic = setOf(
        "the", "and", "temple", "mandir", "fort", "palace", "museum", "garden", "gardens", "park", "lake", "ghat",
        "mahal", "national", "sanctuary", "road", "market", "tomb", "complex", "city", "old", "new", "shri", "sri", "file"
    )

    private fun norm(s: String) = java.text.Normalizer.normalize(s.lowercase(Locale.US), java.text.Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")

    private fun tokens(s: String) = norm(s).split(Regex("[^a-z0-9]+")).filter { it.length >= 3 }

    fun matches(title: String, placeName: String): Boolean {
        val all = tokens(placeName)
        if (all.isEmpty()) return false
        val sig = all.filter { it !in generic }.ifEmpty { all }
        val t = norm(title)
        val hit = sig.count { t.contains(it) }
        return hit >= kotlin.math.ceil(sig.size * 0.6).toInt().coerceAtLeast(1)
    }
}
