package com.ufuq.dream

import android.view.View
import android.widget.LinearLayout
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28,34],qualifiers="w960dp-h540dp-land-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ContextualSettingsTest {
    private val context get()=RuntimeEnvironment.getApplication()
    @Before fun reset(){context.getSharedPreferences("dream_settings",0).edit().clear().commit();DeviceTime.reset();FootballGate.reset()}
    private fun idle()=shadowOf(android.os.Looper.getMainLooper()).idle()
    private fun find(a:SettingsActivity,key:String)=a.window.decorView.findViewWithTag<View>(key)
    private fun click(a:SettingsActivity,key:String){find(a,key).performClick();idle()}
    private fun choose(a:SettingsActivity,key:String,index:Int){click(a,key);ShadowDialog.getLatestDialog().window!!.decorView.findViewWithTag<View>("choice_$index").performClick();idle()}
    private fun section(a:SettingsActivity,index:Int){find(a,"section_$index").requestFocus();idle()}
    private fun capture(a:SettingsActivity,name:String){
        if(android.os.Build.VERSION.SDK_INT!=28)return
        val v=a.window.decorView
        v.measure(View.MeasureSpec.makeMeasureSpec(1920,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(1080,View.MeasureSpec.EXACTLY));v.layout(0,0,1920,1080)
        val bitmap=android.graphics.Bitmap.createBitmap(1920,1080,android.graphics.Bitmap.Config.ARGB_8888)
        for(key in listOf("prayers","prayer_source","prayer_city","font_ar","font_en")){
            val value=find(a,key)?.findViewWithTag<android.widget.TextView>("setting_value")?:continue
            assertTrue(value.width<=120*context.resources.displayMetrics.density+1)
            assertTrue(value.layout.height<=value.height)
        }
        v.draw(android.graphics.Canvas(bitmap));val file=java.io.File("artwork/extras-0.10/context-$name.png");file.parentFile!!.mkdirs();file.outputStream().use{bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
    }
    @Test fun onlyActiveLanguageFontIsVisibleAndIndependentChoicesSurviveSwitchAndCancel(){
        val repo=SettingsRepository(context);val initial=repo.load()
        val ctl=Robolectric.buildActivity(SettingsActivity::class.java).setup();val a=ctl.get()
        assertNotNull(find(a,"font_ar"));assertNull(find(a,"font_en"))
        choose(a,"font_ar",1);choose(a,"language",1)
        assertNull(find(a,"font_ar"));assertNotNull(find(a,"font_en"));capture(a,"english");choose(a,"font_en",1)
        choose(a,"language",0)
        assertEquals("cairo",a.draft.current.fontArabic);assertEquals("lato",a.draft.current.fontEnglish)
        assertNotNull(find(a,"font_ar"));assertNull(find(a,"font_en"));assertEquals(initial,repo.load())
        click(a,"cancel");assertEquals(initial,a.draft.current);assertNull(find(a,"font_en"))
        ctl.pause().stop().destroy()
    }
    @Test fun prayerAndWeatherDependenciesAreIndependentAndHiddenValuesAreRetained(){
        for(language in listOf("ar","en")){
            val repo=SettingsRepository(context)
            val original=repo.load().copy(language=language,latinDigits=language=="en",smart=SmartSettings(prayers=false,prayerCity="jeddah",prayerMethod=1,hanafiAsr=true,weather=false,weatherCity="abha",fahrenheit=true,weatherRefreshMinutes=180))
            repo.save(original)
            val ctl=Robolectric.buildActivity(SettingsActivity::class.java).setup();val a=ctl.get();section(a,3)
            val prayer=listOf("prayer_source","prayer_city","prayer_method","asr");val weather=listOf("weather_city","weather_units","weather_refresh")
            prayer.plus(weather).forEach{assertNull(find(a,it))}
            click(a,"prayers");prayer.forEach{assertNotNull(find(a,it))};weather.forEach{assertNull(find(a,it))}
            assertTrue(find(a,"prayers").hasFocus());capture(a,"prayer-$language")
            assertTrue((find(a,"prayer_city").layoutParams as LinearLayout.LayoutParams).marginStart>0)
            click(a,"weather");weather.forEach{assertNotNull(find(a,it))}
            assertNull(WeatherConsent.policy(a.draft.current.smart,original.smart))
            assertFalse(PrayerApiConsent.allowed(a.draft.current.smart,original.smart))
            click(a,"prayers");prayer.forEach{assertNull(find(a,it))};weather.forEach{assertNotNull(find(a,it))}
            click(a,"weather");weather.forEach{assertNull(find(a,it))}
            assertEquals(original.smart,a.draft.current.smart);assertEquals(original,repo.load())
            click(a,"prayers");click(a,"weather");click(a,"save")
            assertTrue(repo.load().smart.prayers);assertTrue(repo.load().smart.weather)
            section(a,3);click(a,"prayers");click(a,"weather");click(a,"save")
            assertEquals(original.smart,SettingsRepository(context).load().smart)
            ctl.pause().stop().destroy()
        }
    }
    @Test fun fixedAndRotationControlsAreExclusiveAndLegacyOffRemainsFixedUntilExplicitChange(){
        val prefs=context.getSharedPreferences("dream_settings",0)
        val repo=SettingsRepository(context);repo.save(repo.load().copy(imageMode=0,rotationMinutes=0))
        val ctl=Robolectric.buildActivity(SettingsActivity::class.java).setup();val a=ctl.get()
        assertNotNull(find(a,"image"));assertNull(find(a,"interval"));assertNull(find(a,"video_library"))
        assertEquals(0,prefs.getInt("rotation_minutes",-1));assertFalse(a.draft.dirty)
        choose(a,"image_mode",0)
        assertNull(find(a,"image"));assertNotNull(find(a,"interval"));assertEquals(3,a.draft.current.rotationMinutes)
        click(a,"interval")
        val dialog=ShadowDialog.getLatestDialog().window!!.decorView
        assertNotNull(dialog.findViewWithTag<View>("choice_2"));assertNull(dialog.findViewWithTag<View>("choice_3"))
        ShadowDialog.getLatestDialog().onBackPressed();idle()
        choose(a,"image_mode",1);assertNotNull(find(a,"image"));assertNull(find(a,"interval"))
        click(a,"cancel");assertEquals(0,a.draft.current.rotationMinutes);assertEquals(0,prefs.getInt("rotation_minutes",-1))
        choose(a,"image_mode",0);click(a,"save");assertEquals(3,repo.load().rotationMinutes)
        ctl.pause().stop().destroy()
    }
    @Test fun greetingOnlyShowsItsRelevantEditorAndPreservesSavedText(){
        val repo=SettingsRepository(context);repo.save(repo.load().copy(greetingMode=2,greetingText="يا هلا Welcome"))
        val initial=repo.load();val ctl=Robolectric.buildActivity(SettingsActivity::class.java).setup();val a=ctl.get();section(a,2)
        assertNotNull(find(a,"custom"));assertNull(find(a,"preset"))
        choose(a,"greeting",1);assertNotNull(find(a,"preset"));assertNull(find(a,"custom"))
        choose(a,"greeting",3);assertNull(find(a,"preset"));assertNull(find(a,"custom"));assertEquals(initial.greetingText,a.draft.current.greetingText)
        click(a,"cancel");section(a,2);assertNotNull(find(a,"custom"));assertEquals(initial,repo.load())
        ctl.pause().stop().destroy()
    }
}
