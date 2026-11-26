package com.anuragrajpandey.smarttravel

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity

/** In-app browser: websites, booking pages and live hotel prices open here instead of leaving the app. */
class WebPageActivity : AppCompatActivity() {
    private lateinit var web: WebView
    private lateinit var progressBar: ProgressBar
    private lateinit var titleView: TextView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val url = intent.getStringExtra("url").orEmpty()
        val title = intent.getStringExtra("title").orEmpty().ifBlank { Uri.parse(url).host.orEmpty() }

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Ui.BG) }
        val bar = Ui.topBar(this, title)
        titleView = bar.getChildAt(1) as TextView
        root.addView(bar)
        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100; visibility = android.view.View.GONE
        }
        root.addView(progressBar, LinearLayout.LayoutParams(-1, Ui.dp(this, 3)))
        web = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.cacheMode = WebSettings.LOAD_DEFAULT
            settings.setSupportZoom(true)
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    val u = request.url
                    return when (u.scheme) {
                        "http", "https" -> false
                        "tel", "mailto" -> { runCatching { startActivity(Intent(Intent.ACTION_VIEW, u)) }; true }
                        else -> true
                    }
                }
                override fun onPageStarted(view: WebView, u: String, favicon: Bitmap?) { progressBar.visibility = android.view.View.VISIBLE }
                override fun onPageFinished(view: WebView, u: String) {
                    progressBar.visibility = android.view.View.GONE
                    if (intent.getStringExtra("title").isNullOrBlank()) view.title?.let { titleView.text = it }
                }
            }
            webChromeClient = object : android.webkit.WebChromeClient() {
                override fun onProgressChanged(view: WebView, newProgress: Int) { progressBar.progress = newProgress }
            }
        }
        root.addView(web, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        Ui.edgeToEdge(this, root)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (web.canGoBack()) web.goBack() else { isEnabled = false; onBackPressedDispatcher.onBackPressed() }
            }
        })
        if (url.startsWith("http")) web.loadUrl(url) else finish()
    }

    override fun onDestroy() {
        web.stopLoading()
        web.destroy()
        super.onDestroy()
    }

    companion object {
        fun open(ctx: Context, url: String, title: String = "") {
            ctx.startActivity(Intent(ctx, WebPageActivity::class.java).putExtra("url", url).putExtra("title", title))
        }
    }
}
