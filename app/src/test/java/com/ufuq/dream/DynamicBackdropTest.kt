package com.ufuq.dream

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.widget.TextView
import com.ufuq.dream.smart.*
import com.ufuq.dream.reflection.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28,34],qualifiers="w960dp-h540dp-land-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DynamicBackdropTest {
    private val context get()=RuntimeEnvironment.getApplication()
    private val now=Instant.parse("2026-11-02T17:00:00Z")
    @Before fun reset(){
        listOf("dream_settings","smart_prayer_calendar","smart_weather_cache","smart_football_cache").forEach{context.getSharedPreferences(it,0).edit().clear().commit()}
        DeviceTime.reset();FootballGate.reset()
        PrayerCalendarCache(context).write(PrayerRequest(LocalDate.of(2026,11,2),SaudiCities.find("riyadh"),0,false),javaClass.getResourceAsStream("/smart/aladhan-range.json")!!.bufferedReader().use{it.readText()},now.toEpochMilli())
        WeatherCache(context).write(WeatherReading("riyadh",31.6,1,false,now.toEpochMilli(),now.toEpochMilli()))
        FootballCache(context).write("136013",javaClass.getResourceAsStream("/football/captured-hilal.json")!!.bufferedReader().use{it.readText()},now.toEpochMilli())
    }
    private fun layout(display:AmbientDisplay,w:Int=1920){
        display.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(w*9/16,View.MeasureSpec.EXACTLY));display.layout(0,0,w,w*9/16)
    }
    @Test fun everyEnabledCombinationHasOnlyItsOwnContentBackdrop(){
        val base=SettingsRepository(context).load()
        for(language in listOf("ar","en"))for(theme in 0..2){
            var s=base.copy(language=language,latinDigits=language=="en",theme=theme,sceneChoice=theme,imageMode=1)
            val display=AmbientDisplay(context,{s},640,sceneTransitions=false);display.testNow=java.util.Date.from(now)
            for(mask in 0..15){
                val prayer=mask and 1!=0;val weather=mask and 2!=0;val reflection=mask and 4!=0;val football=mask and 8!=0
                s=s.copy(smart=SmartSettings(prayers=prayer,weather=weather,reflectionMode=if(reflection)1 else 0,football=football,footballTeam="136013"));display.refresh();layout(display)
                val footer=display.smartPanel;val editorial=footer.supplements
                val bottom=(footer.background as ContentBackdrop).contentBounds()
                val side=(editorial.background as ContentBackdrop).contentBounds()
                assertEquals("Footer mask=$mask",!prayer,bottom.isEmpty)
                assertEquals("Editorial mask=$mask",!reflection&&!football&&!weather,side.isEmpty)
                if(!bottom.isEmpty){assertTrue(bottom.left>=0&&bottom.right<=footer.width);assertTrue(bottom.top>=0&&bottom.bottom<=footer.height)}
                if(!side.isEmpty){assertTrue(side.left>=0&&side.right<=editorial.width);assertTrue(side.top>=0&&side.bottom<=editorial.height)}
                if(football&&!reflection&&!weather)assertTrue("Fixture keeps empty tall card",side.height()<editorial.height/2)
                if(weather&&!prayer)assertTrue("Weather must not reserve a footer",bottom.isEmpty)
                if(android.os.Build.VERSION.SDK_INT==28&&language=="ar"&&theme==1&&mask in listOf(0,2,3,4,8,15)){
                    if(reflection){
                        val entry=if(mask==15)com.ufuq.dream.reflection.ContentRepository.entries(context).first{it.sourceUrl=="https://tanzil.net/#16:90"} else DailyReflection.entries(context).first{it.source.contains("الشرح")}
                        display.findViewWithTag<ReflectionView>("reflection_block").render(entry,language,ThemeManager.get(theme),1f)
                    }
                    repeat(80){if(display.backgroundRenderer.loadedAsset.isEmpty()){Thread.sleep(5);org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()}}
                    layout(display)
                    val bitmap=Bitmap.createBitmap(1920,1080,Bitmap.Config.ARGB_8888);display.draw(Canvas(bitmap))
                    val file=java.io.File("artwork/extras-0.10/backdrop-$mask.png");file.parentFile!!.mkdirs();file.outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
                }
            }
            display.stop()
        }
    }
    @Test fun longVerseAndShortQuoteResizeTheirBackdropWithoutChangingClockGeometry(){
        val s=SettingsRepository(context).load().copy(smart=SmartSettings(reflectionMode=1))
        val display=AmbientDisplay(context,{s},640,sceneTransitions=false);display.testNow=java.util.Date.from(now);display.refresh()
        val group=display.findViewWithTag<ReflectionView>("reflection_block")
        val entries=DailyReflection.entries(context);val short=entries.first{it.source.contains("الشرح")};val long=entries.first{it.source.contains("٢٨٦")}
        group.render(short,"ar",ThemeManager.get(1),1f);layout(display)
        val shortBounds=(display.smartPanel.supplements.background as ContentBackdrop).contentBounds();val clock=android.graphics.RectF(display.visualContentBounds)
        group.render(long,"ar",ThemeManager.get(1),1f);layout(display)
        val longBounds=(display.smartPanel.supplements.background as ContentBackdrop).contentBounds()
        assertTrue(longBounds.height()>shortBounds.height())
        assertEquals(clock,display.visualContentBounds)
        val text=display.findViewWithTag<TextView>("reflection_original");assertEquals(long.text,text.text.toString());assertTrue(text.layout.height<=text.height)
        display.stop()
    }
    @Test fun emptyFooterReallyPaintsNothingAndFixtureBackdropHasTransparentOuterSpace(){
        var s=SettingsRepository(context).load().copy(smart=SmartSettings(football=true,footballTeam="136013"))
        val display=AmbientDisplay(context,{s},640,sceneTransitions=false);display.testNow=java.util.Date.from(now);display.refresh();layout(display)
        val footer=display.smartPanel;val side=footer.supplements
        val bitmap=Bitmap.createBitmap(1920,1080,Bitmap.Config.ARGB_8888);val canvas=Canvas(bitmap)
        footer.background.draw(canvas)
        assertEquals(0,bitmap.getPixel(1920/2,100))
        val card=display.findViewWithTag<android.widget.LinearLayout>("information_card")
        val cardBackground=card.background as ContentBackdrop
        cardBackground.draw(canvas)
        val rect=cardBackground.contentBounds()
        assertTrue(rect.top>=0 && rect.bottom<=card.height)
        assertTrue(bitmap.getPixel(rect.centerX(),rect.centerY()) ushr 24>0)
        s=s.copy(smart=SmartSettings());display.refresh();layout(display)
        bitmap.eraseColor(0);cardBackground.draw(canvas);footer.background.draw(canvas)
        assertEquals(0,bitmap.getPixel(rect.centerX(),rect.centerY()))
        bitmap.recycle();display.stop()
    }
    @Test @Config(qualifiers="w960dp-h540dp-land-tvdpi")
    fun native720pAndFontMetricsRemainBounded(){
        for(font in UfuqFonts.arabic){
            val s=SettingsRepository(context).load().copy(fontArabic=font.id,smart=SmartSettings(prayers=true,weather=true,reflectionMode=1,football=true,footballTeam="136013"))
            val display=AmbientDisplay(context,{s},640,sceneTransitions=false);display.testNow=java.util.Date.from(now);display.refresh();layout(display,1280)
            val footer=(display.smartPanel.background as ContentBackdrop).contentBounds()
            val side=(display.smartPanel.supplements.background as ContentBackdrop).contentBounds()
            assertFalse(footer.isEmpty);assertFalse(side.isEmpty)
            assertTrue(footer.bottom<=display.smartPanel.height);assertTrue(side.bottom<=display.smartPanel.supplements.height)
            display.stop()
        }
    }
}
