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
        val entries=ContentRepository.entries(context)
        if(mode in 0..3)return DailyReflection.select(entries.filter{it.kind!="dhikr"},date,mode)
        val kinds=when(mode){4->listOf("dhikr");5->listOf("quran","dhikr");6->listOf("hadith","dhikr");7->listOf("quran","hadith","dhikr");else->return null}
        val slot=Math.floorMod(now.epochSecond/300,kinds.size.toLong()).toInt()
        return when(kinds[slot]) {
            "quran"->DailyReflection.select(entries,date,1)
            "hadith"->DailyReflection.select(entries,date,2)
            else->{
                val pool=entries.filter{it.kind=="dhikr"}
                pool.getOrNull(Math.floorMod(now.epochSecond/600+if(now.atZone(ZoneId.systemDefault()).hour<12)0 else 1,pool.size.toLong()).toInt())
            }
        }
    }
}
