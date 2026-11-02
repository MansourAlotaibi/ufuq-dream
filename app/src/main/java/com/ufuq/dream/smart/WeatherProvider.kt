package com.ufuq.dream.smart

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import org.json.JSONObject
import java.time.Instant
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicBoolean
import javax.net.ssl.HttpsURLConnection
import java.net.URL

/** All cached temperatures are Celsius; presentation conversion never causes a network request. */
data class WeatherReading(val cityId: String, val celsius: Double, val code: Int, val daylight: Boolean, val observedAt: Long, val fetchedAt: Long) {
    fun temperature(fahrenheit: Boolean) = if (fahrenheit) celsius * 9 / 5 + 32 else celsius
    fun ageMinutes(now: Long) = ((now - observedAt).coerceAtLeast(0) / 60000)
    fun stale(now: Long) = now < fetchedAt || now - observedAt > 3 * 3600000L
    fun json() = JSONObject().put("city",cityId).put("c",celsius).put("code",code).put("day",daylight).put("observed",observedAt).put("fetched",fetchedAt).toString()
}
object WeatherParsing {
    private val codes = setOf(0,1,2,3,45,48,51,53,55,56,57,61,63,65,66,67,71,73,75,77,80,81,82,85,86,95,96,99)
    private fun valid(city: String, c: Double, code: Int, observed: Long, fetched: Long, now: Long): WeatherReading {
        require(SaudiCities.all.any { it.id == city } && c.isFinite() && c in -90.0..65.0 && code in codes)
        require(observed > 0 && fetched > 0 && observed <= now + 15 * 60000L && fetched <= now + 15 * 60000L)
        require(now - observed <= 48 * 3600000L)
        return WeatherReading(city,c,code,true,observed,fetched)
    }
    fun response(text: String, city: AmbientCity, now: Long): WeatherReading {
        require(text.length <= 65536)
        val root=JSONObject(text);require(root.getJSONObject("current_units").getString("temperature_2m") == "°C")
        require(root.getJSONObject("current_units").getString("time") == "unixtime")
        val current=root.getJSONObject("current")
        val c=current.getDouble("temperature_2m");val code=current.getInt("weather_code")
        val observed=Math.multiplyExact(current.getLong("time"),1000L)
        return valid(city.id,c,code,observed,now,now).copy(daylight=current.getInt("is_day").let { require(it in 0..1); it == 1 })
    }
    fun cache(text: String, now: Long): WeatherReading {
        val j=JSONObject(text)
        return valid(j.getString("city"),j.getDouble("c"),j.getInt("code"),j.getLong("observed"),j.getLong("fetched"),now).copy(daylight=j.getBoolean("day"))
    }
    fun condition(code: Int): String = when(code) {
        0 -> "صافٍ";1,2 -> "غائم جزئيًا";3 -> "غائم";45,48 -> "ضباب"
        in 51..67, in 80..82 -> "أمطار";in 71..77,85,86 -> "ثلوج";95,96,99 -> "عواصف رعدية";else -> "غير متاح"
    }
}
data class WeatherNetworkPolicy(val cityId:String,val intervalMinutes:Int)
object WeatherConsent {
    fun policy(draft:SmartSettings,saved:SmartSettings):WeatherNetworkPolicy? =
        if(draft.weather&&saved.weather&&draft.weatherCity==saved.weatherCity)
            WeatherNetworkPolicy(saved.weatherCity,saved.weatherRefreshMinutes) else null
}
interface WeatherTransport { fun fetch(city: AmbientCity): String }
class OpenMeteoTransport(context: Context) : WeatherTransport {
    private val diagnostics=SmartDiagnostics(context)
    override fun fetch(city: AmbientCity): String {
        require(SaudiCities.all.contains(city))
        val url="https://api.open-meteo.com/v1/forecast?latitude=${city.latitude}&longitude=${city.longitude}&current=temperature_2m,weather_code,is_day&temperature_unit=celsius&timeformat=unixtime&timezone=GMT"
        val connection=URL(url).openConnection() as HttpsURLConnection
        try {
            connection.connectTimeout=8000;connection.readTimeout=8000;connection.instanceFollowRedirects=false
            connection.setRequestProperty("Accept","application/json")
            connection.setRequestProperty("User-Agent","UfuqDream/0.9 non-commercial weather")
            connection.useCaches=false
            SmartFailures.checkResponse(connection,"weather",diagnostics)
            return connection.inputStream.use { input ->
                val output=java.io.ByteArrayOutputStream();val buffer=ByteArray(4096)
                while(true) { if(Thread.currentThread().isInterrupted) throw InterruptedException();val n=input.read(buffer);if(n<0)break;require(output.size()+n<=65536);output.write(buffer,0,n) }
                output.toString("UTF-8")
            }
        } finally { connection.disconnect() }
    }
}
/** One request process-wide; attempts limited even across preview/dream restarts. */
internal object WeatherRateLimit {
    val busy=AtomicBoolean(false)
    private val attempts=mutableMapOf<String,Long>()
    private val due=mutableMapOf<String,Long>()
    private val failures=mutableMapOf<String,Int>()
    private var token=0L
    private var owner:Long?=null
    @Synchronized fun acquire(city: String, now: Long, intervalMinutes: Int): Long? {
        val previous=attempts[city]
        if(due[city]?.let{now<it}==true)return null
        if(!due.containsKey(city) && previous!=null && now>=previous && now-previous<intervalMinutes.coerceAtLeast(30)*60000L)return null
        if(owner!=null)return null
        val lease=++token;owner=lease;busy.set(true);attempts[city]=now;return lease
    }
    @Synchronized fun failed(lease:Long,city:String,now:Long,error:SmartFailure,retryAfter:Long):Long {
        if(owner!=lease)return 60000
        val count=(failures[city]?:0)+1;failures[city]=count
        val delay=SmartFailures.retryDelay(count,error,retryAfter);due[city]=now+delay;return delay
    }
    @Synchronized fun succeeded(lease:Long,city:String){if(owner==lease){due.remove(city);failures.remove(city)}}
    @Synchronized fun owns(lease:Long)=owner==lease
    @Synchronized fun release(lease:Long, canceledCity:String?=null, attemptAt:Long?=null){
        if(owner!=lease)return
        if(canceledCity!=null && attempts[canceledCity]==attemptAt){attempts.remove(canceledCity);due.remove(canceledCity)}
        owner=null;busy.set(false)
    }
    @Synchronized internal fun reset(){attempts.clear();due.clear();failures.clear();owner=null;busy.set(false)}
}

