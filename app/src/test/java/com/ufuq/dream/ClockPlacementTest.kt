package com.ufuq.dream

import android.graphics.Bitmap
import android.graphics.Canvas
import android.media.MediaFormat
import android.os.Looper
import android.view.View
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog
import java.io.File
import java.time.Instant
import java.util.Date

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],qualifiers="w960dp-h540dp-land-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ClockPlacementTest {
 private val context get()=RuntimeEnvironment.getApplication()
 private fun idle()=shadowOf(Looper.getMainLooper()).idle()
 private fun fresh():SettingsRepository {context.getSharedPreferences("dream_settings",0).edit().clear().commit();return SettingsRepository(context)}
 private fun layout(view:View){view.measure(View.MeasureSpec.makeMeasureSpec(1920,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(1080,View.MeasureSpec.EXACTLY));view.layout(0,0,1920,1080)}
 private fun capture(view:View,name:String){layout(view);val bitmap=Bitmap.createBitmap(1920,1080,Bitmap.Config.ARGB_8888);view.draw(Canvas(bitmap));val file=File("../artwork/0.8.0/positions/$name.png");file.parentFile!!.mkdirs();file.outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()}
 private fun capturePositionDialog(a:SettingsActivity,dialog:android.app.Dialog){
  val decor=dialog.window!!.decorView;val w=dialog.window!!.attributes.width
  decor.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(920,View.MeasureSpec.AT_MOST));decor.layout(0,0,decor.measuredWidth,decor.measuredHeight)
  assertTrue(decor.height<=840);assertEquals(720,decor.width)
  val bitmap=Bitmap.createBitmap(1920,1080,Bitmap.Config.ARGB_8888);val canvas=Canvas(bitmap);layout(a.window.decorView);a.window.decorView.draw(canvas)
  canvas.save();canvas.translate(1920-decor.width-1920*.035f,(1080-decor.height)/2f);decor.draw(canvas);canvas.restore()
  val file=File("../artwork/0.8.0/positions/position-selector.png");file.parentFile!!.mkdirs();file.outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
 }
 @Test fun allNinePlacementsAndAutomaticRespectOverscanAtMultipleSizes(){
  for(w in listOf(1280,1920,3840))for(photo in BackgroundRepository.images)for(position in -1..8)for(height in listOf(120,600,1500)){
   val h=w*9/16;val spec=ClockPlacement.spec(w,h,2f,position,photo.composition)
   val (b,scale)=ClockPlacement.visualBounds(spec,spec.contentWidth,height)
   assertTrue(scale>0&&scale<=1);assertTrue(b.left>=w*.05f-1);assertTrue(b.top>=h*.06f-1)
   assertTrue(b.right<=w*.95f+1);assertTrue(b.bottom<=h*.94f+1)
   assertTrue(b.left>=spec.zone.left+spec.reserve-.01);assertTrue(b.bottom<=spec.zone.bottom-spec.reserve+.01)
   if(position==0){assertEquals((spec.zone.left+spec.reserve).toFloat(),b.left,.01f);assertEquals((spec.zone.top+spec.reserve).toFloat(),b.top,.01f)}
   if(position==8){assertEquals((spec.zone.right-spec.reserve).toFloat(),b.right,.01f);assertEquals((spec.zone.bottom-spec.reserve).toFloat(),b.bottom,.01f)}
  }
 }
 @Test fun newPreferenceIsAdditiveValidatedAndSurvivesRestart(){
  val repo=fresh();assertEquals(-1,repo.load().clockPosition)
  for(position in -1..8){val s=repo.load().copy(clockPosition=position);repo.save(s);assertEquals(position,SettingsRepository(context).load().clockPosition)}
  context.getSharedPreferences("dream_settings",0).edit().putInt("clock_position",100).commit();assertEquals(-1,repo.load().clockPosition)
 }
 @Test fun realRendererFitsAllPositionsAcrossThemesAndKeepsArabicLayout(){
  val base=fresh().load().copy(size=2,seconds=true,greetingMode=2,greetingText="يا هلا Welcome Saudi 1234")
  for(theme in 0..2)for(position in -1..8){
   val s=base.copy(theme=theme,sceneChoice=theme,clockPosition=position)
   val display=AmbientDisplay(context,{s},imageLimit=640,sceneTransitions=false)
   display.testNow=Date.from(Instant.parse("2026-11-01T18:41:12Z"));display.start();idle()
   repeat(100){if(display.backgroundRenderer.loadedAsset.isEmpty()){Thread.sleep(5);idle()}}
   layout(display);val bounds=display.visualContentBounds
   assertTrue(bounds.width()>0&&bounds.height()>0);assertTrue(bounds.left>=95);assertTrue(bounds.top>=64)
   assertTrue(bounds.right<=1825);assertTrue(bounds.bottom<=1016)
   if(position in listOf(-1,0,4,8))capture(display,"theme-$theme-position-$position")
   display.stop();assertEquals(0,display.backgroundRenderer.imageBytes);assertEquals(MotionState.IDLE,display.motionLayer.session.state)
  }
 }
 @Test fun positionSelectorStagesOnFocusBackRevertsConfirmRequiresSaveAndCancelRestores(){
  val repo=fresh();val controller=Robolectric.buildActivity(SettingsActivity::class.java).setup();val a=controller.get();val decor=a.window.decorView
  decor.findViewWithTag<View>("section_1").requestFocus();idle();decor.findViewWithTag<View>("position").performClick();idle()
  var dialog=ShadowDialog.getLatestDialog();val root=dialog.window!!.decorView
  val center=root.findViewWithTag<View>("position_4");center.requestFocus();idle()
  assertEquals(4,a.draft.current.clockPosition);assertEquals(-1,repo.load().clockPosition)
  assertEquals(root.findViewWithTag<View>("position_3").id,center.nextFocusLeftId)
  assertEquals(root.findViewWithTag<View>("position_5").id,center.nextFocusRightId)
  assertEquals(root.findViewWithTag<View>("position_1").id,center.nextFocusUpId)
  assertEquals(root.findViewWithTag<View>("position_7").id,center.nextFocusDownId)
  capture(decor,"studio-live-center");capturePositionDialog(a,dialog)
  dialog.onBackPressed();idle();assertEquals(-1,a.draft.current.clockPosition);assertFalse(a.draft.dirty)
  decor.findViewWithTag<View>("position").performClick();idle();dialog=ShadowDialog.getLatestDialog()
  dialog.window!!.decorView.findViewWithTag<View>("position_8").performClick();idle()
  assertEquals(8,a.draft.current.clockPosition);assertEquals(-1,repo.load().clockPosition)
  decor.findViewWithTag<View>("cancel").performClick();idle();assertEquals(-1,a.draft.current.clockPosition)
  decor.findViewWithTag<View>("position").performClick();idle();ShadowDialog.getLatestDialog().window!!.decorView.findViewWithTag<View>("position_2").performClick();idle()
  decor.findViewWithTag<View>("save").performClick();idle();assertEquals(2,SettingsRepository(context).load().clockPosition)
  controller.pause().stop().destroy()
 }
 @Test fun previewUsesIdenticalPositionGeometryWithoutPersisting(){
  val saved=fresh().load();var s=saved.copy(clockPosition=7)
  val studio=PreviewStudio(context){s};studio.start();idle();studio.measure(View.MeasureSpec.makeMeasureSpec(1000,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(700,View.MeasureSpec.AT_MOST));studio.layout(0,0,1000,562)
  val dream=AmbientDisplay(context,{s},imageLimit=640);dream.start();idle();layout(dream)
  assertEquals(dream.visualContentBounds,studio.display.visualContentBounds)
  s=s.copy(clockPosition=0);studio.refresh();layout(studio.display);assertEquals(0,studio.display.settingsSnapshot.clockPosition)
  assertEquals(-1,SettingsRepository(context).load().clockPosition);studio.stop();dream.stop()
 }
 @Test fun videoLoopPolicyPreservesTheLastScheduledFrame(){
  val f=MediaFormat.createVideoFormat("video/avc",1920,1080);f.setInteger(MediaFormat.KEY_FRAME_RATE,25)
  assertEquals(1_040_000_000L,PlaybackTiming.loopEnd(1_000_000_000L,f))
 }
 @Test fun motionPosterIsDownsampledAndReleaseClearsBothSlots(){
  val renderer=BackgroundRenderer(context,1920);val photo=BackgroundRepository.first(1)
  renderer.configurePhoto(ThemeManager.get(1),photo,960)
  repeat(100){if(renderer.loadedAsset.isEmpty()){Thread.sleep(5);idle()}}
  assertEquals(photo.fileName,renderer.loadedAsset);assertTrue(renderer.imageBytes<=960*540*4)
  renderer.release();assertEquals(0,renderer.imageBytes);assertEquals(0,renderer.preloadBytes)
 }
}
