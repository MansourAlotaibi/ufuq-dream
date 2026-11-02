package com.ufuq.dream.smart

import com.ufuq.dream.SettingsRepository
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import android.os.Looper
import java.time.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28])
class PrayerApiTest {
    private val context get()=RuntimeEnvironment.getApplication()
    private val city=SaudiCities.find("riyadh")
    private val query=PrayerRequest(LocalDate.of(2026,11,2),city,0,false)
    private val fetched=Instant.parse("2026-11-02T10:00:00Z").toEpochMilli()
    private fun raw()=javaClass.getResourceAsStream("/smart/aladhan-response.json")!!.bufferedReader().use{it.readText()}
    @Before fun reset(){
        context.getSharedPreferences("smart_prayer_api_cache",0).edit().clear().commit()
        context.getSharedPreferences("dream_settings",0).edit().clear().commit();PrayerApiGate.reset();DeviceTime.reset();context.getSharedPreferences("smart_clock_anchor",0).edit().clear().commit()
        val cm=context.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        shadowOf(cm).setActiveNetworkInfo(org.robolectric.shadows.ShadowNetworkInfo.newInstance(android.net.NetworkInfo.DetailedState.CONNECTED,android.net.ConnectivityManager.TYPE_WIFI,0,true,true))
    }
    @Test fun realResponsePreservesExactInstantsAndIndependentTimezone(){
        val old=java.util.TimeZone.getDefault()
        try {
            java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("America/New_York"))
            val result=PrayerApiParsing.parse(raw(),query,fetched)
            assertEquals(5,result.entries.size)
            assertEquals(Instant.parse("2026-11-02T01:41:00Z"),result.entries[0].instant)
            assertEquals(Instant.parse("2026-11-02T15:43:00Z"),result.entries[4].instant)
            assertTrue(query.url().contains("iso8601=true"));assertTrue(query.url().contains("method=4"))
        }finally{java.util.TimeZone.setDefault(old)}
    }
    @Test fun capturedSaudiRamadanAndDstResponsesPassExactValidation(){
        val rows=org.json.JSONArray(javaClass.getResourceAsStream("/smart/aladhan-cases.json")!!.bufferedReader().use{it.readText()})
        for(i in 0 until rows.length()){
            val row=rows.getJSONObject(i)
            val date=LocalDate.parse(row.getString("date"),java.time.format.DateTimeFormatter.ofPattern("dd-MM-uuuu"))
            val request=PrayerRequest(date,SaudiCities.find(row.getString("city")),if(row.getInt("method")==4)0 else 1,row.getInt("school")==1)
            val response=row.getJSONObject("response")
            val day=PrayerApiParsing.parse(response.toString(),request,fetched)
            day.entries.forEachIndexed { index,entry ->
                assertEquals(OffsetDateTime.parse(response.getJSONObject("data").getJSONObject("timings").getString(PrayerApiParsing.names[index])).toInstant(),entry.instant)
            }
        }
    }
    private fun rejects(edit:(JSONObject)->Unit){
        val response=JSONObject(raw());edit(response)
        assertThrows(IllegalArgumentException::class.java){PrayerApiParsing.parse(response.toString(),query,fetched)}
    }
    @Test fun rejectsWrongDateCoordinatesZoneMethodSchoolOffsetsAndUnorderedTimes(){
        rejects{it.getJSONObject("data").getJSONObject("date").getJSONObject("gregorian").put("date","03-11-2026")}
        rejects{it.getJSONObject("data").getJSONObject("meta").put("latitude",21.4225)}
        rejects{it.getJSONObject("data").getJSONObject("meta").put("timezone","UTC")}
        rejects{it.getJSONObject("data").getJSONObject("meta").getJSONObject("method").put("id",3)}
        rejects{it.getJSONObject("data").getJSONObject("meta").put("school","HANAFI")}
        rejects{it.getJSONObject("data").getJSONObject("meta").getJSONObject("offset").put("Fajr",1)}
        rejects{it.getJSONObject("data").getJSONObject("timings").put("Asr","2026-11-02T09:00:00+03:00")}
        rejects{it.getJSONObject("data").getJSONObject("timings").put("Fajr","2026-11-02T04:41:00Z")}
        rejects{it.getJSONObject("data").getJSONObject("meta").getJSONObject("method").getJSONObject("params").put("Fajr",18.0)}
    }
    @Test fun ramadanIntervalCannotSilentlyBeNinetyMinutes(){
        rejects{it.getJSONObject("data").getJSONObject("date").getJSONObject("hijri").getJSONObject("month").put("number",9)}
        val response=JSONObject(raw());response.getJSONObject("data").getJSONObject("date").getJSONObject("hijri").getJSONObject("month").put("number",9)
        response.getJSONObject("data").getJSONObject("timings").put("Isha","2026-11-02T19:13:00+03:00")
        response.getJSONObject("data").getJSONObject("meta").getJSONObject("offset").put("Isha",30)
        assertEquals(5,PrayerApiParsing.parse(response.toString(),query,fetched).entries.size)
    }
    @Test fun exactPrayerBoundaryAndNextDayDoNotReuseYesterdaysFajr(){
        val day=PrayerApiParsing.parse(raw(),query,fetched)
        assertEquals("الظهر",day.next(day.entries[0].instant,null)!!.entry.name)
        assertNull(day.next(day.entries.last().instant,null))
        val tomorrow=day.copy(request=query.copy(date=query.date.plusDays(1)),entries=day.entries.map{it.copy(instant=it.instant.plusSeconds(86400))})
        assertEquals(tomorrow.entries.first().instant,day.next(day.entries.last().instant,tomorrow)!!.entry.instant)
        val wrong=tomorrow.copy(request=tomorrow.request.copy(hanafi=true))
        assertNull(day.next(day.entries.last().instant,wrong))
        assertThrows(IllegalArgumentException::class.java){day.next(Instant.parse("2026-11-03T00:00:00Z"),tomorrow)}
    }
    @Test fun cacheRejectsCorruptionExpiryDifferentParametersAndFutureClock(){
        val cache=PrayerApiCache(context);cache.write(query,raw(),fetched)
        assertNotNull(cache.read(query,fetched));assertNull(cache.read(query.copy(hanafi=true),fetched))
        assertNull(cache.read(query.copy(date=query.date.plusDays(1)),fetched))
        assertNull(cache.read(query,fetched+49*3600000L));assertNull(cache.read(query,fetched-120000))
        context.getSharedPreferences("smart_prayer_api_cache",0).edit().putString(query.key,"broken").commit()
        assertNull(cache.read(query,fetched))
    }
    @Test fun stagedChoicesNeverAuthorizeNetworkAndLegacySettingsStayOffline(){
        val defaults=SettingsRepository(context).load();assertEquals(0,defaults.smart.prayerSource)
        val online=SmartSettings(prayers=true)
        assertFalse(PrayerApiConsent.allowed(online,SmartSettings()))
        assertFalse(PrayerApiConsent.allowed(online.copy(prayerCity="makkah"),online))
        assertFalse(PrayerApiConsent.allowed(online.copy(hanafiAsr=true),online))
        assertFalse(PrayerApiConsent.allowed(online.copy(prayerMethod=1),online))
        assertTrue(PrayerApiConsent.allowed(online,online))
        context.getSharedPreferences("dream_settings",0).edit().putBoolean("smart_prayers",true).commit()
        assertEquals(1,SettingsRepository(context).load().smart.prayerSource)
        val draft=defaults.copy(smart=online);assertFalse(SettingsRepository(context).load().smart.prayerSource==0)
        SettingsRepository(context).save(draft);assertEquals(online,SettingsRepository(context).load().smart)
    }
    @Test fun requestGateCannotBeReleasedByOldOwnerOrHammerService(){
        val first=PrayerApiGate.acquire(query.key,1000)!!
        assertNull(PrayerApiGate.acquire("other",1000));PrayerApiGate.release(first)
        assertNull(PrayerApiGate.acquire(query.key,2000))
        val second=PrayerApiGate.acquire("other",2000)!!;PrayerApiGate.release(first)
        assertTrue(PrayerApiGate.owns(second));PrayerApiGate.release(second)
    }
    @Test fun savingConsentImmediatelyInvalidatesPreviewWithinSameMinute(){
        val repo=SettingsRepository(context);val draft=repo.load().copy(smart=SmartSettings(prayers=true))
        val panel=SmartPanel(context)
        fun header()=(panel.getChildAt(0) as android.widget.TextView).text.toString()
        val instant=Instant.ofEpochMilli(fetched)
        panel.update(instant,draft,com.ufuq.dream.ThemeManager.get(0))
        assertTrue(header().contains("احفظ"))
        repo.save(draft)
        panel.update(instant,draft,com.ufuq.dream.ThemeManager.get(0))
        assertFalse(header().contains("احفظ"))
        assertTrue(header().contains("تحميل"))
        // Panel was never started: no actual HTTP is allowed in this regression test.
        panel.stop()
    }
    @Test fun stopDropsLateCallbacksAndInvalidNetworkDoesNotInventTimes(){
        val entered=CountDownLatch(1);val leave=CountDownLatch(1);var calls=0;var changes=0
        val transport=object:PrayerApiTransport {override fun fetch(request:PrayerRequest):String {
            calls++;entered.countDown();try{leave.await(2,TimeUnit.SECONDS)}catch (_:InterruptedException){}
            return raw()
        }}
        val session=PrayerApiSession(context,{changes++},transport)
        session.refresh(query,fetched,true);assertEquals(0,calls)
        session.start();session.refresh(query,fetched,false);assertEquals(0,calls)
        session.refresh(query,fetched,true);assertTrue(entered.await(2,TimeUnit.SECONDS));session.stop();leave.countDown()
        Thread.sleep(60);shadowOf(Looper.getMainLooper()).idle()
        assertEquals(0,changes);assertNull(session.read(query,fetched));assertFalse(session.loading)
    }
}
