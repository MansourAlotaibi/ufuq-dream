package com.ufuq.dream

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** One hour-format policy for the clock, prayer times and fixture kickoff. Durations stay durations. */
object TimePresentation {
    fun pattern(hour24:Boolean,seconds:Boolean=false)=(if(hour24)"HH:mm" else "hh:mm")+(if(seconds)":ss" else "")
    fun reading(instant:Instant,zone:ZoneId,s:DisplaySettings,withDate:Boolean=false):String {
        val format=(if(withDate)"EEE d MMM · " else "")+pattern(s.hour24)+(if(s.hour24)"" else " a")
        val value=DateTimeFormatter.ofPattern(format,if(s.language=="en")Locale.US else Locale("ar","SA")).withDecimalStyle(java.time.format.DecimalStyle.STANDARD).withZone(zone).format(instant)
        return if(s.latinDigits)value else value.map{if(it in '0'..'9')'٠'+(it-'0')else it}.joinToString("")
    }
}
