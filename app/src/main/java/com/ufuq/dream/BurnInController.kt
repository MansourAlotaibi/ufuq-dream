package com.ufuq.dream
import android.view.View
import kotlin.math.max
import kotlin.math.min
class BurnInController {
 private var step=0
 fun reset(){step=0}
 fun apply(root:View,view:View,s:DisplaySettings){
 if(s.intensity==0){view.animate().cancel();view.translationX=0f;view.translationY=0f;view.alpha=1f;return}
 val density=root.resources.displayMetrics.density
 val travel=10f*density
 val xRoom=max(0f,min(view.left-root.paddingLeft,root.width-root.paddingRight-(view.left+view.width*view.scaleX).toInt()).toFloat())
 val yRoom=max(0f,min(view.top-root.paddingTop,root.height-root.paddingBottom-(view.top+view.height*view.scaleY).toInt()).toFloat())
 val points=arrayOf(1f to -.6f,-.4f to 1f,-1f to -.4f,.5f to -1f)
 val (x,y)=points[step%points.size];step=(step+1)%12
 view.animate().cancel();view.animate().translationX(x*min(travel,xRoom)).translationY(y*min(travel,yRoom))
 .alpha(if(s.dim&&step%2==0).88f else 1f).setDuration(1600).start()
 }
 fun interval(s:DisplaySettings)=when(s.intensity){3->30_000L;2->45_000L;else->60_000L}
}
