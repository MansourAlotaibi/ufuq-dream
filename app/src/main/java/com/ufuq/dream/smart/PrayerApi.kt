package com.ufuq.dream.smart

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import org.json.JSONObject
import java.net.URL
import java.net.URLEncoder
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicBoolean
import javax.net.ssl.HttpsURLConnection
import kotlin.math.abs

/** Parameters are explicit and included in both request and cache identity. */
data class PrayerRequest(val date: LocalDate, val city: AmbientCity, val method: Int, val hanafi: Boolean) {
    val apiMethod get() = if (method == 0) 4 else 3
    val key get() = "$date:${city.id}:$apiMethod:$hanafi"
    fun calendarUrl():String {
        val format=DateTimeFormatter.ofPattern("dd-MM-uuuu",java.util.Locale.ROOT)
        return url().replace("/timings/${date.format(format)}?","/calendar/from/${date.format(format)}/to/${date.plusDays(29).format(format)}?")
    }
    fun url(): String {
        require(SaudiCities.all.contains(city) && method in 0..1)
        val dateText = date.format(DateTimeFormatter.ofPattern("dd-MM-uuuu", java.util.Locale.ROOT))
        return "https://api.aladhan.com/v1/timings/$dateText?latitude=${city.latitude}&longitude=${city.longitude}" +
            "&method=$apiMethod&school=${if(hanafi)1 else 0}&timezonestring=${URLEncoder.encode(city.zone,"UTF-8")}" +
            "&iso8601=true&calendarMethod=UAQ&latitudeAdjustmentMethod=3&midnightMode=0"
    }
}
data class ApiPrayerDay(val request: PrayerRequest, val entries: List<PrayerEntry>, val fetchedAt: Long) {
    fun next(now: Instant, tomorrow: ApiPrayerDay?): NextPrayer? {
        require(now.atZone(request.city.zoneId).toLocalDate() == request.date)
        val compatible = tomorrow?.takeIf { it.request == request.copy(date=request.date.plusDays(1)) }
        return (entries + compatible?.entries.orEmpty()).firstOrNull { it.instant > now }
            ?.let { NextPrayer(it, Duration.between(now,it.instant)) }
    }
}
object PrayerApiParsing {
    val names = listOf("Fajr","Dhuhr","Asr","Maghrib","Isha")
    private val arabic = listOf("الفجر","الظهر","العصر","المغرب","العشاء")
    fun parse(raw: String, request: PrayerRequest, fetchedAt: Long): ApiPrayerDay {
        require(raw.length <= 65536 && fetchedAt > 0)
        val root=JSONObject(raw)
        require(root.getInt("code")==200 && root.getString("status")=="OK")
        val data=root.getJSONObject("data");val meta=data.getJSONObject("meta")
        val date=data.getJSONObject("date")
        require(date.getJSONObject("gregorian").getString("date") == request.date.format(DateTimeFormatter.ofPattern("dd-MM-uuuu",java.util.Locale.ROOT)))
        require(meta.getString("timezone")==request.city.zone)
        require(abs(meta.getDouble("latitude")-request.city.latitude)<0.001 && abs(meta.getDouble("longitude")-request.city.longitude)<0.001)
        require(meta.getJSONObject("method").getInt("id")==request.apiMethod)
        require(meta.getString("school")==if(request.hanafi)"HANAFI" else "STANDARD")
        val params=meta.getJSONObject("method").getJSONObject("params")
        require(abs(params.getDouble("Fajr")-if(request.method==0)18.5 else 18.0)<0.001)
        if(request.method==0)require(params.getString("Isha")=="90 min") else require(abs(params.getDouble("Isha")-17.0)<0.001)
        require(meta.getString("latitudeAdjustmentMethod")=="ANGLE_BASED" && meta.getString("midnightMode")=="STANDARD")
        val hijri=date.getJSONObject("hijri");require(hijri.getString("method")=="UAQ")
        val month=hijri.getJSONObject("month").getInt("number");require(month in 1..12)
        val ramadan=request.method==0 && month==9
        val timings=data.getJSONObject("timings");val offsets=meta.getJSONObject("offset")
        val entries=names.mapIndexed { i,name ->
            // AlAdhan reports the Umm al-Qura Ramadan extension as an Isha +30 offset.
            require(offsets.getInt(name)==if(name=="Isha"&&ramadan)30 else 0)
            val time=OffsetDateTime.parse(timings.getString(name))
            val zoned=time.toInstant().atZone(request.city.zoneId)
            require(zoned.toLocalDate()==request.date && time.offset==zoned.offset)
            PrayerEntry(arabic[i],time.toInstant())
        }
        require(entries.zipWithNext().all { (a,b) -> b.instant>a.instant })
        require(Duration.between(entries.first().instant,entries.last().instant).toHours() in 6..23)
        if(request.method==0){
            // Fail closed if the service's documented Ramadan interval is not honored.
            require(Duration.between(entries[3].instant,entries[4].instant).seconds == (if(month==9)120 else 90)*60L)
        }
        return ApiPrayerDay(request,entries,fetchedAt)
    }
}
interface PrayerApiTransport { fun fetch(request: PrayerRequest): String }
class AlAdhanTransport(context:Context): PrayerApiTransport {
    private val diagnostics=SmartDiagnostics(context)
    override fun fetch(request: PrayerRequest): String {
        val connection=URL(request.calendarUrl()).openConnection() as HttpsURLConnection
        try {
            connection.connectTimeout=8000;connection.readTimeout=8000;connection.instanceFollowRedirects=false;connection.useCaches=false
            connection.setRequestProperty("Accept","application/json")
            connection.setRequestProperty("User-Agent","UfuqDream/0.9 https://github.com/MansourAlotaibi/ufuq-dream")
            SmartFailures.checkResponse(connection,"prayer",diagnostics)
            return connection.inputStream.use { input ->
                val output=java.io.ByteArrayOutputStream();val buffer=ByteArray(4096)
                while(true){if(Thread.currentThread().isInterrupted)throw InterruptedException();val n=input.read(buffer);if(n<0)break;require(output.size()+n<=PrayerCalendarParsing.MAX_BYTES);output.write(buffer,0,n)}
                output.toString("UTF-8")
            }
        } finally { connection.disconnect() }
    }
}
class PrayerApiCache(context: Context) {
    private val prefs=context.applicationContext.getSharedPreferences("smart_prayer_api_cache",Context.MODE_PRIVATE)
    fun read(request: PrayerRequest, now: Long): ApiPrayerDay? = try {
        prefs.getString(request.key,null)?.let {
            val json=JSONObject(it);val fetched=json.getLong("fetched")
            require(json.getInt("schema")==1 && fetched<=now+60000 && now-fetched<=48*3600000L)
            PrayerApiParsing.parse(json.getString("response"),request,fetched)
        }
    } catch (_: Exception){null}
    @Synchronized fun write(request: PrayerRequest, raw: String, now: Long) {
        PrayerApiParsing.parse(raw,request,now)
        val entries=prefs.all.filterKeys { it!=request.key }.toList().sortedByDescending { (_,value) ->
            try { JSONObject(value as String).getLong("fetched") } catch (_: Exception){0L}
        }
        val edit=prefs.edit()
        entries.drop(31).forEach { edit.remove(it.first) }
        edit.putString(request.key,JSONObject().put("schema",1).put("fetched",now).put("response",raw).toString()).apply()
    }
}
object PrayerApiConsent {
    fun allowed(draft: SmartSettings,saved: SmartSettings) = draft.prayers && saved.prayers &&
        draft.prayerSource==0 && saved.prayerSource==0 && draft.prayerCity==saved.prayerCity &&
        draft.prayerMethod==saved.prayerMethod && draft.hanafiAsr==saved.hanafiAsr
}
/** One foreground request sequence, no alarms/background polling, bounded retries across Studio/Dream. */
internal object PrayerApiGate {
    private var nextToken=0L
    private var owner: Long?=null
    private val attempts=linkedMapOf<String,Long>()
    private val retryAt=mutableMapOf<String,Long>()
    private val failures=mutableMapOf<String,Int>()
    @Synchronized fun acquire(key: String,now: Long): Long? {
        if(owner!=null || retryAt[key]?.let{now<it}==true || (!retryAt.containsKey(key)&&attempts[key]?.let { now>=it && now-it<30*60000L }==true))return null
        val token=++nextToken;owner=token;attempts[key]=now
        while(attempts.size>32){val old=attempts.keys.first();attempts.remove(old);retryAt.remove(old);failures.remove(old)}
        return token
    }
    @Synchronized fun owns(token: Long)=owner==token
    @Synchronized fun failure(token:Long,key:String,now:Long,retryAfter:Long){
        if(owner!=token)return
        val count=(failures[key]?:0)+1;failures[key]=count
        retryAt[key]=now+maxOf(when(count){1->1800000L;2->3600000L;else->21600000L},retryAfter.coerceIn(0,21600000L))
    }
    @Synchronized fun success(token:Long,key:String){if(owner==token){retryAt.remove(key);failures.remove(key)}}
    @Synchronized fun release(token: Long,cancelledKey: String?=null){if(owner==token){owner=null;if(cancelledKey!=null){attempts.remove(cancelledKey);retryAt.remove(cancelledKey)}}}
    @Synchronized internal fun reset(){owner=null;attempts.clear();retryAt.clear();failures.clear()}
}
class PrayerApiSession(context: Context,private val changed: () -> Unit,private val transport: PrayerApiTransport=AlAdhanTransport(context)) {
    init{DeviceTime.initialize(context)}
    private val appContext=context.applicationContext
    private val cache=PrayerApiCache(context)
    private val calendar=PrayerCalendarCache(context)
    private val diagnostics=SmartDiagnostics(context)
    private val main=Handler(Looper.getMainLooper())
    private var worker: java.util.concurrent.ExecutorService?=null
    private var request: Future<*>?=null
    @Volatile private var generation=0
    @Volatile private var active=false
    var loading=false;private set
    var failed=false;private set
    fun start(){active=true}
    fun read(query: PrayerRequest,now: Long)=if(DeviceTime.valid(now))calendar.load(query,now)?.read(query)?:cache.read(query,now) else null
    fun refresh(query: PrayerRequest,now: Long,allowed: Boolean){
        if(!active||!allowed||loading)return
        if(!DeviceTime.valid(now)){failed=true;return}
        if(calendar.load(query,now)?.coverage(query)?.let{it>=7}==true)return
        if(!SmartFailures.connected(appContext)){failed=true;return}
        val lease=PrayerApiGate.acquire(query.key,SystemClock.elapsedRealtime())?:return
        val ticket=generation;loading=true;failed=false
        val executor=worker?.takeUnless { it.isShutdown }?:Executors.newSingleThreadExecutor().also { worker=it }
        val began=AtomicBoolean(false)
        val task=object: java.util.concurrent.FutureTask<Unit>(Runnable {
            began.set(true);var error=false
            try {
                if(active&&ticket==generation&&PrayerApiGate.owns(lease)&&!Thread.currentThread().isInterrupted){
                    val raw=transport.fetch(query)
                    if(active&&ticket==generation&&!Thread.currentThread().isInterrupted){calendar.write(query,raw,System.currentTimeMillis());PrayerApiGate.success(lease,query.key);diagnostics.event("prayer","success")}
                }
            }catch (failure: Exception){error=true;PrayerApiGate.failure(lease,query.key,SystemClock.elapsedRealtime(),(failure as? SmartNetworkException)?.retryMs?:0);diagnostics.event("prayer",SmartFailures.classify(failure).name,(failure as? SmartNetworkException)?.status?:0)}
            finally { PrayerApiGate.release(lease,if(!active||ticket!=generation||Thread.currentThread().isInterrupted)query.key else null) }
            main.post { if(active&&ticket==generation){loading=false;failed=error;changed()} }
        },Unit){ override fun done(){if(!began.get())PrayerApiGate.release(lease,query.key)} }
        request=task
        try {executor.execute(task)}catch (_: java.util.concurrent.RejectedExecutionException){PrayerApiGate.release(lease);loading=false}
    }
    fun stop(){active=false;generation++;request?.cancel(true);request=null;worker?.shutdownNow();worker=null;loading=false;main.removeCallbacksAndMessages(null)}
}
