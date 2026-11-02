package com.ufuq.dream.smart

import android.content.Context
import android.graphics.*
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.ufuq.dream.DisplaySettings
import com.ufuq.dream.AmbientTheme
import com.ufuq.dream.SettingsRepository
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/** Registry is an extension boundary, not a promise that future sports/audio providers exist. */
enum class WidgetSlot { CLOCK, INFORMATION }
data class AmbientWidget(val id: String, val priority: Int, val slot: WidgetSlot, val heightDp: Int, val enabled: (DisplaySettings) -> Boolean)
object WidgetRegistry {
    val clock = AmbientWidget("clock",100,WidgetSlot.CLOCK,0) { true }
    val prayers = AmbientWidget("prayers",80,WidgetSlot.INFORMATION,88) { it.smart.prayers }
    val weather = AmbientWidget("weather",60,WidgetSlot.INFORMATION,42) { it.smart.weather }
    val supported = listOf(clock,prayers,weather)
    fun enabled(settings: DisplaySettings)=supported.filter{it.enabled(settings)}.sortedByDescending{it.priority}
}
data class WidgetZones(val clockHeight: Int, val panel: Rect)
object SmartLayout {
    fun zones(width:Int,height:Int,density:Float,settings:DisplaySettings):WidgetZones {
        val information=WidgetRegistry.enabled(settings).filter{it.slot==WidgetSlot.INFORMATION}
        return place(width,height,density,information.sumOf{it.heightDp}+if(information.isEmpty())0 else 4)
    }
    private fun place(width:Int,height:Int,density:Float,heightDp:Int):WidgetZones {
        if(heightDp==0)return WidgetZones(height,Rect())
        val bottom=(height*.94f).toInt();val reserve=(heightDp*density).toInt().coerceAtMost((height*.34f).toInt())
        val top=(bottom-reserve).coerceAtLeast(0)
        return WidgetZones((top-8*density).toInt().coerceAtLeast(1),Rect((width*.05f).toInt(),top,(width*.95f).toInt(),bottom))
    }
    fun zones(width: Int, height: Int, density: Float, prayer: Boolean, weather: Boolean): WidgetZones {
        return place(width,height,density,(if(prayer)88 else 0)+(if(weather)42 else 0)+if(prayer||weather)4 else 0)
    }
}
/** Fixed-size custom weather marks, no external icon/font asset or animation. */
class WeatherGlyph(private val code: Int, private val ink: Int, private val daylight: Boolean = true): Drawable() {
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=ink;strokeWidth=2f }
    override fun draw(canvas: Canvas) {
        canvas.save();canvas.translate(bounds.left.toFloat(),bounds.top.toFloat());canvas.scale(bounds.width()/32f,bounds.height()/32f)
        paint.style=Paint.Style.STROKE
        if(code<=2 && !daylight)canvas.drawArc(RectF(5f,3f,22f,21f),45f,270f,false,paint)
        if(code<=2 && daylight) { canvas.drawCircle(13f,13f,6f,paint); for(i in 0..7){ val a=i*Math.PI/4;canvas.drawLine(13+9*kotlin.math.cos(a).toFloat(),13+9*kotlin.math.sin(a).toFloat(),13+12*kotlin.math.cos(a).toFloat(),13+12*kotlin.math.sin(a).toFloat(),paint) } }
        if(code>0){paint.style=Paint.Style.FILL;canvas.drawCircle(13f,17f,6f,paint);canvas.drawCircle(20f,16f,7f,paint);canvas.drawRoundRect(7f,17f,27f,24f,4f,4f,paint)}
        if(code>=51){paint.style=Paint.Style.STROKE;canvas.drawLine(10f,26f,9f,30f,paint);canvas.drawLine(18f,26f,17f,30f,paint);canvas.drawLine(25f,26f,24f,30f,paint)}
        canvas.restore()
    }
    override fun setAlpha(alpha: Int){paint.alpha=alpha}
    override fun setColorFilter(filter: ColorFilter?){paint.colorFilter=filter}
    @Deprecated("Drawable opacity") override fun getOpacity()=PixelFormat.TRANSLUCENT
}
class SmartPanel @JvmOverloads constructor(context: Context, private val changed: () -> Unit = {}): LinearLayout(context) {
    private fun dp(n: Int)=(n*resources.displayMetrics.density).toInt()
    private val prayerProvider=PrayerProvider()
    private var weatherRevision=0
    private var renderKey=""
    internal val weatherSession=WeatherSession(context,{weatherRevision++;changed()})
    private var active=false
    private fun text(size: Float)=TextView(context).apply { textSize=size;gravity=Gravity.CENTER;setTextColor(Color.WHITE);setShadowLayer(dp(3).toFloat(),0f,1f,Color.BLACK);isFocusable=false;textDirection=View.TEXT_DIRECTION_FIRST_STRONG_RTL }
    private val header=text(15f)
    private val prayerRow=LinearLayout(context).apply { orientation=HORIZONTAL;layoutDirection=View.LAYOUT_DIRECTION_RTL }
    private val prayerLabels=(0..4).map { text(17f).also { label -> prayerRow.addView(label,LayoutParams(0,-2,1f)) } }
    private val weather=text(19f)
    init {
        orientation=VERTICAL;gravity=Gravity.CENTER_VERTICAL;layoutDirection=View.LAYOUT_DIRECTION_RTL
        isFocusable=false;descendantFocusability=FOCUS_BLOCK_DESCENDANTS
        setPadding(dp(12),dp(4),dp(12),dp(4))
        background=GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,intArrayOf(0x05000000,0x99000000.toInt())).apply { cornerRadius=dp(10).toFloat() }
        addView(header,LayoutParams(-1,-2));addView(prayerRow,LayoutParams(-1,-2).apply { topMargin=dp(3) })
        addView(weather,LayoutParams(-1,-2).apply { topMargin=dp(4) })
    }
    fun start(){active=true;renderKey="";weatherSession.start()}
    fun stop(){active=false;renderKey="";weatherSession.stop()}
    fun update(now: Instant, s: DisplaySettings, theme: AmbientTheme) {
        val key="${now.epochSecond/60}:${s.smart}:${s.theme}:${s.hour24}:${s.latinDigits}:$weatherRevision"
        if(key==renderKey)return
        renderKey=key
        val options=s.smart;visibility=if(options.prayers||options.weather)View.VISIBLE else View.GONE
        fun digits(value: String)=if(s.latinDigits)value else value.map { if(it in '0'..'9') '٠'+(it-'0') else it }.joinToString("")
        val ink=com.ufuq.dream.NightMode.fade(theme.ink,com.ufuq.dream.NightMode.textFactor(java.util.Date.from(now),s.nightDim))
        listOf(header,weather).plus(prayerLabels).forEach { it.setTextColor(ink) }
        header.visibility=if(options.prayers)View.VISIBLE else View.GONE;prayerRow.visibility=header.visibility
        if(options.prayers) {
            try {
                val city=SaudiCities.find(options.prayerCity);val day=prayerProvider.day(now.atZone(city.zoneId).toLocalDate(),city,options)
                val next=prayerProvider.next(now,city,options);val minutes=(next.remaining.seconds+59)/60
                header.text=digits("${city.arabic} · محسوبة وليست رسمية · ${next.entry.name} خلال \u2066${minutes/60}:${(minutes%60).toString().padStart(2,'0')}\u2069")
                val format=DateTimeFormatter.ofPattern(if(s.hour24)"HH:mm" else "hh:mm a",Locale("ar","SA")).withZone(city.zoneId)
                day.entries.forEachIndexed { i,entry -> prayerLabels[i].text=entry.name+"\n\u2066"+digits(format.format(entry.instant))+"\u2069" }
            } catch (_: Exception) { header.text="تعذّر حساب الصلاة لهذه المدينة والتاريخ";prayerLabels.forEach { it.text="—" } }
        }
        weather.visibility=if(options.weather)View.VISIBLE else View.GONE
        if(!options.weather) { weatherSession.stop();if(active)weatherSession.start();return }
        val city=SaudiCities.find(options.weatherCity);val millis=now.toEpochMilli()
        val saved=SettingsRepository(context).load().smart
        val permitted=saved.weather && saved.weatherCity==city.id
        if(active&&permitted)weatherSession.refresh(city,millis,options.weatherRefreshMinutes,true)
        val reading=weatherSession.reading(city,millis)
        // Staged enable/city change never grants network consent. A labeled sample is intentional.
        val sample=reading==null&&!permitted
        val value=reading?:WeatherReading(city.id,28.0,0,true,millis,millis)
        val status=when { sample -> "مثال للمعاينة · لم يُجلب طقس";reading!=null -> "${if(weatherSession.failed)"تعذّر التحديث · " else ""}${if(reading.stale(millis))"بيانات قديمة · " else ""}منذ ${reading.ageMinutes(millis)} د";weatherSession.loading -> "جارٍ التحديث";weatherSession.failed -> "تعذّر الاتصال · لا بيانات";else -> "لا بيانات بعد" }
        val temperature=if(reading!=null||sample)"\u2066${value.temperature(options.fahrenheit).roundToInt()}°${if(options.fahrenheit)"ف" else "م"}\u2069 · ${WeatherParsing.condition(value.code)}" else "—"
        weather.text=digits("${city.arabic} · $temperature · $status · Open-Meteo")
        if(reading!=null||sample)weather.setCompoundDrawablesRelative(WeatherGlyph(value.code,ink,value.daylight).apply { setBounds(0,0,dp(28),dp(28)) },null,null,null)
        else weather.setCompoundDrawablesRelative(null,null,null,null)
    }
}
