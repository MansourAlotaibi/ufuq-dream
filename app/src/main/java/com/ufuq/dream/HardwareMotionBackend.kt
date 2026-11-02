package com.ufuq.dream

import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecList
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Surface
import java.io.FileInputStream
import java.util.concurrent.Executors
import java.util.concurrent.ExecutorService
import java.util.concurrent.atomic.AtomicBoolean

/** Process-wide, including preview versus dream: never own two hardware decoders. */
internal object DecoderLease {
    private val held=AtomicBoolean(false)
    fun acquire()=held.compareAndSet(false,true)
    /** Old worker releases asynchronously; wait off the UI thread instead of racing the next clip. */
    fun acquireAfterRelease(canceled:AtomicBoolean,timeoutMillis:Long=2000):Boolean {
        val deadline=System.nanoTime()+timeoutMillis*1_000_000L
        while(!canceled.get()){
            if(acquire())return true
            if(System.nanoTime()>=deadline)return false
            Thread.sleep(10)
        }
        return false
    }
    fun release(){held.set(false)}
    internal val active get()=held.get()
}
object MotionSupport {
    internal fun knownHardwareName(name:String):Boolean {
        val n=name.lowercase()
        return listOf("omx.amlogic.","omx.mtk.","omx.qcom.","omx.exynos.","omx.nvidia.","omx.brcm.","c2.qti.","c2.exynos.","c2.amlogic.","c2.mtk.").any{n.startsWith(it)}
    }
    fun decoder(width:Int=1920,height:Int=1080):String? = try {
        MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.firstOrNull{info->
            !info.isEncoder && info.supportedTypes.any{it.equals("video/avc",true)} &&
                (if(Build.VERSION.SDK_INT>=29)info.isHardwareAccelerated&&!info.isSoftwareOnly else knownHardwareName(info.name)) &&
                info.getCapabilitiesForType("video/avc").videoCapabilities.areSizeAndRateSupported(width,height,30.0)
        }?.name
    }catch(_:RuntimeException){null}
}
interface MotionBackend {
    fun onLoopBoundary(listener:()->Unit) {}
    fun play(surface:Surface,clip:MotionClip,onFirstFrame:()->Unit,onFailure:()->Unit)
    fun stop()
}

/** Bounded playback support, independent of the stricter creative curation policy. */
internal object MotionProfile {
    fun accepts(width:Int,height:Int,durationUs:Long):Boolean =
        width in 1..1920 && height in 1..1080 && width*9==height*16 &&
            durationUs in 8_000_000L..180_100_000L
}

/** Synchronous codec API runs ONLY on a private serial worker; UI never waits on native decode.
 * Selects a named hardware AVC decoder. No MediaPlayer software fallback, audio, wake lock or network.
 */
