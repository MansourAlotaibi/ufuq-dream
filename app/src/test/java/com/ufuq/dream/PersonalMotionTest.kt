package com.ufuq.dream

import android.graphics.Bitmap
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28])
class PersonalMotionTest {
 private val context get()=RuntimeEnvironment.getApplication()
 private val directory get()=File(context.filesDir,"personal-motion")
 @After fun cleanup(){directory.deleteRecursively();PersonalMotionStore.reset();MediaLibrary.initialize(context)}
 private fun catalog(name:String="local_test.mp4",category:Int=1):File {
  directory.mkdirs();File(directory,"local_test.mp4").writeBytes(ByteArray(2048))
  val b=Bitmap.createBitmap(32,18,Bitmap.Config.ARGB_8888);File(directory,"local_test.jpg").outputStream().use{b.compress(Bitmap.CompressFormat.JPEG,80,it)};b.recycle()
  val entry=JSONObject().put("fileName",name).put("category",category).put("title","اختبار شخصي").put("posterId","alula_1")
  return File(directory,"catalog.json").apply{writeText(JSONObject().put("usage","personal-only").put("clips",JSONArray().put(entry)).toString())}
 }
 @Test fun personalCatalogIsOptionalAndNeverBundled(){
  PersonalMotionStore.reset();assertTrue(PersonalMotionStore.load(context).isEmpty())
  catalog();MediaLibrary.initialize(context);assertEquals(1,MediaLibrary.clips.size)
  val clip=MediaLibrary.clips.single();assertTrue(clip.privateFile);assertEquals("personal:local_test.jpg",clip.posterFileName)
  assertEquals(File(directory,"local_test.mp4").canonicalPath,PersonalMotionStore.file(context,clip).path)
  val s=SettingsRepository(context).load().copy(backgroundType=1,sceneChoice=1)
  val selected=MediaLibrary.selected(s,0);assertEquals(clip,selected.clip);assertEquals("personal:local_test.jpg",selected.photo.fileName)
 }
 @Test fun removalOrInvalidManifestCannotResurrectCachedClips(){
  val manifest=catalog();assertEquals(1,PersonalMotionStore.load(context).size)
  assertTrue(manifest.delete());assertTrue(PersonalMotionStore.load(context).isEmpty());assertTrue(PersonalMotionStore.load(context).isEmpty())
  manifest.writeText("broken");assertTrue(PersonalMotionStore.load(context).isEmpty())
 }
 @Test fun generalSaudiSceneryAppearsOnlyInAllWithItsOwnPoster(){
  catalog(category=-1);val clips=PersonalMotionStore.load(context);assertEquals(1,clips.size)
  assertNotNull(MediaLibrary.items(-1,1,clips).single().clip)
  assertEquals(-1,MediaLibrary.items(-1,1,clips).single().photo.category)
  for(category in 0..2)assertTrue(MediaLibrary.items(category,1,clips).all{it.clip==null})
  File(directory,"local_test.jpg").delete();PersonalMotionStore.reset()
  assertTrue(PersonalMotionStore.load(context).isEmpty())
 }
 @Test fun traversalWrongCategoryAndMissingFileAreRejected(){
  catalog("../local_test.mp4");assertTrue(PersonalMotionStore.load(context).isEmpty())
  catalog(category=2);PersonalMotionStore.reset();assertTrue(PersonalMotionStore.load(context).isEmpty())
  catalog("not_present.mp4");PersonalMotionStore.reset();assertTrue(PersonalMotionStore.load(context).isEmpty())
 }
 @Test fun localVideoAndPosterCannotEscapeAppInternalDirectory(){
  catalog();val clip=PersonalMotionStore.load(context).single()
  assertThrows(IllegalArgumentException::class.java){PersonalMotionStore.file(context,clip.copy(fileName="../other.mp4"))}
  assertThrows(IllegalArgumentException::class.java){PersonalMotionStore.openPoster(context,"personal:../other.jpg")}
  PersonalMotionStore.openPoster(context,"personal:local_test.jpg").use{assertTrue(it.read()>=0)}
 }
 @Test fun noPersonalMediaSelectionIsSilentlyPersisted(){
  context.getSharedPreferences("dream_settings",0).edit().clear().commit();catalog()
  val repo=SettingsRepository(context);val saved=repo.load();assertEquals(0,saved.backgroundType)
  val draft=SettingsDraft(saved);draft.update(saved.copy(backgroundType=1));assertEquals(0,repo.load().backgroundType)
  draft.revert();assertEquals(saved,draft.current);assertEquals(0,repo.load().backgroundType)
 }
 @Test fun intentionalLoopDrainDoesNotTriggerTheDecoderInactivityWatchdog(){
  val watchdog=DecoderWatchdog(0)
  watchdog.progressed(2_000_000_000L)
  // Mi Box can dequeue EOS well ahead of the final scheduled display frame.
  val loopResume=13_000_000_000L
  watchdog.progressed(loopResume)
  assertFalse(watchdog.stalled(loopResume));assertFalse(watchdog.stalled(loopResume+7_999_999_999L))
  assertTrue(watchdog.stalled(loopResume+8_000_000_000L))
 }

 @Test fun nextClipWaitsForThePreviousDecoderReleaseWithBoundedCancellation(){
  DecoderLease.release();assertTrue(DecoderLease.acquire())
  val canceled=java.util.concurrent.atomic.AtomicBoolean(false)
  assertFalse(DecoderLease.acquireAfterRelease(canceled,20))
  canceled.set(true);assertFalse(DecoderLease.acquireAfterRelease(canceled,2000))
  DecoderLease.release();canceled.set(false);assertTrue(DecoderLease.acquireAfterRelease(canceled,100));DecoderLease.release()
 }

}
