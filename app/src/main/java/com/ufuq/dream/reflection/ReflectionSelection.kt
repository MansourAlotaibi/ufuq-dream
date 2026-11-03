package com.ufuq.dream.reflection

import android.content.Context
import com.ufuq.dream.smart.SmartSettings
import java.time.Instant
import java.time.ZoneId

/** Preserve the old four choices; extend combinations without separate competing toggles. */
object ReflectionSelection {
    fun entry(context: Context, now: Instant, settings: SmartSettings): ReflectionEntry? {
        val mode=settings.reflectionChoice
        val date=now.atZone(ZoneId.systemDefault()).toLocalDate()
        if(mode in 0..3)return DailyReflection.today(context,date,mode)
        val kinds=when(mode){4->listOf("dhikr");5->listOf("quran","dhikr");6->listOf("hadith","dhikr");7->listOf("quran","hadith","dhikr");else->return null}
        val slot=Math.floorMod(now.epochSecond/300,kinds.size.toLong()).toInt()
        return when(kinds[slot]) {
            "quran"->DailyReflection.today(context,date,1)
            "hadith"->DailyReflection.today(context,date,2)
            else->Adhkar.entry(now,"ar") // Original religious text remains Arabic in both UI languages.
        }
    }
}
