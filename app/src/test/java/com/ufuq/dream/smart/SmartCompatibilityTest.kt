package com.ufuq.dream.smart

import android.graphics.Bitmap
import android.graphics.Canvas
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
import java.time.Instant

/** Framework simulation, not an emulator or physical Google TV certification. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28,34],qualifiers="w960dp-h540dp-land-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SmartCompatibilityTest {
    private val context get()=RuntimeEnvironment.getApplication()
    @Before fun reset(){
        listOf("dream_settings","smart_prayer_calendar","smart_prayer_api_cache","smart_clock_anchor","smart_weather_cache").forEach{context.getSharedPreferences(it,0).edit().clear().commit()}
        DeviceTime.reset();WeatherRateLimit.reset();PrayerApiGate.reset()
    }
    @Test fun threeThemesKeepEveryWidgetAndClockWithinSafeAreas(){
        val instant=Instant.parse("2026-11-02T17:00:00Z")
        val city=SaudiCities.find("riyadh");val query=PrayerRequest(instant.atZone(city.zoneId).toLocalDate(),city,0,false)
        val raw=javaClass.getResourceAsStream("/smart/aladhan-range.json")!!.bufferedReader().use{it.readText()}
        PrayerCalendarCache(context).write(query,raw,instant.toEpochMilli())
        WeatherCache(context).write(WeatherReading(city.id,31.6,0,false,instant.toEpochMilli(),instant.toEpochMilli()))
        val repo=SettingsRepository(context)
        for(theme in 0..2){
            var value=repo.load().copy(theme=theme,sceneChoice=theme,imageMode=1,imageId=BackgroundRepository.first(theme).id,smart=SmartSettings(prayers=true,weather=true))
            val display=AmbientDisplay(context,{value},640,sceneTransitions=false)
            display.testNow=java.util.Date.from(instant)
            display.start();shadowOf(android.os.Looper.getMainLooper()).idle()
            repeat(100){if(display.backgroundRenderer.loadedAsset.isEmpty()){Thread.sleep(5);shadowOf(android.os.Looper.getMainLooper()).idle()}}
            assertTrue(display.backgroundRenderer.loadedAsset.isNotEmpty())
            for(position in -1..8){
                value=value.copy(clockPosition=position);display.refresh()
                display.measure(View.MeasureSpec.makeMeasureSpec(1920,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(1080,View.MeasureSpec.EXACTLY));display.layout(0,0,1920,1080)
                val clock=display.visualContentBounds;val band=SmartLayout.zones(1920,1080,2f,value).panel
                assertTrue(clock.top>=0);assertTrue(clock.bottom<band.top)
                val panel=display.smartPanel
                for(i in 0 until panel.childCount){val child=panel.getChildAt(i);if(child.visibility==View.VISIBLE)assertTrue("child $i clips",child.bottom<=panel.height)}
            }
            val bitmap=Bitmap.createBitmap(1920,1080,Bitmap.Config.ARGB_8888);display.draw(Canvas(bitmap))
            if(android.os.Build.VERSION.SDK_INT==28){
                val out=java.io.File("artwork/stability-0.9/theme-$theme.png");out.parentFile!!.mkdirs();out.outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
            }
            bitmap.recycle();display.stop()
        }
    }
    @Test fun savedPreferencesAndRemoteCancelDoNotBecomeImplicitConsent(){
        val repo=SettingsRepository(context);val old=repo.load()
        val controller=Robolectric.buildActivity(SettingsActivity::class.java).setup();val activity=controller.get();val root=activity.window.decorView
        root.findViewWithTag<View>("section_3").requestFocus();shadowOf(android.os.Looper.getMainLooper()).idle()
        root.findViewWithTag<View>("weather").performClick();root.findViewWithTag<View>("prayers").performClick()
        assertTrue(activity.draft.dirty);assertEquals(old,repo.load())
        root.findViewWithTag<View>("cancel").performClick();assertEquals(old,activity.draft.current);assertEquals(old,repo.load())
        controller.pause().stop().destroy();assertFalse(WeatherRateLimit.busy.get())
    }
    @Test fun smartTimesAndWeatherRespectArabicLatinAndHourFormats(){
        val instant=Instant.parse("2026-11-02T17:00:00Z");val city=SaudiCities.find("riyadh")
        val raw=javaClass.getResourceAsStream("/smart/aladhan-range.json")!!.bufferedReader().use{it.readText()}
        PrayerCalendarCache(context).write(PrayerRequest(instant.atZone(city.zoneId).toLocalDate(),city,0,false),raw,instant.toEpochMilli())
        WeatherCache(context).write(WeatherReading(city.id,31.6,0,false,instant.toEpochMilli(),instant.toEpochMilli()))
        val base=SettingsRepository(context).load().copy(smart=SmartSettings(prayers=true,weather=true))
        val panel=SmartPanel(context)
        fun collect(view:View):String=when(view){
            is android.widget.TextView->view.text.toString()
            is android.view.ViewGroup->(0 until view.childCount).joinToString(" "){collect(view.getChildAt(it))}
            else->""
        }
        fun allText()=collect(panel)

        panel.update(instant,base.copy(latinDigits=true,hour24=true),ThemeManager.get(0));assertTrue(allText().contains("04:41"));assertTrue(allText().contains("32°"))
        panel.update(instant,base.copy(latinDigits=false,hour24=true),ThemeManager.get(0));assertTrue(allText().contains("٠٤:٤١"));assertTrue(allText().contains("٣٢°"))
        panel.update(instant,base.copy(latinDigits=true,hour24=false),ThemeManager.get(0));assertTrue(allText().contains("02:49"));assertTrue(allText().contains("م"));panel.stop()
    }
    @Test fun weatherGlyphStaysBesideReadingAndCreditRemainsReadable() {
        val instant=Instant.parse("2026-11-02T17:00:00Z")
        WeatherCache(context).write(WeatherReading("riyadh",31.6,1,false,instant.toEpochMilli(),instant.toEpochMilli()))
        val settings=SettingsRepository(context).load().copy(smart=SmartSettings(weather=true))
        val panel=SmartPanel(context)
        panel.update(instant,settings,ThemeManager.get(0))
        for(width in listOf(1152,1728,3456)) {
            panel.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(280,View.MeasureSpec.AT_MOST))
            panel.layout(0,0,width,panel.measuredHeight)
            val reading=panel.findViewWithTag<android.widget.TextView>("weather_reading")
            val credit=panel.findViewWithTag<android.widget.TextView>("weather_credit")
            assertTrue(reading.width < width / 2)
            assertNotNull(reading.compoundDrawablesRelative[0])
            assertEquals("Open-Meteo",credit.text.toString())
            assertTrue(credit.textSize >= 10 * context.resources.displayMetrics.scaledDensity)
            assertEquals(1,(credit.text as android.text.Spanned).getSpans(0,credit.text.length,android.text.style.URLSpan::class.java).size)
        }
        panel.stop()
    }

}
