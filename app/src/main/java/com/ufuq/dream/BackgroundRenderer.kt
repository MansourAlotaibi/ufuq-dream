package com.ufuq.dream
import android.content.Context
import android.graphics.*
import android.os.Handler
import android.os.Looper
import android.view.View
import java.util.concurrent.Executors
import java.util.concurrent.Future
import kotlin.math.min

/** One image, one serial decode, max 1080p. No allocations in draw and no continuous wallpaper. */
class BackgroundRenderer @JvmOverloads constructor(context:Context, private val imageLimit:Int = 1920):View(context){
 private val paint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
 private val destination=RectF()
 var onReady: (() -> Unit)? = null
 var motionPoster=false
  set(value){if(field!=value){field=value;invalidate()}}
 var nightLevel=0f
  set(value){val next=value.coerceIn(0f,1f);if(field!=next){field=next;invalidate()}}
 private val veil=Paint()
 private val main=Handler(Looper.getMainLooper())
 private var executor=Executors.newSingleThreadExecutor()
 private var task:Future<*>?=null
 private var generation=0
 private var image:Bitmap?=null
 private var requested=""
 private var requestedLimit=imageLimit
 private var nextLimit=imageLimit
 private var pendingLimit=imageLimit
 private var theme=ThemeManager.get(1)
 private var style=0
 private var overlay:Shader?=null
 private var overlayIndex=1
 private var nextImage:Bitmap?=null
 private var nextName=""
 private var pendingName=""
 private var preloadGeneration=0
 private var preloadTask:Future<*>?=null
 val preloadBytes:Int get()=nextImage?.allocationByteCount?:0
 var isReleased=true; private set
 var loadedAsset=""; private set
 val imageBytes:Int get()=image?.allocationByteCount?:0
 fun configurePhoto(value:AmbientTheme,photo:BackgroundImage,decodeLimit:Int=imageLimit)=configure(value,if(photo.composition.left>.5f)0 else 1,0,photo.fileName,decodeLimit)
 fun configure(value:AmbientTheme,index:Int,backgroundStyle:Int,photoName:String?=null,decodeLimit:Int=imageLimit){
 theme=value;style=backgroundStyle;overlayIndex=index
 val name=if(style==0)photoName?:CinematicCatalog.asset(index) else ""
 if(name==requested&&requestedLimit==decodeLimit&&!isReleased){invalidate();return}
 requested=name;requestedLimit=decodeLimit;generation++;val ticket=generation;isReleased=false;task?.cancel(true)
 // Drop old references before starting a new decode; no two-image crossfade peak.
 image=null;loadedAsset="";buildOverlay(index);invalidate()
 if(name.isEmpty())return
 if(nextName==name&&nextLimit==decodeLimit&&nextImage!=null){image=nextImage;nextImage=null;nextName="";loadedAsset=name;invalidate();onReady?.invoke();return}
 nextImage=null;nextName="";pendingName="";preloadGeneration++;preloadTask?.cancel(true)
 if(executor.isShutdown)executor=Executors.newSingleThreadExecutor()
 task=executor.submit {
 val decoded=decode(name,decodeLimit)
 val result=decoded
 main.post {if(ticket==generation&&!isReleased){image=result;loadedAsset=if(result!=null)name else "";invalidate();onReady?.invoke()}}
 }
 }

