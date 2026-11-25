package com.anuragrajpandey.smarttravel

import android.graphics.Color
import android.os.Bundle
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MapActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.WHITE
        window.navigationBarColor = Color.WHITE
        val lat = intent.getDoubleExtra("lat", 20.5937)
        val lon = intent.getDoubleExtra("lon", 78.9629)
        val title = intent.getStringExtra("title") ?: "Explore nearby"
        val root = FrameLayout(this).apply { fitsSystemWindows = true; setBackgroundColor(Color.WHITE) }
        val map = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            webChromeClient = WebChromeClient()
            webViewClient = WebViewClient()
        }
        val html = """
            <!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1">
            <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css">
            <style>html,body,#map{height:100%;margin:0;font-family:Arial}.leaflet-control-attribution{font-size:9px}</style>
            <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script></head>
            <body><div id="map"></div><script>
            const map=L.map('map').setView([${lat},${lon}],14);
            L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png',{maxZoom:19,attribution:'© OpenStreetMap contributors'}).addTo(map);
            L.marker([${lat},${lon}]).addTo(map).bindPopup(${org.json.JSONObject.quote(title)}).openPopup();
            </script></body></html>
        """.trimIndent()
        map.loadDataWithBaseURL("https://localhost/", html, "text/html", "UTF-8", null)
        root.addView(map, FrameLayout.LayoutParams(-1, -1))
        val close = TextView(this).apply {
            text = "‹  Back"; textSize = 16f; setTextColor(Color.BLACK)
            setBackgroundColor(0xEEFFFFFF.toInt()); setPadding(24, 18, 24, 18)
            setOnClickListener { finish() }
        }
        root.addView(close, FrameLayout.LayoutParams(-2, -2, android.view.Gravity.TOP or android.view.Gravity.START))
        setContentView(root)
    }
    private fun orgJsonQuotePlaceholder(): String = org.json.JSONObject.quote(intent.getStringExtra("title") ?: "Explore nearby")
}
