package com.anuragrajpandey.smarttravel

import android.content.Context
import android.graphics.Color
import android.text.TextUtils
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlin.concurrent.thread

/** Renders full place/dish information inside the app, so nobody is sent off to Wikipedia. */
object ArticleUi {
    fun render(ctx: Context, parent: LinearLayout, a: Article, showTitle: Boolean = true) {
        fun dp(v: Int) = Ui.dp(ctx, v)
        if (showTitle) {
            parent.addView(Ui.text(ctx, a.title, 24f, Ui.FG, true))
            if (a.description.isNotBlank()) parent.addView(Ui.text(ctx, a.description.replaceFirstChar { it.uppercase() }, 13.5f, Ui.ACCENT, true))
        }
        a.sections.forEachIndexed { idx, s ->
            val card = Ui.card(ctx)
            if (s.heading != null) card.addView(Ui.text(ctx, s.heading, 16.5f, Ui.FG, true))
            else card.addView(Ui.text(ctx, "Overview", 16.5f, Ui.FG, true))
            val bodyView = TextView(ctx).apply {
                text = s.body
                textSize = 14f
                setTextColor(0xFF3A4A63.toInt())
                setLineSpacing(0f, 1.18f)
                val collapsedLines = if (idx == 0) 12 else 6
                maxLines = collapsedLines
                ellipsize = TextUtils.TruncateAt.END
            }
            card.addView(bodyView)
            // Offer "Read more" only when the text is long enough to be clipped.
            if (s.body.length > (if (idx == 0) 650 else 330)) {
                val toggle = TextView(ctx).apply {
                    text = "Read more"
                    textSize = 13.5f
                    setTextColor(Ui.ACCENT)
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                    setPadding(0, dp(8), 0, dp(2))
                    minHeight = dp(40)
                }
                var open = false
                val collapsed = bodyView.maxLines
                toggle.setOnClickListener {
                    open = !open
                    bodyView.maxLines = if (open) Int.MAX_VALUE else collapsed
                    toggle.text = if (open) "Show less" else "Read more"
                }
                card.addView(toggle)
            }
            parent.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
        }
        parent.addView(Ui.text(ctx, "Information from Wikipedia contributors, licensed CC BY-SA 4.0.", 11f, Ui.MUTED))
    }
}

/** Opens any place, dish or city overview in-app. Pass an exact title / Wikipedia URL, or a search phrase. */
class ArticleActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        val ref = intent.getStringExtra("ref").orEmpty()
        val search = intent.getStringExtra("search")
        val fallbackImage = intent.getStringExtra("image")
        val name = intent.getStringExtra("name").orEmpty()
        fun dp(v: Int) = Ui.dp(this, v)

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Ui.BG) }
        root.addView(Ui.topBar(this, name.ifBlank { "Details" }))
        val scroll = androidx.core.widget.NestedScrollView(this)
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(4), dp(16), dp(28)) }
        scroll.addView(body)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        Ui.edgeToEdge(this, root)

        val spinner = android.widget.ProgressBar(this)
        body.addView(spinner, LinearLayout.LayoutParams(-1, dp(70)).apply { topMargin = dp(60) })
        thread {
            try {
                val article = GeoRepository.fetchArticle(ref.ifBlank { name }, search ?: name)
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    body.removeAllViews()
                    val img = article.imageUrl ?: fallbackImage
                    if (img != null) {
                        val iv = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP; background = Ui.shape(this@ArticleActivity, 0xFFE5EAF2.toInt(), 18); clipToOutline = true }
                        ImageLoader.load(img, iv)
                        body.addView(iv, LinearLayout.LayoutParams(-1, dp(220)).apply { bottomMargin = dp(12) })
                    }
                    ArticleUi.render(this, body, article)
                }
            } catch (e: Exception) {
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    body.removeAllViews()
                    body.addView(Ui.text(this, e.message ?: "Couldn't load details. Check your connection.", 14f, Ui.MUTED).apply { setPadding(dp(4), dp(30), dp(4), dp(8)) })
                }
            }
        }
    }

    companion object {
        fun open(ctx: Context, name: String, ref: String? = null, search: String? = null, image: String? = null) {
            ctx.startActivity(
                android.content.Intent(ctx, ArticleActivity::class.java)
                    .putExtra("name", name).putExtra("ref", ref).putExtra("search", search).putExtra("image", image)
            )
        }
    }
}

