package com.ufuq.dream

import android.view.KeyEvent
import android.view.View
import android.widget.TextView
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.Instant
import com.ufuq.dream.smart.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28,34],qualifiers="w960dp-h540dp-land-xhdpi")
class StudioPolishTest {
    private val context get()=RuntimeEnvironment.getApplication()
    @Before fun reset(){context.getSharedPreferences("dream_settings",0).edit().clear().commit();DeviceTime.reset()}
    @Test fun eitherHorizontalArrowReturnsToTabInBothLanguagesWithoutSaving(){
        for(language in listOf("ar","en")){
            val repo=SettingsRepository(context);repo.save(repo.load().copy(language=language))
            val before=repo.load()
            val ctl=Robolectric.buildActivity(SettingsActivity::class.java).setup();val a=ctl.get()
            for(code in listOf(KeyEvent.KEYCODE_DPAD_LEFT,KeyEvent.KEYCODE_DPAD_RIGHT)){
                val row=a.window.decorView.findViewWithTag<View>("theme")
                assertTrue(row.requestFocus())
                assertTrue(row.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN,code)))
                assertTrue(a.window.decorView.findViewWithTag<View>("section_0").hasFocus())
                assertEquals(before,repo.load())
            }
            ctl.pause().stop().destroy()
        }
    }
    @Test fun currentAccentNextNeutralAndCountdownSecondaryResetWhenUnavailable(){
        val now=Instant.parse("2026-11-02T12:00:00Z")
        val city=SaudiCities.find("riyadh")
        val raw=javaClass.getResourceAsStream("/smart/aladhan-range.json")!!.bufferedReader().use{it.readText()}
        PrayerCalendarCache(context).write(PrayerRequest(now.atZone(city.zoneId).toLocalDate(),city,0,false),raw,now.toEpochMilli())
        val settings=SettingsRepository(context).load().copy(smart=SmartSettings(prayers=true,prayerSource=0))
        val panel=SmartPanel(context)
        for(themeIndex in 0..2){
            val theme=ThemeManager.get(themeIndex)
            panel.update(now,settings.copy(theme=themeIndex),theme)
            assertEquals(theme.accent,panel.findViewWithTag<TextView>("prayer_2").currentTextColor)
            assertEquals(theme.ink,panel.findViewWithTag<TextView>("prayer_3").currentTextColor)
            val heading=panel.findViewWithTag<TextView>("prayer_header").text as android.text.Spanned
            val span=heading.getSpans(0,heading.length,android.text.style.ForegroundColorSpan::class.java).single()
            assertEquals(theme.muted,span.foregroundColor)
            assertEquals(heading.length,heading.getSpanEnd(span))
            val duration=heading.subSequence(heading.getSpanStart(span),heading.getSpanEnd(span)).toString()
            assertFalse(duration.contains("المغرب"));assertTrue(duration.contains("س"))
            assertEquals(theme.ink,panel.findViewWithTag<TextView>("prayer_1").currentTextColor)
            assertTrue(panel.findViewWithTag<TextView>("prayer_2").contentDescription.contains("الحالية"))
        }
        val invalid=Instant.parse("2010-01-01T00:00:00Z");val theme=ThemeManager.get(0)
        panel.update(invalid,settings,theme)
        for(index in 0..4){val label=panel.findViewWithTag<TextView>("prayer_$index");assertEquals(theme.ink,label.currentTextColor);assertEquals("—",label.text.toString())}
        panel.stop()
    }
}
