package com.ufuq.dream
import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.*
import java.util.Date

interface SceneRenderer {val view:View; fun update(now:Date,s:DisplaySettings,theme:AmbientTheme);fun dispose();fun setAvailableWidth(width:Int) {}}
class ClockSceneRenderer(private val context:Context,private val scene:Int):SceneRenderer {
 private fun dp(v:Int)=(v*context.resources.displayMetrics.density).toInt()
 private val group=LinearLayout(context).apply{orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER;layoutDirection=View.LAYOUT_DIRECTION_RTL}
 override val view:View=group
 private fun label(size:Float)=TextView(context).apply{textSize=size;gravity=Gravity.CENTER;includeFontPadding=true;textDirection=View.TEXT_DIRECTION_RTL}
 private val eyebrow=label(17f)
 private val timeRow=LinearLayout(context).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;layoutDirection=View.LAYOUT_DIRECTION_LTR}
 private val clock=label(96f).apply{tag="clock_digits";typeface=Typeface.create("sans-serif",Typeface.NORMAL);textDirection=View.TEXT_DIRECTION_LTR;maxLines=1}
 private val seconds=label(26f).apply{textDirection=View.TEXT_DIRECTION_LTR;setPadding(dp(8),dp(28),0,0)}
 private val period=label(24f).apply{setPadding(dp(12),dp(24),0,0)}
 private val first=label(25f);private val second=label(21f)
 private val greeting=label(20f)
 private val provider=DateProvider()
 private var shadowTheme=-1
 private var availableWidth=0
 override fun setAvailableWidth(width:Int){availableWidth=width}
 // Arabic-Indic digits fall back to the Arabic system font, so weight only changes with Latin digits.
 private val regularFace:Typeface=Typeface.create("sans-serif",Typeface.NORMAL)
 private val lightFace:Typeface=Typeface.create("sans-serif-light",Typeface.NORMAL)
 /** A soft dark halo keeps text legible when artwork glows behind it; no hard edge, no layout change. */
 private fun soften(v:TextView){v.setShadowLayer(dp(5).toFloat(),0f,dp(1).toFloat(),0x99000000.toInt())}
 init {
 group.addView(eyebrow);timeRow.addView(clock,LinearLayout.LayoutParams(-2,-2));timeRow.addView(seconds,LinearLayout.LayoutParams(-2,-2));timeRow.addView(period,LinearLayout.LayoutParams(-2,-2));group.addView(timeRow,LinearLayout.LayoutParams(-2,-2))
 group.addView(first);group.addView(second);group.addView(greeting)
 listOf(first,second,greeting).forEach{(it.layoutParams as LinearLayout.LayoutParams).topMargin=dp(10)}
 listOf(eyebrow,clock,seconds,period,first,second,greeting).forEach{soften(it)}
 }
 override fun update(now:Date,s:DisplaySettings,theme:AmbientTheme){
 if(shadowTheme!=s.theme){
  shadowTheme=s.theme
  val shadow=listOf(0xb002100a.toInt(),0xb0160b04.toInt(),0xc0020710.toInt())[s.theme.coerceIn(0,2)]
  listOf(eyebrow,clock,seconds,period,first,second,greeting).forEach{it.setShadowLayer(dp(if(s.theme==2)6 else 5).toFloat(),0f,dp(1).toFloat(),shadow)}
 }
 group.layoutDirection=AppLanguage.direction(s.language)
 listOf(eyebrow,first,second,period).forEach{it.textDirection=if(s.language=="en")View.TEXT_DIRECTION_FIRST_STRONG_LTR else View.TEXT_DIRECTION_FIRST_STRONG_RTL}
 val labels=provider.labels(now,s)
 val night=NightMode.textFactor(now,s.nightDim)
 val base=listOf(68f,84f,100f)[s.size] * (if(s.theme==2)1.08f else 1f)
 clock.textSize=base
 val mainTime=labels.time.substringBeforeLast(':').let{if(s.seconds)it else labels.time}
 seconds.text=if(s.seconds)"\u2066${labels.time.substringAfterLast(':')}\u2069" else ""
 seconds.visibility=if(s.seconds)View.VISIBLE else View.GONE
 seconds.setTextColor(NightMode.fade(if(s.clockPosition>=0)theme.ink else theme.muted,night))
 clock.typeface=if(s.latinDigits)lightFace else regularFace
 clock.setTextColor(NightMode.fade(theme.ink,night));period.setTextColor(NightMode.fade(if(s.clockPosition>=0)theme.ink else theme.accent,night))
 val available=if(availableWidth>0)availableWidth.toFloat() else context.resources.displayMetrics.widthPixels*.36f
 val desiredPx=clock.textSize
 val estimate=clock.paint.measureText(mainTime)+(if(s.hour24)0f else dp(48).toFloat())+(if(s.seconds)dp(48).toFloat() else 0f)
 if(estimate>available*.90f)clock.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,desiredPx*available*.90f/estimate)
 val old=clock.text.toString();clock.text="\u2066${mainTime}\u2069";period.text=labels.period
 period.visibility=if(s.hour24) View.GONE else View.VISIBLE
 if(old.isNotEmpty()&&old!="\u2066${mainTime}\u2069"&&!s.seconds&&s.intensity>0){clock.animate().cancel();clock.alpha=.82f;clock.animate().alpha(1f).setDuration(350).start()}
 val dates=mutableListOf<String>();if(s.gregorian)dates.add(labels.gregorian);if(s.hijri){if(s.hijriFirst) dates.add(0,labels.hijri) else dates.add(labels.hijri)}
 first.text=dates.getOrNull(0).orEmpty();second.text=dates.getOrNull(1).orEmpty()
 first.visibility=if(dates.isEmpty()) View.GONE else View.VISIBLE;second.visibility=if(dates.size<2) View.GONE else View.VISIBLE
 first.textSize=20f;second.textSize=18f
 first.setTextColor(NightMode.fade(theme.ink,night));second.setTextColor(NightMode.fade(if(s.clockPosition>=0)theme.ink else theme.muted,night))
 eyebrow.text=when(s.photoCategory){0->"أُفُق  /  الدرعية";1->"أُفُق  /  العلا";2->"أُفُق  /  ليل الرياض";else->"أُفُق  /  مشاهد سعودية"}
 eyebrow.text=AppLanguage.text(eyebrow.text.toString(),s.language)
 eyebrow.visibility=View.VISIBLE;eyebrow.setTextColor(NightMode.fade(if(s.clockPosition>=0)theme.ink else theme.accent,night))
 val phrase=GreetingProvider.label(now,s)
 greeting.text=android.text.BidiFormatter.getInstance(s.language!="en").unicodeWrap(phrase)
 greeting.textDirection=if(s.language=="en")View.TEXT_DIRECTION_FIRST_STRONG_LTR else View.TEXT_DIRECTION_FIRST_STRONG_RTL
 greeting.setTextColor(NightMode.fade(if(s.clockPosition>=0)theme.ink else theme.muted,night))
 greeting.visibility=if(phrase.isBlank()) View.GONE else View.VISIBLE
 greeting.maxLines=2

 }
 override fun dispose(){clock.animate().cancel();group.animate().cancel()}
}
