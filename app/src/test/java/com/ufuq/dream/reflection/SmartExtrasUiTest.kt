package com.ufuq.dream.reflection

import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.graphics.Bitmap
import android.graphics.Canvas
import com.ufuq.dream.*
import com.ufuq.dream.smart.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog
import java.time.Instant
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28,34],qualifiers="w960dp-h540dp-land-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SmartExtrasUiTest {
    private val context get()=RuntimeEnvironment.getApplication()
    @Before fun reset(){
        listOf("dream_settings","smart_prayer_calendar","smart_weather_cache","smart_football_cache").forEach{context.getSharedPreferences(it,0).edit().clear().commit()}
        DeviceTime.reset();FootballGate.reset()
    }
    private fun clickChoice(index:Int){ShadowDialog.getLatestDialog().findViewById<View>(android.R.id.content).findViewWithTag<View>("choice_$index").performClick()}
    @Test fun optionalPreferencesMigrateOffAndStudioChoicesAreStaged(){
        val repo=SettingsRepository(context);val before=repo.load()
        assertEquals(0,before.smart.reflectionMode);assertFalse(before.smart.football)
        val ctl=Robolectric.buildActivity(SettingsActivity::class.java).setup();val a=ctl.get()
        a.window.decorView.findViewWithTag<View>("section_3").requestFocus();shadowOf(android.os.Looper.getMainLooper()).idle()
        a.window.decorView.findViewWithTag<View>("reflection").performClick();clickChoice(3)
        a.window.decorView.findViewWithTag<View>("football_team").performClick();clickChoice(1);clickChoice(0)
        assertEquals(3,a.draft.current.smart.reflectionMode);assertTrue(a.draft.current.smart.football);assertEquals("136013",a.draft.current.smart.footballTeam)
        assertEquals(before,repo.load())
        a.window.decorView.findViewWithTag<View>("cancel").performClick();assertEquals(before,a.draft.current)
        a.window.decorView.findViewWithTag<View>("section_3").requestFocus();shadowOf(android.os.Looper.getMainLooper()).idle()
        a.window.decorView.findViewWithTag<View>("reflection").performClick();clickChoice(1)
        a.window.decorView.findViewWithTag<View>("save").performClick();assertEquals(1,repo.load().smart.reflectionMode)
        assertEquals(before.smart.prayerCity,repo.load().smart.prayerCity);assertEquals(before.smart.weatherCity,repo.load().smart.weatherCity)
        ctl.pause().stop().destroy()
    }
    @Test fun everyCompleteSourceTextAndReferenceFitsWithoutEllipsis(){
        for(language in listOf("ar","en"))for(entry in DailyReflection.entries(context)){
            val view=ReflectionView(context);view.render(entry,language,ThemeManager.get(1),1f)
            view.measure(View.MeasureSpec.makeMeasureSpec(1180,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(ReflectionView.HEIGHT_DP*2,View.MeasureSpec.EXACTLY));view.layout(0,0,1180,ReflectionView.HEIGHT_DP*2)
            val body=view.findViewWithTag<TextView>("reflection_original");val ref=view.findViewWithTag<TextView>("reflection_reference")
            assertEquals(entry.text,body.text.toString());assertTrue(body.layout.lineCount>0)
            assertEquals(entry.text.length,body.layout.getLineEnd(body.layout.lineCount-1))
            assertEquals(0,body.layout.getEllipsisCount(body.layout.lineCount-1))
            assertTrue("${entry.source}: ${body.layout.height} > ${body.height}",body.layout.height<=body.height)
            assertTrue("Reference clips: ${ref.text}",ref.layout.height<=ref.height)
            assertFalse(body.isFocusable);assertFalse(ref.isFocusable)
        }
    }
    @Test fun sharedRowBudgetAvoidsSummingTwoFullHeightPanels(){
        val base=SettingsRepository(context).load()
        val both=base.copy(smart=SmartSettings(prayers=true,weather=true,reflectionMode=3,football=true,footballTeam="136013"))
        val zone=SmartLayout.zones(1920,1080,2f,both)
        assertTrue(zone.panel.height()<=1080*.26f)
        assertTrue(zone.clockHeight>1080*.65f)
        assertTrue(zone.panel.top>zone.clockHeight)
        assertEquals(5,WidgetRegistry.enabled(both).size)
        assertEquals(1,WidgetRegistry.enabled(base).size)
    }
    @Test fun allWidgetsRemainInsideSafeAreaInBothLanguagesAndAllThemes(){
        val now=Instant.parse("2026-11-02T17:00:00Z")
        val raw=javaClass.getResourceAsStream("/smart/aladhan-range.json")!!.bufferedReader().use{it.readText()}
        val city=SaudiCities.find("riyadh")
        PrayerCalendarCache(context).write(PrayerRequest(LocalDate.of(2026,11,2),city,0,false),raw,now.toEpochMilli())
        WeatherCache(context).write(WeatherReading("riyadh",31.6,1,false,now.toEpochMilli(),now.toEpochMilli()))
        // Dated actual provider reference, not a production live-data fallback.
        val fixture=javaClass.getResourceAsStream("/football/captured-hilal.json")!!.bufferedReader().use{it.readText()}
        FootballCache(context).write("136013",fixture,now.toEpochMilli())
        for(language in listOf("ar","en"))for(theme in 0..2){
            var value=SettingsRepository(context).load().copy(language=language,latinDigits=language=="en",theme=theme,sceneChoice=theme,imageMode=1,smart=SmartSettings(prayers=true,weather=true,reflectionMode=3,football=true,footballTeam="136013"))
            val display=AmbientDisplay(context,{value},640,sceneTransitions=false);display.testNow=java.util.Date.from(now)
            display.start();shadowOf(android.os.Looper.getMainLooper()).idle()
            repeat(80){if(display.backgroundRenderer.loadedAsset.isEmpty()){Thread.sleep(5);shadowOf(android.os.Looper.getMainLooper()).idle()}}
            for(position in (-1..8).toList()){
                value=value.copy(clockPosition=position);display.refresh()
                display.measure(View.MeasureSpec.makeMeasureSpec(1920,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(1080,View.MeasureSpec.EXACTLY));display.layout(0,0,1920,1080)
                val editorial=display.smartPanel.supplements
                val clock=display.visualContentBounds
                assertFalse("Clock and editorial overlap",android.graphics.RectF.intersects(clock,android.graphics.RectF(editorial.left.toFloat(),editorial.top.toFloat(),editorial.right.toFloat(),editorial.bottom.toFloat())))
                assertTrue("$language theme=$theme position=$position width=${clock.width()} height=${clock.height()} place=${display.findViewWithTag<TextView>("place_label").text}",clock.width()>1920*.28f)
                assertTrue(display.visualContentBounds.bottom<SmartLayout.zones(1920,1080,2f,value).panel.top)
                val panel=display.smartPanel
                for(index in 0 until panel.childCount){val child=panel.getChildAt(index);if(child.visibility==View.VISIBLE)assertTrue("Panel child clips",child.bottom<=panel.height)}
                val body=display.findViewWithTag<TextView>("reflection_original");assertTrue(body.text.isNotBlank());assertTrue(body.layout.height<=body.height)
                for(entry in DailyReflection.entries(context)) {
                    panel.findViewWithTag<com.ufuq.dream.reflection.ReflectionView>("reflection_block")?.let { error("Editorial must not remain inside footer") }
                    display.findViewWithTag<ReflectionView>("reflection_block").render(entry,language,ThemeManager.get(theme),1f)
                    display.requestLayout()
                    display.measure(View.MeasureSpec.makeMeasureSpec(1920,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(1080,View.MeasureSpec.EXACTLY));display.layout(0,0,1920,1080)
                    assertEquals(entry.text,body.text.toString())
                    assertTrue("${entry.source} clips in editorial column",body.layout.height<=body.height)
                }
                val football=display.findViewWithTag<TextView>("football_widget");assertTrue(football.text.contains("×"));assertTrue(football.layout.height<=football.height)
            }
            value=value.copy(clockPosition=-1);display.refresh()
            display.measure(View.MeasureSpec.makeMeasureSpec(1920,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(1080,View.MeasureSpec.EXACTLY));display.layout(0,0,1920,1080)
            if(android.os.Build.VERSION.SDK_INT==28){
                val bitmap=Bitmap.createBitmap(1920,1080,Bitmap.Config.ARGB_8888);display.draw(Canvas(bitmap))
                val file=java.io.File("artwork/extras-0.10/theme-$theme-$language.png");file.parentFile!!.mkdirs();file.outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
            }
            display.stop();assertFalse(display.smartPanel.footballSession.running);assertEquals(0,display.backgroundRenderer.imageBytes)
        }
    }
    @Test
    @Config(qualifiers="w960dp-h540dp-land-tvdpi")
    fun editorialAndLargeClockFit720pAcrossAllManualPositions(){
        for(position in -1..8) {
            val settings=SettingsRepository(context).load().copy(clockPosition=position,smart=SmartSettings(prayers=true,weather=true,reflectionMode=3,football=true,footballTeam="136013"))
            val display=AmbientDisplay(context,{settings},640,sceneTransitions=false)
            display.refresh()
            for(entry in DailyReflection.entries(context)) {
                display.findViewWithTag<ReflectionView>("reflection_block").render(entry,"ar",ThemeManager.get(1),1f)
                display.measure(View.MeasureSpec.makeMeasureSpec(1280,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(720,View.MeasureSpec.EXACTLY));display.layout(0,0,1280,720)
                val body=display.findViewWithTag<TextView>("reflection_original")
                assertEquals(entry.text,body.text.toString())
                assertTrue("${entry.source}: ${body.layout.height} > ${body.height}",body.layout.height<=body.height)
                val column=display.smartPanel.supplements
                assertFalse(android.graphics.RectF.intersects(display.visualContentBounds,android.graphics.RectF(column.left.toFloat(),column.top.toFloat(),column.right.toFloat(),column.bottom.toFloat())))
                assertTrue(display.visualContentBounds.width()>1280*.28f)
            }
            display.stop()
        }
    }
    @Test
    @Config(qualifiers="w960dp-h540dp-land-tvdpi")
    fun completeTextFits720pAndReferenceIsNeverCut(){
        val density=context.resources.displayMetrics.density
        for(entry in DailyReflection.entries(context)){
            val view=ReflectionView(context);view.render(entry,"ar",ThemeManager.get(1),1f)
            val width=(590*density).toInt();val height=(ReflectionView.HEIGHT_DP*density).toInt()
            view.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));view.layout(0,0,width,height)
            val body=view.findViewWithTag<TextView>("reflection_original")
            assertEquals(entry.text,body.text.toString());assertTrue("${entry.source}: layout ${body.layout.height} > body ${body.height}",body.layout.height<=body.height)
            assertEquals(entry.text.length,body.layout.getLineEnd(body.layout.lineCount-1))
        }
    }

}
