package com.ufuq.dream

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

/** Shared TV surfaces; all interactive rows own focus rather than their labels. */
class SettingsUi(private val context: Context) {
    val ink = Color.parseColor("#F2EBDD")
    val muted = Color.parseColor("#B9B4A6")
    var themeIndex: Int = 1
    var language: String = "ar"
    var typography:DisplaySettings?=null
    fun tr(value:String)=AppLanguage.text(value,language)
    val accent get() = ThemeManager.get(themeIndex).accent
    fun dp(value: Int) = (value * context.resources.displayMetrics.density).toInt()
    fun surface(focused: Boolean, selected: Boolean = false) = GradientDrawable().apply {
        setColor(if(focused) ThemeManager.get(themeIndex).middle else if(selected) ThemeManager.get(themeIndex).glow else ThemeManager.get(themeIndex).middle)
        if(!focused&&!selected)alpha=150
        cornerRadius = dp(12).toFloat()
        if (focused) setStroke(dp(2), ink)
    }
    fun text(value: String, size: Float, secondary: Boolean = false) = TextView(context).apply {
        text = tr(value); textSize = size; setTextColor(if (secondary) muted else ink)
        gravity = Gravity.START or Gravity.CENTER_VERTICAL
        textDirection = if(language=="en")View.TEXT_DIRECTION_FIRST_STRONG_LTR else View.TEXT_DIRECTION_FIRST_STRONG_RTL
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        isFocusable = false
        typography?.let{UfuqFonts.apply(this,it)}
    }
    fun row(title: String, description: String, value: String, help: (String) -> Unit, click: () -> Unit): LinearLayout {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL; layoutDirection = AppLanguage.direction(language)
            gravity = Gravity.CENTER_VERTICAL; minimumHeight = dp(76)
            setPadding(dp(18), dp(10), dp(18), dp(10)); isFocusable = true; isFocusableInTouchMode = true; isClickable = true
            descendantFocusability = android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS
            background = surface(false)
        }
        val labels = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        labels.addView(text(title, 21f)); labels.addView(text(description, 14f, true).apply { setPadding(0, dp(4), 0, 0) })
        row.addView(labels, LinearLayout.LayoutParams(0, -2, 1f))
        if(value.isNotEmpty()) row.addView(text(value, 17f).apply {
            tag="setting_value";maxWidth=dp(120)
            setTextColor(accent); gravity = Gravity.CENTER
            setPadding(dp(12), dp(8), dp(12), dp(8))
            background = GradientDrawable().apply { setColor(0x33000000); cornerRadius = dp(8).toFloat() }
        }, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(12) })
        row.setOnFocusChangeListener { _, focused -> row.background = surface(focused); if(focused)help(description) }
        row.setOnClickListener { click() }
        return row
    }
}
