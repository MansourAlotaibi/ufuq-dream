package com.ufuq.dream

import android.view.View
import android.widget.TextView
import android.graphics.Typeface
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
import com.ufuq.dream.smart.*
import java.time.Instant
import java.time.LocalDate
import java.util.TimeZone
import java.security.MessageDigest

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28,34],qualifiers="w960dp-h540dp-land-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TypographyAndTimeTest {
    private val context get()=RuntimeEnvironment.getApplication()
    @Before fun clear(){
        listOf("dream_settings","smart_prayer_calendar","smart_weather_cache","smart_football_cache").forEach{context.getSharedPreferences(it,0).edit().clear().commit()}
        DeviceTime.reset();FootballGate.reset()
    }
    @Test fun oldGridMigratesInMemoryOnlyAndPreservesOriginalOnExplicitSave(){
        val p=context.getSharedPreferences("dream_settings",0)
        for(old in -1..8){
            p.edit().putInt("clock_position",old).remove("legacy_clock_position").commit()
            val repo=SettingsRepository(context);val value=repo.load()
            assertEquals(when(old){0,3,6->3;2,5,8->5;else->-1},value.clockPosition)
            assertEquals(old,p.getInt("clock_position",-100))
            repo.save(value)
            assertEquals(value.clockPosition,p.getInt("clock_position",-100))
            if(old!=value.clockPosition)assertEquals(old,p.getInt("legacy_clock_position",-100))
        }
    }
    @Test fun fontSourcesAndLicensesMatchOriginalLauncherHashes(){
        val manifest=org.json.JSONObject(context.assets.open("font-provenance.json").bufferedReader().use{it.readText()}).getJSONArray("files")
        for(i in 0 until manifest.length()){
            val r=manifest.getJSONObject(i);val path=r.getString("file").substringAfter("/assets/")
            val bytes=context.assets.open(path).use{it.readBytes()}
            assertEquals(r.getString("sha256"),MessageDigest.getInstance("SHA-256").digest(bytes).joinToString(""){"%02x".format(it)})
        }
        for(lang in listOf("ar","en"))for(choice in UfuqFonts.choices(lang).drop(1))assertNotNull(UfuqFonts.face(context,choice.id,lang))
        assertTrue(UfuqFonts.cacheSize()<=3)
    }
    @Test fun customFontRestoresAndDoesNotChangeReligiousBodyOrClockFace(){
        val base=SettingsRepository(context).load();val v=TextView(context);val original=v.typeface
        UfuqFonts.apply(v,base.copy(fontArabic="cairo"));assertNotEquals(original,v.typeface)
        UfuqFonts.apply(v,base);assertEquals(original,v.typeface)
        for(tag in listOf("reflection_original","clock_digits")){
            val protected=TextView(context).apply{this.tag=tag}
            val face=protected.typeface
            UfuqFonts.apply(protected,base.copy(fontArabic="tajawal"));assertEquals(face,protected.typeface)
        }
    }
    @Test fun fontChoicesStageCancelSaveAndRemainIndependentAcrossLanguages(){
        val repo=SettingsRepository(context);val before=repo.load()
        val ctl=Robolectric.buildActivity(SettingsActivity::class.java).setup();val a=ctl.get()
        fun choose(tag:String,index:Int){a.window.decorView.findViewWithTag<View>(tag).performClick();ShadowDialog.getLatestDialog().window!!.decorView.findViewWithTag<View>("choice_$index").performClick();shadowOf(android.os.Looper.getMainLooper()).idle()}
        choose("font_ar",1);assertEquals("cairo",a.draft.current.fontArabic);assertEquals(before,repo.load())
        a.window.decorView.findViewWithTag<View>("cancel").performClick();assertEquals(before,a.draft.current)
        choose("font_ar",2);choose("language",1);choose("font_en",1)
        a.window.decorView.findViewWithTag<View>("save").performClick()
        assertEquals("tajawal",repo.load().fontArabic);assertEquals("lato",repo.load().fontEnglish)
        ctl.pause().stop().destroy()
        context.getSharedPreferences("dream_settings",0).edit().putString("font_ar_family","../bad").commit()
        assertEquals("default",repo.load().fontArabic);assertEquals("lato",repo.load().fontEnglish)
    }
    @Test fun everyBundledFontFitsSharedPreviewAndKeepsCompleteReligiousText(){
        val now=Instant.parse("2026-11-02T17:00:00Z");val city=SaudiCities.find("riyadh")
        PrayerCalendarCache(context).write(PrayerRequest(LocalDate.of(2026,11,2),city,0,false),javaClass.getResourceAsStream("/smart/aladhan-range.json")!!.bufferedReader().use{it.readText()},now.toEpochMilli())
        WeatherCache(context).write(WeatherReading("riyadh",31.6,1,false,now.toEpochMilli(),now.toEpochMilli()))
        FootballCache(context).write("136013",javaClass.getResourceAsStream("/football/captured-hilal.json")!!.bufferedReader().use{it.readText()},now.toEpochMilli())
        val original=com.ufuq.dream.reflection.DailyReflection.entries(context).first{it.source.contains("٢٨٦")}
        for(language in listOf("ar","en"))for(font in UfuqFonts.choices(language))for(side in listOf(-1,3,5)){
            val s=SettingsRepository(context).load().copy(language=language,latinDigits=language=="en",hour24=false,fontArabic=if(language=="ar")font.id else "default",fontEnglish=if(language=="en")font.id else "default",clockPosition=side,smart=SmartSettings(prayers=true,weather=true,reflectionMode=1,football=true,footballTeam="136013"))
            val display=AmbientDisplay(context,{s},640,sceneTransitions=false);display.testNow=java.util.Date.from(now);display.refresh()
            display.findViewWithTag<com.ufuq.dream.reflection.ReflectionView>("reflection_block").render(original,language,ThemeManager.get(s.theme),1f)
            display.measure(View.MeasureSpec.makeMeasureSpec(1920,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(1080,View.MeasureSpec.EXACTLY));display.layout(0,0,1920,1080)
            if(font.file!=null)assertEquals(UfuqFonts.face(context,font.id,language),display.findViewWithTag<TextView>("weather_reading").typeface)
            for(tag in listOf("prayer_0","prayer_1","prayer_2","prayer_3","prayer_4","weather_reading","reflection_original","reflection_reference","football_widget")){
                val text=display.findViewWithTag<TextView>(tag)
                assertTrue("${font.id} $tag layout ${text.layout.height} > view ${text.height}",text.layout.height<=text.height)
                for(line in 0 until text.layout.lineCount)assertTrue("${font.id} $tag line $line ${text.layout.getLineMax(line)} > ${text.width}, layout=${text.layout.width}",text.layout.getLineMax(line)<=text.width+1)
            }
            assertEquals(original.text,display.findViewWithTag<TextView>("reflection_original").text.toString())
            if(android.os.Build.VERSION.SDK_INT==28&&side==-1&&font.id in listOf("cairo","lato")) {
                repeat(80){if(display.backgroundRenderer.loadedAsset.isEmpty()){Thread.sleep(5);shadowOf(android.os.Looper.getMainLooper()).idle()}}
                display.measure(View.MeasureSpec.makeMeasureSpec(1920,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(1080,View.MeasureSpec.EXACTLY));display.layout(0,0,1920,1080)
                val bitmap=android.graphics.Bitmap.createBitmap(1920,1080,android.graphics.Bitmap.Config.ARGB_8888)
                display.draw(android.graphics.Canvas(bitmap));val file=java.io.File("artwork/extras-0.10/font-$language-${font.id}.png");file.parentFile!!.mkdirs();file.outputStream().use{bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
            }
            display.stop()
        }
    }
    @Test
    @Config(qualifiers="w960dp-h540dp-land-tvdpi")
    fun fontsAlsoFit720p(){
        // The 1080p test exercises all faces and data; this verifies native font metrics at TV density.
        val now=Instant.parse("2026-11-02T17:00:00Z")
        for(language in listOf("ar","en"))for(font in UfuqFonts.choices(language)) {
            val s=SettingsRepository(context).load().copy(language=language,latinDigits=language=="en",fontArabic=if(language=="ar")font.id else "default",fontEnglish=if(language=="en")font.id else "default",smart=SmartSettings(prayers=true,weather=true,reflectionMode=1,football=true,footballTeam="136013"))
            val display=AmbientDisplay(context,{s},640,sceneTransitions=false);display.testNow=java.util.Date.from(now);display.refresh()
            val entry=com.ufuq.dream.reflection.DailyReflection.entries(context).first{it.source.contains("٢٨٦")}
            display.findViewWithTag<com.ufuq.dream.reflection.ReflectionView>("reflection_block").render(entry,language,ThemeManager.get(s.theme),1f)
            display.measure(View.MeasureSpec.makeMeasureSpec(1280,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(720,View.MeasureSpec.EXACTLY));display.layout(0,0,1280,720)
            for(tag in listOf("weather_reading","reflection_original","reflection_reference","football_widget")){
                val text=display.findViewWithTag<TextView>(tag)
                assertTrue("720p ${font.id} $tag: ${text.layout.height} > ${text.height}",text.layout.height<=text.height)
            }
            assertTrue(display.visualContentBounds.width()>1280*.28f);display.stop()
        }
    }
    @Test fun deviceTimezoneChangeUpdatesIcuClockAndBothDatesWithoutGlobalIcuMutation(){
        val old=TimeZone.getDefault();val now=java.util.Date.from(Instant.parse("2026-11-02T22:30:00Z"))
        val s=SettingsRepository(context).load().copy(language="en",latinDigits=true,hour24=true)
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"));val utc=DateProvider().labels(now,s)
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Riyadh"));val riyadh=DateProvider().labels(now,s)
            assertEquals("22:30",utc.time);assertEquals("01:30",riyadh.time)
            assertTrue(utc.gregorian.startsWith("Monday"));assertTrue(riyadh.gregorian.startsWith("Tuesday"))
            assertNotEquals(utc.hijri,riyadh.hijri)
        } finally {TimeZone.setDefault(old)}
    }
    @Test fun twelveHourPolicyReachesClockEveryPrayerAndFixtureKickoff(){
        val old=TimeZone.getDefault();TimeZone.setDefault(TimeZone.getTimeZone("Asia/Riyadh"))
        try {
            val now=Instant.parse("2026-11-02T17:00:00Z");val city=SaudiCities.find("riyadh")
            val raw=javaClass.getResourceAsStream("/smart/aladhan-range.json")!!.bufferedReader().use{it.readText()}
            PrayerCalendarCache(context).write(PrayerRequest(LocalDate.of(2026,11,2),city,0,false),raw,now.toEpochMilli())
            FootballCache(context).write("136013",javaClass.getResourceAsStream("/football/captured-hilal.json")!!.bufferedReader().use{it.readText()},now.toEpochMilli())
            val initial=SettingsRepository(context).load()
            for(language in listOf("ar","en"))for(hour24 in listOf(true,false)) {
                val s=initial.copy(language=language,latinDigits=language=="en",hour24=hour24,smart=SmartSettings(prayers=true,football=true,footballTeam="136013"))
                val display=AmbientDisplay(context,{s},640,sceneTransitions=false);display.testNow=java.util.Date.from(now);display.refresh()
                val prayer=display.findViewWithTag<TextView>("prayer_2").text.toString()
                val match=display.findViewWithTag<TextView>("football_widget").text.toString()
                val clock=DateProvider().labels(java.util.Date.from(now),s)
                fun digits(value:String)=if(language=="en")value else value.map{if(it in '0'..'9')'٠'+(it-'0') else it}.joinToString("")
                assertTrue(prayer.contains(digits(if(hour24)"14:49" else "02:49")))
                assertTrue(match.contains(digits(if(hour24)"21:15" else "09:15")))
                assertEquals(digits(if(hour24)"20:00" else "08:00"),clock.time)
                if(!hour24){assertTrue(prayer.contains(if(language=="en")"PM" else "م"));assertTrue(match.contains(if(language=="en")"PM" else "م"))}
                display.stop()
            }
        } finally {TimeZone.setDefault(old)}
    }
}
