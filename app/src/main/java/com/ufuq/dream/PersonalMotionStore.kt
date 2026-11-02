package com.ufuq.dream

import android.content.Context
import org.json.JSONObject
import java.io.File

/** Optional user-owned local media. No network, external-storage access or bundled stock footage.
 * Catalog is atomically published after files are verified by the personal provisioning workflow.
 * Only bounded, relative filenames inside this app's internal files directory are accepted.
 */
internal object PersonalMotionStore {
    private var token=""
    private var snapshot:List<MotionClip> = emptyList()
    private val filename=Regex("[a-z0-9_-]+\\.mp4")
    @Synchronized fun load(context:Context):List<MotionClip>{
        val catalog=File(context.filesDir,"personal-motion/catalog.json")
        val key="${catalog.absolutePath}:${catalog.lastModified()}:${catalog.length()}"
        if(key==token)return snapshot
        token=key
        snapshot=try{
            if(!catalog.isFile||catalog.length() !in 1..32768)emptyList() else {
            val json=JSONObject(catalog.readText(Charsets.UTF_8))
            require(json.optString("usage")=="personal-only")
            val entries=json.getJSONArray("clips");require(entries.length()<=12)
            val seen=mutableSetOf<String>()
            buildList {
                for(i in 0 until entries.length()){
                    val item=entries.getJSONObject(i)
                    val name=item.getString("fileName")
                    val category=item.getInt("category")
                    require(filename.matches(name)&&category in -1..2&&seen.add(name))
                    val file=File(catalog.parentFile,name)
                    require(file.isFile&&file.length() in 1024..128L*1024*1024)
                    require(file.canonicalFile.parentFile==catalog.parentFile!!.canonicalFile)
                    val poster=BackgroundRepository.find(item.getString("posterId"))
                    // General Saudi scenery (-1) belongs only in All, never falsely in AlUla/Diriyah.
                    require(category==-1||poster.category==category)
                    if(category==-1)require(File(catalog.parentFile,name.removeSuffix(".mp4")+".jpg").let{it.isFile&&it.length() in 1..2L*1024*1024})
                    add(MotionClip("personal_${name.removeSuffix(".mp4")}",item.getString("title").take(80),category,name,poster.id,privateFile=true,posterFileName=File(catalog.parentFile,name.removeSuffix(".mp4")+".jpg").takeIf{it.isFile}?.let{"personal:"+it.name}))
                }
            }.toList()
            }
        }catch(_:Exception){emptyList()}
        return snapshot
    }
    fun file(context:Context,clip:MotionClip):File {
        require(clip.privateFile&&filename.matches(clip.fileName))
        val directory=File(context.filesDir,"personal-motion").canonicalFile
        val file=File(directory,clip.fileName).canonicalFile
        require(file.parentFile==directory&&file.isFile&&file.length() in 1024..128L*1024*1024)
        return file
    }
    fun openPoster(context:Context,name:String):java.io.InputStream {
        val relative=name.removePrefix("personal:")
        require(Regex("[a-z0-9_-]+\\.jpg").matches(relative))
        val directory=File(context.filesDir,"personal-motion").canonicalFile
        val file=File(directory,relative).canonicalFile
        require(file.parentFile==directory&&file.isFile&&file.length() in 1..2L*1024*1024)
        return file.inputStream()
    }
    internal fun reset(){token="";snapshot=emptyList()}
}
