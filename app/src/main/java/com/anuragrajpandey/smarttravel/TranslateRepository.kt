package com.anuragrajpandey.smarttravel

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class Lang(val name: String, val code: String, val tts: String = code)
data class Translation(val text: String, val detected: String?)

/** Online translation (nothing to download). Tries Google's web translate endpoint, then MyMemory as a fallback. */
object TranslateRepository {
    const val AUTO = "auto"
    val languages = listOf(
        Lang("English", "en", "en"), Lang("Hindi", "hi"), Lang("Bengali", "bn"), Lang("Tamil", "ta"), Lang("Telugu", "te"),
        Lang("Marathi", "mr"), Lang("Gujarati", "gu"), Lang("Kannada", "kn"), Lang("Malayalam", "ml"), Lang("Punjabi", "pa"),
        Lang("Urdu", "ur"), Lang("Odia", "or"), Lang("Assamese", "as"), Lang("Nepali", "ne"),
        Lang("Spanish", "es"), Lang("French", "fr"), Lang("German", "de"), Lang("Italian", "it"), Lang("Portuguese", "pt"),
        Lang("Russian", "ru"), Lang("Japanese", "ja"), Lang("Korean", "ko"), Lang("Chinese", "zh-CN", "zh"),
        Lang("Arabic", "ar"), Lang("Thai", "th"), Lang("Indonesian", "id"), Lang("Turkish", "tr")
    )

    fun nameOf(code: String) = if (code == AUTO) "Detect language" else languages.firstOrNull { it.code.equals(code, true) }?.name
        ?: languages.firstOrNull { it.code.substringBefore('-').equals(code.substringBefore('-'), true) }?.name ?: code

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    fun translate(text: String, from: String, to: String): Translation {
        val clean = text.trim()
        if (clean.isEmpty()) return Translation("", null)
        if (from == to) return Translation(clean, from)
        val cached = Net.cached("TR|$from|$to|$clean", 3_600_000L)
        if (cached != null) return JSONObject(cached).let { Translation(it.getString("t"), it.optString("d").ifBlank { null }) }
        val result = try {
            viaGoogle(clean, from, to)
        } catch (e: Exception) {
            viaMyMemory(clean, if (from == AUTO) "en" else from, to)
        }
        Net.put("TR|$from|$to|$clean", JSONObject().put("t", result.text).put("d", result.detected ?: "").toString())
        return result
    }

    private fun chunks(text: String, max: Int): List<String> {
        if (text.length <= max) return listOf(text)
        val out = ArrayList<String>()
        val sb = StringBuilder()
        for (piece in text.split(Regex("(?<=[.!?।\\n])\\s*"))) {
            if (sb.length + piece.length + 1 > max && sb.isNotEmpty()) { out.add(sb.toString().trim()); sb.setLength(0) }
            if (piece.length > max) piece.chunked(max).forEach { out.add(it) } else sb.append(piece).append(' ')
        }
        if (sb.isNotBlank()) out.add(sb.toString().trim())
        return out
    }

    private fun http(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 8000
        c.readTimeout = 12000
        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 13) FlipDoor/1.1")
        return try {
            if (c.responseCode !in 200..299) throw IllegalStateException("HTTP ${c.responseCode}")
            c.inputStream.bufferedReader().use { it.readText() }
        } finally { c.disconnect() }
    }

    private fun viaGoogle(text: String, from: String, to: String): Translation {
        var detected: String? = null
        val sb = StringBuilder()
        for (part in chunks(text, 1500)) {
            val root = JSONArray(http("https://translate.googleapis.com/translate_a/single?client=gtx&sl=$from&tl=$to&dt=t&q=${enc(part)}"))
            val segs = root.getJSONArray(0)
            for (i in 0 until segs.length()) sb.append(segs.getJSONArray(i).optString(0, ""))
            if (detected == null) detected = root.optString(2).ifBlank { null }
            sb.append(' ')
        }
        val out = sb.toString().trim()
        if (out.isEmpty()) throw IllegalStateException("Empty translation")
        return Translation(out, detected)
    }

    private fun viaMyMemory(text: String, from: String, to: String): Translation {
        val sb = StringBuilder()
        for (part in chunks(text, 450)) {
            val j = JSONObject(http("https://api.mymemory.translated.net/get?q=${enc(part)}&langpair=${enc("$from|$to")}"))
            if (j.optInt("responseStatus", 200) != 200) throw IllegalStateException(j.optString("responseDetails", "Translation unavailable"))
            sb.append(j.getJSONObject("responseData").optString("translatedText")).append(' ')
        }
        return Translation(sb.toString().trim(), from)
    }
}
