package com.ufuq.dream

/** Active-time playlist. A due video advances at its natural end, not mid-shot.
 * This schedules real clips; it does not fabricate a longer source duration.
 */
internal class CinematicRotation {
    private var accumulated=0L
    private var resumed:Long?=null
    private var itemStarted=0L
    var index=0L; private set
    fun resume(now:Long){if(resumed==null)resumed=now}
    fun pause(now:Long){accumulated=elapsed(now);resumed=null}
    private fun elapsed(now:Long)=accumulated+(resumed?.let{(now-it).coerceAtLeast(0)}?:0L)
    fun remaining(now:Long,minutes:Int):Long = if(minutes<=0)Long.MAX_VALUE
        else (minutes*60_000L-(elapsed(now)-itemStarted)).coerceAtLeast(0L)
    fun step(now:Long,minutes:Int,waitForVideoEnd:Boolean,boundary:Boolean=false):Long {
        if(resumed!=null && minutes>0 && remaining(now,minutes)==0L && (!waitForVideoEnd||boundary)){
            index++;itemStarted=elapsed(now)
        }
        return index
    }
}