 private fun openArtwork(name:String):java.io.InputStream =
  if(name.startsWith("personal:"))PersonalMotionStore.openPoster(context,name)else context.assets.open("cinematic/$name")
 private fun decode(name:String,decodeLimit:Int):Bitmap? = try {
  val bounds=BitmapFactory.Options().apply{inJustDecodeBounds=true}
  openArtwork(name).use{BitmapFactory.decodeStream(it,null,bounds)}
  var sample=1
  val limit=minOf(imageLimit,decodeLimit,1920,resources.displayMetrics.widthPixels.coerceAtLeast(240)).coerceAtLeast(240)
  while(bounds.outWidth/sample>limit||bounds.outHeight/sample>limit*9/16)sample*=2
  val options=BitmapFactory.Options().apply{inSampleSize=sample;inPreferredConfig=Bitmap.Config.ARGB_8888;inScaled=false}
  openArtwork(name).use{BitmapFactory.decodeStream(it,null,options)}
 }catch(_:Exception){null}catch(_:OutOfMemoryError){null}
 fun preload(name:String,decodeLimit:Int=imageLimit){
  if(isReleased||(name==requested&&decodeLimit==requestedLimit)||(name==nextName&&decodeLimit==nextLimit)||(name==pendingName&&decodeLimit==pendingLimit)||executor.isShutdown)return
  preloadTask?.cancel(true);nextImage=null;nextName="";pendingName=name;pendingLimit=decodeLimit;preloadGeneration++
  val preloadTicket=preloadGeneration
  val ticket=generation
  preloadTask=executor.submit {
   val bitmap=decode(name,decodeLimit)
   main.post{if(ticket==generation&&preloadTicket==preloadGeneration&&!isReleased){pendingName="";nextImage=bitmap;nextLimit=decodeLimit;nextName=if(bitmap!=null)name else ""}}
  }
 }
 private fun buildOverlay(index:Int){
 val left=index!=0
 overlay=LinearGradient(0f,0f,width.toFloat().coerceAtLeast(1f),0f,
 if(left)intArrayOf(0xbb000000.toInt(),0x22000000) else intArrayOf(0x22000000,0xbb000000.toInt()),null,Shader.TileMode.CLAMP)
 }
 override fun onSizeChanged(w:Int,h:Int,oldw:Int,oldh:Int){buildOverlay(overlayIndex)}
 override fun onDraw(canvas:Canvas){
 super.onDraw(canvas);canvas.drawColor(theme.base)
 val bitmap=image
 if(style==0&&bitmap!=null){
 val scale=min(width.toFloat()/bitmap.width,height.toFloat()/bitmap.height)
 val w=bitmap.width*scale;val h=bitmap.height*scale
 destination.set((width-w)/2,(height-h)/2,(width+w)/2,(height+h)/2)
 paint.shader=null;paint.alpha=255;canvas.drawBitmap(bitmap,null,destination,paint)
 if(!motionPoster){paint.shader=overlay;canvas.drawRect(0f,0f,width.toFloat(),height.toFloat(),paint);paint.shader=null}
 } else if(style==1){
 // Authored image-free horizon relief, deliberately quiet; no simulated photo.
 paint.color=theme.middle;canvas.drawRect(0f,height*.72f,width.toFloat(),height.toFloat(),paint)
 paint.color=theme.accent;paint.alpha=24;paint.strokeWidth=resources.displayMetrics.density
 canvas.drawLine(width*.07f,height*.72f,width*.93f,height*.72f,paint);paint.alpha=255
 }
 if(nightLevel>0f){veil.color=Color.BLACK;veil.alpha=(nightLevel*255).toInt();canvas.drawRect(0f,0f,width.toFloat(),height.toFloat(),veil)}
 }
 /** Sparse poster samples only, never TextureView readback or video-frame copies. */
 fun brightness(bounds:RectF,viewportWidth:Int,viewportHeight:Int):Float? {
  val bitmap=image?:return null
  if(viewportWidth<=0||viewportHeight<=0)return null
  var sum=0f
  for(x in 1..3)for(y in 1..3){
   val px=((bounds.left+bounds.width()*x/4)/viewportWidth*bitmap.width).toInt().coerceIn(0,bitmap.width-1)
   val py=((bounds.top+bounds.height()*y/4)/viewportHeight*bitmap.height).toInt().coerceIn(0,bitmap.height-1)
   val color=bitmap.getPixel(px,py);sum+=(Color.red(color)*.2126f+Color.green(color)*.7152f+Color.blue(color)*.0722f)/255f
  }
  return sum/9
 }
 fun release(){generation++;preloadGeneration++;pendingName="";isReleased=true;preloadTask?.cancel(true);preloadTask=null;nextImage=null;nextName="";task?.cancel(true);task=null;executor.shutdownNow();main.removeCallbacksAndMessages(null);image=null;loadedAsset="";requested="";invalidate()}
 override fun onDetachedFromWindow(){release();super.onDetachedFromWindow()}
}
