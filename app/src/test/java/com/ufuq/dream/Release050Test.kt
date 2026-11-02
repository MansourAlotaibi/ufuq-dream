package com.ufuq.dream

import android.os.Looper
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Duration
import java.time.Instant
import java.util.Date
import java.util.TimeZone

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],qualifiers="w960dp-h540dp-land-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class Release050Test {
 private val context get()=RuntimeEnvironment.getApplication()
 private val prefs get()=context.getSharedPreferences("dream_settings",0)
 @Before fun clear(){prefs.edit().clear().commit()}
 @Test fun initialScenesPersistAndWrapAcrossRepositoryInstances(){
  assertEquals(listOf(0,1,2,0,1,2), (0..5).map{SettingsRepository(context).nextAutoStart()})
  assertEquals(0,SettingsRepository(context).peekAutoStart())
  val rotation=SceneRotation(2);rotation.resume(100)
  assertEquals(2,rotation.scene(100));assertEquals(0,rotation.scene(900100))
 }
 @Test fun livePreviewDoesNotConsumeSessionCursor(){
  val repo=SettingsRepository(context);repo.nextAutoStart()
  val activity=Robolectric.buildActivity(SettingsActivity::class.java).setup()
  shadowOf(Looper.getMainLooper()).idle()
  assertEquals(1,repo.peekAutoStart())
  activity.pause().stop().destroy()
  assertEquals(1,repo.peekAutoStart())
 }
 @Test fun arabicAndLatinDigitsOnApi28RetainArabicCalendarNames(){
  val previous=TimeZone.getDefault();TimeZone.setDefault(TimeZone.getTimeZone("Asia/Riyadh"))
  try{
   val now=Date.from(Instant.parse("2026-11-01T18:41:12Z"));val s=SettingsRepository(context).load()
   val latin=DateProvider().labels(now,s.copy(hour24=true,latinDigits=true))
   assertEquals("21:41",latin.time);assertTrue(latin.gregorian.contains("2026"));assertTrue(latin.hijri.contains("1448"))
   assertFalse((latin.time+latin.gregorian+latin.hijri).any{it in '٠'..'٩'})
   assertTrue(latin.hijri.contains("هـ"));assertTrue(latin.gregorian.any{it in '\u0621'..'\u064a'})
   val arabic=DateProvider().labels(now,s.copy(hour24=false,latinDigits=false))
   assertEquals("٠٩:٤١",arabic.time);assertEquals("م",arabic.period)
   assertTrue(arabic.hijri.contains("١٤٤٨"))
  }finally{TimeZone.setDefault(previous)}
 }
 @Test fun optionalNightModeDoesNotChangeExistingPreferences(){
  prefs.edit().putInt("schema",5).putInt("scene_choice",2).putBoolean("hour24",false).commit()
  val repo=SettingsRepository(context);val saved=repo.load()
  assertFalse(saved.nightDim);assertFalse(saved.latinDigits)
  val draft=SettingsDraft(saved);draft.update(saved.copy(nightDim=true,latinDigits=true))
  assertFalse(repo.load().nightDim);draft.revert();assertEquals(saved,draft.current)
  draft.update(saved.copy(nightDim=true,latinDigits=true));repo.save(draft.current)
  assertTrue(SettingsRepository(context).load().nightDim);assertTrue(repo.load().latinDigits)
 }
 @Test fun nightRampAndOffAreDeterministic(){
  val previous=TimeZone.getDefault();TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
  try{
   fun at(time:String)=Date.from(Instant.parse("2026-11-01T${time}:00Z"))
   assertEquals(0f,NightMode.level(at("22:00")),.001f)
   assertEquals(.5f,NightMode.level(at("22:45")),.001f)
   assertEquals(1f,NightMode.level(at("23:30")),.001f)
   assertEquals(.5f,NightMode.level(at("05:45")),.001f)
   assertEquals(0f,NightMode.level(at("06:30")),.001f)
   assertEquals(0f,NightMode.backgroundDim(at("23:30"),false),0f)
   assertEquals(1f,NightMode.textFactor(at("23:30"),false),0f)
  }finally{TimeZone.setDefault(previous)}
 }
 @Test fun startupAndRapidStopReleasePendingDecodeAndCallbacks(){
  val display=AmbientDisplay(context,imageLimit=640)
  display.start(fadeIn=true)
  assertEquals(0f,display.alpha,0f)
  shadowOf(Looper.getMainLooper()).idle()
  display.stop();display.stop()
  shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2))
  assertFalse(display.isRunning);assertFalse(display.awaitingImage)
  assertEquals("",display.backgroundRenderer.loadedAsset)
  assertNull(display.backgroundRenderer.onReady)
  display.start();assertEquals(1f,display.alpha,0f);display.stop()
 }
 @Test fun serviceTeardownBeforeWindowAttachIsIdempotent(){
  val service=Robolectric.buildService(UfuqDreamService::class.java).create().get()
  service.onDreamingStopped();service.onDreamingStopped();service.onDestroy()
  assertEquals(0,SettingsRepository(context).peekAutoStart())
 }
 @Test fun actualDreamSessionsConsumeOnceAndFixedScenesNeverConsume(){
  val host=Robolectric.buildActivity(android.app.Activity::class.java).setup()
  val controller=Robolectric.buildService(UfuqDreamService::class.java).create()
  val service=controller.get()
  val window=android.service.dreams.DreamService::class.java.getDeclaredField("mWindow")
  window.isAccessible=true;window.set(service,host.get().window)
  service.onAttachedToWindow()
  assertEquals(0,SettingsRepository(context).peekAutoStart())
  service.onDreamingStarted();service.onDreamingStarted()
  assertEquals(1,SettingsRepository(context).peekAutoStart())
  service.onDreamingStopped();service.onDreamingStarted()
  assertEquals(2,SettingsRepository(context).peekAutoStart())
  service.onDreamingStopped()
  val repo=SettingsRepository(context);repo.save(repo.load().copy(sceneChoice=1))
  service.onDreamingStarted();assertEquals(2,repo.peekAutoStart())
  service.onDreamingStopped()
  repo.save(repo.load().copy(sceneChoice=-1,imageMode=1))
  service.onDreamingStarted();assertEquals(2,repo.peekAutoStart());service.onDreamingStopped()
  repo.save(repo.load().copy(imageMode=0,rotationMinutes=0))
  service.onDreamingStarted();assertEquals(2,repo.peekAutoStart());service.onDreamingStopped()
  service.onDetachedFromWindow();window.set(service,null);controller.destroy()
  host.pause().stop().destroy()
 }
 @Test fun thumbnailCacheReusesThreeImagesAndDropsReferencesOnDestroy(){
  val controller=Robolectric.buildActivity(SettingsActivity::class.java).setup()
  val activity=controller.get()
  val loader=SettingsActivity::class.java.getDeclaredMethod("thumbnail",String::class.java).apply{isAccessible=true}
  val cacheField=SettingsActivity::class.java.getDeclaredField("thumbs").apply{isAccessible=true}
  CinematicCatalog.assets.forEach { asset ->
   val first=loader.invoke(activity,asset) as android.graphics.Bitmap
   assertSame(first,loader.invoke(activity,asset));assertTrue(first.allocationByteCount<=240*135*4)
  }
  val cache=cacheField.get(activity) as Map<*,*>
  assertEquals(3,cache.size)
  controller.pause().stop().destroy();assertEquals(0,cache.size)
 }

 @Test fun newStudioControlsStageCancelAndSaveTogether(){
  val repo=SettingsRepository(context)
  val controller=Robolectric.buildActivity(SettingsActivity::class.java).setup()
  val a=controller.get();val root=a.window.decorView
  root.findViewWithTag<android.view.View>("night").performClick()
  root.findViewWithTag<android.view.View>("section_1").requestFocus()
  shadowOf(Looper.getMainLooper()).idle()
  root.findViewWithTag<android.view.View>("digits").performClick()
  org.robolectric.shadows.ShadowDialog.getLatestDialog().findViewById<android.view.View>(android.R.id.content)
   .findViewWithTag<android.view.View>("choice_1").performClick()
  assertTrue(a.draft.current.nightDim);assertTrue(a.draft.current.latinDigits)
  assertFalse(repo.load().nightDim);assertFalse(repo.load().latinDigits)
  root.findViewWithTag<android.view.View>("cancel").performClick()
  assertFalse(a.draft.current.nightDim);assertFalse(a.draft.current.latinDigits)
  root.findViewWithTag<android.view.View>("section_0").requestFocus()
  shadowOf(Looper.getMainLooper()).idle()
  root.findViewWithTag<android.view.View>("night").performClick()
  root.findViewWithTag<android.view.View>("save").performClick()
  controller.pause().stop().destroy();assertTrue(SettingsRepository(context).load().nightDim)
 }
 @Test fun startupRevealsOnlyAfterDecodeAndArabicAppNameResolves(){
  val display=AmbientDisplay(context,imageLimit=640);display.start(fadeIn=true)
  shadowOf(Looper.getMainLooper()).idle()
  repeat(100){
   if(display.backgroundRenderer.loadedAsset.isEmpty()){Thread.sleep(10);shadowOf(Looper.getMainLooper()).idle()}
  }
  assertTrue(display.backgroundRenderer.imageBytes in 1..(640*360*4))
  assertFalse(display.awaitingImage);display.stop()
  assertEquals(0,display.backgroundRenderer.imageBytes)
  val config=android.content.res.Configuration(context.resources.configuration)
  config.setLocale(java.util.Locale("ar"))
  assertEquals("أُفُق",context.createConfigurationContext(config).getString(com.ufuq.dream.R.string.app_name))
 }

}
