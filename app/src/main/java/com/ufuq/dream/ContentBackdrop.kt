package com.ufuq.dream

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import kotlin.math.ceil
import kotlin.math.floor

/** Paint around visible text, not the larger safe region reserved for widget placement.
 * No bitmap, animation, layout mutation or text shortening. Canvas transforms also serve Preview.
 */
class ContentBackdrop(
    private val owner:ViewGroup,
    colors:IntArray,
    cornerDp:Int,
    private val horizontalDp:Int,
    private val verticalDp:Int,
    private val fullWidth:Boolean=false
):Drawable(){
    private val shape=GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,colors).apply{
        cornerRadius=cornerDp*owner.resources.displayMetrics.density
    }
    private val ink=Rect()
    private val scratch=Rect()
    private val density get()=owner.resources.displayMetrics.density
    internal fun contentBounds():Rect {
        ink.setEmpty()
        if(owner.visibility==View.VISIBLE&&owner.width>0&&owner.height>0)collect(owner)
        if(!ink.isEmpty){
            ink.inset(-(horizontalDp*density).toInt(),-(verticalDp*density).toInt())
            if(!ink.intersect(0,0,owner.width,owner.height))ink.setEmpty()
            if(fullWidth && !ink.isEmpty){ink.left=0;ink.right=owner.width}
        }
        return Rect(ink)
    }
    private fun collect(view:View){
        if(view.visibility!=View.VISIBLE)return
        if(view is TextView && view.text.isNotBlank()){
            val layout=view.layout?:return
            if(view.width<=0||view.height<=0||layout.lineCount==0)return
            var left=Float.POSITIVE_INFINITY;var right=Float.NEGATIVE_INFINITY
            for(line in 0 until layout.lineCount){left=minOf(left,layout.getLineLeft(line));right=maxOf(right,layout.getLineRight(line))}
            // totalPaddingTop includes vertical gravity; the layout can occupy only a small
            // part of a tall, safely measured TextView. Keep marks/diacritics and padding.
            scratch.set(floor(left).toInt()+view.totalPaddingLeft,view.totalPaddingTop,
                ceil(right).toInt()+view.totalPaddingLeft,view.totalPaddingTop+layout.height)
            // Weather's compound icon is part of its content and attribution stays adjacent.
            if(view.tag=="weather_reading" && view.compoundDrawables.any{it!=null})scratch.set(0,0,view.width,view.height)
            if(!scratch.intersect(0,0,view.width,view.height))scratch.setEmpty()
            if(!scratch.isEmpty){owner.offsetDescendantRectToMyCoords(view,scratch);ink.union(scratch)}
        }else if(view is ViewGroup)for(i in 0 until view.childCount)collect(view.getChildAt(i))
    }
    override fun draw(canvas:Canvas){
        val rect=contentBounds()
        if(!rect.isEmpty){shape.bounds=rect;shape.draw(canvas)}
    }
    override fun setAlpha(alpha:Int){shape.alpha=alpha;invalidateSelf()}
    override fun setColorFilter(colorFilter:ColorFilter?){shape.colorFilter=colorFilter;invalidateSelf()}
    @Deprecated("Drawable opacity") override fun getOpacity()=PixelFormat.TRANSLUCENT
}
