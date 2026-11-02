package com.ufuq.dream.smart

import android.content.Context
import org.json.JSONObject
import java.time.LocalDate

data class PrayerCalendar(val start: PrayerRequest,val fetchedAt: Long,val days: List<ApiPrayerDay>) {
    fun read(query: PrayerRequest)=days.firstOrNull{it.request==query}
    fun coverage(query: PrayerRequest): Int {
        if(start.copy(date=query.date)!=query)return 0
        return (0..29).takeWhile{read(query.copy(date=query.date.plusDays(it.toLong())))!=null}.size
    }
}
object PrayerCalendarParsing {
    const val DAYS=30
    const val MAX_BYTES=512*1024
    fun parse(raw:String,query:PrayerRequest,fetched:Long):PrayerCalendar {
        require(raw.length<=MAX_BYTES)
        val root=JSONObject(raw);require(root.getInt("code")==200&&root.getString("status")=="OK")
        val rows=root.getJSONArray("data");require(rows.length()==DAYS)
        val days=(0 until DAYS).map { index ->
            val expected=query.copy(date=query.date.plusDays(index.toLong()))
            val daily=JSONObject().put("code",200).put("status","OK").put("data",rows.getJSONObject(index))
            PrayerApiParsing.parse(daily.toString(),expected,fetched)
        }
        return PrayerCalendar(query,fetched,days)
    }
}
/** A whole validated rolling range is committed in one SharedPreferences transaction; at most3 configurations. */
class PrayerCalendarCache(context:Context) {
    private val prefs=context.applicationContext.getSharedPreferences("smart_prayer_calendar",Context.MODE_PRIVATE)
    private var memoRaw:String?=null
    private var memo:PrayerCalendar?=null
    private fun key(query:PrayerRequest)="${query.city.id}:${query.apiMethod}:${query.hanafi}"
    fun load(query:PrayerRequest,now:Long):PrayerCalendar?=try {
        if(!DeviceTime.valid(now))null else prefs.getString(key(query),null)?.let { raw ->
            val snapshot=if(raw==memoRaw)memo!! else {
                require(raw.length<=PrayerCalendarParsing.MAX_BYTES*2)
                val envelope=JSONObject(raw);require(envelope.getInt("schema")==1&&envelope.getString("provider")=="AlAdhan")
                val fetched=envelope.getLong("fetched");require(fetched>0&&fetched<=now+60000)
                val start=query.copy(date=LocalDate.parse(envelope.getString("start")))
                require(envelope.getString("city")==query.city.id && envelope.getString("zone")==query.city.zone)
                require(envelope.getDouble("latitude")==query.city.latitude&&envelope.getDouble("longitude")==query.city.longitude)
                require(envelope.getInt("method")==query.apiMethod&&envelope.getBoolean("hanafi")==query.hanafi)
                PrayerCalendarParsing.parse(envelope.getString("response"),start,fetched).also{memoRaw=raw;memo=it}
            }
            snapshot.takeIf{it.fetchedAt<=now+60000&&query.date>=it.start.date&&query.date<it.start.date.plusDays(30)}
        }
    }catch(_:Exception){null}
    @Synchronized fun write(query:PrayerRequest,raw:String,now:Long){
        val parsed=PrayerCalendarParsing.parse(raw,query,now)
        val envelope=JSONObject().put("schema",1).put("provider","AlAdhan").put("city",query.city.id)
            .put("latitude",query.city.latitude).put("longitude",query.city.longitude).put("zone",query.city.zone)
            .put("method",query.apiMethod).put("hanafi",query.hanafi).put("start",query.date.toString())
            .put("fetched",now).put("response",raw).toString()
        val other=prefs.all.filterKeys{it!=key(query)}.toList().sortedByDescending { (_,value)->
            try{JSONObject(value as String).getLong("fetched")}catch(_:Exception){0L}
        }
        val edit=prefs.edit().putString(key(query),envelope)
        other.drop(2).forEach{edit.remove(it.first)}
        if(!edit.commit())throw SmartNetworkException(SmartFailure.STORAGE)
        memoRaw=envelope;memo=parsed
    }
}
