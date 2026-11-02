package com.ufuq.dream.smart

import com.batoulapps.adhan.CalculationMethod
import com.batoulapps.adhan.Madhab
import com.batoulapps.adhan.Coordinates
import com.batoulapps.adhan.PrayerTimes
import com.batoulapps.adhan.data.DateComponents
import java.time.Instant
import java.time.LocalDate
import java.time.Duration

/** Calculated estimates, not an official Saudi timetable. Cache is bounded by date/city/parameters. */
data class PrayerEntry(val name: String, val instant: Instant)
data class PrayerDay(val date: LocalDate, val city: AmbientCity, val entries: List<PrayerEntry>, val ramadan: Boolean)
data class NextPrayer(val entry: PrayerEntry, val remaining: Duration)
class PrayerProvider {
    private val cache = object : LinkedHashMap<String, PrayerDay>(8, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, PrayerDay>?) = size > 8
    }
    @Synchronized fun day(date: LocalDate, city: AmbientCity, settings: SmartSettings): PrayerDay {
        val key = "$date:${city.id}:${settings.prayerMethod}:${settings.hanafiAsr}"
        return cache.getOrPut(key) {
            val hijri = android.icu.util.Calendar.getInstance(android.icu.util.TimeZone.getTimeZone(city.zone), android.icu.util.ULocale("ar_SA@calendar=islamic-umalqura"))
            hijri.timeInMillis = date.atTime(12, 0).atZone(city.zoneId).toInstant().toEpochMilli()
            val ramadan = hijri.get(android.icu.util.Calendar.MONTH) == 8
            val parameters = (if (settings.prayerMethod == 0) CalculationMethod.UMM_AL_QURA else CalculationMethod.MUSLIM_WORLD_LEAGUE).parameters
            // Explicit current convention; the legacy Java README describes 18 degrees.
            if (settings.prayerMethod == 0) { parameters.fajrAngle = 18.5; parameters.ishaInterval = if (ramadan) 120 else 90 }
            parameters.madhab = if (settings.hanafiAsr) Madhab.HANAFI else Madhab.SHAFI
            val times = PrayerTimes(Coordinates(city.latitude, city.longitude), DateComponents(date.year, date.monthValue, date.dayOfMonth), parameters)
            val entries = listOf("الفجر" to times.fajr, "الظهر" to times.dhuhr, "العصر" to times.asr, "المغرب" to times.maghrib, "العشاء" to times.isha)
                .map { (name, time) -> PrayerEntry(name, requireNotNull(time) { "Unavailable solar event" }.toInstant()) }
            require(entries.zipWithNext().all { (a,b) -> b.instant > a.instant })
            PrayerDay(date, city, entries, ramadan)
        }
    }
    fun next(now: Instant, city: AmbientCity, settings: SmartSettings): NextPrayer {
        val date = now.atZone(city.zoneId).toLocalDate()
        val next = (day(date,city,settings).entries + day(date.plusDays(1),city,settings).entries).first { it.instant > now }
        return NextPrayer(next, Duration.between(now, next.instant))
    }
    internal val cachedDays get() = cache.size
}