class WeatherCache(context: Context) {
    private val prefs=context.applicationContext.getSharedPreferences("smart_weather_cache",Context.MODE_PRIVATE)
    fun read(city: AmbientCity, now: Long): WeatherReading? = try {
        prefs.getString(city.id,null)?.let { WeatherParsing.cache(it,now) }?.takeIf { it.cityId==city.id }
    } catch (_: Exception) { null }
    fun write(reading: WeatherReading) { prefs.edit().putString(reading.cityId,reading.json()).apply() }
}
/** Activity/Dream-owned session. stop cancels callbacks and prevents new requests; I/O has finite timeouts. */
class WeatherSession(context: Context, private val changed: () -> Unit,
    private val transport: WeatherTransport=OpenMeteoTransport(context),
    private val connected: () -> Boolean = { SmartFailures.connected(context) }) {
    init{DeviceTime.initialize(context)}
    private val cache=WeatherCache(context)
    private val diagnostics=SmartDiagnostics(context)
    private val manager=context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
    private val main=Handler(Looper.getMainLooper())
    private var executor: java.util.concurrent.ExecutorService?=null
    private var request: Future<*>?=null
    @Volatile private var generation=0
    @Volatile private var active=false
    private var observed=false
    private val callback=object:android.net.ConnectivityManager.NetworkCallback(){
        override fun onAvailable(network:android.net.Network){main.post {if(active)changed()}}
        override fun onLost(network:android.net.Network){main.post {if(active)changed()}}
    }
    var loading=false;private set
    var failed=false;private set
    var failure:SmartFailure?=null;private set
    fun start(){
        if(active)return
        active=true
        try{manager.registerDefaultNetworkCallback(callback);observed=true}catch(_:RuntimeException){}
    }
    fun reading(city: AmbientCity, now: Long) = if(DeviceTime.valid(now))cache.read(city,now) else null
    fun refresh(city: AmbientCity, now: Long, minutes: Int, allowed: Boolean) {
        if(!active||!allowed||loading)return
        if(!SaudiCities.all.contains(city)){failed=true;failure=SmartFailure.INVALID_DATA;return}
        if(!DeviceTime.valid(now)){failed=true;failure=SmartFailure.CLOCK;return}
        if(!connected()){failed=true;failure=SmartFailure.OFFLINE;return}
        val old=cache.read(city,now)
        if(old!=null && now>=old.fetchedAt && now-old.fetchedAt<minutes.coerceAtLeast(30)*60000L)return
        val attemptAt=SystemClock.elapsedRealtime()
        val lease=WeatherRateLimit.acquire(city.id,attemptAt,minutes)?:return
        loading=true;failed=false;val ticket=generation
        val worker=executor?.takeUnless { it.isShutdown }?:Executors.newSingleThreadExecutor().also { executor=it }
        val began=AtomicBoolean(false)
        val task=object : java.util.concurrent.FutureTask<Unit>(Runnable {
            began.set(true)
            var error:SmartFailure?=null;var delay=0L
            try {
                if(!WeatherRateLimit.owns(lease)||!active||ticket!=generation||Thread.currentThread().isInterrupted)return@Runnable
                val result=WeatherParsing.response(transport.fetch(city),city,System.currentTimeMillis())
                if(active&&ticket==generation&&!Thread.currentThread().isInterrupted){
                    cache.write(result);WeatherRateLimit.succeeded(lease,city.id);diagnostics.event("weather","success")
                }
            } catch (e: Exception) {
                if(active&&ticket==generation&&!Thread.currentThread().isInterrupted){
                    error=SmartFailures.classify(e)
                    delay=WeatherRateLimit.failed(lease,city.id,SystemClock.elapsedRealtime(),error,(e as? SmartNetworkException)?.retryMs?:0)
                    diagnostics.event("weather",error.name,(e as? SmartNetworkException)?.status?:0)
                }
            } finally {
                val canceled=!active||ticket!=generation||Thread.currentThread().isInterrupted
                WeatherRateLimit.release(lease,if(canceled)city.id else null,if(canceled)attemptAt else null)
            }
            main.post { if(active&&ticket==generation){
                loading=false;failed=error!=null;failure=error;changed()
                if(delay>0)main.postDelayed({if(active&&ticket==generation)changed()},delay)
            } }
        }, Unit) {
            override fun done(){if(!began.get())WeatherRateLimit.release(lease,city.id,attemptAt)}
        }
        request=task
        try { worker.execute(task) } catch (_: java.util.concurrent.RejectedExecutionException) { WeatherRateLimit.release(lease,city.id,attemptAt);loading=false }
    }
    fun stop(){
        active=false;generation++;request?.cancel(true);request=null;executor?.shutdownNow();executor=null;loading=false;main.removeCallbacksAndMessages(null)
        if(observed){try{manager.unregisterNetworkCallback(callback)}catch(_:RuntimeException){};observed=false}
    }
    internal val running get()=active
}