class HardwareMotionBackend(private val context:Context):MotionBackend {
    private val main=Handler(Looper.getMainLooper())
    private var executor:ExecutorService?=null
    private var canceled=AtomicBoolean(true)
    private var boundary:(()->Unit)?=null
    override fun onLoopBoundary(listener:()->Unit){boundary=listener}
    override fun play(surface:Surface,clip:MotionClip,onFirstFrame:()->Unit,onFailure:()->Unit){
        canceled.set(true)
        val stop=AtomicBoolean(false);canceled=stop
        val worker=executor?.takeUnless{it.isShutdown}?:Executors.newSingleThreadExecutor().also{executor=it}
        worker.submit {
            var extractor:MediaExtractor?=null;var codec:MediaCodec?=null;var leased=false;var stage="open"
            try {
                if(stop.get())return@submit
                check(DecoderLease.acquireAfterRelease(stop));leased=true
                val source=MediaExtractor();extractor=source
                if(clip.privateFile)FileInputStream(PersonalMotionStore.file(context,clip)).use{source.setDataSource(it.fd)}
                else context.assets.openFd("motion/${clip.fileName}").use{source.setDataSource(it.fileDescriptor,it.startOffset,it.length)}
                val track=(0 until source.trackCount).firstOrNull{source.getTrackFormat(it).getString(MediaFormat.KEY_MIME)=="video/avc"}?:error("No AVC track")
                val format=source.getTrackFormat(track)
                val w=format.getInteger(MediaFormat.KEY_WIDTH);val h=format.getInteger(MediaFormat.KEY_HEIGHT)
                val duration=format.getLong(MediaFormat.KEY_DURATION)
                require(MotionProfile.accepts(w,h,duration))
                if(format.containsKey(MediaFormat.KEY_FRAME_RATE))require(format.getInteger(MediaFormat.KEY_FRAME_RATE) in 1..30)
                val name=MotionSupport.decoder(w,h)?:error("No approved hardware decoder")
                if(context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0)android.util.Log.d("UfuqMotion","hardwareDecoder=$name ${w}x${h}")
                source.selectTrack(track)
                val decoder=MediaCodec.createByCodecName(name);codec=decoder
                stage="configure";decoder.configure(format,surface,null,0)
                var notified=false
                decoder.setOnFrameRenderedListener({_,_,_->
                    if(!notified&&!stop.get()){
                        notified=true;onFirstFrame()
                        try{decoder.setOnFrameRenderedListener(null,main)}catch(_:RuntimeException){}
                    }
                },main)
                stage="start";decoder.start()
                val info=MediaCodec.BufferInfo()
                var inputEnded=false;var epoch=0L;val watchdog=DecoderWatchdog(System.nanoTime());var lastScheduled=0L
                while(!stop.get()){
                    check(!watchdog.stalled(System.nanoTime())){"Decoder stalled"}
                    if(!inputEnded){
                        stage="input";val input=decoder.dequeueInputBuffer(10_000)
                        if(input>=0){
                            val buffer=decoder.getInputBuffer(input)?:error("Missing input buffer");buffer.clear()
                            val bytes=source.readSampleData(buffer,0)
                            if(bytes<0){decoder.queueInputBuffer(input,0,0,0,MediaCodec.BUFFER_FLAG_END_OF_STREAM);inputEnded=true}
                            else{decoder.queueInputBuffer(input,0,bytes,source.sampleTime,0);source.advance()}
                        }
                    }
                    stage="output";val output=decoder.dequeueOutputBuffer(info,10_000)
                    if(output>=0){
                        watchdog.progressed(System.nanoTime())
                        if(info.size>0){
                            if(epoch==0L)epoch=System.nanoTime()-info.presentationTimeUs*1000L
                            lastScheduled=epoch+info.presentationTimeUs*1000L
                            decoder.releaseOutputBuffer(output,lastScheduled)
                        }else decoder.releaseOutputBuffer(output,false)
                        if(info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0){
                            // Do not flush frames that were scheduled for a future display timestamp.
                            // Only this private worker waits; cancellation is checked at most every 10ms.
                            val loopEnd=PlaybackTiming.loopEnd(lastScheduled,format)
                            while(!stop.get()&&System.nanoTime()<loopEnd)Thread.sleep(10)
                            if(stop.get())break
                            main.post{if(!stop.get())boundary?.invoke()}
                            // Synchronous-mode flush: clear obsolete indices, seek to the first sync sample, restart timestamps.
                            stage="loop-flush";decoder.flush();source.seekTo(0,MediaExtractor.SEEK_TO_CLOSEST_SYNC);inputEnded=false;epoch=0L
                            watchdog.progressed(System.nanoTime()) // Intentional final-frame wait is not decoder inactivity.
                        }
                    }
                }
            }catch(error:Exception){if(!stop.get()){if(context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0)android.util.Log.w("UfuqMotion","Playback failed at $stage: ${error.javaClass.simpleName}: ${error.message}");main.post{if(!stop.get())onFailure()}}}
            catch(_:OutOfMemoryError){if(!stop.get())main.post{if(!stop.get())onFailure()}}
            finally{
                try{codec?.stop()}catch(_:RuntimeException){}
                try{codec?.release()}catch(_:RuntimeException){}
                try{extractor?.release()}catch(_:RuntimeException){}
                if(leased)DecoderLease.release()
            }
        }
    }
    override fun stop(){canceled.set(true);executor?.shutdown();executor=null;main.removeCallbacksAndMessages(null)}
}

/** Keep the final queued frame for one frame period before seeking. No main-thread sleeps. */
internal object PlaybackTiming {
    fun loopEnd(lastScheduled:Long,format:MediaFormat):Long {
        val fps=if(format.containsKey(MediaFormat.KEY_FRAME_RATE))format.getInteger(MediaFormat.KEY_FRAME_RATE).coerceIn(1,30)else 30
        return lastScheduled+1_000_000_000L/fps
    }
}

/** Output inactivity only; reset after an intentional timed loop drain as well as decoded output. */
internal class DecoderWatchdog(startNanos:Long){
    private var lastProgress=startNanos
    fun progressed(nowNanos:Long){lastProgress=nowNanos}
    fun stalled(nowNanos:Long)=nowNanos-lastProgress>=8_000_000_000L
}
