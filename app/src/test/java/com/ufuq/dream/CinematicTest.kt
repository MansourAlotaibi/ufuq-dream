package com.ufuq.dream

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import android.os.Looper
import android.view.View
import android.widget.EditText
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog
import java.io.File
import java.time.Instant
import java.util.Date
import java.util.TimeZone
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],qualifiers="w960dp-h540dp-land-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CinematicTest {
 @get:org.junit.Rule val saudiTimeZone = SaudiTimeZoneRule()
    private val context get() = RuntimeEnvironment.getApplication()
    private fun idle() = shadowOf(Looper.getMainLooper()).idle()
    private fun fresh(): SettingsRepository {
        context.getSharedPreferences("dream_settings",0).edit().clear().commit()
        return SettingsRepository(context)
    }
    private fun waitForImage(display: AmbientDisplay) {
        repeat(100) { if(display.backgroundRenderer.loadedAsset.isNotEmpty())return;Thread.sleep(10);idle() }
        fail("Asset failed to load")
    }
    private fun capture(view: View, name: String) {
        view.measure(View.MeasureSpec.makeMeasureSpec(1920,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(1080,View.MeasureSpec.EXACTLY))
        view.layout(0,0,1920,1080)
        val bitmap=Bitmap.createBitmap(1920,1080,Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        val file=File("../artwork/0.8.0/screenshots/$name.png");file.parentFile?.mkdirs()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) };bitmap.recycle()
    }
    private fun choose(activity: SettingsActivity, key:String,index:Int) {
        activity.window.decorView.findViewWithTag<View>(key).performClick()
        ShadowDialog.getLatestDialog().findViewById<View>(android.R.id.content).findViewWithTag<View>("choice_$index").performClick();idle()
    }
    private fun section(activity:SettingsActivity,index:Int) { activity.window.decorView.findViewWithTag<View>("section_$index").requestFocus();idle() }

    @Test fun newInstallDefaultsToAutomaticAndMigrationIsIdempotent() {
        val repo=fresh();assertEquals(-1,repo.load().sceneChoice)
        SettingsRepository(context);assertEquals(-1,repo.load().sceneChoice)
        assertEquals(1,repo.load().size)
    }
    @Test fun legacyFixedSceneAndHiddenChoicesArePreserved() {
        val prefs=context.getSharedPreferences("dream_settings",0)
        prefs.edit().clear().putInt("schema",4).putInt("theme",2).putInt("mode",3).putInt("size",0)
            .putInt("intensity",0).putBoolean("greeting",false).putBoolean("hijri_first",true).commit()
        val s=SettingsRepository(context).load()
        assertEquals(2,s.sceneChoice);assertEquals(1,s.size);assertEquals(3,s.greetingMode);assertTrue(s.hijriFirst)
        assertEquals(3,prefs.getInt("mode",-1));assertEquals(0,prefs.getInt("size",-1));assertEquals(0,prefs.getInt("intensity",-1))
        assertEquals(0,prefs.getInt("legacy_size",-1))
        SettingsRepository(context);assertEquals(2,SettingsRepository(context).load().sceneChoice)
    }
    @Test fun savedSceneAndGreetingSurviveRepositoryRestart() {
        val repo=fresh();val value=repo.load().copy(sceneChoice=1,hour24=false,size=2,greetingMode=2,greetingText="  حياك الله Saudi  ")
        repo.save(value)
        val saved=SettingsRepository(context).load()
        assertEquals(1,saved.sceneChoice);assertEquals("حياك الله Saudi",saved.greetingText);assertFalse(saved.hour24)
        val draft=SettingsDraft(saved);draft.update(saved.copy(sceneChoice=0));draft.revert();assertEquals(saved,draft.current)
    }
    @Test fun rotationCountsActiveTimeOnlyAndWraps() {
        val clock=SceneRotation();clock.resume(1000)
        assertEquals(0,clock.scene(900999));assertEquals(1,clock.scene(901000))
        clock.pause(901000);assertEquals(1,clock.scene(9000000))
        clock.resume(9000000);assertEquals(1,clock.scene(9899999));assertEquals(2,clock.scene(9900000))
        assertEquals(0,clock.scene(10800000));assertEquals(SceneRotation.PERIOD,clock.remaining(10800000))
    }
    @Test fun greetingSanitizationLimitAndCalendar() {
        assertEquals("يا هلا Saudi",GreetingText.clean("  يا هلا\n Saudi  "))
        assertEquals("Safe",GreetingText.clean("\u202eSafe\u2069"))
        val emoji=GreetingText.clean("😀".repeat(50));assertEquals(40,emoji.codePointCount(0,emoji.length));assertFalse(emoji.endsWith("\uD83D"))
        val repo=fresh();val now=Date.from(Instant.parse("2026-11-01T18:41:12Z"))
        val previous=TimeZone.getDefault();TimeZone.setDefault(TimeZone.getTimeZone("Asia/Riyadh"))
        try {
            val s=repo.load().copy(hour24=false)
            val labels=DateProvider().labels(now,s)
            assertEquals("٠٩:٤١",labels.time);assertEquals("م",labels.period)
            assertTrue(labels.gregorian.contains("٢٠٢٦"));assertTrue(labels.hijri.contains("١٤٤٨"))
            assertEquals("٢١:٤١",DateProvider().labels(now,s.copy(hour24=true)).time)
            assertEquals("",GreetingProvider.label(now,s.copy(greetingMode=3)))
            assertEquals("صباح الخير",GreetingProvider.label(now,s.copy(greetingMode=1,greetingPreset=2)))
            assertEquals("حياك الله Saudi",GreetingProvider.label(now,s.copy(greetingMode=2,greetingText="حياك الله Saudi")))
        } finally { TimeZone.setDefault(previous) }
    }
    @Test fun eachSceneRendersAndReleasesItsImage() {
        val base=fresh().load()
        for(scene in 0..2) {
            val display=AmbientDisplay(context,{base.copy(sceneChoice=scene,size=if(scene==2)2 else 1,greetingMode=1,greetingPreset=scene)})
            display.testNow=Date.from(Instant.parse("2026-11-01T18:41:00Z"))
            display.start();idle();waitForImage(display)
            assertEquals(BackgroundRepository.first(scene).fileName,display.backgroundRenderer.loadedAsset)
            assertTrue(display.backgroundRenderer.imageBytes<=1920*1080*4)
            capture(display,"scene-$scene")
            assertTrue(display.contentBounds().height()>0)
            display.stop();assertFalse(display.isRunning);assertEquals(0,display.backgroundRenderer.imageBytes)
        }
    }
    @Test fun largestClockAndFortyCharacterGreetingFitSafeZone() {
        val base=fresh().load()
        for(scene in 0..2) {
            val display=AmbientDisplay(context,{base.copy(sceneChoice=scene,size=2,greetingMode=2,greetingText="مرحباً بكم في بيتنا Welcome Home Saudi 1234")})
            display.start();idle();waitForImage(display)
            capture(display,"large-text-$scene")
            val group=display.getChildAt(3) as android.view.ViewGroup
            assertTrue("scene $scene content fits height",group.getChildAt(0).height<=group.height)
            display.stop()
        }
    }
    @Test fun automaticPhotosRotateAtThreeMinutesAndThemeStaysIndependent() {
        var value=fresh().load()
        val display=AmbientDisplay(context,{value})
        display.start();idle();waitForImage(display);assertEquals(0,display.settingsSnapshot.photoCategory)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMinutes(3).plusSeconds(2));waitForImage(display)
        assertEquals(1,display.settingsSnapshot.photoCategory)
        display.stop();val stopped=display.settingsSnapshot.photoCategory
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMinutes(30));assertEquals(stopped,display.settingsSnapshot.photoCategory)
        value=value.copy(sceneChoice=2);display.start();idle();waitForImage(display)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMinutes(16));assertEquals(2,display.settingsSnapshot.photoCategory)
        display.stop()
    }
    @Test fun studioUsesSharedLayoutAndBoundedImageMemory() {
        val draft=SettingsDraft(fresh().load())
        val studio=PreviewStudio(context){draft.current}
        studio.measure(View.MeasureSpec.makeMeasureSpec(1000,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(700,View.MeasureSpec.AT_MOST));studio.layout(0,0,1000,562)
        assertEquals(562,studio.measuredHeight);assertFalse(studio.isFocusable)
        studio.start();idle();waitForImage(studio.display)
        assertTrue(studio.display.backgroundRenderer.imageBytes<=640*360*4)
        listOf(draft.current.copy(size=2),draft.current.copy(hour24=false),draft.current.copy(gregorian=false),draft.current.copy(hijri=false),draft.current.copy(greetingMode=2,greetingText="يا هلا Saudi")).forEach {
            draft.update(it);studio.refresh();assertEquals(it.hour24,studio.display.settingsSnapshot.hour24);assertEquals(it.greetingText,studio.display.settingsSnapshot.greetingText)
        }
        studio.stop();assertEquals(0,studio.display.backgroundRenderer.imageBytes)
    }
    @Test fun consumerStudioStagesSavesCancelsAndRestoresDraft() {
        val repo=fresh()
        val controller=Robolectric.buildActivity(SettingsActivity::class.java).setup()
        val a=controller.get();val decor=a.window.decorView
        capture(decor,"studio-automatic")
        assertNotNull(decor.findViewWithTag<View>("section_3"));assertNull(decor.findViewWithTag<View>("section_4"));assertNull(decor.findViewWithTag<View>("compare"))
        choose(a,"scene",3);assertEquals(2,a.draft.current.sceneChoice);assertEquals(-1,repo.load().sceneChoice)
        section(a,1);choose(a,"size",1);choose(a,"time",0)
        decor.findViewWithTag<View>("gregorian").performClick();idle();decor.findViewWithTag<View>("hijri").performClick();idle()
        section(a,2);choose(a,"greeting",1);choose(a,"preset",1)
        capture(decor,"studio-greeting")
        assertEquals(1,a.draft.current.greetingPreset);assertFalse(a.draft.current.hour24);assertTrue(a.draft.dirty)
        decor.findViewWithTag<View>("cancel").performClick();idle();assertEquals(repo.load(),a.draft.current)
        assertEquals(View.GONE,decor.findViewWithTag<View>("save").parent.let{it as View}.visibility)
        section(a,0);choose(a,"scene",1)
        decor.findViewWithTag<View>("save").performClick();idle();assertEquals(0,SettingsRepository(context).load().sceneChoice)
        choose(a,"scene",2)
        val bundle=Bundle();controller.saveInstanceState(bundle).pause().stop().destroy()
        val restored=Robolectric.buildActivity(SettingsActivity::class.java).create(bundle).start().resume().visible()
        assertEquals(1,restored.get().draft.current.sceneChoice);assertTrue(restored.get().draft.dirty)
        restored.pause().stop().destroy();assertEquals(0,SettingsRepository(context).load().sceneChoice)
    }
    @Test fun customEditorUpdatesPreviewAndCancelsWithoutSaving() {
        val repo=fresh();val controller=Robolectric.buildActivity(SettingsActivity::class.java).setup();val a=controller.get()
        section(a,2);choose(a,"greeting",2)
        var dialog=ShadowDialog.getLatestDialog()
        var editor=dialog.findViewById<View>(android.R.id.content).findViewWithTag<EditText>("greeting_editor")
        editor.setText("  يا هلا Saudi  ");idle();assertEquals("يا هلا Saudi",a.draft.current.greetingText)
        assertEquals("",repo.load().greetingText)
        dialog.cancel();idle();assertEquals(0,a.draft.current.greetingMode)
        choose(a,"greeting",2);dialog=ShadowDialog.getLatestDialog()
        editor=dialog.findViewById<View>(android.R.id.content).findViewWithTag("greeting_editor")
        editor.setText("😀".repeat(45));assertEquals(40,editor.text.toString().codePointCount(0,editor.text.length))
        editor.setText("حياك الله Saudi")
        dialog.findViewById<View>(android.R.id.content).findViewWithTag<View>("custom_done").performClick();idle()
        assertEquals(2,a.draft.current.greetingMode)
        capture(a.window.decorView,"studio-custom")
        a.window.decorView.findViewWithTag<View>("save").performClick();idle();assertEquals("حياك الله Saudi",SettingsRepository(context).load().greetingText)
        controller.pause().stop().destroy()
    }
    @Test fun discardExitDoesNotSilentlyPersistAndServiceStopIsSafe() {
        val repo=fresh();val controller=Robolectric.buildActivity(SettingsActivity::class.java).setup();val a=controller.get()
        choose(a,"scene",3)
        a.window.decorView.findViewWithTag<View>("section_0").requestFocus();a.onBackPressed()
        ShadowDialog.getLatestDialog().findViewById<View>(android.R.id.content).findViewWithTag<View>("choice_1").performClick()
        assertTrue(a.isFinishing);assertEquals(-1,repo.load().sceneChoice)
        controller.pause().stop().destroy()
        val service=Robolectric.buildService(UfuqDreamService::class.java).create().get();service.onDreamingStopped();service.onDestroy()
    }
}
