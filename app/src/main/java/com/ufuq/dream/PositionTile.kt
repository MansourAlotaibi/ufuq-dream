package com.ufuq.dream

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout

/** Nine tiny vector diagrams, not nine bitmap/video previews. Physical left/right is explicit. */
@android.annotation.SuppressLint("ViewConstructor") // Programmatic-only tile with explicit slot/actions; never XML inflated.
internal class PositionTile(context:Context,val slot:Int,selected:Boolean,ui:SettingsUi,preview:()->Unit,commit:()->Unit):FrameLayout(context){
    init {
        id=View.generateViewId();tag="position_$slot"
        isFocusable=true;isFocusableInTouchMode=true;isClickable=true
        descendantFocusability=ViewGroup.FOCUS_BLOCK_DESCENDANTS
        background=ui.surface(false,selected)
        val diagram=object:View(context){
            val paint=Paint(Paint.ANTI_ALIAS_FLAG)
            override fun onDraw(canvas:Canvas){
                val w=ui.dp(54).toFloat();val h=w*9/16;val x=(width-w)/2;val y=ui.dp(8).toFloat()
                paint.style=Paint.Style.STROKE;paint.strokeWidth=resources.displayMetrics.density;paint.color=ui.muted
                canvas.drawRoundRect(x,y,x+w,y+h,ui.dp(3).toFloat(),ui.dp(3).toFloat(),paint)
                val dotW=ui.dp(12).toFloat();val dotH=ui.dp(7).toFloat();val inset=ui.dp(4)
                val dx=x+inset+(w-2*inset-dotW)*(slot%3)/2f
                val dy=y+inset+(h-2*inset-dotH)*(slot/3)/2f
                paint.style=Paint.Style.FILL;paint.color=ui.accent
                canvas.drawRoundRect(dx,dy,dx+dotW,dy+dotH,ui.dp(2).toFloat(),ui.dp(2).toFloat(),paint)
            }
        }
        addView(diagram,LayoutParams(-1,ui.dp(43)))
        addView(ui.text((if(selected)"✓  " else "")+ClockPlacement.label(slot),15f).apply{
            gravity=Gravity.CENTER;setPadding(ui.dp(3),0,ui.dp(3),0);maxLines=2
        },LayoutParams(-1,ui.dp(34)).apply{gravity=Gravity.BOTTOM;bottomMargin=ui.dp(3)})
        setOnFocusChangeListener{_,focused->background=ui.surface(focused,selected);if(focused)preview()}
        setOnClickListener{commit()}
    }
}
