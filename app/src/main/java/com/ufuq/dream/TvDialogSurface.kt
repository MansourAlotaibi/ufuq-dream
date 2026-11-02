package com.ufuq.dream

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ScrollView

/** The rounded app surface owns every visible dialog pixel; the native window owns none.
 * Short lists wrap; long lists scroll within the same TV-safe maximum. No fill-viewport footer.
 */
internal object TvDialogSurface {
    fun show(dialog:Dialog,content:View,theme:AmbientTheme,widthPx:Int,direction:Int=View.LAYOUT_DIRECTION_RTL){
        val context=dialog.context
        fun dp(value:Int)=(value*context.resources.displayMetrics.density).toInt()
        content.background=null
        content.minimumHeight=0
        content.layoutDirection=direction
        content.tag="dialog_panel"
        val scroll=BoundedDialogScroll(context).apply{
            tag="dialog_scroll"
            background=null
            minimumHeight=0
            isFillViewport=false
            isFocusable=false
            layoutDirection=direction
            // D-pad focus and automatic scrolling provide navigation; omit phone-style edge chrome.
            isVerticalScrollBarEnabled=false
            isVerticalFadingEdgeEnabled=false
            overScrollMode=View.OVER_SCROLL_NEVER
            setScrollIndicators(0)
            addView(content,FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        val surface=FrameLayout(context).apply{
            tag="dialog_surface"
            minimumHeight=0;minimumWidth=0
            layoutDirection=direction
            background=GradientDrawable().apply{setColor(theme.base);cornerRadius=dp(20).toFloat()}
            clipToOutline=true
            addView(scroll,FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        dialog.setContentView(surface)
        dialog.show()
        dialog.window?.apply{
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            decorView.setPadding(0,0,0,0)
            setLayout(widthPx.coerceAtMost(context.resources.displayMetrics.widthPixels-dp(64)).coerceAtLeast(1),ViewGroup.LayoutParams.WRAP_CONTENT)
        }
    }
}

internal class BoundedDialogScroll(context:Context):ScrollView(context){
    internal val maximumHeight:Int get(){
        val density=resources.displayMetrics.density
        val safe=(resources.displayMetrics.heightPixels-(80*density).toInt()).coerceAtLeast(1)
        return minOf((420*density).toInt(),safe)
    }
    override fun onMeasure(widthMeasureSpec:Int,heightMeasureSpec:Int){
        val available=if(MeasureSpec.getMode(heightMeasureSpec)==MeasureSpec.UNSPECIFIED)maximumHeight
            else minOf(maximumHeight,MeasureSpec.getSize(heightMeasureSpec))
        super.onMeasure(widthMeasureSpec,MeasureSpec.makeMeasureSpec(available,MeasureSpec.AT_MOST))
    }
}
