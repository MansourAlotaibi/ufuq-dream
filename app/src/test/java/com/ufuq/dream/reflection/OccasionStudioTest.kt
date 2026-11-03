package com.ufuq.dream.reflection

import android.view.View
import android.widget.EditText
import android.widget.TextView
import com.ufuq.dream.*
import com.ufuq.dream.smart.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28,34],qualifiers="w960dp-h540dp-land-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class OccasionStudioTest {
    private val context get()=RuntimeEnvironment.getApplication()
    @Before fun reset(){context.getSharedPreferences("dream_settings",0).edit().clear().commit();DeviceTime.reset()}
    @Test fun legacyTwoTogglesMergeWithoutLosingSelectedTypesOrWritingDuringRead(){
        val prefs=context.getSharedPreferences("dream_settings",0)
        for(mode in 0..3){
            prefs.edit().putInt("smart_reflection_mode",mode).putBoolean("smart_adhkar",true).commit()
            val snapshot=SmartSettings.read(prefs)
            assertEquals(listOf(4,5,6,7)[mode],snapshot.reflectionChoice);assertFalse(snapshot.adhkar)
            assertTrue(prefs.getBoolean("smart_adhkar",false))
            snapshot.write(prefs.edit()); // Writes are intentionally not applied until save.
            assertTrue(prefs.getBoolean("smart_adhkar",false))
        }
    }
    @Test fun allCombinedModesRotateOnlyTheirChosenTypes(){
        val base=Instant.parse("2026-11-03T00:00:00Z")
        for((mode,kinds) in mapOf(4 to setOf("dhikr"),5 to setOf("quran","dhikr"),6 to setOf("hadith","dhikr"),7 to setOf("quran","hadith","dhikr"))){
            val seen=(0..8).map{ReflectionSelection.entry(context,base.plusSeconds(it*300L),SmartSettings(reflectionMode=mode))!!.kind}.toSet()
            assertEquals(kinds,seen)
        }
        assertNull(ReflectionSelection.entry(context,base,SmartSettings()))
    }
    @Test fun nearestIncludesFoundingDayRollsAtMidnightAndDoesNotSelectPastEvents(){
        val zone=ZoneId.of("Asia/Riyadh");val now=Instant.parse("2026-02-22T09:00:00Z")
        val foundation=Occasions.next(now,5,zone)!!
        assertEquals(LocalDate.of(2026,2,22),foundation.date);assertEquals(0L,foundation.days);assertFalse(foundation.estimated)
        assertEquals(365L,Occasions.next(now.plusSeconds(86400),5,zone)!!.days+1)
        for(date in listOf("2026-01-01T12:00:00Z","2026-09-22T12:00:00Z","2026-12-31T12:00:00Z")){
            val instant=Instant.parse(date);val candidates=(1..5).map{Occasions.next(instant,it,zone)!!}
            assertEquals(candidates.minOf{it.days},Occasions.next(instant,6,zone)!!.days)
        }
    }
    @Test fun customDatesValidateArabicDigitsLeapDaysAndExpiredEvents(){
        assertEquals(LocalDate.of(2028,2,29),Occasions.customDate("٢٠٢٨-٠٢-٢٩"))
        assertNull(Occasions.customDate("2027-02-29"));assertNull(Occasions.customDate("2027-13-01"));assertNull(Occasions.customDate("garbage"))
        assertEquals("",Occasions.label(Instant.now(),7,"ar","", "2027-01-01"))
        assertTrue(Occasions.label(Instant.parse("2026-11-03T12:00:00Z"),7,"en","My day","2020-01-01").contains("ended"))
    }
    @Test fun editorCancelValidationAndSaveNeverPersistSilently(){
        val repo=SettingsRepository(context);val saved=repo.load()
        val controller=Robolectric.buildActivity(SettingsActivity::class.java).setup();val a=controller.get()
        a.window.decorView.findViewWithTag<View>("section_3").requestFocus();Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        fun editor(){
            a.window.decorView.findViewWithTag<View>("occasion").performClick()
            ShadowDialog.getLatestDialog().findViewById<View>(android.R.id.content).findViewWithTag<View>("choice_2").performClick()
        }
        editor();var dialog=ShadowDialog.getLatestDialog()
        dialog.findViewById<View>(android.R.id.content).findViewWithTag<EditText>("occasion_name_editor").setText("موعدي")
        dialog.cancel();assertEquals(saved,a.draft.current);assertEquals(saved,repo.load())
        editor();dialog=ShadowDialog.getLatestDialog()
        dialog.findViewById<View>(android.R.id.content).findViewWithTag<EditText>("occasion_name_editor").setText("موعدي")
        dialog.findViewById<View>(android.R.id.content).findViewWithTag<EditText>("occasion_date_editor").setText("2027-02-30")
        dialog.findViewById<View>(android.R.id.content).findViewWithTag<View>("occasion_done").performClick();assertTrue(dialog.isShowing)
        dialog.findViewById<View>(android.R.id.content).findViewWithTag<EditText>("occasion_date_editor").setText("٢٠٢٧-٠٢-٢٢")
        dialog.findViewById<View>(android.R.id.content).findViewWithTag<View>("occasion_done").performClick()
        assertEquals(7,a.draft.current.smart.occasion);assertEquals("2027-02-22",a.draft.current.smart.occasionDate)
        assertEquals(saved,repo.load())
        a.window.decorView.findViewWithTag<View>("save").performClick()
        assertEquals("موعدي",repo.load().smart.occasionName)
        controller.pause().stop().destroy()
    }
    @Test fun occasionHasIndependentClockLocationAndNeverLeaksIntoFootballCard(){
        val baseline=SettingsRepository(context).load()
        for(language in listOf("ar","en"))for(theme in 0..2){
            val settings=baseline.copy(language=language,latinDigits=language=="en",smart=SmartSettings(reflectionMode=7,occasion=6,football=true,footballTeam="136013"))
            val scene=ClockSceneRenderer(context,0);scene.update(java.util.Date.from(Instant.parse("2026-11-03T12:00:00Z")),settings,ThemeManager.get(theme))
            val label=scene.view.findViewWithTag<TextView>("occasion_widget")
            assertEquals(View.VISIBLE,label.visibility);assertTrue(label.text.isNotBlank())
            val panel=SmartPanel(context);panel.update(Instant.parse("2026-11-03T12:00:00Z"),settings,ThemeManager.get(theme))
            assertNull(panel.supplements.findViewWithTag<View>("occasion_widget"))
            assertFalse(panel.supplements.findViewWithTag<TextView>("football_widget").text.contains("تقديري"))
            panel.stop();scene.dispose()
        }
    }
    @Test fun occasionFits1080p(){occasionFits(1920,1080)}
    @Test @Config(qualifiers="w960dp-h540dp-land-tvdpi") fun occasionFits720p(){occasionFits(1280,720)}
    private fun occasionFits(width:Int,height:Int){
        val now=Instant.parse("2026-11-03T12:00:00Z")
        for(language in listOf("ar","en"))for(theme in 0..2){
            var value=SettingsRepository(context).load().copy(language=language,latinDigits=language=="en",theme=theme,size=2,
                smart=SmartSettings(reflectionMode=7,occasion=7,occasionName=if(language=="en")"A special day with family" else "موعدنا الجميل مع العائلة",occasionDate="2027-02-22"))
            val display=AmbientDisplay(context,{value},640,sceneTransitions=false);display.testNow=java.util.Date.from(now)
            display.refresh()
            fun layout(){display.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));display.layout(0,0,width,height)}
            for(font in UfuqFonts.choices(language)){
                value=value.copy(fontArabic=if(language=="ar")font.id else "default",fontEnglish=if(language=="en")font.id else "default")
                val enabled=value
                value=value.copy(smart=value.smart.copy(occasion=0));display.refresh();layout()
                val originalClockWidth=display.visualContentBounds.width()
                value=enabled;display.refresh();layout()
                val label=display.findViewWithTag<TextView>("occasion_widget")
                assertEquals(View.VISIBLE,label.visibility)
                assertTrue("Occasion clips $language $theme ${font.id}",label.layout.height<=label.height)
                assertTrue(display.visualContentBounds.left>=0 && display.visualContentBounds.right<=width)
                assertTrue(display.visualContentBounds.top>=0 && display.visualContentBounds.bottom<=height)
                assertTrue("Clock shrank $width $language $theme ${font.id}: ${display.visualContentBounds.width()} < $originalClockWidth",display.visualContentBounds.width()>=originalClockWidth*.95f)
                if(width==1920 && language=="ar" && theme in 0..2 && font.id=="default" && android.os.Build.VERSION.SDK_INT==28){
                    repeat(50){if(display.backgroundRenderer.loadedAsset.isEmpty()){Thread.sleep(10);Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()}}
                    layout()
                    val bitmap=android.graphics.Bitmap.createBitmap(width,height,android.graphics.Bitmap.Config.ARGB_8888)
                    display.draw(android.graphics.Canvas(bitmap))
                    val file=java.io.File("artwork/occasion-0.11.0-dev2/theme-$theme.png");file.parentFile!!.mkdirs()
                    file.outputStream().use{bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
                }
            }
            display.stop()
        }
    }

}
