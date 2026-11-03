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
    private val body=TextView(context).apply {
        tag="reflection_original";gravity=Gravity.CENTER;textDirection=View.TEXT_DIRECTION_RTL
        includeFontPadding=true;setElegantTextHeight(true);isFocusable=false
        setAutoSizeTextTypeUniformWithConfiguration(17,21,1,TypedValue.COMPLEX_UNIT_SP)
        setShadowLayer(3*resources.displayMetrics.density,0f,1f,Color.BLACK)
    }
    private val reference=TextView(context).apply {
        tag="reflection_reference";textSize=14f;setAutoSizeTextTypeUniformWithConfiguration(12,14,1,TypedValue.COMPLEX_UNIT_SP);gravity=Gravity.CENTER;isFocusable=false
        setShadowLayer(2*resources.displayMetrics.density,0f,1f,Color.BLACK)
    }
    init {
        orientation=VERTICAL;gravity=Gravity.CENTER;isFocusable=false
        addView(body,LayoutParams(-1,0,1f))
        addView(reference,LayoutParams(-1,(32*resources.displayMetrics.density).toInt()))
    }
    override fun onLayout(changed:Boolean,l:Int,t:Int,r:Int,b:Int) {
        super.onLayout(changed,l,t,r,b)
        // Keep the citation close to the actual text, even when a short quotation
        // receives the same safe column as a long verse. The text keeps bounded sizing.
        val textHeight=body.layout?.height?:body.height
        val y=(body.top+(body.height+textHeight)/2+(4*resources.displayMetrics.density).toInt()).coerceAtMost(height-reference.height)
        reference.layout(reference.left,y,reference.right,y+reference.height)
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
