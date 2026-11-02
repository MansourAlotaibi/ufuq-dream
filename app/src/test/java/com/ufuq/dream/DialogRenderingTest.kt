package com.ufuq.dream

import android.app.Dialog
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],qualifiers="w960dp-h540dp-land-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DialogRenderingTest {
 private val context get()=RuntimeEnvironment.getApplication()
 private fun idle()=shadowOf(Looper.getMainLooper()).idle()
 private fun open(a:SettingsActivity,count:Int):Dialog {
  val choose=SettingsActivity::class.java.declaredMethods.first{it.name=="choose"}.apply{isAccessible=true}
  val callback:(Int)->Unit={}
  choose.invoke(a,"خيارات العرض",List(count){"الخيار ${it+1}"},0,false,null,null,callback)
  idle();return ShadowDialog.getLatestDialog()
 }
 private fun draw(dialog:Dialog,name:String):Pair<Bitmap,View> {
  val decor=dialog.window!!.decorView
  val params=dialog.window!!.attributes
  val w=params.width.takeIf{it>0}?:1180
  val mode=if(params.height>0)View.MeasureSpec.EXACTLY else View.MeasureSpec.AT_MOST
  decor.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(if(params.height>0)params.height else 920,mode))
  decor.layout(0,0,decor.measuredWidth,decor.measuredHeight)
  val frame=Bitmap.createBitmap(1920,1080,Bitmap.Config.ARGB_8888);val canvas=Canvas(frame)
  canvas.drawColor(0xff090d0c.toInt());canvas.save();canvas.translate((1920-decor.width)/2f,(1080-decor.height)/2f);decor.draw(canvas);canvas.restore()
  val file=File("../artwork/0.8.0/dialogs/$name.png");file.parentFile?.mkdirs();file.outputStream().use{frame.compress(Bitmap.CompressFormat.PNG,100,it)}
  return frame to decor
 }
 @Test fun dialogGallery(){
  val output=StringBuilder()
  for(theme in 0..2){
   context.getSharedPreferences("dream_settings",0).edit().clear().putInt("scene_choice",1).putInt("theme",theme).commit()
   val controller=Robolectric.buildActivity(SettingsActivity::class.java).setup();val a=controller.get()
   for(count in listOf(2,3,4,13)){
    val d=open(a,count);val (bitmap,decor)=draw(d,"theme-$theme-options-$count")
    output.append("theme=$theme count=$count windowHeight=${d.window!!.attributes.height} decor=${decor.width}x${decor.height} windowBackground=${d.window!!.decorView.background?.javaClass?.simpleName}\n")
    val surface=decor.findViewWithTag<View>("dialog_surface")
    val scroll=decor.findViewWithTag<BoundedDialogScroll>("dialog_scroll")
    val panel=decor.findViewWithTag<View>("dialog_panel")
    assertEquals(ViewGroup.LayoutParams.WRAP_CONTENT,d.window!!.attributes.height)
    assertEquals(0,android.graphics.Color.alpha((decor.background as android.graphics.drawable.ColorDrawable).color))
    assertEquals(0,decor.paddingTop+decor.paddingBottom+decor.paddingLeft+decor.paddingRight)
    assertTrue(surface.clipToOutline);assertEquals(View.LAYOUT_DIRECTION_RTL,panel.layoutDirection)
    assertEquals(0,surface.minimumHeight);assertEquals(0,panel.minimumHeight)
    assertEquals(ThemeManager.get(theme).base,(surface.background as android.graphics.drawable.GradientDrawable).color!!.defaultColor)
    assertTrue(decor.height<=scroll.maximumHeight)
    assertEquals(scroll.height,surface.height)
    if(count<10){
     assertEquals(panel.height,scroll.height)
     val expected=((32+46+count*76+(count-1)*6)*context.resources.displayMetrics.density).toInt()
     assertEquals(expected,decor.height)
     assertFalse(scroll.canScrollVertically(1))
    }else{assertTrue(panel.height>scroll.height);assertTrue(scroll.canScrollVertically(1))}
    val left=(1920-decor.width)/2;val top=(1080-decor.height)/2
    assertEquals(ThemeManager.get(theme).base,bitmap.getPixel(left+10,540))
    if(count<10)assertEquals(ThemeManager.get(theme).base,bitmap.getPixel(960,top+decor.height-12))
    assertEquals(0xff090d0c.toInt(),bitmap.getPixel(left+1,top+1))
    assertFalse(scroll.isVerticalScrollBarEnabled)
    bitmap.recycle()
    d.dismiss();idle()
   }
   controller.pause().stop().destroy()
  }
  File("../artwork/0.8.0/dialogs/layout-metrics.txt").writeText(output.toString())
 }
 private fun click(a:SettingsActivity,key:String){
  val owner=a.window.decorView.findViewWithTag<View>(key)
  owner.requestFocus();idle()
  owner.performClick();idle()
 }
 private fun choose(a:SettingsActivity,key:String,index:Int){
  click(a,key);ShadowDialog.getLatestDialog().findViewById<View>(android.R.id.content).findViewWithTag<View>("choice_$index").performClick();idle()
 }
 private fun allTexts(v:View):List<android.widget.TextView> = if(v is android.widget.TextView)listOf(v) else if(v is ViewGroup)(0 until v.childCount).flatMap{allTexts(v.getChildAt(it))} else emptyList()
 @Test fun actualDialogsHaveNoBlankFooterAndBackRestoresOwner(){
  context.getSharedPreferences("dream_settings",0).edit().clear().commit()
  val controller=Robolectric.buildActivity(SettingsActivity::class.java).setup();val a=controller.get();val repo=SettingsRepository(context);val saved=repo.load()
  for(key in listOf("image_mode")){
   click(a,key);val d=ShadowDialog.getLatestDialog();draw(d,"actual-$key").first.recycle()
   val surface=d.window!!.decorView.findViewWithTag<View>("dialog_surface")
   val panel=d.window!!.decorView.findViewWithTag<View>("dialog_panel")
   assertEquals(surface.height,panel.height)
   assertEquals(1,allTexts(panel).count{it.text.toString()=="✓"})
   d.onBackPressed();idle();assertFalse(d.isShowing)
   assertTrue(a.window.decorView.findViewWithTag<View>(key).hasFocus())
   assertEquals(saved,repo.load());assertFalse(a.draft.dirty)
  }
  a.window.decorView.findViewWithTag<View>("section_1").requestFocus();idle()
  click(a,"time");val d=ShadowDialog.getLatestDialog();draw(d,"actual-time").first.recycle()
  val first=d.window!!.decorView.findViewWithTag<View>("choice_0");val second=d.window!!.decorView.findViewWithTag<View>("choice_1")
  assertTrue(second.hasFocus())
  // Android ViewRoot performs D-pad focus traversal after key dispatch. Exercise that platform route directly.
  assertSame(first,second.focusSearch(View.FOCUS_UP));second.focusSearch(View.FOCUS_UP).requestFocus();idle();assertTrue(first.hasFocus())
  assertSame(second,first.focusSearch(View.FOCUS_DOWN));first.focusSearch(View.FOCUS_DOWN).requestFocus();idle();assertTrue(second.hasFocus())
  first.performClick();idle();assertFalse(a.draft.current.hour24);assertTrue(repo.load().hour24)
  assertTrue(a.window.decorView.findViewWithTag<View>("time").hasFocus())
  controller.pause().stop().destroy()
 }
 @Test fun thirteenPhotoRowsScrollAndRestoreFocusWithoutSaving(){
  context.getSharedPreferences("dream_settings",0).edit().clear().commit()
  val controller=Robolectric.buildActivity(SettingsActivity::class.java).setup();val a=controller.get();val repo=SettingsRepository(context)
  choose(a,"image_mode",1);click(a,"image")
  val d=ShadowDialog.getLatestDialog();draw(d,"actual-photo-picker").first.recycle()
  val scroll=d.window!!.decorView.findViewWithTag<BoundedDialogScroll>("dialog_scroll")
  val panel=d.window!!.decorView.findViewWithTag<ViewGroup>("dialog_panel")
  assertEquals(14,panel.childCount);assertTrue(panel.height>scroll.height);assertTrue(scroll.canScrollVertically(1))
  val last=d.window!!.decorView.findViewWithTag<View>("choice_12")
  last.requestFocus();idle();scroll.fullScroll(View.FOCUS_DOWN);idle()
  assertTrue(last.hasFocus());assertTrue(scroll.scrollY>0)
  draw(d,"actual-photo-picker-bottom").first.recycle()
  d.onBackPressed();shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(200))
  assertTrue("image focus=${a.currentFocus?.tag}",a.window.decorView.findViewWithTag<View>("image").hasFocus())
  assertEquals(0,repo.load().imageMode);assertEquals(1,a.draft.current.imageMode)
  controller.pause().stop().destroy()
 }
 @Test fun boundedScrollAlsoRespectsSmallerImeOrWindowSpace(){
  val scroll=BoundedDialogScroll(context)
  scroll.addView(View(context).apply{minimumHeight=2000},android.widget.FrameLayout.LayoutParams(-1,-2))
  scroll.measure(View.MeasureSpec.makeMeasureSpec(1180,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(300,View.MeasureSpec.EXACTLY))
  assertTrue(scroll.measuredHeight<=300)
 }

 @Test fun dismissCannotUndoNewerTabNavigation(){
  context.getSharedPreferences("dream_settings",0).edit().clear().commit()
  val controller=Robolectric.buildActivity(SettingsActivity::class.java).setup();val a=controller.get()
  a.window.decorView.findViewWithTag<View>("section_1").requestFocus();idle();click(a,"time")
  ShadowDialog.getLatestDialog().window!!.decorView.findViewWithTag<View>("choice_0").performClick()
  // A rapid remote action can reach another tab before the dismiss handler is processed.
  a.window.decorView.findViewWithTag<View>("section_0").requestFocus();idle()
  assertNotNull(a.window.decorView.findViewWithTag<View>("night"))
  assertTrue(a.window.decorView.findViewWithTag<View>("section_0").hasFocus())
  controller.pause().stop().destroy()
 }

}
