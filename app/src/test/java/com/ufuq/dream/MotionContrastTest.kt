package com.ufuq.dream

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Looper
import android.view.View
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.Instant
import java.util.Date

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],qualifiers="w960dp-h540dp-land-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MotionContrastTest {
 private val previousZone=java.util.TimeZone.getDefault()
 @org.junit.Before fun useSaudiTime(){java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Asia/Riyadh"))}
 private val context get()=RuntimeEnvironment.getApplication()
 private val folder get()=File(context.filesDir,"personal-motion")
 @After fun cleanup(){java.util.TimeZone.setDefault(previousZone);folder.deleteRecursively();PersonalMotionStore.reset();MediaLibrary.initialize(context)}
 @Test fun nightOptOutRemainsOffAndDarkFootageRetainsDetail(){
  assertEquals(0f,MotionContrastPolicy.nightDim(0f,.1f),0f)
  assertEquals(.06f,MotionContrastPolicy.nightDim(.42f,.1f),.001f)
  assertEquals(.16f,MotionContrastPolicy.nightDim(.42f,.8f),.001f)
  assertEquals(.10f,MotionContrastPolicy.nightDim(.42f,null),.001f)
  assertEquals(.03f,MotionContrastPolicy.nightDim(.03f,.1f),.001f)
 }
 private fun frame(display:AmbientDisplay):Bitmap {
  display.forceLayout()
  display.measure(View.MeasureSpec.makeMeasureSpec(1920,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(1080,View.MeasureSpec.EXACTLY));display.layout(0,0,1920,1080)
  return Bitmap.createBitmap(1920,1080,Bitmap.Config.ARGB_8888)
 }
 private fun capture(bitmap:Bitmap,display:AmbientDisplay,name:String){
  display.draw(Canvas(bitmap));val f=File("../artwork/0.8.2/$name.png");f.parentFile!!.mkdirs();f.outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
 }
 @Test fun sharedPosterRenderingPreservesTheCityOutsideTheTextRegion(){
  folder.mkdirs();File(folder,"review_city.mp4").writeBytes(ByteArray(2048)) // Technical placeholder, not genuine playable media.
  val supplied=System.getenv("UFUQ_REVIEW_MEDIA")?.let{File(it,"riyadh_dusk.jpg")}
  if(supplied?.isFile==true)supplied.copyTo(File(folder,"review_city.jpg"),overwrite=true)
  else {
   val solid=Bitmap.createBitmap(1920,1080,Bitmap.Config.ARGB_8888);solid.eraseColor(Color.rgb(65,75,85))
   File(folder,"review_city.jpg").outputStream().use{solid.compress(Bitmap.CompressFormat.JPEG,90,it)};solid.recycle()
  }
  val entry=JSONObject().put("fileName","review_city.mp4").put("category",2).put("posterId","riyadh_1").put("title","Contrast review")
  File(folder,"catalog.json").writeText(JSONObject().put("usage","personal-only").put("clips",JSONArray().put(entry)).toString())
  PersonalMotionStore.reset();context.getSharedPreferences("dream_settings",0).edit().clear().commit()
  val repo=SettingsRepository(context);val settings=repo.load().copy(sceneChoice=2,backgroundType=1,nightDim=true,greetingMode=1)
  val display=AmbientDisplay(context,{settings},imageLimit=1920,sceneTransitions=false)
  display.testNow=Date.from(Instant.parse("2026-11-01T21:00:00Z"));display.start();shadowOf(Looper.getMainLooper()).idle()
  repeat(200){if(display.backgroundRenderer.loadedAsset.isEmpty()){Thread.sleep(5);shadowOf(Looper.getMainLooper()).idle()}}
  assertEquals("personal:review_city.jpg",display.backgroundRenderer.loadedAsset)
  val before=frame(display)
  // Reproduce the previous background treatment on the exact same source poster, not a TV capture.
  display.backgroundRenderer.motionPoster=false;display.backgroundRenderer.nightLevel=.42f;display.contrastVeil.clear()
  capture(before,display,"riyadh-treatment-before-host")
  val after=frame(display);display.backgroundRenderer.motionPoster=true
  capture(after,display,"riyadh-treatment-after-host")
  val x=1500;val y=650
  fun light(c:Int)=Color.red(c)+Color.green(c)+Color.blue(c)
  assertTrue("City detail should remain outside the text contrast area",light(after.getPixel(x,y))>light(before.getPixel(x,y)))
  assertTrue(display.backgroundRenderer.nightLevel<=.16f)
  assertTrue(settings.nightDim);assertFalse(repo.load().nightDim) // Preview never persists a preference.
  before.recycle();after.recycle();display.stop()
 }
}
