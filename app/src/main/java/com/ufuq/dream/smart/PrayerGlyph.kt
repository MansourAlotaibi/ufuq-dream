package com.ufuq.dream.smart

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable

/** Decorative period symbols only. Never used to calculate or infer prayer times. */
class PrayerGlyph(private val prayer:Int,ink:Int):Drawable(){
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=ink;strokeWidth=1.5f;strokeCap=Paint.Cap.ROUND;style=Paint.Style.STROKE }
    override fun draw(canvas:Canvas){
        val saved=canvas.save()
        canvas.translate(bounds.left.toFloat(),bounds.top.toFloat());canvas.scale(bounds.width()/24f,bounds.height()/24f)
        if(prayer==4){
            canvas.drawPath(Path().apply{moveTo(14f,3f);cubicTo(4f,1f,1f,15f,10f,20f);cubicTo(15f,23f,21f,19f,21f,14f);cubicTo(11f,17f,7f,8f,14f,3f)},paint)
            canvas.drawLine(18f,3f,18f,7f,paint);canvas.drawLine(16f,5f,20f,5f,paint)
        }else{
            val cy=when(prayer){0,3->17f;1->10f;else->13f}
            if(prayer==0||prayer==3)canvas.drawArc(RectF(8f,cy-4f,16f,cy+4f),180f,180f,false,paint)
            else canvas.drawCircle(12f,cy,4f,paint)
            for(i in 0..4){
                val a=Math.PI+i*Math.PI/4
                canvas.drawLine(12f+6f*kotlin.math.cos(a).toFloat(),cy+6f*kotlin.math.sin(a).toFloat(),12f+8f*kotlin.math.cos(a).toFloat(),cy+8f*kotlin.math.sin(a).toFloat(),paint)
            }
            val horizon=if(prayer==0||prayer==3)17f else 20f
            canvas.drawLine(3f,horizon,21f,horizon,paint)
            if(prayer==3)canvas.drawLine(6f,21f,18f,21f,paint)
        }
        canvas.restoreToCount(saved)
    }
    override fun setAlpha(alpha:Int){paint.alpha=alpha;invalidateSelf()}
    override fun setColorFilter(filter:ColorFilter?){paint.colorFilter=filter;invalidateSelf()}
    @Deprecated("Drawable opacity") override fun getOpacity()=PixelFormat.TRANSLUCENT
}
