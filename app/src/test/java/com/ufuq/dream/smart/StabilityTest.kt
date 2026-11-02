package com.ufuq.dream.smart

import android.os.Looper
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28])
class StabilityTest {
    private val context get()=RuntimeEnvironment.getApplication()
    private val city=SaudiCities.find("riyadh")
    private val query=PrayerRequest(LocalDate.of(2026,11,2),city,0,false)
    private val now=Instant.parse("2026-11-02T17:00:00Z").toEpochMilli()
    private fun raw()=javaClass.getResourceAsStream("/smart/aladhan-range.json")!!.bufferedReader().use{it.readText()}
    @Before fun reset(){
        listOf("smart_prayer_calendar","smart_prayer_api_cache","smart_weather_cache","smart_diagnostics","smart_clock_anchor").forEach{context.getSharedPreferences(it,0).edit().clear().commit()}
        WeatherRateLimit.reset();PrayerApiGate.reset();DeviceTime.reset()
    }
    @Test fun realThirtyDayCalendarCrossesMonthBoundary(){
        val calendar=PrayerCalendarParsing.parse(raw(),query,now)
        assertEquals(30,calendar.days.size);assertEquals(LocalDate.of(2026,12,1),calendar.days.last().request.date)
        assertEquals(30,calendar.coverage(query));assertEquals(7,calendar.coverage(query.copy(date=query.date.plusDays(23))))
        assertEquals(6,calendar.coverage(query.copy(date=query.date.plusDays(24))))
        assertTrue(query.calendarUrl().contains("/from/02-11-2026/to/01-12-2026?"))
    }
    @Test fun cacheWorksOfflineBeyondFortyEightHoursAndRejectsOtherConfiguration(){
        val cache=PrayerCalendarCache(context);cache.write(query,raw(),now)
        val day=query.copy(date=query.date.plusDays(20));val later=now+20*86400000L
        assertNotNull(cache.load(day,later)?.read(day))
        assertNull(cache.load(query.copy(city=SaudiCities.find("makkah")),now))
        assertNull(cache.load(query.copy(method=1),now));assertNull(cache.load(query.copy(hanafi=true),now))
        assertNull(cache.load(query.copy(date=query.date.plusDays(30)),now+30*86400000L))
    }
    @Test fun invalidOrPartialBatchNeverReplacesValidSnapshot(){
        val cache=PrayerCalendarCache(context);cache.write(query,raw(),now)
        val broken=JSONObject(raw());broken.getJSONArray("data").getJSONObject(10).getJSONObject("meta").put("timezone","UTC")
        assertThrows(IllegalArgumentException::class.java){cache.write(query,broken.toString(),now+1000)}
        assertEquals(now,PrayerCalendarCache(context).load(query,now+1000)!!.fetchedAt)
        broken.getJSONArray("data").remove(0)
        assertThrows(IllegalArgumentException::class.java){PrayerCalendarParsing.parse(broken.toString(),query,now)}
        assertNotNull(PrayerCalendarCache(context).load(query,now))
    }
    @Test fun syntheticYearBoundaryAndLeapDayAreConsecutiveNotSingleMonth(){
        // Synthetic dates exercise identity/range behavior, not astronomical accuracy.
        for(start in listOf(LocalDate.of(2026,12,24),LocalDate.of(2028,2,20))){
            val root=JSONObject(raw());val rows=root.getJSONArray("data")
            for(i in 0 until rows.length()){
                val date=start.plusDays(i.toLong());val row=rows.getJSONObject(i)
                row.getJSONObject("date").getJSONObject("gregorian").put("date",date.format(java.time.format.DateTimeFormatter.ofPattern("dd-MM-uuuu")))
                val timings=row.getJSONObject("timings")
                for(name in PrayerApiParsing.names){val old=OffsetDateTime.parse(timings.getString(name));timings.put(name,OffsetDateTime.of(date,old.toLocalTime(),old.offset).toString())}
            }
            val parsed=PrayerCalendarParsing.parse(root.toString(),query.copy(date=start),now)
            assertEquals(start.plusDays(29),parsed.days.last().request.date)
            assertEquals(30,parsed.coverage(query.copy(date=start)))
        }
    }
    @Test fun exactMidnightUsesTomorrowAndNeverOldDate(){
        val calendar=PrayerCalendarParsing.parse(raw(),query,now)
        val today=calendar.days.first();val tomorrow=calendar.days[1]
        val afterIsha=today.entries.last().instant.plusSeconds(1)
        assertEquals(tomorrow.entries.first().instant,today.next(afterIsha,tomorrow)!!.entry.instant)
        val midnight=query.date.plusDays(1).atStartOfDay(city.zoneId).toInstant()
        assertThrows(IllegalArgumentException::class.java){today.next(midnight,tomorrow)}
        assertEquals(tomorrow.entries.first().instant,tomorrow.next(midnight,calendar.days[2])!!.entry.instant)
    }
    @Test fun grossClockErrorsAndConfirmedWallJumpsAreRejected(){
        assertFalse(DeviceTime.valid(0));assertFalse(DeviceTime.plausible(Instant.parse("2101-01-01T00:00:00Z").toEpochMilli()))
        DeviceTime.verifyServerDate(System.currentTimeMillis())
        assertTrue(DeviceTime.valid(System.currentTimeMillis()))
        assertFalse(DeviceTime.valid(System.currentTimeMillis()+86400000L))
        assertThrows(SmartNetworkException::class.java){DeviceTime.verifyServerDate(System.currentTimeMillis()-3600000)}
    }
    @Test fun errorsAreTypedAndRetriesBounded(){
        assertEquals(SmartFailure.DNS,SmartFailures.classify(java.net.UnknownHostException("private message")))
        assertEquals(SmartFailure.TLS,SmartFailures.classify(javax.net.ssl.SSLHandshakeException("private")))
        assertEquals(SmartFailure.TIMEOUT,SmartFailures.classify(java.net.SocketTimeoutException()))
        assertEquals(SmartFailure.INVALID_DATA,SmartFailures.classify(org.json.JSONException("bad")))
        assertEquals(60000L,SmartFailures.retryDelay(1,SmartFailure.DNS))
        assertEquals(300000L,SmartFailures.retryDelay(2,SmartFailure.TIMEOUT))
        assertEquals(3600000L,SmartFailures.retryDelay(100,SmartFailure.IO))
        assertEquals(21600000L,SmartFailures.retryDelay(1,SmartFailure.RATE_LIMIT,Long.MAX_VALUE))
    }
    @Test fun canceledInFlightWeatherDoesNotSuppressNextSessionForThirtyMinutes(){
        val entered=CountDownLatch(1);val leave=CountDownLatch(1);val ended=CountDownLatch(1)
        val first=WeatherSession(context,{},object:WeatherTransport{override fun fetch(city:AmbientCity):String{
            entered.countDown();try{leave.await(2,TimeUnit.SECONDS)}catch(_:InterruptedException){}
            ended.countDown();throw java.io.IOException("cancelled")
        }},connected={true})
        first.start();first.refresh(city,System.currentTimeMillis(),60,true)
        assertTrue(entered.await(2,TimeUnit.SECONDS));first.stop();leave.countDown();assertTrue(ended.await(2,TimeUnit.SECONDS))
        repeat(100){if(WeatherRateLimit.busy.get())Thread.sleep(5)}
        var called=false
        val next=WeatherSession(context,{},object:WeatherTransport{override fun fetch(city:AmbientCity):String{called=true;throw java.net.SocketTimeoutException()}},connected={true})
        next.start();next.refresh(city,System.currentTimeMillis(),60,true)
        repeat(100){if(!called)Thread.sleep(5)}
        assertTrue(called);next.stop()
    }
    @Test fun noInternetDoesNotSendARequestAndCachedWeatherStillWorks(){
        val time=System.currentTimeMillis();WeatherCache(context).write(WeatherReading(city.id,31.6,0,false,time,time))
        var calls=0
        val session=WeatherSession(context,{},object:WeatherTransport{override fun fetch(city:AmbientCity):String{calls++;error("must not request")}},connected={false})
        session.start();session.refresh(city,time,60,true)
        assertEquals(0,calls);assertEquals(SmartFailure.OFFLINE,session.failure);assertEquals(31.6,session.reading(city,time)!!.celsius,.001)
        assertNull(session.reading(city,time+49*3600000L));session.stop()
    }
    @Test fun failureKindsSurviveSanitizedDiagnosticsWithoutLeakingMessages(){
        val diagnostics=SmartDiagnostics(context);diagnostics.event("weather","http",429);diagnostics.event("weather",SmartFailure.RATE_LIMIT.name)
        assertTrue(diagnostics.detail("weather").contains("429"));assertTrue(diagnostics.detail("weather").contains("RATE_LIMIT"))
        val invalidCity=city.copy(id="unlisted",latitude=0.0)
        val session=WeatherSession(context,{},object:WeatherTransport{override fun fetch(city:AmbientCity):String{error("no")}},connected={true})
        session.start();session.refresh(invalidCity,System.currentTimeMillis(),60,true)
        assertEquals(SmartFailure.INVALID_DATA,session.failure);session.stop()
    }
    @Test fun httpStatusAndRetryAfterAreValidatedWithoutRealNetwork(){
        fun connection(status:Int)=object:javax.net.ssl.HttpsURLConnection(java.net.URL("https://example.invalid/")){
            override fun getCipherSuite()="TEST"
            override fun getLocalCertificates():Array<java.security.cert.Certificate>?=null
            override fun getServerCertificates():Array<java.security.cert.Certificate> = emptyArray()
            override fun disconnect(){}
            override fun usingProxy()=false
            override fun connect(){}
            override fun getResponseCode()=status
            override fun getHeaderField(name:String?)=if(name=="Retry-After")"120" else null
            override fun getHeaderFieldDate(name:String?,default:Long)=if(name=="Date")System.currentTimeMillis() else default
        }
        val diagnostics=SmartDiagnostics(context)
        val rate=assertThrows(SmartNetworkException::class.java){SmartFailures.checkResponse(connection(429),"weather",diagnostics)}
        assertEquals(SmartFailure.RATE_LIMIT,rate.failure);assertEquals(120000L,rate.retryMs)
        val http=assertThrows(SmartNetworkException::class.java){SmartFailures.checkResponse(connection(503),"weather",diagnostics)}
        assertEquals(503,http.status);assertEquals(SmartFailure.HTTP,http.failure)
        SmartFailures.checkResponse(connection(200),"weather",diagnostics)
    }
    @Test fun dnsTimeoutHttpAndMalformedResponsesReachSessionErrorState(){
        val errors=listOf(java.net.UnknownHostException(),java.net.SocketTimeoutException(),SmartNetworkException(SmartFailure.HTTP,503),org.json.JSONException("invalid"))
        for(error in errors){
            WeatherRateLimit.reset();var calls=0
            val session=WeatherSession(context,{},object:WeatherTransport{override fun fetch(city:AmbientCity):String{calls++;throw error}},connected={true})
            session.start();session.refresh(city,System.currentTimeMillis(),60,true)
            repeat(200){if(session.loading){Thread.sleep(5);shadowOf(Looper.getMainLooper()).idle()}}
            assertTrue(session.failed);assertEquals(SmartFailures.classify(error),session.failure);assertNull(session.reading(city,System.currentTimeMillis()))
            session.refresh(city,System.currentTimeMillis(),60,true);assertEquals(1,calls);session.stop()
        }
    }
    @Test fun prayerFailuresBackOffAndOldTokensCannotUnlockNewRequests(){
        val a=PrayerApiGate.acquire(query.key,1000)!!
        PrayerApiGate.failure(a,query.key,1000,0);PrayerApiGate.release(a)
        assertNull(PrayerApiGate.acquire(query.key,1800000))
        val b=PrayerApiGate.acquire(query.key,1801000)!!
        PrayerApiGate.failure(b,query.key,1801000,0);PrayerApiGate.release(a)
        assertTrue(PrayerApiGate.owns(b));PrayerApiGate.release(b)
        assertNull(PrayerApiGate.acquire(query.key,5400000))
        assertNotNull(PrayerApiGate.acquire(query.key,5401000))
    }
    @Test fun rollingCacheEvictsOldestConfigurationAtThreeSnapshots(){
        val cache=PrayerCalendarCache(context)
        // Synthetic coordinates exercise storage identity/eviction,not astronomical accuracy.
        val cities=listOf("riyadh","makkah","madinah","jeddah").map{SaudiCities.find(it)}
        cities.forEachIndexed { index,city ->
            val root=JSONObject(raw());val rows=root.getJSONArray("data")
            for(i in 0 until rows.length()){val meta=rows.getJSONObject(i).getJSONObject("meta");meta.put("latitude",city.latitude).put("longitude",city.longitude)}
            cache.write(query.copy(city=city),root.toString(),now+index*1000)
        }
        assertEquals(3,context.getSharedPreferences("smart_prayer_calendar",0).all.size)
        assertNull(cache.load(query,now+4000))
        assertNotNull(cache.load(query.copy(city=cities.last()),now+4000))
    }
    @Test fun storageCommitFailureIsReportedAndDoesNotPretendPersistenceSucceeded(){
        val real=context.getSharedPreferences("smart_prayer_calendar",0)
        val wrapper=object:android.content.ContextWrapper(context){
            override fun getApplicationContext():android.content.Context=this
            override fun getSharedPreferences(name:String,mode:Int):android.content.SharedPreferences {
                return java.lang.reflect.Proxy.newProxyInstance(javaClass.classLoader,arrayOf(android.content.SharedPreferences::class.java)){_,method,args ->
                    if(method.name=="edit"){
                        var editor:Any?=null
                        editor=java.lang.reflect.Proxy.newProxyInstance(javaClass.classLoader,arrayOf(android.content.SharedPreferences.Editor::class.java)){_,m,_->
                            when(m.name){"commit"->false;"apply"->null;else->editor}
                        };editor
                    }else method.invoke(real,*(args?:emptyArray()))
                } as android.content.SharedPreferences
            }
        }
        val error=assertThrows(SmartNetworkException::class.java){PrayerCalendarCache(wrapper).write(query,raw(),now)}
        assertEquals(SmartFailure.STORAGE,error.failure);assertTrue(real.all.isEmpty())
    }
}
