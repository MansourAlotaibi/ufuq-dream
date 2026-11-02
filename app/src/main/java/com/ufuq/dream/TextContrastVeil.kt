package com.ufuq.dream

import android.content.Context
import android.graphics.*
import android.view.View
import kotlin.math.max

/** A soft local scrim; no panel, blur, texture allocation or per-frame brightness sampling. */
internal class TextContrastVeil(context:Context):View(context){
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    private val region=RectF()
    private var strength=-1
    fun update(bounds:RectF,brightness:Float?,manual:Boolean){
        val next=if(!manual)0 else when{brightness==null->46;brightness>.65f->56;brightness>.35f->40;else->24}
        if(region==bounds&&strength==next)return
        region.set(bounds);strength=next
        paint.shader=if(next==0)null else RadialGradient(bounds.centerX(),bounds.centerY(),max(bounds.width(),bounds.height()).coerceAtLeast(1f)*.92f,
            intArrayOf(Color.argb(next*255/100,0,0,0),Color.argb(next*140/100,0,0,0),Color.TRANSPARENT),floatArrayOf(0f,.6f,1f),Shader.TileMode.CLAMP)
        invalidate()
    }
    override fun onDraw(canvas:Canvas){if(strength>0)canvas.drawRect(0f,0f,width.toFloat(),height.toFloat(),paint)}
    fun clear(){paint.shader=null;strength=0;region.setEmpty();invalidate()}
}
