package com.ufuq.dream

import android.view.View
import android.view.ViewGroup
import android.widget.TextView
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
import java.time.Instant
import java.util.Date

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28,34],qualifiers="w960dp-h540dp-land-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LanguageTest {
    private val context get()=RuntimeEnvironment.getApplication()
    private val prefs get()=context.getSharedPreferences("dream_settings",0)
    @Before fun reset(){prefs.edit().clear().commit();com.ufuq.dream.smart.DeviceTime.reset()}
    private fun chooseEnglish(activity:SettingsActivity){
        activity.window.decorView.findViewWithTag<View>("language").performClick()
        ShadowDialog.getLatestDialog().findViewById<View>(android.R.id.content).findViewWithTag<View>("choice_1").performClick()
    }
    @Test fun languageStagesRevertsSavesAndRestartsWithMatchingDigits(){
        val repo=SettingsRepository(context)
        val ctl=Robolectric.buildActivity(SettingsActivity::class.java).setup();val a=ctl.get()
        chooseEnglish(a)
        assertEquals("en",a.draft.current.language);assertTrue(a.draft.current.latinDigits)
        assertEquals("ar",repo.load().language)
        assertNull(a.window.decorView.findViewWithTag<View>("digits"))
        a.window.decorView.findViewWithTag<View>("cancel").performClick()
        assertEquals("ar",a.draft.current.language);assertFalse(a.draft.current.latinDigits)
        chooseEnglish(a);a.window.decorView.findViewWithTag<View>("save").performClick()
        assertEquals("en",repo.load().language);assertTrue(repo.load().latinDigits)
        ctl.pause().stop().destroy()
        val restarted=Robolectric.buildActivity(SettingsActivity::class.java).setup()
        assertEquals("en",restarted.get().draft.current.language)
        restarted.pause().stop().destroy()
    }
    @Test fun languageControlsDateNamesPeriodNumeralsAndNotCustomText(){
        val repo=SettingsRepository(context);val base=repo.load()
        val now=Date.from(Instant.parse("2026-11-03T09:12:00Z"))
        val en=base.copy(language="en",latinDigits=true,hour24=false)
        val english=DateProvider().labels(now,en)
        assertTrue(english.time.any{it in '0'..'9'});assertFalse((english.time+english.gregorian+english.hijri).any{it in '\u0621'..'\u064a'})
        assertTrue(english.hijri.contains("AH"));assertTrue(english.period in listOf("AM","PM"))
        val arabic=DateProvider().labels(now,base.copy(language="ar",latinDigits=false))
        assertTrue(arabic.time.any{it in '٠'..'٩'});assertTrue(arabic.hijri.contains("هـ"))
        val mixed="حياك الله Welcome 123"
        assertEquals(mixed,GreetingProvider.label(now,en.copy(greetingMode=2,greetingText=mixed)))
        assertFalse(GreetingProvider.label(now,en.copy(greetingMode=0)).any{it in '\u0621'..'\u064a'})
    }
    @Test fun oldSettingsRemainArabicAndOtherPreferencesStayIntact(){
        prefs.edit().putInt("scene_choice",2).putBoolean("latin_digits",true).putString("greeting_text","My greeting").putInt("clock_position",8).commit()
        val repo=SettingsRepository(context);val before=repo.load()
        assertEquals("ar",before.language);assertFalse(before.latinDigits)
        repo.save(before.copy(language="en"))
        val after=repo.load()
        assertTrue(after.latinDigits);assertEquals(5,after.clockPosition);assertEquals("My greeting",after.greetingText);assertEquals(2,after.sceneChoice)
        repo.save(after.copy(language="ar",latinDigits=true))
        assertFalse(repo.load().latinDigits)
    }
    @Test fun englishSettingsAreTranslatedAndPickerDeadEndIsHidden(){
        val repo=SettingsRepository(context);repo.save(repo.load().copy(language="en"))
        val ctl=Robolectric.buildActivity(SettingsActivity::class.java).setup();val a=ctl.get()
        fun texts(v:View):List<String> = when(v){
            is TextView->listOf(v.text.toString())
            is ViewGroup->(0 until v.childCount).flatMap{texts(v.getChildAt(it))}
            else->emptyList()
        }
        for(section in 0..3){
            a.window.decorView.findViewWithTag<View>("section_$section").requestFocus()
            shadowOf(android.os.Looper.getMainLooper()).idle()
            val settingsText=texts(a.window.decorView).filter{it.isNotBlank()}
            assertFalse(settingsText.joinToString("\n"),settingsText.any{line->line.any{it in '\u0621'..'\u064a'}})
            assertNull(a.window.decorView.findViewWithTag<View>("digits"))
        }
        assertNull(a.window.decorView.findViewWithTag<View>("import_video"))
        ctl.pause().stop().destroy()
    }
    @Test fun bilingualStudioScreenshotsUseRealSharedRenderer(){
        for(language in listOf("ar","en")){
            val repo=SettingsRepository(context);repo.save(repo.load().copy(language=language))
            val ctl=Robolectric.buildActivity(SettingsActivity::class.java).setup();val a=ctl.get()
            val root=a.window.decorView
            root.measure(View.MeasureSpec.makeMeasureSpec(1920,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(1080,View.MeasureSpec.EXACTLY));root.layout(0,0,1920,1080)
            repeat(50){Thread.sleep(5);shadowOf(android.os.Looper.getMainLooper()).idle()}
            val image=android.graphics.Bitmap.createBitmap(1920,1080,android.graphics.Bitmap.Config.ARGB_8888)
            root.draw(android.graphics.Canvas(image))
            val file=java.io.File("artwork/language/studio-$language-api${android.os.Build.VERSION.SDK_INT}.png");file.parentFile!!.mkdirs();file.outputStream().use{image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};image.recycle()
            assertEquals(AppLanguage.direction(language),(a.window.decorView.findViewWithTag<View>("section_0").parent.parent.parent.parent as View).layoutDirection)
            ctl.pause().stop().destroy()
        }
    }
    @Test fun stagedLanguageSurvivesRecreationWithoutBecomingSaved(){
        val ctl=Robolectric.buildActivity(SettingsActivity::class.java).setup()
        chooseEnglish(ctl.get());ctl.recreate()
        assertEquals("en",ctl.get().draft.current.language)
        assertEquals("ar",SettingsRepository(context).load().language)
        assertTrue(ctl.get().draft.dirty)
        ctl.pause().stop().destroy()
    }
    @Test fun weatherAndPrayerNamesUseSelectedLanguageWithoutNetwork(){
        val now=Instant.parse("2026-11-02T17:00:00Z")
        val city=com.ufuq.dream.smart.SaudiCities.find("riyadh")
        val raw=javaClass.getResourceAsStream("/smart/aladhan-range.json")!!.bufferedReader().use{it.readText()}
        com.ufuq.dream.smart.PrayerCalendarCache(context).write(com.ufuq.dream.smart.PrayerRequest(now.atZone(city.zoneId).toLocalDate(),city,0,false),raw,now.toEpochMilli())
        com.ufuq.dream.smart.WeatherCache(context).write(com.ufuq.dream.smart.WeatherReading("riyadh",30.5,1,false,now.toEpochMilli(),now.toEpochMilli()))
        val settings=SettingsRepository(context).load().copy(language="en",latinDigits=true,smart=com.ufuq.dream.smart.SmartSettings(prayers=true,weather=true))
        val panel=com.ufuq.dream.smart.SmartPanel(context)
        panel.update(now,settings,ThemeManager.get(0))
        fun texts(v:View):String=if(v is TextView)v.text.toString() else if(v is ViewGroup)(0 until v.childCount).joinToString(" "){texts(v.getChildAt(it))} else ""
        val text=texts(panel)
        assertTrue(text.contains("Riyadh"));assertTrue(text.contains("Fajr"));assertTrue(text.contains("Partly cloudy"))
        assertFalse(text,text.any{it in '\u0621'..'\u064a'})
        panel.stop()
    }

}
