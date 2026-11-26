package com.anuragrajpandey.smarttravel

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.card.MaterialCardView
import java.util.Locale
import kotlin.math.abs

class PlacesCarouselView(
    private val context: Context,
    private val onPlaceClick: (MostVisitedPlace) -> Unit,
    private val onPlanClick: (MostVisitedPlace) -> Unit
) {
    private val blue = 0xFF006CE4.toInt()
    private val gold = 0xFFFFB700.toInt()
    private val ink = 0xFF172B4D.toInt()
    private val muted = 0xFF718096.toInt()

    private val slideGradients = listOf(
        intArrayOf(0xFF0B3C5D.toInt(), 0xFF328CC1.toInt()),
        intArrayOf(0xFF1D4E89.toInt(), 0xFF00B2CA.toInt()),
        intArrayOf(0xFF2C5364.toInt(), 0xFF203A43.toInt()),
        intArrayOf(0xFF512D6D.toInt(), 0xFF00C1D4.toInt()),
        intArrayOf(0xFF833AB4.toInt(), 0xFFFD1D1D.toInt()),
        intArrayOf(0xFF134E5E.toInt(), 0xFF71B280.toInt())
    )

    val container: LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
    }

    private val pager: ViewPager2 = ViewPager2(context).apply {
        offscreenPageLimit = 2
    }
    private val dotsRow: LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
    }
    private val thumbRow: LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
    }
    private val thumbScroll: HorizontalScrollView = HorizontalScrollView(context).apply {
        isHorizontalScrollBarEnabled = false
        addView(thumbRow)
    }

    private var places: List<MostVisitedPlace> = emptyList()
    private var currentPos = 0

    init {
        pager.layoutParams = LinearLayout.LayoutParams(-1, dp(276))
        setupHorizontalTouchFriendly(pager)
        pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                currentPos = position
                updateIndicators()
            }
        })
        container.addView(pager)
        container.addView(dotsRow, LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(10)
            bottomMargin = dp(10)
        })
        container.addView(thumbScroll, LinearLayout.LayoutParams(-1, -2).apply {
            bottomMargin = dp(4)
        })
    }

    fun submitPlaces(items: List<MostVisitedPlace>) {
        places = items
        currentPos = 0
        pager.adapter = SlideAdapter(items)
        buildThumbs(items)
        updateIndicators()
        thumbScroll.visibility = if (items.size > 1) View.VISIBLE else View.GONE
        dotsRow.visibility = if (items.size > 1) View.VISIBLE else View.GONE
    }

    private fun setupHorizontalTouchFriendly(vp: ViewPager2) {
        val innerRv = vp.getChildAt(0) as? RecyclerView ?: return
        var startX = 0f
        var startY = 0f
        innerRv.addOnItemTouchListener(object : RecyclerView.OnItemTouchListener {
            override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        startX = e.x
                        startY = e.y
                        rv.parent?.requestDisallowInterceptTouchEvent(true)
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = abs(e.x - startX)
                        val dy = abs(e.y - startY)
                        if (dy > dx && dy > dp(10)) {
                            rv.parent?.requestDisallowInterceptTouchEvent(false)
                        } else {
                            rv.parent?.requestDisallowInterceptTouchEvent(true)
                        }
                    }
                }
                return false
            }
            override fun onTouchEvent(rv: RecyclerView, e: MotionEvent) {}
            override fun onRequestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {}
        })
    }

    private fun buildThumbs(items: List<MostVisitedPlace>) {
        thumbRow.removeAllViews()
        items.forEachIndexed { index, place ->
            val card = MaterialCardView(context).apply {
                radius = dp(12).toFloat()
                cardElevation = dp(1).toFloat()
                strokeWidth = dp(2)
                strokeColor = if (index == currentPos) blue else Color.TRANSPARENT
                setCardBackgroundColor(0xFFE5EAF2.toInt())
                setOnClickListener {
                    if (pager.currentItem == index) {
                        onPlaceClick(place)
                    } else {
                        pager.setCurrentItem(index, true)
                    }
                }
            }
            val box = FrameLayout(context)
            val grad = GradientDrawable(GradientDrawable.Orientation.TL_BR, slideGradients[index % slideGradients.size])
            val bgView = View(context).apply { background = grad }
            box.addView(bgView, FrameLayout.LayoutParams(-1, -1))
            val img = ImageView(context).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
            }
            ImageLoader.load(place.imageUrl, img)
            box.addView(img, FrameLayout.LayoutParams(-1, -1))
            val caption = TextView(context).apply {
                text = place.name
                textSize = 9.5f
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                setTextColor(Color.WHITE)
                setTypeface(typeface, Typeface.BOLD)
                setPadding(dp(5), dp(3), dp(5), dp(4))
                background = GradientDrawable(
                    GradientDrawable.Orientation.BOTTOM_TOP,
                    intArrayOf(0xCC0D1B2A.toInt(), 0x000D1B2A)
                )
            }
            box.addView(caption, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
            card.addView(box, FrameLayout.LayoutParams(dp(86), dp(62)))
            thumbRow.addView(card, LinearLayout.LayoutParams(-2, -2).apply {
                rightMargin = dp(8)
            })
        }
    }

    private fun updateIndicators() {
        dotsRow.removeAllViews()
        val count = places.size
        for (i in 0 until count) {
            val active = i == currentPos
            val dot = View(context).apply {
                background = GradientDrawable().apply {
                    setColor(if (active) blue else 0xFFCBD5E1.toInt())
                    cornerRadius = dp(4).toFloat()
                }
            }
            dotsRow.addView(dot, LinearLayout.LayoutParams(dp(if (active) 20 else 7), dp(7)).apply {
                setMargins(dp(3), 0, dp(3), 0)
            })
        }
        for (i in 0 until thumbRow.childCount) {
            val card = thumbRow.getChildAt(i) as? MaterialCardView ?: continue
            card.strokeColor = if (i == currentPos) blue else Color.TRANSPARENT
        }
    }

    private inner class SlideAdapter(private val items: List<MostVisitedPlace>) :
        RecyclerView.Adapter<SlideAdapter.SlideVH>() {

        inner class SlideVH(val card: MaterialCardView, val root: FrameLayout) : RecyclerView.ViewHolder(card)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SlideVH {
            val card = MaterialCardView(context).apply {
                layoutParams = ViewGroup.LayoutParams(-1, -1)
                radius = dp(22).toFloat()
                cardElevation = dp(3).toFloat()
                strokeWidth = dp(1)
                strokeColor = 0x22000000
            }
            val root = FrameLayout(context)
            card.addView(root, FrameLayout.LayoutParams(-1, -1))
            return SlideVH(card, root)
        }

        override fun getItemCount(): Int = items.size

        override fun onBindViewHolder(holder: SlideVH, position: Int) {
            val place = items[position]
            val root = holder.root
            root.removeAllViews()

            // Gradient fallback backdrop
            val grad = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                slideGradients[position % slideGradients.size]
            )
            root.addView(View(context).apply { background = grad }, FrameLayout.LayoutParams(-1, -1))

            // Photo
            val imageView = ImageView(context).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
            }
            root.addView(imageView, FrameLayout.LayoutParams(-1, -1))
            ImageLoader.load(place.imageUrl, imageView)

            // Dark scrim for legible overlay text
            val scrim = View(context).apply {
                background = GradientDrawable(
                    GradientDrawable.Orientation.BOTTOM_TOP,
                    intArrayOf(0xEB091526.toInt(), 0x99091526.toInt(), 0x1A091526)
                )
            }
            root.addView(scrim, FrameLayout.LayoutParams(-1, dp(195), Gravity.BOTTOM))

            // Top badges
            val topBar = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(14), dp(14), dp(14), 0)
            }
            val badge = TextView(context).apply {
                text = place.category.uppercase(Locale.getDefault())
                textSize = 10f
                setTextColor(Color.WHITE)
                setTypeface(typeface, Typeface.BOLD)
                background = GradientDrawable().apply {
                    setColor(0x88000000.toInt())
                    cornerRadius = dp(10).toFloat()
                }
                setPadding(dp(10), dp(5), dp(10), dp(5))
            }
            val counter = TextView(context).apply {
                text = "${position + 1} / ${items.size}"
                textSize = 11f
                setTextColor(Color.WHITE)
                setTypeface(typeface, Typeface.BOLD)
                background = GradientDrawable().apply {
                    setColor(0x88000000.toInt())
                    cornerRadius = dp(10).toFloat()
                }
                setPadding(dp(9), dp(5), dp(9), dp(5))
            }
            topBar.addView(badge, LinearLayout.LayoutParams(-2, -2))
            topBar.addView(View(context), LinearLayout.LayoutParams(0, 1, 1f))
            topBar.addView(counter, LinearLayout.LayoutParams(-2, -2))
            root.addView(topBar, FrameLayout.LayoutParams(-1, -2, Gravity.TOP))

            // Bottom content overlay
            val bottom = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(16), dp(10), dp(16), dp(15))
            }
            val titleView = TextView(context).apply {
                text = place.name
                textSize = 21f
                maxLines = 2
                ellipsize = TextUtils.TruncateAt.END
                setTextColor(Color.WHITE)
                setTypeface(typeface, Typeface.BOLD)
            }
            val locView = TextView(context).apply {
                text = place.location
                textSize = 12f
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                setTextColor(0xFFDCEBFF.toInt())
                setPadding(0, dp(2), 0, dp(4))
                Ui.leadIcon(this, R.drawable.ic_place, 14, 0xFFDCEBFF.toInt())
            }
            val summaryView = TextView(context).apply {
                text = place.summary
                textSize = 12.5f
                maxLines = 2
                ellipsize = TextUtils.TruncateAt.END
                setTextColor(0xFFEAF2FF.toInt())
            }
            val actionsRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(10), 0, 0)
            }
            val viewBtn = TextView(context).apply {
                text = "View photos & info"
                textSize = 12f
                setTextColor(Color.WHITE)
                setTypeface(typeface, Typeface.BOLD)
                background = GradientDrawable().apply {
                    setColor(0x44FFFFFF)
                    cornerRadius = dp(10).toFloat()
                }
                setPadding(dp(11), dp(10), dp(11), dp(10))
                setOnClickListener { onPlaceClick(place) }
            }
            val planBtn = TextView(context).apply {
                text = "Plan trip"
                textSize = 12f
                setTextColor(ink)
                setTypeface(typeface, Typeface.BOLD)
                background = GradientDrawable().apply {
                    setColor(gold)
                    cornerRadius = dp(10).toFloat()
                }
                setPadding(dp(12), dp(10), dp(12), dp(10))
                Ui.leadIcon(this, R.drawable.ic_flight, 16, ink)
                setOnClickListener { onPlanClick(place) }
            }
            actionsRow.addView(viewBtn, LinearLayout.LayoutParams(-2, -2).apply { rightMargin = dp(8) })
            actionsRow.addView(planBtn, LinearLayout.LayoutParams(-2, -2))

            bottom.addView(titleView)
            bottom.addView(locView)
            bottom.addView(summaryView)
            bottom.addView(actionsRow)
            root.addView(bottom, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))

            holder.card.setOnClickListener { onPlaceClick(place) }
        }
    }

    private fun dp(v: Int) = (v * context.resources.displayMetrics.density).toInt()
}

