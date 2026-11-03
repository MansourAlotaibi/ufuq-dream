package com.ufuq.dream.smart

import android.content.Context
import android.graphics.*
import android.graphics.drawable.Drawable
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.ufuq.dream.DisplaySettings
import com.ufuq.dream.AmbientTheme
import com.ufuq.dream.SettingsRepository
import java.time.Instant
import kotlin.math.roundToInt

/** Registry defines bounded, independently enabled clock and information modules. */
enum class WidgetSlot { CLOCK, INFORMATION }
data class AmbientWidget(val id: String, val priority: Int, val slot: WidgetSlot, val heightDp: Int, val enabled: (DisplaySettings) -> Boolean)
object WidgetRegistry {
    val clock = AmbientWidget("clock",100,WidgetSlot.CLOCK,0) { true }
    val prayers = AmbientWidget("prayers",80,WidgetSlot.INFORMATION,88) { it.smart.prayers }
    val weather = AmbientWidget("weather",60,WidgetSlot.INFORMATION,42) { it.smart.weather }
    val reflection = AmbientWidget("reflection",40,WidgetSlot.INFORMATION,com.ufuq.dream.reflection.ReflectionView.HEIGHT_DP) { it.smart.reflectionMode in 1..3 }
    val football = AmbientWidget("football",30,WidgetSlot.INFORMATION,64) { it.smart.football && FootballTeams.find(it.smart.footballTeam)!=null }
    val supported = listOf(clock,prayers,weather,reflection,football)
    fun enabled(settings: DisplaySettings)=supported.filter{it.enabled(settings)}.sortedByDescending{it.priority}
}
data class WidgetZones(val clockHeight: Int, val panel: Rect)
object SmartLayout {
    fun zones(width:Int,height:Int,density:Float,settings:DisplaySettings,minimumFooter:Int=0):WidgetZones {
        val information=WidgetRegistry.enabled(settings).filter{it.slot==WidgetSlot.INFORMATION}
        val base=information.filterNot{it.id=="reflection"||it.id=="football"}.sumOf{it.heightDp}
        return place(width,height,density,base+if(base==0)0 else 4,minimumFooter=minimumFooter)
    }
    private fun place(width:Int,height:Int,density:Float,heightDp:Int,fraction:Float=.34f,minimumFooter:Int=0):WidgetZones {
        if(heightDp==0)return WidgetZones(height,Rect())
        val bottom=(height*.94f).toInt();val reserve=maxOf((heightDp*density).toInt(),minimumFooter).coerceAtMost((height*fraction).toInt())
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
    private var prayerRevision=0
    internal val prayerApi=PrayerApiSession(context,{prayerRevision++;changed()})
    private var weatherRevision=0
    private var renderKey=""
    internal val weatherSession=WeatherSession(context,{weatherRevision++;changed()})
    private var footballRevision=0
    internal val footballSession=FootballSession(context,{footballRevision++;changed()})
    private var active=false
    private fun text(size: Float)=TextView(context).apply { textSize=size;gravity=Gravity.CENTER;setTextColor(Color.WHITE);setShadowLayer(dp(3).toFloat(),0f,1f,Color.BLACK);isFocusable=false;textDirection=View.TEXT_DIRECTION_FIRST_STRONG_RTL }
    private val header=text(15f).apply { tag="prayer_header" }
    private val prayerRow=LinearLayout(context).apply { orientation=HORIZONTAL;layoutDirection=View.LAYOUT_DIRECTION_RTL }
    private val prayerLabels=(0..4).map { index -> text(17f).also { label -> label.tag="prayer_$index"; prayerRow.addView(label,LayoutParams(0,-2,1f)) } }
    private val reflectionBlock=com.ufuq.dream.reflection.ReflectionView(context).apply { tag="reflection_block";visibility=View.GONE }
    private val football=text(17f).apply { tag="football_widget";visibility=View.GONE;maxLines=3;setAutoSizeTextTypeUniformWithConfiguration(14,18,1,android.util.TypedValue.COMPLEX_UNIT_SP) }
    internal val supplements=LinearLayout(context).apply {
        orientation=VERTICAL;gravity=Gravity.CENTER_VERTICAL;visibility=View.GONE;tag="supplementary_row"
        setPadding(dp(16),dp(16),dp(16),dp(16));isFocusable=false;descendantFocusability=FOCUS_BLOCK_DESCENDANTS
        background=com.ufuq.dream.ContentBackdrop(this,intArrayOf(0x00000000,0x38000000),18,16,12)
    }
    private val weather=text(19f).apply { tag="weather_reading";compoundDrawablePadding=dp(8) }
    private val weatherRow=LinearLayout(context).apply { orientation=HORIZONTAL;gravity=Gravity.CENTER;layoutDirection=View.LAYOUT_DIRECTION_RTL;tag="weather_row" }
    private val weatherCredit=text(10f).apply {
        tag="weather_credit"
        text=android.text.SpannableString("Open-Meteo").apply{setSpan(object:android.text.style.URLSpan("https://open-meteo.com/"){
            override fun updateDrawState(ds:android.text.TextPaint){super.updateDrawState(ds);ds.isUnderlineText=false}
        },0,length,android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)}
    }
    init {
        orientation=VERTICAL;gravity=Gravity.CENTER_VERTICAL;layoutDirection=View.LAYOUT_DIRECTION_RTL
        isFocusable=false;descendantFocusability=FOCUS_BLOCK_DESCENDANTS
        setPadding(dp(12),dp(4),dp(12),dp(4))
        background=com.ufuq.dream.ContentBackdrop(this,intArrayOf(0x05000000,0x99000000.toInt()),10,12,8)
        addView(header,LayoutParams(-1,-2));addView(prayerRow,LayoutParams(-1,-2).apply { topMargin=dp(3) })
        weatherRow.addView(weather,LayoutParams(-2,-2))
        weatherRow.addView(weatherCredit,LayoutParams(-2,-2).apply { marginStart=dp(12) })
        addView(weatherRow,LayoutParams(-1,-2).apply { topMargin=dp(4) })
        supplements.addView(reflectionBlock,LayoutParams(-1,0,1f))
        supplements.addView(football,LayoutParams(-1,dp(64)).apply{topMargin=dp(12)})

    }
    fun start(){active=true;renderKey="";weatherSession.start();prayerApi.start();footballSession.start()}
    fun stop(){active=false;renderKey="";weatherSession.stop();prayerApi.stop();footballSession.stop()}
    fun update(now: Instant, s: DisplaySettings, theme: AmbientTheme) {
        val savedOptions=SettingsRepository(context).load().smart
        val key="${s.language}:${now.epochSecond/60}:${s.smart}:$savedOptions:${s.theme}:${s.hour24}:${s.latinDigits}:$weatherRevision:$prayerRevision:$footballRevision"
        if(key==renderKey)return
        renderKey=key
        fun tr(value:String)=com.ufuq.dream.AppLanguage.text(value,s.language)
        val english=s.language=="en"
        layoutDirection=com.ufuq.dream.AppLanguage.direction(s.language)
        weatherRow.layoutDirection=layoutDirection;prayerRow.layoutDirection=layoutDirection
        listOf(header,weather).plus(prayerLabels).forEach{it.textDirection=if(english)View.TEXT_DIRECTION_FIRST_STRONG_LTR else View.TEXT_DIRECTION_FIRST_STRONG_RTL}
        val options=s.smart;visibility=if(options.prayers||options.weather||options.reflectionMode in 1..3||(options.football && FootballTeams.find(options.footballTeam)!=null))View.VISIBLE else View.GONE
        fun digits(value: String)=if(s.latinDigits)value else value.map { if(it in '0'..'9') '٠'+(it-'0') else it }.joinToString("")
        val ink=com.ufuq.dream.NightMode.fade(theme.ink,com.ufuq.dream.NightMode.textFactor(java.util.Date.from(now),s.nightDim))
        listOf(header,weather,weatherCredit).plus(prayerLabels).forEach { it.setTextColor(ink);it.setLinkTextColor(ink) }
        header.visibility=if(options.prayers)View.VISIBLE else View.GONE;prayerRow.visibility=header.visibility
        if(options.prayers) {
            try {
                require(DeviceTime.valid(now.toEpochMilli()))
                val city=SaudiCities.find(options.prayerCity);val date=now.atZone(city.zoneId).toLocalDate()
                val entries:List<PrayerEntry>;val next:NextPrayer?;val source:String
                if(options.prayerSource==0){
                    val query=PrayerRequest(date,city,options.prayerMethod,options.hanafiAsr)
                    val allowed=PrayerApiConsent.allowed(options,savedOptions)
                    if(active){prayerApi.start();prayerApi.refresh(query,now.toEpochMilli(),allowed)}
                    val day=prayerApi.read(query,now.toEpochMilli())
                    entries=day?.entries.orEmpty()
                    next=day?.next(now,prayerApi.read(query.copy(date=date.plusDays(1)),now.toEpochMilli()))
                    source=if(day!=null)"" else if(!DeviceTime.valid(now.toEpochMilli()))"تحقق من تاريخ الجهاز ووقته" else
                        if(!allowed)"احفظ اختيارك لعرض المواقيت" else if(prayerApi.failed)"المواقيت غير متاحة حاليًا" else "جارٍ تحميل المواقيت"
                }else{
                    prayerApi.stop()
                    val day=prayerProvider.day(date,city,options)
                    entries=day.entries;next=prayerProvider.next(now,city,options)
                    source=""
                }
                val minutes=next?.let{(it.remaining.seconds+59)/60}
                val countdown=minutes?.let{if(english)"${it/60}h ${it%60}m" else "${it/60} س و${it%60} د"}.orEmpty()
                val remaining=next?.let{if(english)" · ${tr(it.entry.name)} in $countdown" else " · ${it.entry.name} بعد $countdown"}.orEmpty()
                val heading=digits(tr(city.arabic)+(if(source.isBlank())"" else " · ${tr(source)}")+remaining)
                header.text=android.text.SpannableString(heading).apply {
                    if(countdown.isNotEmpty())setSpan(android.text.style.ForegroundColorSpan(
                        com.ufuq.dream.NightMode.fade(theme.muted,com.ufuq.dream.NightMode.textFactor(java.util.Date.from(now),s.nightDim))),
                        length-digits(countdown).length,length,android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }

                val highlights=PrayerHighlights.at(now,entries,next)
                val factor=com.ufuq.dream.NightMode.textFactor(java.util.Date.from(now),s.nightDim)
                prayerLabels.forEachIndexed { i,label ->
                    label.text=entries.getOrNull(i)?.let { tr(it.name)+"\n\u2066"+com.ufuq.dream.TimePresentation.reading(it.instant,city.zoneId,s)+"\u2069" }?:"—"
                    label.setTextColor(when(i){
                        highlights.current -> com.ufuq.dream.NightMode.fade(theme.accent,factor)
                        else -> ink
                    })
                    val status=when(i){highlights.current->if(english)"Current" else "الحالية";highlights.next->if(english)"Next" else "القادمة";else->""}
                    label.contentDescription=listOf(label.text.toString(),status).filter{it.isNotBlank()}.joinToString(" · ")
                }
            } catch (_: Exception) { header.text=tr(if(!DeviceTime.valid(now.toEpochMilli()))"تحقق من تاريخ الجهاز ووقته" else "تعذّر التحقق من أوقات الصلاة");prayerLabels.forEach { it.text="—";it.contentDescription="—" } }
        }else{prayerApi.stop()}
        reflectionBlock.visibility=if(options.reflectionMode in 1..3)View.VISIBLE else View.GONE
        if(reflectionBlock.visibility==View.VISIBLE) {
            try {
                val entry=com.ufuq.dream.reflection.DailyReflection.today(context,now.atZone(java.time.ZoneId.systemDefault()).toLocalDate(),options.reflectionMode)
                if(entry!=null)reflectionBlock.render(entry,s.language,theme,com.ufuq.dream.NightMode.textFactor(java.util.Date.from(now),s.nightDim))
                else reflectionBlock.visibility=View.GONE
            }catch(_:Exception){reflectionBlock.unavailable(s.language,theme,com.ufuq.dream.NightMode.textFactor(java.util.Date.from(now),s.nightDim))}
        }
        football.visibility=if(options.football && FootballTeams.find(options.footballTeam)!=null)View.VISIBLE else View.GONE
        football.setTextColor(ink);football.textDirection=if(english)View.TEXT_DIRECTION_FIRST_STRONG_LTR else View.TEXT_DIRECTION_FIRST_STRONG_RTL
        if(football.visibility==View.VISIBLE) {
            if(active){footballSession.start();footballSession.refresh(options.footballTeam,now.toEpochMilli(),FootballConsent.allowed(options,savedOptions))}
            val feed=footballSession.read(options.footballTeam,now.toEpochMilli())
            val fixture=feed?.next(now)
            football.text=if(fixture!=null) {
                val zone=java.time.ZoneId.systemDefault()

                val home=FootballTeams.localized(fixture.home,s.language);val away=FootballTeams.localized(fixture.away,s.language)
                "${android.text.BidiFormatter.getInstance(!english).unicodeWrap(home)} × ${android.text.BidiFormatter.getInstance(!english).unicodeWrap(away)}\n${android.text.BidiFormatter.getInstance(!english).unicodeWrap(com.ufuq.dream.TimePresentation.reading(fixture.start,zone,s,true))}"+(if(!feed.fresh(now.toEpochMilli()))" · ${tr("جدول محفوظ")}" else "")
            }else tr(when {
                !DeviceTime.valid(now.toEpochMilli())->"تحقق من تاريخ الجهاز ووقته"
                !FootballConsent.allowed(options,savedOptions)->"احفظ فريقك لعرض المباراة"
                footballSession.loading->"جارٍ تحميل المباراة القادمة"
                feed!=null->"لا توجد مباراة قادمة متاحة"
                else->"المباراة القادمة غير متاحة حاليًا"
            })
        }else footballSession.stop()
        supplements.layoutDirection=layoutDirection
        supplements.visibility=if(reflectionBlock.visibility==View.VISIBLE||football.visibility==View.VISIBLE)View.VISIBLE else View.GONE
        weatherRow.visibility=if(options.weather)View.VISIBLE else View.GONE
        weather.visibility=weatherRow.visibility;weatherCredit.visibility=weather.visibility
        if(!options.weather) { weatherSession.stop();if(active)weatherSession.start();return }
        val city=SaudiCities.find(options.weatherCity);val millis=now.toEpochMilli()
        val policy=WeatherConsent.policy(options,savedOptions)
        val permitted=policy!=null
        if(active&&policy!=null)weatherSession.refresh(city,millis,policy.intervalMinutes,true)
        val reading=weatherSession.reading(city,millis)
        weatherCredit.visibility=if(reading!=null)View.VISIBLE else View.GONE
        // Never fabricate weather values, even in a staged preview.
        val status=when {
            !DeviceTime.valid(millis)->"تحقق من تاريخ الجهاز ووقته"
            reading!=null && reading.stale(millis)->"قراءة سابقة"
            reading!=null->""
            !permitted->"احفظ اختيارك لعرض الطقس"
            weatherSession.loading->"جارٍ تحميل الطقس"
            else->"الطقس غير متاح حاليًا"
        }
        val temperature=if(reading!=null)"\u2066${reading.temperature(options.fahrenheit).roundToInt()}°${if(options.fahrenheit){if(english)"F" else "ف"} else {if(english)"C" else "م"}}\u2069 · ${tr(WeatherParsing.condition(reading.code))}" else ""
        weather.text=digits(listOf(tr(city.arabic),temperature,tr(status)).filter{it.isNotBlank()}.joinToString(" · "))
        if(reading!=null)weather.setCompoundDrawablesRelative(WeatherGlyph(reading.code,ink,reading.daylight).apply { setBounds(0,0,dp(28),dp(28)) },null,null,null)
        else weather.setCompoundDrawablesRelative(null,null,null,null)
    }
}