/** Small round icon badge used in list tiles. */
object FrameLayout2 {
    fun badge(ctx: Context, res: Int): View = android.widget.FrameLayout(ctx).apply {
        background = Ui.shape(ctx, Ui.ACCENT_SOFT, 22)
        addView(Ui.iconView(ctx, res, Ui.ACCENT), android.widget.FrameLayout.LayoutParams(Ui.dp(ctx, 22), Ui.dp(ctx, 22), android.view.Gravity.CENTER))
    }
}

/** Dish cards shown on Explore, the Food tab and area guides; always open details in-app. */
object FoodUi {
    private fun open(ctx: Context, item: FoodItem) = ArticleActivity.open(ctx, item.name, ref = item.wikiTitle, image = item.imageUrl)

    fun tallCard(ctx: Context, item: FoodItem): View {
        val card = com.google.android.material.card.MaterialCardView(ctx).apply {
            radius = Ui.dp(ctx, 16).toFloat(); cardElevation = Ui.dp(ctx, 1).toFloat()
            strokeWidth = Ui.dp(ctx, 1); strokeColor = Ui.BORDER
            setOnClickListener { open(ctx, item) }
        }
        val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        val img = ImageView(ctx).apply { scaleType = ImageView.ScaleType.CENTER_CROP; setBackgroundColor(0xFFFFF2E6.toInt()) }
        if (item.imageUrl != null) ImageLoader.load(item.imageUrl, img) else { img.setImageResource(R.drawable.ic_restaurant); img.scaleType = ImageView.ScaleType.CENTER_INSIDE; img.setPadding(Ui.dp(ctx, 34), Ui.dp(ctx, 34), Ui.dp(ctx, 34), Ui.dp(ctx, 34)) }
        col.addView(img, LinearLayout.LayoutParams(-1, Ui.dp(ctx, 104)))
        col.addView(Ui.text(ctx, item.name, 14.5f, Ui.FG, true).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END; setPadding(Ui.dp(ctx, 10), Ui.dp(ctx, 8), Ui.dp(ctx, 10), 0) })
        col.addView(Ui.text(ctx, item.description, 11.5f, Ui.MUTED).apply { maxLines = 2; ellipsize = TextUtils.TruncateAt.END; setPadding(Ui.dp(ctx, 10), 0, Ui.dp(ctx, 10), Ui.dp(ctx, 8)) })
        card.addView(col)
        return card
    }

    fun addRow(ctx: Context, parent: LinearLayout, item: FoodItem) {
        val box = Ui.card(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL; isClickable = true; setOnClickListener { open(ctx, item) } }
        val img = ImageView(ctx).apply { scaleType = ImageView.ScaleType.CENTER_CROP; background = Ui.shape(ctx, 0xFFFFF2E6.toInt(), 14); clipToOutline = true }
        if (item.imageUrl != null) ImageLoader.load(item.imageUrl, img) else { img.setImageResource(R.drawable.ic_restaurant); img.scaleType = ImageView.ScaleType.CENTER_INSIDE; img.setPadding(Ui.dp(ctx, 22), Ui.dp(ctx, 22), Ui.dp(ctx, 22), Ui.dp(ctx, 22)) }
        box.addView(img, LinearLayout.LayoutParams(Ui.dp(ctx, 84), Ui.dp(ctx, 84)).apply { rightMargin = Ui.dp(ctx, 12) })
        val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        col.addView(Ui.text(ctx, item.name, 16f, Ui.FG, true))
        col.addView(Ui.text(ctx, item.description, 12f, Ui.MUTED).apply { maxLines = 3; ellipsize = TextUtils.TruncateAt.END })
        box.addView(col, LinearLayout.LayoutParams(0, -2, 1f))
        parent.addView(box, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = Ui.dp(ctx, 9) })
    }
}