object HiddenGemCardsHelper {
    private val blue = 0xFF006CE4.toInt()
    private val gold = 0xFFFFB700.toInt()
    private val ink = 0xFF172B4D.toInt()
    private val muted = 0xFF718096.toInt()
    private val border = 0xFFE5EAF2.toInt()
    private val gemGradients = listOf(
        intArrayOf(0xFF134E5E.toInt(), 0xFF71B280.toInt()),
        intArrayOf(0xFF3A1C71.toInt(), 0xFFD76D77.toInt()),
        intArrayOf(0xFF0F2027.toInt(), 0xFF2C5364.toInt()),
        intArrayOf(0xFF283E51.toInt(), 0xFF4B79A1.toInt()),
        intArrayOf(0xFF42275A.toInt(), 0xFF734B6D.toInt())
    )

    fun createCard(
        context: Context,
        place: MostVisitedPlace,
        index: Int,
        fullWidth: Boolean,
        onPlaceClick: (MostVisitedPlace) -> Unit,
        onPlanClick: (MostVisitedPlace) -> Unit
    ): MaterialCardView {
        fun dp(v: Int) = (v * context.resources.displayMetrics.density).toInt()

        val card = MaterialCardView(context).apply {
            radius = dp(18).toFloat()
            cardElevation = dp(2).toFloat()
            setCardBackgroundColor(Color.WHITE)
            strokeWidth = dp(1)
            strokeColor = border
            setOnClickListener { onPlaceClick(place) }
        }
        val box = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        // Photo banner
        val banner = FrameLayout(context)
        val bannerHeight = dp(if (fullWidth) 168 else 134)
        val grad = GradientDrawable(GradientDrawable.Orientation.TL_BR, gemGradients[index % gemGradients.size])
        banner.addView(View(context).apply { background = grad }, FrameLayout.LayoutParams(-1, -1))

        val img = ImageView(context).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
        ImageLoader.load(place.imageUrl, img)
        banner.addView(img, FrameLayout.LayoutParams(-1, -1))

        val scrim = View(context).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.BOTTOM_TOP,
                intArrayOf(0xB8091526.toInt(), 0x00091526)
            )
        }
        banner.addView(scrim, FrameLayout.LayoutParams(-1, dp(72), Gravity.BOTTOM))

        val badge = TextView(context).apply {
            text = place.category.uppercase(Locale.getDefault())
            textSize = 9.5f
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
            background = GradientDrawable().apply {
                setColor(0x88000000.toInt())
                cornerRadius = dp(9).toFloat()
            }
            setPadding(dp(8), dp(4), dp(8), dp(4))
        }
        banner.addView(badge, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.START).apply {
            topMargin = dp(10)
            leftMargin = dp(10)
        })

        val locOverlay = TextView(context).apply {
            text = place.location
            textSize = 11f
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
            Ui.leadIcon(this, R.drawable.ic_place, 13, Color.WHITE)
        }
        banner.addView(locOverlay, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM).apply {
            leftMargin = dp(12)
            rightMargin = dp(12)
            bottomMargin = dp(8)
        })
        box.addView(banner, LinearLayout.LayoutParams(-1, bannerHeight))

        // Text + actions
        val info = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(13), dp(11), dp(13), dp(12))
        }
        val title = TextView(context).apply {
            text = place.name
            textSize = if (fullWidth) 17f else 15.5f
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setTextColor(ink)
            setTypeface(typeface, Typeface.BOLD)
        }
        val summary = TextView(context).apply {
            text = place.summary
            textSize = 12.5f
            maxLines = if (fullWidth) 3 else 2
            ellipsize = TextUtils.TruncateAt.END
            setTextColor(muted)
            setPadding(0, dp(3), 0, dp(9))
        }
        val actions = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val exploreBtn = TextView(context).apply {
            text = "Explore"
            textSize = 12f
            setTextColor(blue)
            setTypeface(typeface, Typeface.BOLD)
            background = GradientDrawable().apply {
                setColor(0xFFEAF3FF.toInt())
                cornerRadius = dp(10).toFloat()
            }
            setPadding(dp(11), dp(10), dp(11), dp(10))
            setOnClickListener { onPlaceClick(place) }
        }
        val planBtn = TextView(context).apply {
            text = "Plan"
            textSize = 12f
            setTextColor(ink)
            setTypeface(typeface, Typeface.BOLD)
            background = GradientDrawable().apply {
                setColor(gold)
                cornerRadius = dp(10).toFloat()
            }
            setPadding(dp(12), dp(10), dp(12), dp(10))
            Ui.leadIcon(this, R.drawable.ic_flight, 16, ink)
            setOnClickListener { onPlanClick(place) }
        }
        actions.addView(exploreBtn, LinearLayout.LayoutParams(-2, -2).apply { rightMargin = dp(8) })
        actions.addView(planBtn, LinearLayout.LayoutParams(-2, -2))

        info.addView(title)
        info.addView(summary)
        info.addView(actions)
        box.addView(info)

        card.addView(box)
        card.layoutParams = if (fullWidth) {
            LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) }
        } else {
            LinearLayout.LayoutParams(dp(236), -2).apply { rightMargin = dp(12) }
        }
        return card
    }
}
