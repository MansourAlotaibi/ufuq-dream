package com.ufuq.dream
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.graphics.Rect
import android.widget.FrameLayout
import java.util.Date

class AmbientDisplay @JvmOverloads constructor(
 context:Context,
 private val settingsSource: () -> DisplaySettings = { SettingsRepository(context).load() },
 private val imageLimit: Int = 1920,
 startScene: Int = 0,
 private val sceneTransitions:Boolean = true
):FrameLayout(context){
 private val handler=Handler(Looper.getMainLooper())
 internal val backgroundRenderer=BackgroundRenderer(context,imageLimit)
 internal val motionLayer=MotionLayer(context)
 private val zone=FrameLayout(context)
 internal val contrastVeil=TextContrastVeil(context)
 private var isMotionBackground=false
 private var rawNight=0f
 private var currentLuminance:Float?=null
 private val samplingViewport=android.graphics.RectF()
 private var currentPhoto=BackgroundRepository.first(-1)
 private var measuredContentWidth=0
 internal var visualContentBounds=android.graphics.RectF();private set
 private val burn=BurnInController()
 private val rotation=CinematicRotation()
 private val startOffset=startScene
 private var lastImage=""
 private val prefetch=Runnable { if(alive && settings.imageMode==0 && settings.rotationMinutes>0){
  val now=android.os.SystemClock.elapsedRealtime()
  val next=MediaLibrary.selected(settings,rotation.index+1,startOffset)
  backgroundRenderer.preload(next.photo.fileName,if(next.clip!=null)minOf(imageLimit,960)else imageLimit)
 }}
 private var lastScene=-1
 private var transitioning=false
 private var waitingForImage=false
 private var epoch=0
 internal val awaitingImage get()=waitingForImage
 private var renderer:SceneRenderer?=null
 private var scene=-1;private var alive=false
 private var settings=settingsSource()
 private val presentationNow=Date()
 var testNow:Date?=null
 internal val settingsSnapshot get()=settings
 val isRunning get()=alive
 init{addView(backgroundRenderer,LayoutParams(-1,-1));addView(motionLayer,LayoutParams(-1,-1));addView(contrastVeil,LayoutParams(-1,-1));addView(zone,LayoutParams(1,1));setWillNotDraw(false);motionLayer.onLoopBoundary={if(alive)refresh(atMotionBoundary=true)}}
 private val tick=object:Runnable{override fun run(){if(!alive)return;refresh();val period=if(settings.seconds)1000L else 60_000L;handler.postDelayed(this,period-Math.floorMod(System.currentTimeMillis(),period))}}
 private val rotate = object:Runnable { override fun run(){ if(alive)refresh() }}
 private val movement=object:Runnable{override fun run(){if(!alive)return
  renderer?.let{burn.apply(zone,it.view,settings)}
  handler.postDelayed(this,60_000L)
 }}
 fun start(fadeIn:Boolean=false){stop();zone.alpha=1f;backgroundRenderer.alpha=1f;alpha=if(fadeIn)0f else 1f;alive=true;waitingForImage=fadeIn;
 backgroundRenderer.onReady={if(alive){requestLayout();if(waitingForImage)reveal()}};rotation.resume(android.os.SystemClock.elapsedRealtime());lastImage="";scene=-1;burn.reset();handler.post(tick);handler.postDelayed(movement,60_000L)}
 fun refresh(atMotionBoundary:Boolean=false){
  if(transitioning)return
  val saved=settingsSource()
  val now=android.os.SystemClock.elapsedRealtime()
  val automatic=saved.imageMode==0&&saved.rotationMinutes>0
  val step=rotation.step(now,if(automatic)saved.rotationMinutes else 0,
   isMotionBackground&&motionLayer.session.state==MotionState.PLAYING,atMotionBoundary)
  val media=MediaLibrary.selected(saved,step,startOffset)
  val photo=media.photo
  val selected=photo.category
  if(alive && sceneTransitions && lastImage.isNotEmpty() && media.id!=lastImage){
   transitioning=true
   val ticket=epoch
   animate().alpha(0f).setDuration(300).withEndAction {
    if(alive&&ticket==epoch){transitioning=false;lastScene=-1;lastImage="";waitingForImage=true;refresh()}
   }.start()
   return
  }
  if(media.id!=lastImage)currentLuminance=null
  isMotionBackground=media.clip!=null
  backgroundRenderer.motionPoster=isMotionBackground
  currentPhoto=photo
  settings=saved.copy(photoCategory=selected,mode=0,intensity=1,alternate=false,dim=false,background=0)
  if(renderer==null||scene!=0){scene=0;build(0)}
  val theme=ThemeManager.get(settings.theme)
  backgroundRenderer.configurePhoto(theme,photo,if(media.clip!=null)minOf(imageLimit,960)else imageLimit)
  val clockNow=testNow?:presentationNow.apply{time=System.currentTimeMillis()}
  rawNight=NightMode.backgroundDim(clockNow,saved.nightDim)
  backgroundRenderer.nightLevel=if(isMotionBackground)MotionContrastPolicy.nightDim(rawNight,currentLuminance)else rawNight
  motionLayer.configure(if(alive)media.clip else null,if(photo.composition.left>.5f)0 else 1,backgroundRenderer.nightLevel)
  renderer?.update(clockNow,settings,theme)
  handler.removeCallbacks(rotate);handler.removeCallbacks(prefetch)
  if(alive&&saved.imageMode==0&&saved.rotationMinutes>0){
   val remaining=rotation.remaining(now,saved.rotationMinutes)
   // Once due, the playing clip's end callback advances the playlist. Never busy-poll.
   if(remaining>0){handler.postDelayed(rotate,remaining);handler.postDelayed(prefetch,(remaining-5000).coerceAtLeast(1000))}
  }
  lastScene=selected;lastImage=media.id
  requestLayout()
  if(waitingForImage && backgroundRenderer.loadedAsset==photo.fileName)reveal()
 }
 private fun reveal(){
  if(!alive||!waitingForImage)return
  waitingForImage=false
  val ticket=epoch
  post {if(alive&&ticket==epoch)animate().alpha(1f).setStartDelay(0).setDuration(600).start()}
 }
 private fun build(mode:Int){measuredContentWidth=0;renderer?.dispose();zone.removeAllViews();renderer=ClockSceneRenderer(context,mode)
 zone.addView(renderer!!.view,LayoutParams(LayoutParams.MATCH_PARENT,LayoutParams.WRAP_CONTENT))}
 override fun onMeasure(widthSpec:Int,heightSpec:Int){
 val width=MeasureSpec.getSize(widthSpec);val height=MeasureSpec.getSize(heightSpec)
 backgroundRenderer.measure(MeasureSpec.makeMeasureSpec(width,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(height,MeasureSpec.EXACTLY))
 motionLayer.measure(MeasureSpec.makeMeasureSpec(width,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(height,MeasureSpec.EXACTLY))
 contrastVeil.measure(MeasureSpec.makeMeasureSpec(width,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(height,MeasureSpec.EXACTLY))
 val spec=ClockPlacement.spec(width,height,resources.displayMetrics.density,settings.clockPosition,currentPhoto.composition)
 renderer?.let{
  if(measuredContentWidth!=spec.contentWidth){
   measuredContentWidth=spec.contentWidth;it.setAvailableWidth(spec.contentWidth)
   it.update(testNow?:presentationNow,settings,ThemeManager.get(settings.theme))
  }
  if(it.view.layoutParams.width!=spec.contentWidth)it.view.layoutParams=it.view.layoutParams.apply{this.width=spec.contentWidth}
 }
 zone.measure(MeasureSpec.makeMeasureSpec(spec.zone.width(),MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(spec.zone.height(),MeasureSpec.EXACTLY))
 // Measure natural height before applying a coherent whole-group fit, rather than clipping long dates/greetings.
 renderer?.view?.measure(MeasureSpec.makeMeasureSpec(spec.contentWidth,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(0,MeasureSpec.UNSPECIFIED))
 setMeasuredDimension(width,height)
 }
 override fun onLayout(changed:Boolean,l:Int,t:Int,r:Int,b:Int){
 backgroundRenderer.layout(0,0,width,height);motionLayer.layout(0,0,width,height);contrastVeil.layout(0,0,width,height)
 val spec=ClockPlacement.spec(width,height,resources.displayMetrics.density,settings.clockPosition,currentPhoto.composition)
 zone.layout(spec.zone.left,spec.zone.top,spec.zone.right,spec.zone.bottom)
 val child=renderer?.view?:return
 val (bounds,scale)=ClockPlacement.visualBounds(spec,child.measuredWidth,child.measuredHeight)
 if(visualContentBounds!=bounds){child.animate().cancel();child.translationX=0f;child.translationY=0f}
 child.pivotX=0f;child.pivotY=0f;child.scaleX=scale;child.scaleY=scale
 val x=(bounds.left-spec.zone.left).toInt();val y=(bounds.top-spec.zone.top).toInt()
 child.layout(x,y,x+child.measuredWidth,y+child.measuredHeight)
 visualContentBounds.set(bounds)
 if(isMotionBackground){
  samplingViewport.set(0f,0f,width.toFloat(),height.toFloat())
  currentLuminance=backgroundRenderer.brightness(samplingViewport,width,height)
  val dim=MotionContrastPolicy.nightDim(rawNight,currentLuminance)
  backgroundRenderer.nightLevel=dim;motionLayer.updateDimming(dim)
 }
 contrastVeil.update(bounds,backgroundRenderer.brightness(bounds,width,height),settings.clockPosition>=0||isMotionBackground)
 }
 fun contentBounds():Rect {val v=renderer?.view?:return Rect();return Rect(v.left,v.top,v.right,v.bottom)}
 fun reschedule(){handler.removeCallbacks(tick);if(alive)handler.post(tick)}
 fun stop(){epoch++;animate().cancel();backgroundRenderer.onReady=null;waitingForImage=false;transitioning=false;rotation.pause(android.os.SystemClock.elapsedRealtime());backgroundRenderer.animate().cancel();alive=false;zone.animate().cancel();handler.removeCallbacksAndMessages(null);renderer?.dispose();motionLayer.stop();contrastVeil.clear();backgroundRenderer.release()}
 override fun onDetachedFromWindow(){stop();super.onDetachedFromWindow()}
}
