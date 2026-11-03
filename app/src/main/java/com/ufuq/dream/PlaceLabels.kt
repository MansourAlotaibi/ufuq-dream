package com.ufuq.dream

import android.content.Context
import org.json.JSONArray

/** Verified photo location, independent of category/theme/lighting and of artistic picker titles. */
object PlaceLabels {
    internal data class Name(val ar:String,val en:String)
    @Volatile private var cached:Map<String,Name>?=null
    private fun valid(value:String)=value.isNotBlank()&&value.length<=96&&value.none{it.isISOControl()||it in '\u202a'..'\u202e'||it in '\u2066'..'\u2069'}
    internal fun parse(raw:String):Map<String,Name> {
        val items=JSONArray(raw);require(items.length()<=128)
        return buildMap {
            for(i in 0 until items.length()){
                val row=items.getJSONObject(i);val location=row.optJSONObject("displayLocation")?:continue
                val id=row.getString("id");val ar=location.optString("ar").trim();val en=location.optString("en").trim()
                if(id.length<=80&&valid(ar)&&valid(en))put(id,Name(ar,en))
            }
        }
    }
    @Synchronized private fun locations(context:Context):Map<String,Name> {
        cached?.let{return it}
        val names=try {context.applicationContext.assets.open("photo-provenance.json").bufferedReader().use{parse(it.readText())}}
            catch(_:Exception){emptyMap()}
        cached=names;return names
    }
    fun forMedia(context:Context,media:MediaItem,language:String):String {
        // An imported clip's stock poster or category cannot establish the clip's actual location.
        if(media.clip!=null)return if(media.clip.privateFile){if(language=="en")"Personal video" else "فيديو شخصي"}
            else {if(language=="en")"Motion background" else "خلفية متحركة"}
        val name=locations(context)[media.photo.id]
        return if(name!=null){if(language=="en")name.en else name.ar}
            else if(language=="en")"Saudi scenes" else "مشاهد سعودية"
    }
}
