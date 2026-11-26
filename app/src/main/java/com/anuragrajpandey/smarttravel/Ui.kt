package com.anuragrajpandey.smarttravel

import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.ImageViewCompat

/** Shared design tokens and view helpers (icons, chips, edge-to-edge insets). */
object Ui {
    const val BG = 0xFFF5F7FB.toInt()
    const val FG = 0xFF172B4D.toInt()
    const val MUTED = 0xFF718096.toInt()
    const val ACCENT = 0xFF006CE4.toInt()
    const val ACCENT_SOFT = 0xFFEAF3FF.toInt()
    const val GOLD = 0xFFFFB700.toInt()
    const val BORDER = 0xFFE5EAF2.toInt()

    fun dp(ctx: Context, v: Int) = (v * ctx.resources.displayMetrics.density).toInt()

    fun shape(ctx: Context, color: Int, radiusDp: Int = 16) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(ctx, radiusDp).toFloat()
    }

    fun ripple(ctx: Context, color: Int, radiusDp: Int): RippleDrawable =
        RippleDrawable(ColorStateList.valueOf(0x22000000), shape(ctx, color, radiusDp), null)

    /**
     * Makes the screen draw edge-to-edge and pads [root] by the status bar, display cutout
     * (notch / camera hole), navigation bar and keyboard, so nothing sits under the notch.
     * Must be called after setContentView(root).
     */
    fun edgeToEdge(activity: Activity, root: View, lightBars: Boolean = true) {
        val w = activity.window
        WindowCompat.setDecorFitsSystemWindows(w, false)
        @Suppress("DEPRECATION")
        run {
            w.statusBarColor = Color.TRANSPARENT
            w.navigationBarColor = Color.TRANSPARENT
        }
        if (Build.VERSION.SDK_INT >= 29) w.isNavigationBarContrastEnforced = false
        val c = WindowCompat.getInsetsController(w, w.decorView)
        c.isAppearanceLightStatusBars = lightBars
        c.isAppearanceLightNavigationBars = lightBars
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            v.setPadding(bars.left, bars.top, bars.right, maxOf(bars.bottom, ime.bottom))
            WindowInsetsCompat.CONSUMED
        }
        ViewCompat.requestApplyInsets(root)
    }

    fun iconView(ctx: Context, @DrawableRes res: Int, tint: Int? = null): ImageView = ImageView(ctx).apply {
        setImageResource(res)
        scaleType = ImageView.ScaleType.FIT_CENTER
        if (tint != null) ImageViewCompat.setImageTintList(this, ColorStateList.valueOf(tint))
    }

    fun iconLp(ctx: Context, sizeDp: Int) = LinearLayout.LayoutParams(dp(ctx, sizeDp), dp(ctx, sizeDp))

    /** A 48dp touch target with a centred vector icon. */
    fun iconButton(
        ctx: Context, @DrawableRes res: Int, tint: Int = FG, bg: Int? = null,
        touchDp: Int = 48, iconDp: Int = 24, onClick: () -> Unit
    ): FrameLayout = FrameLayout(ctx).apply {
        background = ripple(ctx, bg ?: Color.TRANSPARENT, touchDp / 2)
        isClickable = true
        isFocusable = true
        addView(
            iconView(ctx, res, tint),
            FrameLayout.LayoutParams(dp(ctx, iconDp), dp(ctx, iconDp), Gravity.CENTER)
        )
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(dp(ctx, touchDp), dp(ctx, touchDp))
    }

    fun text(
        ctx: Context, s: String, sizeSp: Float = 14f, color: Int = FG, bold: Boolean = false
    ) = TextView(ctx).apply {
        text = s
        textSize = sizeSp
        setTextColor(color)
        if (bold) setTypeface(typeface, Typeface.BOLD)
        setPadding(0, dp(ctx, 3), 0, dp(ctx, 5))
    }

    /** Icon + label in one row (used for chips, buttons and meta lines). */
    fun iconText(
        ctx: Context, @DrawableRes res: Int, s: String, sizeSp: Float = 14f,
        color: Int = FG, iconDp: Int = 18, bold: Boolean = false, gapDp: Int = 6
    ) = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(iconView(ctx, res, color), iconLp(ctx, iconDp).apply { rightMargin = dp(ctx, gapDp) })
        addView(TextView(ctx).apply {
            text = s
            textSize = sizeSp
            setTextColor(color)
            if (bold) setTypeface(typeface, Typeface.BOLD)
        })
    }

    /** Filled button with optional leading icon. 48dp minimum height. */
    fun button(
        ctx: Context, label: String, @DrawableRes iconRes: Int? = null,
        fg: Int = Color.WHITE, bg: Int = ACCENT, radiusDp: Int = 14, onClick: () -> Unit
    ) = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        minimumHeight = dp(ctx, 48)
        background = ripple(ctx, bg, radiusDp)
        setPadding(dp(ctx, 14), dp(ctx, 10), dp(ctx, 14), dp(ctx, 10))
        if (iconRes != null) addView(iconView(ctx, iconRes, fg), iconLp(ctx, 20).apply { rightMargin = dp(ctx, 8) })
        addView(TextView(ctx).apply {
            text = label
            textSize = 14f
            setTextColor(fg)
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
        })
        isClickable = true
        isFocusable = true
        setOnClickListener { onClick() }
    }

    fun chip(
        ctx: Context, label: String, selected: Boolean, @DrawableRes iconRes: Int? = null,
        onClick: () -> Unit
    ) = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = dp(ctx, 40)
        setPadding(dp(ctx, 14), dp(ctx, 8), dp(ctx, 16), dp(ctx, 8))
        isClickable = true
        isFocusable = true
        setOnClickListener { onClick() }
        setChipSelected(this, ctx, selected, iconRes, label)
    }

    private fun setChipSelected(v: LinearLayout, ctx: Context, selected: Boolean, iconRes: Int?, label: String) {
        v.removeAllViews()
        val fg = if (selected) Color.WHITE else FG
        v.background = ripple(ctx, if (selected) ACCENT else Color.WHITE, 22)
        if (iconRes != null) v.addView(iconView(ctx, iconRes, fg), iconLp(ctx, 18).apply { rightMargin = dp(ctx, 6) })
        else v.setPadding(dp(ctx, 16), dp(ctx, 8), dp(ctx, 16), dp(ctx, 8))
        v.addView(TextView(ctx).apply {
            text = label
            textSize = 13f
            setTextColor(fg)
            setTypeface(typeface, Typeface.BOLD)
        })
    }

    /** Re-style a chip made with [chip] after selection changes. */
    fun restyleChip(chip: LinearLayout, selected: Boolean, @DrawableRes iconRes: Int? = null, label: String) =
        setChipSelected(chip, chip.context, selected, iconRes, label)

    fun topBar(
        activity: Activity, title: String, fg: Int = FG, trailing: List<View> = emptyList()
    ): LinearLayout = LinearLayout(activity).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(activity, 6), dp(activity, 6), dp(activity, 10), dp(activity, 4))
        addView(iconButton(activity, R.drawable.ic_arrow_back, fg) { activity.finish() })
        addView(
            text(activity, title, 19f, fg, true).apply { maxLines = 1 },
            LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(activity, 4) }
        )
        trailing.forEach { addView(it) }
    }

    fun card(ctx: Context, color: Int = Color.WHITE, radiusDp: Int = 18) = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        background = GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(ctx, radiusDp).toFloat()
            setStroke(dp(ctx, 1), BORDER)
        }
        setPadding(dp(ctx, 16), dp(ctx, 13), dp(ctx, 16), dp(ctx, 13))
    }

    fun dist(km: Double) = if (km < 1) "${(km * 1000).toInt()} m" else String.format(java.util.Locale.US, "%.1f km", km)

    /** Puts a small tinted vector icon in front of a TextView's text. */
    fun leadIcon(tv: TextView, @DrawableRes res: Int, sizeDp: Int, tint: Int) {
        val ctx = tv.context
        val d = androidx.core.content.ContextCompat.getDrawable(ctx, res)!!.mutate()
        d.setBounds(0, 0, dp(ctx, sizeDp), dp(ctx, sizeDp))
        tv.setCompoundDrawables(d, null, null, null)
        tv.compoundDrawablePadding = dp(ctx, 6)
        androidx.core.widget.TextViewCompat.setCompoundDrawableTintList(tv, ColorStateList.valueOf(tint))
    }
}
