package com.anuragrajpandey.smarttravel

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.google.android.material.card.MaterialCardView
import java.net.URLEncoder
import java.util.Locale

/**
 * Google Maps shown inside the app (WebView), so users never leave FlipDoor.
 * Uses the official Maps Embed API when MAPS_EMBED_KEY is set in gradle.properties,
 * otherwise Google's keyless "output=embed" map.
 */
object MapUi {
    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
    private fun coord(v: Double) = String.format(Locale.US, "%.6f", v)

    /** mode: "place" (pin) | "search" (nearby query) | "route" (from -> to). */
    fun embedUrl(
        lat: Double, lon: Double, mode: String = "place", query: String? = null,
        fromLat: Double? = null, fromLon: Double? = null
    ): String {
        val key = BuildConfig.MAPS_EMBED_KEY
        val here = "${coord(lat)},${coord(lon)}"
        if (key.isNotBlank()) {
            return when {
                mode == "search" && query != null ->
                    "https://www.google.com/maps/embed/v1/search?key=$key&q=${enc(query)}&center=$here&zoom=14"
                mode == "route" && fromLat != null && fromLon != null ->
                    "https://www.google.com/maps/embed/v1/directions?key=$key&origin=${coord(fromLat)},${coord(fromLon)}&destination=$here"
                else -> "https://www.google.com/maps/embed/v1/place?key=$key&q=$here&zoom=15"
            }
        }
        return when {
            mode == "search" && query != null ->
                "https://maps.google.com/maps?q=${enc("$query near $here")}&ll=$here&z=14&output=embed&hl=en"
            mode == "route" && fromLat != null && fromLon != null ->
                "https://maps.google.com/maps?saddr=${coord(fromLat)},${coord(fromLon)}&daddr=$here&output=embed&hl=en"
            else -> "https://maps.google.com/maps?q=$here&z=15&output=embed&hl=en"
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    fun createWebView(ctx: Context): WebView = WebView(ctx).apply {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.loadWithOverviewMode = true
        settings.useWideViewPort = true
        settings.cacheMode = WebSettings.LOAD_DEFAULT
        setBackgroundColor(0xFFE5EAF2.toInt())
        webViewClient = object : WebViewClient() {
            // Keep every navigation inside the embedded map.
            override fun shouldOverrideUrlLoading(view: WebView, request: android.webkit.WebResourceRequest) = false
        }
        webChromeClient = WebChromeClient()
    }

    fun load(web: WebView, url: String) {
        val safe = url.replace("&", "&amp;").replace("\"", "&quot;")
        val html = "<!doctype html><html><head><meta name='viewport' content='width=device-width,initial-scale=1'>" +
            "<style>html,body{margin:0;height:100%;background:#e5eaf2}iframe{border:0;width:100%;height:100%}</style></head>" +
            "<body><iframe src=\"$safe\" allowfullscreen referrerpolicy=\"no-referrer-when-downgrade\"></iframe></body></html>"
        web.loadDataWithBaseURL("https://www.google.com/", html, "text/html", "UTF-8", null)
    }

    /** A rounded map card to place inside a scrolling screen. */
    @SuppressLint("ClickableViewAccessibility")
    fun inlineMap(
        ctx: Context, lat: Double, lon: Double, mode: String = "place", query: String? = null,
        heightDp: Int = 230, onExpand: (() -> Unit)? = null
    ): View {
        val card = MaterialCardView(ctx).apply {
            radius = Ui.dp(ctx, 18).toFloat()
            cardElevation = Ui.dp(ctx, 2).toFloat()
            setCardBackgroundColor(Color.WHITE)
        }
        val frame = FrameLayout(ctx)
        val web = createWebView(ctx)
        // Let the map pan/zoom without the surrounding ScrollView stealing the gesture.
        web.setOnTouchListener { v, _ -> v.parent.requestDisallowInterceptTouchEvent(true); false }
        frame.addView(web, FrameLayout.LayoutParams(-1, -1))
        if (onExpand != null) {
            val expand = Ui.iconButton(ctx, R.drawable.ic_map, Color.WHITE, 0xCC006CE4.toInt(), 44, 22) { onExpand() }
            frame.addView(expand, FrameLayout.LayoutParams(Ui.dp(ctx, 44), Ui.dp(ctx, 44), Gravity.TOP or Gravity.END).apply {
                topMargin = Ui.dp(ctx, 10); rightMargin = Ui.dp(ctx, 10)
            })
        }
        card.addView(frame, FrameLayout.LayoutParams(-1, Ui.dp(ctx, heightDp)))
        // Start the map a moment after the screen appears so scrolling/first paint stay smooth.
        web.postDelayed({ load(web, embedUrl(lat, lon, mode, query)) }, 450)
        return card
    }

    fun openMap(ctx: Context, lat: Double, lon: Double, title: String, query: String? = null) {
        ctx.startActivity(
            android.content.Intent(ctx, MapActivity::class.java)
                .putExtra("lat", lat).putExtra("lon", lon).putExtra("title", title)
                .putExtra("query", query)
        )
    }
}
