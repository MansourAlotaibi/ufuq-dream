package com.ufuq.dream

/** Category controls photo filtering/layout only. Theme controls palette independently. */
data class BackgroundImage(val id:String,val title:String,val category:Int,val fileName:String,val description:String="",val composition:PhotoComposition=PhotoComposition.forAsset(id,category))
object BackgroundRepository {
 val images=listOf(
  BackgroundImage("alula_1","العلا · نافذة الغروب",1,"alula_1.jpg","Deep sandstone shade on the left; sunstar is the small focal accent."),
  BackgroundImage("alula_2","العلا · صخر ونجوم",1,"alula_2.jpg","Warm sculptural rocks against a restrained Milky Way; text left."),
  BackgroundImage("alula_3","العلا · قوس السماء",1,"alula_3.jpg","Arch occupies the right; quiet sky and rock field on the left."),
  BackgroundImage("alula_4","العلا · مرآة جبل الفيل",1,"alula_4.jpg","Reflection and camel scale on the right; keep left text understated."),
  BackgroundImage("alula_5","العلا · درب النجوم",1,"alula_5.jpg","Canyon framing, dark left wall supports live text."),
  BackgroundImage("diriyah_1","الدرعية · ممر الطين",0,"diriyah_1.jpg","Clean converging walls; right-side text over the quiet clay plane."),
  BackgroundImage("diriyah_2","الدرعية · إيقاع نجد",0,"diriyah_2.jpg","Retained authored Najdi roof rhythm and warm earthen planes."),
  BackgroundImage("diriyah_3","الدرعية · ظلال الطريف",0,"diriyah_3.jpg","Sculptural facade and warm stone; no foreground obstruction."),
  BackgroundImage("riyadh_1","الرياض · أفق العاصمة",2,"riyadh_1.jpg","Retained featured skyline; strong visual hierarchy on right."),
  BackgroundImage("riyadh_2","الرياض · شرايين الليل",2,"riyadh_2.jpg","Retained distinct wide city-at-night vista, quiet upper-left sky."),
  BackgroundImage("riyadh_3","الرياض · قمر الماليّة",2,"riyadh_3.jpg","Retained moon/night composition; open dark left field."),
  BackgroundImage("riyadh_4","الرياض · آخر الضوء",2,"riyadh_4.jpg","Golden dusk, skyline right of center, quiet upper left."),
  BackgroundImage("riyadh_5","الرياض · وهج المدينة",2,"riyadh_5.jpg","Contemporary towers in night air; warm road lines below text.")
 )
 fun group(category:Int):List<BackgroundImage> {
  if(category>=0)return images.filter{it.category==category}
  val groups=(0..2).map{c->images.filter{it.category==c}}
  return buildList {for(i in 0 until groups.maxOf{it.size})groups.forEach{group->group.getOrNull(i)?.let{add(it)}}}
 }
 fun first(category:Int)=group(category).firstOrNull()?:images.first()
 fun find(id:String)=images.firstOrNull{it.id==id}?:images.first()
 fun selected(s:DisplaySettings,step:Long,start:Int=0):BackgroundImage {
  if(s.imageMode==1||s.rotationMinutes==0){
   val image=find(s.imageId)
   return if(s.sceneChoice<0||image.category==s.sceneChoice)image else first(s.sceneChoice)
  }
  val group=group(s.sceneChoice)
  return group[Math.floorMod(if(s.rotationMinutes==0)0L else step+start,group.size.toLong()).toInt()]
 }
}

/** Active-time clock; stopping does not charge invisible wall time. */
class BackgroundRotation {
 private var accumulated=0L
 private var resumed:Long?=null
 fun resume(now:Long){if(resumed==null)resumed=now}
 fun pause(now:Long){resumed?.let{accumulated+=(now-it).coerceAtLeast(0)};resumed=null}
 private fun elapsed(now:Long)=accumulated+(resumed?.let{(now-it).coerceAtLeast(0)}?:0)
 fun step(now:Long,minutes:Int)=if(minutes<=0)0 else elapsed(now)/(minutes*60_000L)
 fun remaining(now:Long,minutes:Int)=if(minutes<=0)Long.MAX_VALUE else minutes*60_000L-elapsed(now)%(minutes*60_000L)
}
