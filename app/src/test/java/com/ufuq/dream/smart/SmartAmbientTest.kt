package com.ufuq.dream.smart

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import com.ufuq.dream.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog
import java.time.*
import java.util.Date
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],qualifiers="w960dp-h540dp-land-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SmartAmbientTest {
    private val context get()=RuntimeEnvironment.getApplication()
    private val city get()=SaudiCities.find("riyadh")
    @Before fun reset(){context.getSharedPreferences("dream_settings",0).edit().clear().commit();context.getSharedPreferences("smart_weather_cache",0).edit().clear().commit();WeatherRateLimit.reset();DeviceTime.reset();context.getSharedPreferences("smart_clock_anchor",0).edit().clear().commit();
        val cm=context.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        shadowOf(cm).setActiveNetworkInfo(org.robolectric.shadows.ShadowNetworkInfo.newInstance(android.net.NetworkInfo.DetailedState.CONNECTED,android.net.ConnectivityManager.TYPE_WIFI,0,true,true))
    }
    private fun now()=System.currentTimeMillis()
    private fun response(at:Long=now(),temperature:String="28.5",code:Int=0)="""{"current_units":{"temperature_2m":"°C","time":"unixtime"},"current":{"time":${at/1000},"temperature_2m":$temperature,"weather_code":$code,"is_day":1}}"""
    @Test fun matchesPublishedComputedSchedulesWithinThreeMinutes(){
        val text=javaClass.getResourceAsStream("/smart/prayer-reference.json")!!.bufferedReader().use{it.readText()}
        val data=org.json.JSONArray(text);val provider=PrayerProvider()
        for(i in 0 until data.length()){
            val row=data.getJSONObject(i);val city=SaudiCities.find(row.getString("city"))
            val date=LocalDate.parse(row.getString("date"),java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy"))
            val names=listOf("Fajr","Dhuhr","Asr","Maghrib","Isha")
            provider.day(date,city,SmartSettings()).entries.forEachIndexed { index,entry ->
                val expected=date.atTime(LocalTime.parse(row.getJSONObject("timings").getString(names[index]))).atZone(city.zoneId).toInstant()
                assertTrue("${city.id} ${names[index]} expected $expected got ${entry.instant}",kotlin.math.abs(Duration.between(expected,entry.instant).seconds)<=180)
            }
        }
    }
    @Test fun prayerTimesAreOrderedOfflineWithRamadanAndAsrParameters(){
        val p=PrayerProvider();val s=SmartSettings(prayers=true)
        val regular=p.day(LocalDate.of(2026,11,2),city,s)
        assertEquals(5,regular.entries.size);assertFalse(regular.ramadan)
        assertEquals(90,Duration.between(regular.entries[3].instant,regular.entries[4].instant).toMinutes().toInt())
        val ramadan=p.day(LocalDate.of(2026,3,15),city,s);assertTrue(ramadan.ramadan)
        assertEquals(120,Duration.between(ramadan.entries[3].instant,ramadan.entries[4].instant).toMinutes().toInt())
        assertTrue(p.day(regular.date,city,s.copy(hanafiAsr=true)).entries[2].instant > regular.entries[2].instant)
        assertNotEquals(regular.entries,p.day(regular.date,city,s.copy(prayerMethod=1)).entries)
        repeat(30){p.day(regular.date.plusDays(it.toLong()),city,s)};assertTrue(p.cachedDays<=8)
    }
    @Test fun nextPrayerUsesCityMidnightAndNeverDeviceDefaultZone(){
        val previous=java.util.TimeZone.getDefault()
        try {
            val p=PrayerProvider();val s=SmartSettings(prayers=true);val instant=Instant.parse("2026-11-02T20:59:59Z")
            java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Pacific/Honolulu"));val a=p.next(instant,city,s)
            java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Asia/Tokyo"));val b=p.next(instant,city,s)
            assertEquals(a,b);assertEquals("الفجر",a.entry.name);assertTrue(a.remaining.seconds>0)
            assertEquals(LocalDate.of(2026,11,3),a.entry.instant.atZone(city.zoneId).toLocalDate())
            val exact=p.day(LocalDate.of(2026,11,2),city,s).entries[0].instant
            assertEquals("الظهر",p.next(exact,city,s).entry.name)
        } finally { java.util.TimeZone.setDefault(previous) }
    }
    @Test fun dstCityUsesIanaRulesAndDailyCacheKeys(){
        val london=SaudiCities.find("london");val p=PrayerProvider();val s=SmartSettings()
        assertNotEquals(LocalDate.of(2026,3,28).atStartOfDay(london.zoneId).offset,LocalDate.of(2026,3,30).atStartOfDay(london.zoneId).offset)
        for(date in listOf(LocalDate.of(2026,3,29),LocalDate.of(2026,10,25))){val d=p.day(date,london,s);assertEquals(5,d.entries.size);assertTrue(d.entries.zipWithNext().all{it.second.instant>it.first.instant})}
    }
    @Test fun parsesCapturedProviderResponseWithoutNetwork(){
        val json=javaClass.getResourceAsStream("/smart/weather-reference.json")!!.bufferedReader().use{it.readText()}
        val captured=org.json.JSONObject(json);val time=captured.getJSONObject("current").getLong("time")*1000
        val reading=WeatherParsing.response(json,city,time)
        assertEquals(captured.getJSONObject("current").getDouble("temperature_2m"),reading.celsius,.001)
        assertEquals(time,reading.observedAt)
    }
    @Test fun weatherParsingConversionFreshnessAndCacheAreBounded(){
        val time=now();val r=WeatherParsing.response(response(time),city,time)
        assertEquals(28.5,r.celsius,.001);assertEquals(83.3,r.temperature(true),.01);assertEquals("صافٍ",WeatherParsing.condition(r.code))
        val cache=WeatherCache(context);cache.write(r);assertEquals(r,cache.read(city,time));assertNull(cache.read(SaudiCities.find("jeddah"),time))
        assertTrue(r.stale(time+4*3600000));assertTrue(r.stale(time-1000));assertNull(cache.read(city,time+49*3600000L))
        for(body in listOf(response(time,"1000"),response(time,code=999),"{}",response(time+3600000))){try{WeatherParsing.response(body,city,time);fail("Invalid weather accepted")}catch(_:Exception){}}
    }
    @Test fun draftRefreshFrequencyCannotChangeSavedNetworkPolicy(){
        val saved=SmartSettings(weather=true,weatherRefreshMinutes=180)
        assertEquals(180,WeatherConsent.policy(saved.copy(weatherRefreshMinutes=30),saved)!!.intervalMinutes)
        assertNull(WeatherConsent.policy(saved.copy(weatherCity="jeddah"),saved))
        assertNull(WeatherConsent.policy(saved.copy(weather=false),saved))
        assertNull(WeatherConsent.policy(saved,saved.copy(weather=false)))
        assertEquals(30,WeatherConsent.policy(saved.copy(weatherRefreshMinutes=30),saved.copy(weatherRefreshMinutes=30))!!.intervalMinutes)
    }
    @Test fun rateLimitSurvivesRestartAndEnforcesSingleRequest(){
        val first=WeatherRateLimit.acquire(city.id,1000,30)!!;assertNull(WeatherRateLimit.acquire("jeddah",1000,30));WeatherRateLimit.release(first)
        assertNull(WeatherRateLimit.acquire(city.id,1799999,30));val second=WeatherRateLimit.acquire(city.id,1801000,30)!!
        WeatherRateLimit.release(first);assertTrue(WeatherRateLimit.busy.get());WeatherRateLimit.release(second);assertFalse(WeatherRateLimit.busy.get())
    }
    @Test fun networkIsOptInSessionScopedAndFailuresLeaveCache(){
        val time=now();val cached=WeatherParsing.response(response(time-4*3600000L),city,time-4*3600000L);WeatherCache(context).write(cached)
        var calls=0;var callbacks=0
        val session=WeatherSession(context,{callbacks++},object:WeatherTransport{override fun fetch(city:AmbientCity):String{calls++;throw java.io.IOException("offline")}})
        session.refresh(city,time,30,true);assertEquals(0,calls)
        session.start();session.refresh(city,time,30,false);assertEquals(0,calls)
        session.refresh(city,time,30,true)
        repeat(200){if(session.loading){Thread.sleep(5);shadowOf(Looper.getMainLooper()).idle()}}
        assertEquals(1,calls);assertTrue(session.failed);assertEquals(cached,session.reading(city,time));assertEquals(1,callbacks)
        session.stop();assertFalse(session.running);session.refresh(city,time,30,true);assertEquals(1,calls)
    }
    @Test fun canceledRequestsReleaseAdmissionAndIgnoreLateCallbacks(){
        var callbacks=0
        repeat(5){
            WeatherRateLimit.reset()
            val session=WeatherSession(context,{callbacks++},object:WeatherTransport{override fun fetch(city:AmbientCity):String { Thread.sleep(500);return response() }})
            session.start();session.refresh(city,now(),30,true);session.stop()
            repeat(100){if(WeatherRateLimit.busy.get()){Thread.sleep(5);shadowOf(Looper.getMainLooper()).idle()}}
            shadowOf(Looper.getMainLooper()).idle();assertFalse(WeatherRateLimit.busy.get());assertFalse(session.running)
        }
        assertEquals(0,callbacks)
    }
    @Test fun successfulFetchCachesResultAndDoesNotPollAgain(){
        var calls=0;var callbacks=0
        val session=WeatherSession(context,{callbacks++},object:WeatherTransport{override fun fetch(city:AmbientCity):String{calls++;return response()}})
        session.start();session.refresh(city,now(),30,true)
        repeat(200){if(session.loading){Thread.sleep(5);shadowOf(Looper.getMainLooper()).idle()}}
        assertEquals(1,calls);assertEquals(1,callbacks);assertFalse(session.failed);assertNotNull(session.reading(city,now()))
        session.refresh(city,now(),30,true);assertEquals(1,calls);session.stop()
    }
    @Test fun saveCancelMigrationAndStudioPreviewRemainIsolated(){
        val repo=SettingsRepository(context);val original=repo.load();assertFalse(original.smart.prayers);assertFalse(original.smart.weather)
        val controller=Robolectric.buildActivity(SettingsActivity::class.java).setup();val a=controller.get();val root=a.window.decorView
        root.findViewWithTag<View>("section_3").requestFocus();shadowOf(Looper.getMainLooper()).idle()
        root.findViewWithTag<View>("prayers").performClick();root.findViewWithTag<View>("weather").performClick()
        assertTrue(a.draft.current.smart.prayers);assertTrue(a.draft.current.smart.weather);assertEquals(original,repo.load());assertFalse(WeatherRateLimit.busy.get())
        root.findViewWithTag<View>("cancel").performClick();assertEquals(original,a.draft.current)
        root.findViewWithTag<View>("section_3").requestFocus();root.findViewWithTag<View>("prayers").performClick();root.findViewWithTag<View>("save").performClick()
        assertTrue(SettingsRepository(context).load().smart.prayers);assertFalse(repo.load().smart.weather);controller.pause().stop().destroy()
    }
    @Test fun smartStudioOptionsUpdateImmediatelyWithoutSaving(){
        val repo=SettingsRepository(context);val before=repo.load()
        val controller=Robolectric.buildActivity(SettingsActivity::class.java).setup();val a=controller.get();val root=a.window.decorView
        fun choose(key:String,index:Int){root.findViewWithTag<View>(key).performClick();ShadowDialog.getLatestDialog().findViewById<View>(android.R.id.content).findViewWithTag<View>("choice_$index").performClick();shadowOf(Looper.getMainLooper()).idle()}
        root.findViewWithTag<View>("section_3").requestFocus();shadowOf(Looper.getMainLooper()).idle()
        root.findViewWithTag<View>("prayers").performClick();root.findViewWithTag<View>("weather").performClick()
        choose("prayer_city",2);choose("weather_city",3);choose("prayer_method",1);root.findViewWithTag<View>("asr").performClick()
        root.findViewWithTag<View>("weather_units").performClick();choose("weather_refresh",2)
        val studio=root.findViewWithTag<PreviewStudio>("studio");val snapshot=studio.display.settingsSnapshot.smart
        assertEquals("madinah",snapshot.prayerCity);assertEquals("jeddah",snapshot.weatherCity);assertEquals(1,snapshot.prayerMethod);assertTrue(snapshot.hanafiAsr);assertTrue(snapshot.fahrenheit);assertEquals(180,snapshot.weatherRefreshMinutes)
        fun labels(v:View):List<String> = when(v){is android.widget.TextView->listOf(v.text.toString());is android.view.ViewGroup->(0 until v.childCount).flatMap{labels(v.getChildAt(it))};else->emptyList()}
        assertTrue(labels(studio).any{it.contains("جدة")&&it.contains("احفظ")});assertFalse(labels(studio).any{it.contains("28°")})
        assertEquals(before,repo.load());assertFalse(WeatherRateLimit.busy.get())
        root.findViewWithTag<View>("cancel").performClick();assertEquals(before,a.draft.current)
        controller.pause().stop().destroy()
    }
    @Test fun allManualPositionsAndAutomaticAvoidSmartBandAtThreeResolutions(){
        for((w,h,density) in listOf(Triple(1280,720,1.33f),Triple(1920,1080,2f),Triple(3840,2160,4f)))for(position in -1..8)for(prayer in listOf(false,true))for(weather in listOf(false,true)){
            val zones=SmartLayout.zones(w,h,density,prayer,weather)
            val spec=ClockPlacement.spec(w,zones.clockHeight,density,position,PhotoComposition.forAsset("alula_1",1))
            val bounds=ClockPlacement.visualBounds(spec,700,650).first
            assertTrue(bounds.left>=0&&bounds.right<=w);assertTrue(bounds.top>=0&&bounds.bottom<=zones.clockHeight)
            if(prayer||weather){assertTrue(bounds.bottom<zones.panel.top);assertTrue(zones.panel.bottom<=h*.95f)}else assertEquals(h,zones.clockHeight)
        }
    }
    @Test fun sharedRendererProducesSmartGalleryWithoutNetworkAndReleases(){
        val base=SettingsRepository(context).load().copy(smart=SmartSettings(prayers=true,weather=true),greetingMode=1,greetingPreset=0)
        for(theme in 0..2){
            val display=AmbientDisplay(context,{base.copy(theme=theme,imageMode=1,imageId=listOf("diriyah_1","alula_1","riyadh_1")[theme])},1920,sceneTransitions=false)
            display.testNow=Date.from(Instant.parse("2026-11-02T15:30:00Z"));display.start();shadowOf(Looper.getMainLooper()).idle()
            repeat(100){if(display.backgroundRenderer.imageBytes==0){Thread.sleep(10);shadowOf(Looper.getMainLooper()).idle()}}
            display.measure(View.MeasureSpec.makeMeasureSpec(1920,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(1080,View.MeasureSpec.EXACTLY));display.layout(0,0,1920,1080)
            val bitmap=Bitmap.createBitmap(1920,1080,Bitmap.Config.ARGB_8888);display.draw(Canvas(bitmap))
            val f=File("../artwork/0.9.0/smart-theme-$theme.png");f.parentFile!!.mkdirs();f.outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
            assertEquals(View.VISIBLE,display.smartPanel.visibility)
            assertTrue(display.visualContentBounds.bottom<display.smartPanel.top)
            for(i in 0 until display.smartPanel.childCount){val child=display.smartPanel.getChildAt(i);if(child.visibility==View.VISIBLE)assertTrue("Widget clipped",child.bottom<=display.smartPanel.height)};assertFalse(WeatherRateLimit.busy.get())
            display.stop();assertFalse(display.smartPanel.weatherSession.running);assertEquals(0,display.backgroundRenderer.imageBytes)
        }
        val controller=Robolectric.buildActivity(SettingsActivity::class.java).setup();val activity=controller.get();val root=activity.window.decorView
        root.findViewWithTag<View>("section_3").requestFocus();shadowOf(Looper.getMainLooper()).idle()
        root.findViewWithTag<View>("prayers").performClick();root.findViewWithTag<View>("weather").performClick()
        val studio=root.findViewWithTag<PreviewStudio>("studio");studio.display.testNow=Date.from(Instant.parse("2026-11-02T15:30:00Z"));studio.refresh()
        repeat(100){if(studio.display.backgroundRenderer.imageBytes==0){Thread.sleep(10);shadowOf(Looper.getMainLooper()).idle()}}
        root.measure(View.MeasureSpec.makeMeasureSpec(1920,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(1080,View.MeasureSpec.EXACTLY));root.layout(0,0,1920,1080)
        val bitmap=Bitmap.createBitmap(1920,1080,Bitmap.Config.ARGB_8888);root.draw(Canvas(bitmap))
        File("../artwork/0.9.0/studio-smart.png").outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
        assertTrue(activity.draft.dirty);assertFalse(SettingsRepository(context).load().smart.weather);assertFalse(WeatherRateLimit.busy.get())
        controller.pause().stop().destroy()
    }
}
