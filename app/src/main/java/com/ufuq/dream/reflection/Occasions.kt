package com.ufuq.dream.reflection

import android.icu.util.IslamicCalendar
import android.icu.util.TimeZone
import com.ufuq.dream.AppLanguage
import com.ufuq.dream.smart.DeviceTime
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** Calendar estimates, never an official moon-sighting announcement. No network requests. */
object Occasions {
    data class Countdown(val name: String, val date: LocalDate, val days: Long, val estimated: Boolean)
    fun next(now: Instant, selection: Int, zone: ZoneId = ZoneId.systemDefault()): Countdown? {
        if(selection==6)return (1..5).mapNotNull{next(now,it,zone)}.minByOrNull{it.days}
        if(selection !in 1..5 || !DeviceTime.valid(now.toEpochMilli())) return null
        val today = now.atZone(zone).toLocalDate()
        if(selection==4 || selection==5) {
            var date=if(selection==4)LocalDate.of(today.year,9,23) else LocalDate.of(today.year,2,22)
            if(date<today)date=date.plusYears(1)
            return Countdown(if(selection==4)"اليوم الوطني" else "يوم التأسيس",date,ChronoUnit.DAYS.between(today,date),false)
        }
        val calendar=IslamicCalendar(TimeZone.getTimeZone(zone.id)).apply {
            calculationType=IslamicCalendar.CalculationType.ISLAMIC_UMALQURA
            timeInMillis=now.toEpochMilli()
        }
        val year=calendar.get(android.icu.util.Calendar.YEAR)
        val month=when(selection){1->8;2->9;else->11}
        val day=if(selection==3)10 else 1
        fun date(y:Int):LocalDate {
            calendar.clear();calendar.set(y,month,day,12,0,0)
            return Instant.ofEpochMilli(calendar.timeInMillis).atZone(zone).toLocalDate()
        }
        var target=date(year)
        if(target<today)target=date(year+1)
        return Countdown(when(selection){1->"رمضان";2->"عيد الفطر";else->"عيد الأضحى"},target,ChronoUnit.DAYS.between(today,target),true)
    }
    fun customDate(raw: String): LocalDate? = try {
        val normalized=raw.trim().map{when(it){in '٠'..'٩'->'0'+(it-'٠');in '۰'..'۹'->'0'+(it-'۰');else->it}}.joinToString("")
        require(normalized.matches(Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}")))
        LocalDate.parse(normalized).takeIf{it.year in 2020..2100}
    } catch(_:Exception){null}
    fun label(now: Instant, selection: Int, language: String, customName: String="", customDate: String=""): String {
        val item=if(selection==7) {
            if(!DeviceTime.valid(now.toEpochMilli()))return ""
            val date=customDate(customDate)?:return ""
            val name=com.ufuq.dream.GreetingText.clean(customName)
            if(name.isBlank())return ""
            Countdown(name,date,ChronoUnit.DAYS.between(now.atZone(ZoneId.systemDefault()).toLocalDate(),date),false)
        } else next(now,selection)?:return ""
        val name=android.text.BidiFormatter.getInstance(language!="en").unicodeWrap(if(selection==7)item.name else AppLanguage.text(item.name,language))
        val text=if(language=="en") {
            (if(item.days<0L)"$name · ended" else if(item.days==0L)"$name today" else "$name · ${item.days} days")+(if(item.estimated)" · estimated" else "")
        } else {
            (if(item.days<0L)"$name · انتهت" else if(item.days==0L)"$name اليوم" else "$name · بعد ${item.days} يوم")+(if(item.estimated)" · تقديري" else "")
        }
        return if(language=="en")text else text.map{if(it in '0'..'9')'٠'+(it-'0') else it}.joinToString("")
    }
}
