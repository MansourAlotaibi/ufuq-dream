package com.ufuq.dream
import android.icu.text.SimpleDateFormat
import android.icu.util.ULocale
import java.util.Date
import java.util.Calendar
interface HijriProvider {
    fun label(now:Date):String
    /** Providers that can switch digit style override this; simple fakes only implement [label]. */
    fun label(now:Date,latinDigits:Boolean):String = label(now)
    fun label(now:Date,latinDigits:Boolean,language:String):String=label(now,latinDigits)
}
class UmmAlQuraProvider:HijriProvider {
 override fun label(now:Date):String = label(now,false)
 override fun label(now:Date,latinDigits:Boolean):String = label(now,latinDigits,"ar")
 override fun label(now:Date,latinDigits:Boolean,language:String):String = try {
  val tag=if(language=="en")"en_SA@calendar=islamic-umalqura;numbers=latn" else if(latinDigits)"ar_SA@calendar=islamic-umalqura;numbers=latn" else "ar_SA@calendar=islamic-umalqura"
  SimpleDateFormat(if(language=="en")"d MMMM y 'AH'" else "d MMMM y 'هـ'",ULocale(tag)).format(now)
 } catch (_:IllegalArgumentException){AppLanguage.text("التاريخ الهجري غير متاح",language)}
}
data class TimeLabels(val time:String,val period:String,val gregorian:String,val hijri:String)
class DateProvider(private val hijriProvider:HijriProvider=UmmAlQuraProvider()) {
 fun labels(now:Date,s:DisplaySettings):TimeLabels {
 val locale=ULocale(if(s.language=="en")"en_US@calendar=gregorian;numbers=latn" else if(s.latinDigits)"ar_SA@calendar=gregorian;numbers=latn" else "ar_SA@calendar=gregorian")
 val pattern=TimePresentation.pattern(s.hour24,s.seconds)
 val hour=Calendar.getInstance().apply{time=now}.get(Calendar.HOUR_OF_DAY)
 return TimeLabels(SimpleDateFormat(pattern,locale).format(now),if(s.hour24) "" else if(s.language=="en") {if(hour<12)"AM" else "PM"} else if(hour<12) "ص" else "م",
 SimpleDateFormat(if(s.language=="en")"EEEE, d MMMM y" else "EEEE، d MMMM y",locale).format(now),hijriProvider.label(now,s.latinDigits,s.language))
 }
}
object GreetingProvider {
 fun label(now:Date,s:DisplaySettings):String {
 val phrase=when(s.greetingMode) {
  1 -> GreetingText.presets[s.greetingPreset.coerceIn(0,3)]
  2 -> GreetingText.clean(s.greetingText)
  3 -> ""
  else -> label(now)
 }
 return if(s.greetingMode==2)phrase else AppLanguage.text(phrase,s.language)
 }
 fun label(now:Date):String {val h=Calendar.getInstance().apply{time=now}.get(Calendar.HOUR_OF_DAY)
 return when(h){in 5..11->"صباح الخير";in 12..16->"حياك الله";in 17..22->"مساء النور";else->"يا هلا"}}
}
