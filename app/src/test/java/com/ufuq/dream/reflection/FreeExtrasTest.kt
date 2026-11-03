package com.ufuq.dream.reflection

import android.view.View
import android.widget.TextView
import com.ufuq.dream.*
import com.ufuq.dream.smart.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28,34],qualifiers="w960dp-h540dp-land-xhdpi")
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
class FreeExtrasTest {
    private val context get()=RuntimeEnvironment.getApplication()
    @Before fun reset(){context.getSharedPreferences("dream_settings",0).edit().clear().commit();DeviceTime.reset()}
    @Test fun newOptionsDefaultOffAndExplicitSnapshotsPersist(){
        val repo=SettingsRepository(context);val baseline=repo.load()
        assertFalse(baseline.smart.adhkar);assertEquals(0,baseline.smart.occasion)
        val draft=baseline.copy(smart=baseline.smart.copy(reflectionMode=4,occasion=2))
        assertEquals(baseline,repo.load())
        repo.save(draft);assertEquals(draft,SettingsRepository(context).load())
        repo.save(baseline);assertEquals(baseline,repo.load())
        context.getSharedPreferences("dream_settings",0).edit().putInt("smart_occasion",99).commit()
        assertEquals(0,repo.load().smart.occasion)
    }
    @Test fun studioStagesBothOptionsAndCancelRevertsWithoutWrites(){
        val repo=SettingsRepository(context);val saved=repo.load()
        val controller=org.robolectric.Robolectric.buildActivity(SettingsActivity::class.java).setup()
        val activity=controller.get()
        fun section(){activity.window.decorView.findViewWithTag<View>("section_3").requestFocus();org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()}
        section();activity.window.decorView.findViewWithTag<View>("reflection").performClick()
        org.robolectric.shadows.ShadowDialog.getLatestDialog().findViewById<View>(android.R.id.content).findViewWithTag<View>("choice_4").performClick()
        activity.window.decorView.findViewWithTag<View>("occasion").performClick()
        org.robolectric.shadows.ShadowDialog.getLatestDialog().findViewById<View>(android.R.id.content).findViewWithTag<View>("choice_1").performClick()
        assertEquals(4,activity.draft.current.smart.reflectionChoice);assertEquals(6,activity.draft.current.smart.occasion)
        assertEquals(saved,repo.load())
        activity.window.decorView.findViewWithTag<View>("cancel").performClick()
        assertEquals(saved,activity.draft.current);assertEquals(saved,repo.load())
        section();activity.window.decorView.findViewWithTag<View>("reflection").performClick()
        org.robolectric.shadows.ShadowDialog.getLatestDialog().findViewById<View>(android.R.id.content).findViewWithTag<View>("choice_4").performClick()
        activity.window.decorView.findViewWithTag<View>("save").performClick()
        assertEquals(4,SettingsRepository(context).load().smart.reflectionChoice)
        controller.pause().stop().destroy()
    }
    @Test fun remembranceKeepsVerbatimArabicReferencesAndHasDeterministicRotation(){
        val now=Instant.parse("2026-11-03T06:00:00Z");val zone=ZoneId.of("Asia/Riyadh")
        assertEquals(2,Adhkar.all.size)
        assertTrue(Adhkar.all.all{it.kind=="dhikr" && it.source.contains("صحيح") && it.sourceUrl.startsWith("https://")})
        assertEquals(Adhkar.entry(now,"ar",zone).text,Adhkar.entry(now,"en",zone).text)
        assertNotEquals(Adhkar.entry(now,"ar",zone).text,Adhkar.entry(now.plusSeconds(600),"ar",zone).text)
        assertTrue(Adhkar.entry(now,"ar",zone).source.startsWith("صحيح"))
        assertNotEquals(Adhkar.entry(now,"ar",zone).text,Adhkar.entry(now.plusSeconds(43200),"ar",zone).text)
    }
    @Test fun nationalDayUsesCalendarDaysIncludesTodayAndRollsForward(){
        val zone=ZoneId.of("Asia/Riyadh")
        assertEquals(1L,Occasions.next(Instant.parse("2026-09-22T10:00:00Z"),4,zone)!!.days)
        assertEquals(0L,Occasions.next(Instant.parse("2026-09-23T10:00:00Z"),4,zone)!!.days)
        assertEquals(2027,Occasions.next(Instant.parse("2026-09-24T10:00:00Z"),4,zone)!!.date.year)
        assertNull(Occasions.next(Instant.now(),0,zone));assertNull(Occasions.next(Instant.EPOCH,1,zone))
    }
    @Test fun hijriCountdownIsMarkedEstimatedAndHandlesTargetAndYearBoundaries(){
        for(zone in listOf(ZoneId.of("Asia/Riyadh"),ZoneId.of("Pacific/Auckland")))for(choice in 1..3){
            val result=Occasions.next(Instant.parse("2026-12-31T18:00:00Z"),choice,zone)!!
            assertTrue(result.estimated);assertTrue(result.days in 0..355)
            val atTarget=result.date.atTime(12,0).atZone(zone).toInstant()
            assertEquals(0L,Occasions.next(atTarget,choice,zone)!!.days)
            assertTrue(Occasions.next(atTarget.plusSeconds(86400),choice,zone)!!.days>300)
        }
        assertTrue(Occasions.label(Instant.now(),1,"en").endsWith("estimated"))
    }
    @Test fun legacyFootballCacheAndNewLeagueSurviveRoundtripWithoutExtraNetwork(){
        val now=System.currentTimeMillis();val at=Instant.ofEpochMilli(now+86400000).toString()
        val row=JSONObject().put("idHomeTeam","136013").put("idAwayTeam","136022").put("strHomeTeam","A").put("strAwayTeam","B").put("strTimestamp",at).put("strLeague","Saudi Pro League")
        val raw=JSONObject().put("events",org.json.JSONArray().put(row)).toString()
        val cache=FootballCache(context);cache.write("136013",raw,now)
        assertEquals("Saudi Pro League",cache.read("136013",now)!!.fixtures.single().competition)
        val prefs=context.getSharedPreferences("smart_football_cache",0)
        val old=JSONObject(prefs.getString("136013","")!!);old.getJSONArray("fixtures").getJSONObject(0).remove("competition")
        prefs.edit().putString("136013",old.toString()).commit()
        assertEquals("",cache.read("136013",now)!!.fixtures.single().competition)
        assertThrows(IllegalArgumentException::class.java){FootballParsing.validate(FootballFixture("136013","136022","A","B",Instant.parse(at),"bad\nleague"),"136013")}
    }
    @Test fun newWidgetsAreVisibleOnlyWhenEnabledAndNeverStartNetworkFromUnsavedPreview(){
        val panel=SmartPanel(context);val baseline=SettingsRepository(context).load();val now=Instant.now()
        panel.update(now,baseline.copy(smart=SmartSettings(adhkar=true,occasion=4)),ThemeManager.get(0))
        assertEquals(View.VISIBLE,panel.supplements.visibility)
        assertEquals(View.VISIBLE,panel.supplements.findViewWithTag<View>("reflection_block").visibility)
        assertNull(panel.supplements.findViewWithTag<View>("occasion_widget"))
        val scene=ClockSceneRenderer(context,0);scene.update(java.util.Date.from(now),baseline.copy(smart=SmartSettings(occasion=4)),ThemeManager.get(0))
        assertEquals(View.VISIBLE,scene.view.findViewWithTag<View>("occasion_widget").visibility)
        panel.update(now,baseline,ThemeManager.get(0))
        assertEquals(View.GONE,panel.supplements.visibility)
        panel.stop()
    }
    @Test fun devotionalTextAndCitationFitWithoutEllipsis(){
        for(entry in Adhkar.all)for(language in listOf("ar","en")) {
            val view=ReflectionView(context);view.render(entry,language,ThemeManager.get(1),1f)
            val density=context.resources.displayMetrics.density
            val height=(ReflectionView.HEIGHT_DP*density).toInt()
            view.measure(View.MeasureSpec.makeMeasureSpec((440*density).toInt(),View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));view.layout(0,0,view.measuredWidth,height)
            val body=view.findViewWithTag<TextView>("reflection_original")
            assertEquals(entry.text,body.text.toString());assertTrue(body.layout.height<=body.height)
            assertEquals(entry.text.length,body.layout.getLineEnd(body.layout.lineCount-1))
            val ref=view.findViewWithTag<TextView>("reflection_reference")
            assertTrue(ref.layout.height<=ref.height)
        }
    }
}
