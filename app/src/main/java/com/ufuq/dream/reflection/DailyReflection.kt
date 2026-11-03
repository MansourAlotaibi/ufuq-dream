package com.ufuq.dream.reflection

import android.content.Context
import org.json.JSONObject
import java.time.LocalDate

/** Adapted from Ufuq Launcher DailyContent (MIT). Data retain their separate source licenses. */
data class ReflectionEntry(val kind:String,val text:String,val source:String,val sourceUrl:String,
    val licenseUrl:String,val attribution:String,val notice:String)

object DailyReflection {
    @Volatile private var cached:List<ReflectionEntry>?=null
    @Synchronized fun entries(context:Context):List<ReflectionEntry> {
        cached?.let{return it}
        val raw=context.assets.open("daily_reflection.json").bufferedReader(Charsets.UTF_8).use{it.readText()}
        return parse(raw).also{cached=it}
    }
    internal fun parse(raw:String):List<ReflectionEntry> {
        require(raw.length in 1..65536)
        val root=JSONObject(raw);require(root.getInt("formatVersion")==1)
        val array=root.getJSONArray("entries");require(array.length() in 1..64)
        return (0 until array.length()).map { index ->
            val row=array.getJSONObject(index)
            val kind=row.getString("kind");require(kind in listOf("quran","hadith"))
            val entry=ReflectionEntry(kind,row.getString("text"),row.getString("source"),row.getString("sourceUrl"),row.getString("licenseUrl"),row.getString("attribution"),row.getString("notice"))
            require(entry.text.isNotBlank() && entry.text.length<=4096 && entry.source.isNotBlank() && entry.attribution.isNotBlank() && entry.notice.isNotBlank())
            require(if(kind=="quran")entry.sourceUrl.startsWith("https://tanzil.net/#") && entry.licenseUrl=="https://creativecommons.org/licenses/by/3.0/" else entry.sourceUrl.startsWith("https://ar.wikisource.org/w/index.php?oldid=") && entry.licenseUrl=="https://creativecommons.org/licenses/by-sa/4.0/")
            entry // Source text remains exactly unchanged, including diacritics and punctuation.
        }
    }
    fun select(entries:List<ReflectionEntry>,date:LocalDate,mode:Int):ReflectionEntry? {
        if(mode !in 1..3)return null
        val pool=entries.filter{mode==3 || it.kind==if(mode==1)"quran" else "hadith"}
        return pool.takeIf{it.isNotEmpty()}?.get(Math.floorMod(date.toEpochDay(),pool.size.toLong()).toInt())
    }
    fun today(context:Context,date:LocalDate,mode:Int)=select(entries(context),date,mode)
}
