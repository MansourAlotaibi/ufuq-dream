package com.ufuq.dream

import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Bitmap
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
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
import org.robolectric.shadows.ShadowDialog
import java.time.Duration
import java.time.Instant
import java.util.Date
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],qualifiers="w960dp-h540dp-land-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PhotoLibraryTest {
 private val context get()=RuntimeEnvironment.getApplication()
 @Before fun clear(){context.getSharedPreferences("dream_settings",0).edit().clear().commit()}
 private fun idle()=shadowOf(Looper.getMainLooper()).idle()
 private fun waitUntil(test:()->Boolean){repeat(200){if(test())return;Thread.sleep(10);idle()};fail("Decode timed out")}
 private fun choose(a:SettingsActivity,key:String,index:Int){
  a.window.decorView.findViewWithTag<View>(key).performClick()
  ShadowDialog.getLatestDialog().findViewById<View>(android.R.id.content).findViewWithTag<View>("choice_$index").performClick();idle()
 }
 @Test fun thirteenRealPhotosHaveLicenseRecordsAndBoundedThumbnails(){
  val records=org.json.JSONArray(context.assets.open("photo-provenance.json").bufferedReader().use{it.readText()})
  assertEquals(13,records.length());assertEquals(13,BackgroundRepository.images.map{it.id}.distinct().size)
  assertEquals(listOf(3,5,5),(0..2).map{BackgroundRepository.group(it).size})
  BackgroundRepository.images.forEach{image->
   val item=(0 until records.length()).map{records.getJSONObject(it)}.first{it.getString("id")==image.id}
   assertTrue(item.getString("source").startsWith("https://commons.wikimedia.org/"));assertTrue(item.getString("author").isNotBlank())
   assertTrue(item.getString("license").startsWith("CC"));assertTrue(item.getString("licenseUrl").startsWith("https://"))
   val bytes=context.assets.open("cinematic/${image.fileName}").use{it.readBytes()}
   val digest=java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString(""){"%02x".format(it)}
   assertEquals(item.getString("sha256"),digest)
   val bounds=BitmapFactory.Options().apply{inJustDecodeBounds=true};BitmapFactory.decodeByteArray(bytes,0,bytes.size,bounds)
   assertEquals(1920,bounds.outWidth);assertEquals(1080,bounds.outHeight)
   val thumb=context.assets.open("thumbnails/${image.fileName}").use{BitmapFactory.decodeStream(it)}!!
   assertTrue(thumb.allocationByteCount<=240*135*4)
  }
 }
 @Test fun categoryFiltersAndFixedChoicesNeverLeakOtherGroups(){
  val base=SettingsRepository(context).load()
  for(category in -1..2){
   val s=base.copy(sceneChoice=category)
   val sequence=(0..19).map{BackgroundRepository.selected(s,it.toLong()).id}
   assertEquals(BackgroundRepository.group(category).size,sequence.distinct().size)
   if(category>=0)assertTrue(sequence.all{BackgroundRepository.find(it).category==category})
   val fixed=s.copy(imageMode=1,imageId=BackgroundRepository.first(category).id)
   assertEquals(1,(0..19).map{BackgroundRepository.selected(fixed,it.toLong(),2).id}.distinct().size)
   assertEquals(BackgroundRepository.selected(fixed,0),BackgroundRepository.selected(fixed.copy(imageMode=0,rotationMinutes=0),999))
  }
 }
 @Test fun activeRotationSupportsAllIntervalsAndPause(){
  for(minutes in listOf(1,3,5)){
   val r=BackgroundRotation();r.resume(100)
   assertEquals(0L,r.step(100,minutes));assertEquals(1L,r.step(100+minutes*60000L,minutes))
   r.pause(100+minutes*60000L);assertEquals(1L,r.step(99999999,minutes))
   r.resume(99999999);assertEquals(minutes*60000L,r.remaining(99999999,minutes))
  }
  assertEquals(Long.MAX_VALUE,BackgroundRotation().remaining(10000,0))
 }
 @Test fun migrationKeepsFixedSceneAndThemeIndependentAndSecondsOptIn(){
  val prefs=context.getSharedPreferences("dream_settings",0)
  prefs.edit().putInt("schema",5).putInt("scene_choice",2).putInt("theme",2).putBoolean("seconds",true).commit()
  val repo=SettingsRepository(context);val s=repo.load()
  assertEquals(2,s.sceneChoice);assertEquals(2,s.theme);assertFalse(s.seconds);assertEquals(3,s.rotationMinutes)
  val new=s.copy(theme=0,sceneChoice=1,imageMode=1,imageId="alula_4",seconds=true,rotationMinutes=5)
  val draft=SettingsDraft(s);draft.update(new);assertEquals(s,repo.load());draft.revert();assertEquals(s,draft.current)
  repo.save(new);val saved=SettingsRepository(context).load()
  assertEquals(0,saved.theme);assertEquals(1,saved.sceneChoice);assertEquals("alula_4",saved.imageId);assertTrue(saved.seconds)
 }
 @Test fun preloadPromotesOneImageAndStopDropsBothReferences(){
  val renderer=BackgroundRenderer(context,640)
  renderer.configurePhoto(ThemeManager.get(0),BackgroundRepository.images[0]);waitUntil{renderer.imageBytes>0}
  renderer.preload(BackgroundRepository.images[1].fileName);waitUntil{renderer.preloadBytes>0}
  assertTrue(renderer.imageBytes+renderer.preloadBytes<=2*640*360*4)
  renderer.configurePhoto(ThemeManager.get(2),BackgroundRepository.images[1])
  assertEquals(BackgroundRepository.images[1].fileName,renderer.loadedAsset);assertEquals(0,renderer.preloadBytes)
  renderer.preload(BackgroundRepository.images[2].fileName);renderer.release()
  Thread.sleep(30);idle();assertEquals(0,renderer.imageBytes);assertEquals(0,renderer.preloadBytes)
 }
 @Test fun allThirteenImagesRenderWithIndependentPaletteAndSubordinateSeconds(){
  var value=SettingsRepository(context).load().copy(imageMode=1,theme=0,seconds=true,size=2,latinDigits=true)
  val display=AmbientDisplay(context,{value},640);display.testNow=Date.from(Instant.parse("2026-11-01T18:41:12Z"))
  fun texts(v:View):List<TextView> = if(v is TextView)listOf(v) else if(v is ViewGroup)(0 until v.childCount).flatMap{texts(v.getChildAt(it))} else emptyList()
  BackgroundRepository.images.forEachIndexed{index,image->
   value=value.copy(imageId=image.id);display.start();idle();waitUntil{display.backgroundRenderer.loadedAsset==image.fileName}
   display.measure(View.MeasureSpec.makeMeasureSpec(1920,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(1080,View.MeasureSpec.EXACTLY));display.layout(0,0,1920,1080)
   assertEquals(0,display.settingsSnapshot.theme);assertEquals(image.category,display.settingsSnapshot.photoCategory)
   val labels=texts(display);val clock=labels.first{it.text.contains("21:41")};val seconds=labels.first{it.text.toString()=="\u206612\u2069"}
   assertTrue(seconds.textSize<clock.textSize);assertTrue(display.contentBounds().height()>0)
   val group=display.getChildAt(3) as ViewGroup;assertTrue(group.getChildAt(0).width<=group.width);assertTrue(group.getChildAt(0).height<=group.height)
   val bitmap=Bitmap.createBitmap(1920,1080,Bitmap.Config.ARGB_8888);display.draw(Canvas(bitmap))
   val file=File("../artwork/0.8.0/screenshots/library-$index.png");file.parentFile?.mkdirs();file.outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,95,it)};bitmap.recycle()
   display.stop();assertEquals(0,display.backgroundRenderer.imageBytes)
  }
 }
 @Test fun studioStagesThemeCategoryImageIntervalAndSecondsWithoutSaving(){
  val repo=SettingsRepository(context);val initial=repo.load()
  val controller=Robolectric.buildActivity(SettingsActivity::class.java).setup();val a=controller.get();val root=a.window.decorView
  choose(a,"theme",0);choose(a,"scene",2);assertEquals(0,a.draft.current.theme);assertEquals(1,a.draft.current.sceneChoice)
  choose(a,"image_mode",1);choose(a,"image",3);assertEquals("alula_4",a.draft.current.imageId)
  choose(a,"image_mode",0);choose(a,"interval",3);choose(a,"image",2)
  assertEquals(0,a.draft.current.rotationMinutes);assertEquals("alula_3",a.draft.current.imageId)
  root.findViewWithTag<View>("section_1").requestFocus();idle();root.findViewWithTag<View>("seconds").performClick()
  val studio=root.findViewWithTag<PreviewStudio>("studio")
  shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1));assertTrue(studio.display.settingsSnapshot.seconds)
  assertEquals(initial,repo.load());root.findViewWithTag<View>("cancel").performClick();assertEquals(initial,a.draft.current)
  root.findViewWithTag<View>("section_0").requestFocus();idle();choose(a,"theme",2);choose(a,"interval",2)
  root.findViewWithTag<View>("save").performClick();assertEquals(2,repo.load().theme);assertEquals(5,repo.load().rotationMinutes)
  controller.pause().stop().destroy()
 }
}
