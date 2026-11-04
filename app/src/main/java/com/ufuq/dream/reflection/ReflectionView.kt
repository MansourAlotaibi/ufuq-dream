package com.ufuq.dream.reflection

import android.content.Context
import android.graphics.Color
import android.text.SpannableString
import android.text.Spanned
import android.text.style.URLSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.ufuq.dream.AppLanguage
import com.ufuq.dream.AmbientTheme
import com.ufuq.dream.NightMode

/** Complete verbatim text, never an ellipsized religious quotation or a machine translation. */
class ReflectionView(context:Context):LinearLayout(context) {
    companion object { const val HEIGHT_DP=148 }
    private var fittedKey:List<Any?>?=null
    private val body=TextView(context).apply {
        tag="reflection_original";gravity=Gravity.CENTER;textDirection=View.TEXT_DIRECTION_RTL
        includeFontPadding=true;setElegantTextHeight(true);isFocusable=false
        setAutoSizeTextTypeWithDefaults(TextView.AUTO_SIZE_TEXT_TYPE_NONE)
        setShadowLayer(3*resources.displayMetrics.density,0f,1f,Color.BLACK)
    }
    private val reference=TextView(context).apply {
        tag="reflection_reference";textSize=14f;setAutoSizeTextTypeUniformWithConfiguration(12,14,1,TypedValue.COMPLEX_UNIT_SP);gravity=Gravity.CENTER;isFocusable=false
        setShadowLayer(2*resources.displayMetrics.density,0f,1f,Color.BLACK)
    }
    init {
        orientation=VERTICAL;gravity=Gravity.CENTER;isFocusable=false
        addView(body,LayoutParams(-1,LayoutParams.WRAP_CONTENT))
        addView(reference,LayoutParams(-1,(32*resources.displayMetrics.density).toInt()).apply { topMargin=(4*resources.displayMetrics.density).toInt() })
    }
    override fun onMeasure(widthMeasureSpec:Int,heightMeasureSpec:Int) {
        // Measure the complete body before placing the citation. Never reposition the
        // citation inside a weighted TextView using a possibly stale Layout height:
        // API28 autosizing can rebuild that Layout during the subsequent draw.
        val width=(MeasureSpec.getSize(widthMeasureSpec)-paddingLeft-paddingRight).coerceAtLeast(1)
        val bound=if(MeasureSpec.getMode(heightMeasureSpec)==MeasureSpec.UNSPECIFIED)
            (HEIGHT_DP*resources.displayMetrics.density).toInt() else MeasureSpec.getSize(heightMeasureSpec)
        val referenceParams=reference.layoutParams as LayoutParams
        val available=(bound-paddingTop-paddingBottom-referenceParams.height-referenceParams.topMargin).coerceAtLeast(1)
        // Use TextView's actual font/fallback metrics, including elegant Arabic
        // leading and font padding, rather than a separate estimated StaticLayout.
        val key=listOf(body.text.toString(),width,available,resources.displayMetrics.scaledDensity,body.typeface)
        if(key!=fittedKey) {
            for(size in 21 downTo 12) {
                body.setTextSize(TypedValue.COMPLEX_UNIT_SP,size.toFloat())
                body.forceLayout()
                body.measure(MeasureSpec.makeMeasureSpec(width,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(0,MeasureSpec.UNSPECIFIED))
                if(body.measuredHeight<=available)break
            }
            fittedKey=key
        }
        super.onMeasure(widthMeasureSpec,heightMeasureSpec)
    }
    fun unavailable(language:String,theme:AmbientTheme,factor:Float) {
        body.text=AppLanguage.text("النص غير متاح حاليًا",language);body.setTextColor(NightMode.fade(theme.ink,factor));reference.text=""
    }
    fun render(entry:ReflectionEntry,language:String,theme:AmbientTheme,factor:Float) {
        body.text=entry.text
        body.setTextColor(NightMode.fade(theme.ink,factor))
        val provider=if(entry.kind=="quran")"Tanzil" else "Wikisource"
        val kind=AppLanguage.text(when(entry.kind){"quran"->"آية";"dhikr"->"ذكر";else->"حديث"},language)
        val citation=entry.source.substringBefore(" (ويكي مصدر")
        val label="$kind · ${AppLanguage.text(citation,language)}"+(if(entry.kind=="quran")" · $provider" else "")
        reference.text=if(entry.kind=="quran")SpannableString(label).apply{setSpan(URLSpan(entry.sourceUrl),length-provider.length,length,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)} else label
        reference.textDirection=if(language=="en")View.TEXT_DIRECTION_FIRST_STRONG_LTR else View.TEXT_DIRECTION_FIRST_STRONG_RTL
        reference.setTextColor(NightMode.fade(theme.muted,factor));reference.setLinkTextColor(NightMode.fade(theme.muted,factor))
    }
}
