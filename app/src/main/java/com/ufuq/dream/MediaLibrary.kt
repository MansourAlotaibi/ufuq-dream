package com.ufuq.dream

/** Only rights-reviewed, H.264 1080p production clips may enter the shipped catalog. */
data class MotionClip(val id:String,val title:String,val category:Int,val fileName:String,val posterId:String,val privateFile:Boolean=false,val posterFileName:String?=null)
data class MediaItem(val photo:BackgroundImage,val clip:MotionClip?=null) {
    val id get()=clip?.id?:photo.id
    val title get()=clip?.title?:photo.title
    val category get()=clip?.category?:photo.category
}
object MediaLibrary {
    // Empty deliberately: no footage passed both provenance and cinematic quality review.
    var clips:List<MotionClip> = emptyList();private set
    internal fun initialize(context:android.content.Context){clips=PersonalMotionStore.load(context)}
    fun items(category:Int,type:Int,motion:List<MotionClip> = clips):List<MediaItem> {
        val photos=BackgroundRepository.group(category).map{MediaItem(it)}
        val videos=motion.filter{category<0||it.category==category}.map{clip->
            val poster=BackgroundRepository.find(clip.posterId)
            MediaItem(if(clip.posterFileName!=null)poster.copy(fileName=clip.posterFileName,category=clip.category)else poster,clip)
        }
        return when(type) {
            1 -> videos.ifEmpty{photos} // Always a usable static fallback, without overwriting the preference.
            2 -> if(videos.isEmpty())photos else buildList {
                val count=maxOf(photos.size,videos.size)
                for(i in 0 until count){photos.getOrNull(i)?.let{add(it)};videos.getOrNull(i)?.let{add(it)}}
            }
            else -> photos
        }
    }
    fun selected(s:DisplaySettings,step:Long,start:Int=0,motion:List<MotionClip> = clips):MediaItem {
        val pool=items(s.sceneChoice,s.backgroundType,motion)
        if(s.imageMode==1||s.rotationMinutes==0){
            val id=s.fixedAssetId.ifBlank{s.imageId}
            return pool.firstOrNull{it.id==id}?:pool.firstOrNull{it.id==s.imageId}?:pool.first()
        }
        return pool[Math.floorMod(step+start,pool.size.toLong()).toInt()]
    }
}
