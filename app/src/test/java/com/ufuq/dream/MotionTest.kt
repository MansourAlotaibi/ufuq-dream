package com.ufuq.dream

import android.view.Surface
import android.os.Looper
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],qualifiers="w960dp-h540dp-land-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MotionTest {
 private val context get()=RuntimeEnvironment.getApplication()
 private val textures=mutableListOf<android.graphics.SurfaceTexture>()
 private fun surface()=Surface(android.graphics.SurfaceTexture(0).also{textures.add(it)})
 @org.junit.After fun releaseTextures(){textures.forEach{it.release()}}
 private val fixture=MotionClip("test_alula","Synthetic test metadata only",1,"missing.mp4","alula_1")
 private class Fake:MotionBackend {
  var boundary:(()->Unit)?=null
  override fun onLoopBoundary(listener:()->Unit){boundary=listener}
  var plays=0;var stops=0;var ready:(()->Unit)?=null;var error:(()->Unit)?=null
  override fun play(surface:Surface,clip:MotionClip,onFirstFrame:()->Unit,onFailure:()->Unit){plays++;ready=onFirstFrame;error=onFailure}
  override fun stop(){stops++}
 }
 @Test fun playlistBoundaryIgnoresStoppedAndPreparingPlayers(){
  val created=mutableListOf<Fake>();var ends=0
  val session=MotionSession({Fake().also{created.add(it)}}){}
  session.onLoopBoundary={ends++}
  val surface=surface();session.start(surface,fixture);val first=created.last()
  first.boundary!!();assertEquals(0,ends)
  first.ready!!();first.boundary!!();assertEquals(1,ends)
  session.stop();first.boundary!!();assertEquals(1,ends)
  session.start(surface,fixture.copy(id="replacement"));val second=created.last()
  first.boundary!!();assertEquals(1,ends)
  second.ready!!();second.boundary!!();assertEquals(2,ends)
  session.stop();surface.release()
 }
 @Test fun photosDefaultAndUnavailableMotionFallsBackWithoutPreferenceLoss(){
  context.getSharedPreferences("dream_settings",0).edit().clear().commit()
  val repo=SettingsRepository(context);assertEquals(0,repo.load().backgroundType)
  val s=repo.load().copy(backgroundType=1,sceneChoice=1)
  assertNull(MediaLibrary.selected(s,0).clip)
  repo.save(s);assertEquals(1,repo.load().backgroundType)
  assertEquals(1,MediaLibrary.selected(repo.load(),0).category)
 }
 @Test fun motionFilteringMixedRotationAndFixedChoiceAreIndependentOfTheme(){
  val clips=listOf(fixture,fixture.copy(id="test_riyadh",category=2,posterId="riyadh_1"))
  val base=SettingsRepository(context).load().copy(backgroundType=1,sceneChoice=1,theme=2)
  assertEquals(fixture,MediaLibrary.selected(base,9,motion=clips).clip)
  assertEquals(1,MediaLibrary.items(1,1,clips).size)
  val mixed=base.copy(backgroundType=2)
  assertTrue(MediaLibrary.items(1,2,clips).any{it.clip!=null});assertTrue(MediaLibrary.items(1,2,clips).any{it.clip==null})
  val fixed=mixed.copy(imageMode=1,fixedAssetId=fixture.id)
  assertEquals(fixture,MediaLibrary.selected(fixed,999,motion=clips).clip)
  assertEquals(2,fixed.theme)
 }
 @Test fun playbackFailureLeavesPosterAndDoesNotRetrySameAsset(){
  val backend=Fake();val states=mutableListOf<MotionState>();val session=MotionSession({backend}){states.add(it)}
  val surface=surface();session.start(surface,fixture);assertEquals(MotionState.PREPARING,session.state)
  backend.error!!();assertEquals(MotionState.FALLBACK,session.state);assertEquals(1,backend.stops)
  session.start(surface,fixture);assertEquals(1,backend.plays)
  backend.ready!!();assertEquals(MotionState.FALLBACK,session.state)
  session.stop();surface.release();assertEquals(MotionState.IDLE,session.state)
 }
 @Test fun stopAndReplacementIgnoreStaleFrameCallbacks(){
  val created=mutableListOf<Fake>();val session=MotionSession({Fake().also{created.add(it)}}){}
  val surface=surface();session.start(surface,fixture);val old=created.last();session.stop()
  old.ready!!();assertEquals(MotionState.IDLE,session.state)
  session.start(surface,fixture.copy(id="second"));val second=created.last()
  old.error!!();assertEquals(MotionState.PREPARING,session.state)
  second.ready!!();assertEquals(MotionState.PLAYING,session.state)
  session.fail();assertEquals(MotionState.FALLBACK,session.state)
  session.stop();session.stop();surface.release();assertTrue(created.all{it.stops>=1})
 }
 @Test fun hardwareLeaseIsExclusiveAndSoftwareNamesAreRejected(){
  DecoderLease.release();assertTrue(DecoderLease.acquire());assertFalse(DecoderLease.acquire());DecoderLease.release();assertFalse(DecoderLease.active)
  assertFalse(MotionSupport.knownHardwareName("OMX.google.h264.decoder"))
  assertFalse(MotionSupport.knownHardwareName("c2.android.avc.decoder"))
  assertFalse(MotionSupport.knownHardwareName("unknown.decoder"))
  assertTrue(MotionSupport.knownHardwareName("OMX.amlogic.avc.decoder.awesome"))
 }
 @Test fun missingMp4FailsAsynchronouslyAndReleasesLease(){
  val backend=HardwareMotionBackend(context);val surface=surface();var failed=false
  backend.play(surface,fixture,{fail("Missing file must never produce a frame")},{failed=true})
  repeat(200){if(!failed){Thread.sleep(5);shadowOf(Looper.getMainLooper()).idle()}}
  assertTrue(failed);backend.stop();surface.release()
  repeat(100){if(DecoderLease.active)Thread.sleep(5)}
  assertFalse(DecoderLease.active)
 }
 @Test fun backgroundTypeAndFixedClipStayStagedUntilExplicitSave(){
  context.getSharedPreferences("dream_settings",0).edit().clear().commit()
  val repo=SettingsRepository(context);val s=repo.load();val draft=SettingsDraft(s)
  draft.update(s.copy(backgroundType=2,fixedAssetId=fixture.id))
  assertEquals(0,repo.load().backgroundType);draft.revert();assertEquals(s,draft.current)
  repo.save(s.copy(backgroundType=2,fixedAssetId=fixture.id));val restored=SettingsRepository(context).load()
  assertEquals(2,restored.backgroundType);assertEquals(fixture.id,restored.fixedAssetId)
  // Missing future media is a safe in-category photo, never a reset or network download.
  assertNull(MediaLibrary.selected(restored.copy(imageMode=1),0).clip)
 }
 @Test fun missingPhotoDecodesToThemeFallbackAndSignalsReady(){
  val renderer=BackgroundRenderer(context,640);var ready=false;renderer.onReady={ready=true}
  renderer.configurePhoto(ThemeManager.get(0),BackgroundImage("missing","Missing test only",1,"not-a-photo.jpg"))
  repeat(200){if(!ready){Thread.sleep(5);shadowOf(Looper.getMainLooper()).idle()}}
  assertTrue(ready);assertEquals(0,renderer.imageBytes);assertEquals("",renderer.loadedAsset);renderer.release()
 }
 @Test fun unavailableMotionChoicesCannotMutateStudioDraft(){
  context.getSharedPreferences("dream_settings",0).edit().clear().commit()
  val controller=org.robolectric.Robolectric.buildActivity(SettingsActivity::class.java).setup()
  val a=controller.get()
  assertNull(a.window.decorView.findViewWithTag<android.view.View>("background_type"))
  assertEquals(0,a.draft.current.backgroundType);assertFalse(a.draft.dirty)
  controller.pause().stop().destroy()
 }
 @Test fun shippedCatalogNeverPretendsPendingVideoIsApproved(){
  val records=org.json.JSONObject(context.assets.open("motion-rights.json").bufferedReader().use{it.readText()})
  assertEquals(0,records.getJSONArray("approved").length());assertTrue(MediaLibrary.clips.isEmpty())
  assertEquals(3,records.getJSONArray("pending").length())
  val display=AmbientDisplay(context,imageLimit=640);display.start()
  shadowOf(Looper.getMainLooper()).idle();assertEquals(MotionState.IDLE,display.motionLayer.session.state)
  display.stop();assertEquals(MotionState.IDLE,display.motionLayer.session.state)
 }

 @Test fun initializationExceptionFallsBackAndRapidSessionsReleaseEveryBackend(){
  val broken=MotionSession({throw IllegalArgumentException("Unsupported codec test fixture")}){}
  val target=surface();broken.start(target,fixture);assertEquals(MotionState.FALLBACK,broken.state);broken.stop()
  val created=mutableListOf<Fake>();val session=MotionSession({Fake().also{created.add(it)}}){}
  repeat(20){session.start(target,fixture);val backend=created.last();session.stop();backend.ready!!();backend.error!!();assertEquals(MotionState.IDLE,session.state)}
  assertEquals(20,created.size);assertTrue(created.all{it.stops==1});target.release()
 }
 @Test fun stoppingStartupBeforeDecodeNeverRevealsOrRetainsBitmaps(){
  val display=AmbientDisplay(context,imageLimit=640)
  repeat(12){display.start(fadeIn=true);shadowOf(Looper.getMainLooper()).idle();display.stop()}
  repeat(30){Thread.sleep(5);shadowOf(Looper.getMainLooper()).idle()}
  assertFalse(display.isRunning);assertFalse(display.awaitingImage);assertEquals(0,display.backgroundRenderer.imageBytes)
  assertEquals(0,display.backgroundRenderer.preloadBytes);assertEquals(MotionState.IDLE,display.motionLayer.session.state)
 }

}
