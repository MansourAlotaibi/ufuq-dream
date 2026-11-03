package com.ufuq.dream

import android.content.Context
import android.graphics.Typeface
import android.util.LruCache
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import java.util.WeakHashMap

/** Launcher font choices; original OFL files, no network, no altered glyph metrics. */
object UfuqFonts {
    data class Choice(val id:String,val name:String,val file:String?=null)
    val arabic=listOf(Choice("default","الافتراضي"),Choice("cairo","Cairo · القاهرة","Cairo-Regular.ttf"),
        Choice("tajawal","Tajawal · تجوال","Tajawal-Regular.ttf"),Choice("ibm-plex-sans-arabic","IBM Plex Sans Arabic","IBMPlexSansArabic-Regular.ttf"),
        Choice("readex-pro","Readex Pro · ريدكس برو","ReadexPro-Regular.ttf"),Choice("almarai","Almarai · المراعي","Almarai-Regular.ttf"))
    val english=listOf(Choice("default","الافتراضي"),Choice("lato","Lato","Lato-Regular.ttf"))
    private val cache=LruCache<String,Typeface>(3)
    private val originals=WeakHashMap<TextView,Typeface>()
    fun choices(language:String)=if(language=="en")english else arabic
    fun normalize(id:String,language:String)=choices(language).firstOrNull{it.id==id}?.id?:"default"
    fun id(s:DisplaySettings)=if(s.language=="en")s.fontEnglish else s.fontArabic
    fun name(id:String,language:String)=choices(language).firstOrNull{it.id==id}?.name?:"الافتراضي"
    @Synchronized fun face(context:Context,id:String,language:String):Typeface? {
        val file=choices(language).firstOrNull{it.id==id}?.file?:return null
        cache.get(file)?.let{return it}
        return try { Typeface.createFromAsset(context.applicationContext.assets,"fonts/$file").also{cache.put(file,it)} }
        catch(_:RuntimeException){null}
    }
    fun apply(view:View,s:DisplaySettings) {
        if(view is TextView && view !is android.widget.EditText && view.tag !in listOf("clock_digits","reflection_original")) {
            val original=originals.getOrPut(view){view.typeface?:Typeface.DEFAULT}
            val selected=face(view.context,id(s),s.language)
            val desired=if(selected==null)original else Typeface.create(selected,original.style)
            if(view.typeface!=desired)view.typeface=desired
        }
        if(view is ViewGroup)for(i in 0 until view.childCount)apply(view.getChildAt(i),s)
    }
    internal fun cacheSize()=cache.size()
}
