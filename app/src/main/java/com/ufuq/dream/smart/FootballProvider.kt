package com.ufuq.dream.smart

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.concurrent.FutureTask
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import javax.net.ssl.HttpsURLConnection
import java.net.URL

/** Adapted from Ufuq Launcher's MIT fixture parser. No logos, live scores or private API key. */
data class FootballFixture(val homeId:String,val awayId:String,val home:String,val away:String,val start:Instant) {
    fun json()=JSONObject().put("homeId",homeId).put("awayId",awayId).put("home",home).put("away",away).put("start",start.toEpochMilli())
}
object FootballParsing {
    private fun timestamp(event:JSONObject):Instant? {
        val stamp=event.optString("strTimestamp").takeUnless{it.isBlank()||it=="null"}
            ?: "${event.optString("dateEvent")}T${event.optString("strTime")}"
        return try{Instant.parse(stamp)}catch(_:Exception){
            try{OffsetDateTime.parse(stamp).toInstant()}catch(_:Exception){
                try{LocalDateTime.parse(stamp).toInstant(ZoneOffset.UTC)}catch(_:Exception){null}
            }
        }
    }
    internal fun validate(f:FootballFixture,team:String):FootballFixture {
        require(f.homeId.matches(Regex("[0-9]{1,12}"))&&f.awayId.matches(Regex("[0-9]{1,12}")))
        require(f.homeId!=f.awayId && (team==f.homeId||team==f.awayId))
        require(f.home.isNotBlank()&&f.away.isNotBlank()&&f.home.length<=96&&f.away.length<=96)
        require((f.home+f.away).none{Character.isISOControl(it)||it in '\u202a'..'\u202e'||it in '\u2066'..'\u2069'})
        require(DeviceTime.plausible(f.start.toEpochMilli()))
        return f
    }
    fun parse(raw:String,team:String):List<FootballFixture> {
        require(FootballTeams.find(team)!=null && raw.length in 1..262144)
        val root=JSONObject(raw);require(root.has("events"))
        if(root.isNull("events"))return emptyList()
        val events=root.getJSONArray("events");require(events.length()<=100)
        var owned=false
        val result=mutableListOf<FootballFixture>()
        for(index in 0 until events.length()) {
            val event=events.optJSONObject(index)?:continue
            if(team!=event.optString("idHomeTeam")&&team!=event.optString("idAwayTeam"))continue
            owned=true
            val start=timestamp(event)?:continue
            try{result.add(validate(FootballFixture(event.optString("idHomeTeam"),event.optString("idAwayTeam"),event.optString("strHomeTeam").trim(),event.optString("strAwayTeam").trim(),start),team))}catch(_:IllegalArgumentException){}
        }
        require(events.length()==0 || owned && result.isNotEmpty()) { "Invalid or mismatched fixture feed" }
        return result.distinctBy{Triple(it.homeId,it.awayId,it.start)}.sortedBy{it.start}.take(32)
    }
}
data class FootballFeed(val team:String,val fetchedAt:Long,val fixtures:List<FootballFixture>) {
    fun next(now:Instant)=fixtures.firstOrNull{it.start>now}
    fun fresh(now:Long)=now>=fetchedAt && now-fetchedAt<3600000L
}
class FootballCache(context:Context) {
    private val prefs=context.applicationContext.getSharedPreferences("smart_football_cache",0)
    fun read(team:String,now:Long):FootballFeed?=try {
        require(FootballTeams.find(team)!=null && DeviceTime.valid(now))
        val raw=prefs.getString(team,null);require(raw!=null)
        require(raw.length<=65536)
        val root=JSONObject(raw);require(root.getString("team")==team)
        val fetched=root.getLong("fetched");require(fetched>0&&now>=fetched&&now-fetched<=86400000L)
        val array=root.getJSONArray("fixtures");require(array.length()<=32)
        val fixtures=(0 until array.length()).map { index ->
            val f=array.getJSONObject(index)
            FootballParsing.validate(FootballFixture(f.getString("homeId"),f.getString("awayId"),f.getString("home"),f.getString("away"),Instant.ofEpochMilli(f.getLong("start"))),team)
        }.sortedBy{it.start}
        FootballFeed(team,fetched,fixtures)
    }catch(_:Exception){null}
    fun write(team:String,raw:String,now:Long) {
        require(DeviceTime.valid(now))
        val fixtures=FootballParsing.parse(raw,team)
        val json=JSONObject().put("team",team).put("fetched",now).put("fixtures",JSONArray().apply{fixtures.forEach{put(it.json())}}).toString()
        val edit=prefs.edit().putString(team,json)
        val other=prefs.all.keys.filter{it!=team}.sortedByDescending{key->try{JSONObject(prefs.getString(key,"").orEmpty()).optLong("fetched")}catch(_:Exception){0L}}
        other.drop(7).forEach{edit.remove(it)}
        if(!edit.commit())throw SmartNetworkException(SmartFailure.STORAGE)
    }
}
object FootballConsent {
    fun allowed(draft:SmartSettings,saved:SmartSettings)=draft.football && saved.football &&
        draft.footballTeam==saved.footballTeam && FootballTeams.find(draft.footballTeam)!=null
}
fun interface FootballTransport { fun fetch(team:String):String }
class SportsDbTransport(context:Context):FootballTransport {
    private val diagnostics=SmartDiagnostics(context.applicationContext)
    override fun fetch(team:String):String {
        require(FootballTeams.find(team)!=null)
        // 123 is the upstream launcher's documented public test identifier, not a private key.
        val connection=URL("https://www.thesportsdb.com/api/v1/json/123/eventsnext.php?id=$team").openConnection() as HttpsURLConnection
        connection.connectTimeout=8000;connection.readTimeout=8000;connection.instanceFollowRedirects=false
        connection.setRequestProperty("Accept","application/json")
        try {
            SmartFailures.checkResponse(connection,"football",diagnostics)
            val deadline=SystemClock.elapsedRealtime()+20000L
            return connection.inputStream.use{input->
                val output=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192)
                while(true){
                    if(Thread.currentThread().isInterrupted)throw java.io.InterruptedIOException()
                    if(SystemClock.elapsedRealtime()>deadline)throw java.net.SocketTimeoutException()
                    val count=input.read(buffer);if(count<0)break
                    require(output.size()+count<=262144);output.write(buffer,0,count)
                }
                output.toString("UTF-8")
            }
        }finally{connection.disconnect()}
    }
}
internal object FootballGate {
    data class Lease(val team:String,val at:Long)
    private var owner:Lease?=null
    private val after=mutableMapOf<String,Long>()
    private val failures=mutableMapOf<String,Int>()
    @Synchronized fun acquire(team:String,now:Long):Lease? {
        if(owner!=null||now<(after[team]?:0))return null
        return Lease(team,now).also{owner=it;after[team]=now+60000L}
    }
    @Synchronized fun delay(team:String,now:Long)=if(owner!=null)1000L else ((after[team]?:0)-now).coerceAtLeast(1000L)
    @Synchronized fun success(lease:Lease){if(owner===lease)failures.remove(lease.team)}
    @Synchronized fun failure(lease:Lease,error:SmartFailure,retry:Long) {
        if(owner!==lease)return
        val count=(failures[lease.team]?:0)+1;failures[lease.team]=count.coerceAtMost(4)
        after[lease.team]=SystemClock.elapsedRealtime()+SmartFailures.retryDelay(count,error,retry)
    }
    @Synchronized fun release(lease:Lease,canceled:Boolean) {
        if(owner!==lease)return
        owner=null
        if(canceled)after.remove(lease.team)
    }
    @Synchronized internal fun reset(){owner=null;after.clear();failures.clear()}
}
class FootballSession(context:Context,private val changed:()->Unit,
    private val transport:FootballTransport=SportsDbTransport(context),
    private val connected:()->Boolean={SmartFailures.connected(context.applicationContext)}) {
    private val cache=FootballCache(context)
    private val diagnostics=SmartDiagnostics(context.applicationContext)
    private val main=Handler(Looper.getMainLooper())
    @Volatile private var active=false
    @Volatile private var generation=0
    private var executor:java.util.concurrent.ExecutorService?=null
    private var request:FutureTask<Unit>?=null
    private var retryPending=false
    var loading=false;private set
    var failed=false;private set
    fun start(){active=true}
    fun read(team:String,now:Long)=cache.read(team,now)
    private fun retry(delay:Long){
        if(retryPending||!active)return
        retryPending=true;val ticket=generation
        main.postDelayed({retryPending=false;if(active&&ticket==generation)changed()},delay.coerceAtLeast(1000L))
    }
    fun refresh(team:String,now:Long,allowed:Boolean) {
        if(!active||!allowed||loading||FootballTeams.find(team)==null)return
        if(!DeviceTime.valid(now)||!connected()){failed=true;return}
        if(cache.read(team,now)?.fresh(now)==true)return
        val lease=FootballGate.acquire(team,SystemClock.elapsedRealtime())?:run{retry(FootballGate.delay(team,SystemClock.elapsedRealtime()));return}
        loading=true;failed=false;val ticket=generation
        val worker=executor?.takeUnless{it.isShutdown}?:Executors.newSingleThreadExecutor().also{executor=it}
        val began=AtomicBoolean(false)
        val task=object:FutureTask<Unit>(Runnable {
            began.set(true);var error=false
            try {
                if(!active||ticket!=generation||Thread.currentThread().isInterrupted)return@Runnable
                val raw=transport.fetch(team)
                if(active&&ticket==generation&&!Thread.currentThread().isInterrupted){cache.write(team,raw,System.currentTimeMillis());FootballGate.success(lease);diagnostics.event("football","success")}
            }catch(e:Exception){
                if(active&&ticket==generation&&!Thread.currentThread().isInterrupted){
                    error=true;val kind=SmartFailures.classify(e)
                    FootballGate.failure(lease,kind,(e as? SmartNetworkException)?.retryMs?:0)
                    diagnostics.event("football",kind.name,(e as? SmartNetworkException)?.status?:0)
                }
            }finally{FootballGate.release(lease,!active||ticket!=generation||Thread.currentThread().isInterrupted)}
            main.post{if(active&&ticket==generation){loading=false;failed=error;changed();if(error)retry(FootballGate.delay(team,SystemClock.elapsedRealtime()))}}
        },Unit){override fun done(){if(!began.get())FootballGate.release(lease,true)}}
        request=task
        try{worker.execute(task)}catch(_:java.util.concurrent.RejectedExecutionException){FootballGate.release(lease,true);loading=false}
    }
    fun stop(){active=false;generation++;request?.cancel(true);request=null;executor?.shutdownNow();executor=null;loading=false;retryPending=false;main.removeCallbacksAndMessages(null)}
    internal val running get()=active
}
