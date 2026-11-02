package com.ufuq.dream

import android.graphics.Rect
import android.graphics.RectF
import kotlin.math.roundToInt

/** Normalized composition metadata belongs to artwork, never to its palette. */
data class PhotoComposition(val left:Float,val top:Float,val right:Float,val bottom:Float,val anchorY:Float){
    companion object {
        fun forAsset(id:String,category:Int):PhotoComposition = when(id){
            "alula_1"->PhotoComposition(.055f,.08f,.43f,.86f,.18f)
            "alula_2"->PhotoComposition(.58f,.09f,.95f,.88f,.36f)
            "alula_3","alula_4"->PhotoComposition(.055f,.10f,.43f,.86f,.38f)
            "alula_5"->PhotoComposition(.055f,.10f,.44f,.90f,.42f)
            "riyadh_3","riyadh_4"->PhotoComposition(.055f,.065f,.43f,.72f,.30f)
            else->when(category){
                0->PhotoComposition(.58f,.13f,.95f,.86f,.47f)
                2->PhotoComposition(.055f,.06f,.43f,.72f,.30f)
                else->PhotoComposition(.055f,.13f,.43f,.86f,.44f)
            }
        }
    }
}
data class PlacementSpec(val zone:Rect,val contentWidth:Int,val x:Float,val y:Float,val reserve:Int)
object ClockPlacement {
    const val AUTOMATIC=-1
    val labels=listOf("أعلى اليسار","أعلى الوسط","أعلى اليمين","المنتصف يسارًا","الوسط","المنتصف يمينًا","أسفل اليسار","أسفل الوسط","أسفل اليمين")
    fun label(value:Int)=if(value in 0..8)labels[value] else "تلقائي"
    fun spec(width:Int,height:Int,density:Float,position:Int,composition:PhotoComposition):PlacementSpec {
        val safe=Rect((width*.05f).roundToInt(),(height*.06f).roundToInt(),(width*.95f).roundToInt(),(height*.94f).roundToInt())
        val manual=position in 0..8
        val zone=if(manual)safe else Rect((width*composition.left).roundToInt(),(height*composition.top).roundToInt(),(width*composition.right).roundToInt(),(height*composition.bottom).roundToInt()).apply{intersect(safe)}
        val reserve=minOf((12*density).roundToInt(),zone.width()/8,zone.height()/8).coerceAtLeast(0)
        val contentWidth=minOf((width*.38f).roundToInt(),zone.width()-2*reserve).coerceAtLeast(1)
        return PlacementSpec(zone,contentWidth,if(manual)(position%3)/2f else .5f,if(manual)(position/3)/2f else composition.anchorY,reserve)
    }
    fun visualBounds(spec:PlacementSpec,naturalWidth:Int,naturalHeight:Int):Pair<RectF,Float>{
        val roomW=(spec.zone.width()-2*spec.reserve).coerceAtLeast(1)
        val roomH=(spec.zone.height()-2*spec.reserve).coerceAtLeast(1)
        val scale=minOf(1f,roomW.toFloat()/naturalWidth.coerceAtLeast(1),roomH.toFloat()/naturalHeight.coerceAtLeast(1))
        val w=naturalWidth*scale;val h=naturalHeight*scale
        val left=spec.zone.left+spec.reserve+(roomW-w)*spec.x
        val top=spec.zone.top+spec.reserve+(roomH-h)*spec.y
        return RectF(left,top,left+w,top+h) to scale
    }
}
