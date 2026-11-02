package com.ufuq.dream

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.SurfaceTexture
import android.os.Handler
import android.os.Looper
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.widget.FrameLayout

enum class MotionState { IDLE, PREPARING, PLAYING, FALLBACK }
/** Main-thread lifecycle state machine. Errors cannot retry forever or reveal a stale decoder frame. */
class MotionSession(private val factory:()->MotionBackend,private val diagnostics:Boolean=false,private val changed:(MotionState)->Unit) {
    var onLoopBoundary:(()->Unit)?=null
    private var backend:MotionBackend?=null
    private var generation=0
    private var clipId=""
    var state=MotionState.IDLE;private set
    private fun state(value:MotionState){state=value;if(diagnostics)android.util.Log.d("UfuqMotion","state=$value");changed(value)}
    fun start(surface:Surface,clip:MotionClip){
        if(clipId==clip.id)return
        stop();clipId=clip.id;val ticket=generation
        state(MotionState.PREPARING)
        try{
            val player=factory();backend=player
            player.onLoopBoundary{if(ticket==generation&&state==MotionState.PLAYING)onLoopBoundary?.invoke()}
            player.play(surface,clip,{if(ticket==generation&&state==MotionState.PREPARING)state(MotionState.PLAYING)},
                {if(ticket==generation){backend?.stop();backend=null;state(MotionState.FALLBACK)}})
        }catch(_:RuntimeException){backend?.stop();backend=null;state(MotionState.FALLBACK)}
    }
    fun fail(){generation++;backend?.stop();backend=null;state(MotionState.FALLBACK)}
    fun stop(){generation++;backend?.stop();backend=null;clipId="";state(MotionState.IDLE)}
}

/** Actual video pixels share the Dream/Studio viewport; the static poster remains underneath. */
class MotionLayer(context:Context):FrameLayout(context),TextureView.SurfaceTextureListener {
    var onLoopBoundary:(()->Unit)?=null
    private val handler=Handler(Looper.getMainLooper())
    private var texture:TextureView?=null
    private var surface:Surface?=null
    private var clip:MotionClip?=null
    private var category=1
    private var night=0f
    private val shade=object:View(context){
        val paint=Paint()
        fun rebuild(){invalidate()}
        // Contrast belongs to the text-group veil, not a dark gradient across the city.
        override fun onDraw(canvas:Canvas){if(night>0){paint.color=android.graphics.Color.BLACK;paint.alpha=(night*255).toInt();canvas.drawRect(0f,0f,width.toFloat(),height.toFloat(),paint)}}
    }

    internal val session:MotionSession=MotionSession({HardwareMotionBackend(context.applicationContext)},context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0){state->
        texture?.animate()?.cancel();shade.animate().cancel()
        if(state==MotionState.PLAYING){
            texture?.animate()?.alpha(1f)?.setDuration(300)?.start()
            shade.visibility=View.VISIBLE;shade.alpha=0f;shade.animate().alpha(1f).setDuration(300).start()
        }else{
            // A non-zero preparing alpha lets TextureView acquire its surface on older TVs.
            texture?.alpha=if(state==MotionState.PREPARING).001f else 0f
            shade.visibility=View.INVISIBLE
        }
        if(state!=MotionState.PREPARING)handler.removeCallbacks(timeout)
    }
    private val timeout:Runnable=Runnable{if(clip!=null&&session.state!=MotionState.PLAYING)session.fail()}
    init{isFocusable=false;importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS;session.onLoopBoundary={onLoopBoundary?.invoke()}}
    fun configure(value:MotionClip?,photoCategory:Int,nightLevel:Float){
        if(category!=photoCategory){category=photoCategory;if(texture!=null)shade.rebuild()}
        updateDimming(nightLevel)
        if(value==clip)return
        stop();clip=value
        if(value==null)return
        val view=TextureView(context).apply{isOpaque=false;alpha=.001f;surfaceTextureListener=this@MotionLayer;isFocusable=false}
        texture=view;addView(view,LayoutParams(-1,-1));addView(shade,LayoutParams(-1,-1));shade.visibility=View.INVISIBLE;shade.rebuild()
        handler.postDelayed(timeout,8000)
        if(view.isAvailable)view.surfaceTexture?.let{onSurfaceTextureAvailable(it,view.width,view.height)}
    }
    fun updateDimming(value:Float){if(night!=value){night=value;if(texture!=null)shade.invalidate()}}
    override fun onSurfaceTextureAvailable(value:SurfaceTexture,width:Int,height:Int){
        surface?.release();surface=Surface(value)
        clip?.let{session.start(surface!!,it);handler.removeCallbacks(timeout);handler.postDelayed(timeout,8000)}
    }
    override fun onSurfaceTextureSizeChanged(value:SurfaceTexture,width:Int,height:Int){}
    override fun onSurfaceTextureUpdated(value:SurfaceTexture){}
    override fun onSurfaceTextureDestroyed(value:SurfaceTexture):Boolean{session.stop();surface?.release();surface=null;handler.removeCallbacksAndMessages(null);return true}
    fun stop(){handler.removeCallbacksAndMessages(null);session.stop();clip=null;texture?.animate()?.cancel();shade.animate().cancel();texture?.surfaceTextureListener=null;removeAllViews();texture=null;surface?.release();surface=null}
    override fun onDetachedFromWindow(){stop();super.onDetachedFromWindow()}
}
