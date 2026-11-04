package com.ufuq.dream.reflection

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.ufuq.dream.smart.DeviceTime
import com.ufuq.dream.smart.SmartSettings
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.FutureTask
import javax.net.ssl.HttpsURLConnection

object ContentUpdatePolicy {
    const val INTERVAL=15L*24*60*60*1000
    const val DAY=24L*60*60*1000
    fun consent(draft:SmartSettings,saved:SmartSettings)=draft.contentUpdates && saved.contentUpdates && draft.hasReflection && saved.hasReflection
    fun online(context:Context):Boolean=try {
        val manager=context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        manager.getNetworkCapabilities(manager.activeNetwork)?.let{it.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) && it.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)}==true
    }catch(_:SecurityException){false}
    fun due(now:Long,checked:Long,after:Long)=DeviceTime.valid(now) && now>=after && (checked==0L || now>=checked && now-checked>=INTERVAL)
    fun retryDelay(failures:Int)=when(failures){1->DAY;2->3*DAY;3->7*DAY;else->INTERVAL}
}
data class ContentResponse(val status:Int,val bytes:ByteArray=byteArrayOf(),val etag:String="")
class ContentHttpException(val status:Int,val retryAfterMs:Long):java.io.IOException("Content HTTP failure")
interface ContentTransport {fun fetch(etag:String):ContentResponse;fun cancel() {}}
class GithubContentTransport:ContentTransport {
    companion object {const val URL_STRING="https://raw.githubusercontent.com/MansourAlotaibi/ufuq-dream/main/content/library.json"}
    @Volatile private var connection:HttpsURLConnection?=null
    override fun fetch(etag:String):ContentResponse {
        val c=URL(URL_STRING).openConnection() as HttpsURLConnection;connection=c
        c.connectTimeout=8000;c.readTimeout=8000;c.instanceFollowRedirects=false
        c.setRequestProperty("Accept","application/json")
        if(etag.isNotBlank() && etag.length<=200 && etag.none{Character.isISOControl(it)})c.setRequestProperty("If-None-Match",etag)
        try {
            val status=c.responseCode
            if(status==304)return ContentResponse(304)
            if(status!=200){
                val seconds=c.getHeaderField("Retry-After")?.toLongOrNull()
                val retry=seconds?.coerceIn(0,ContentUpdatePolicy.INTERVAL/1000)?.times(1000) ?: (c.getHeaderFieldDate("Retry-After",0)-System.currentTimeMillis()).coerceIn(0,ContentUpdatePolicy.INTERVAL)
                throw ContentHttpException(status,retry)
            }
            DeviceTime.verifyServerDate(c.getHeaderFieldDate("Date",0))
            val deadline=SystemClock.elapsedRealtime()+20000L
            val bytes=c.inputStream.use{input->
                val output=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192)
                while(true){
                    if(Thread.currentThread().isInterrupted)throw java.io.InterruptedIOException()
                    if(SystemClock.elapsedRealtime()>deadline)throw java.net.SocketTimeoutException()
                    val n=input.read(buffer);if(n<0)break
                    require(output.size()+n<=LibraryVerification.MAX_BYTES);output.write(buffer,0,n)
                }
                output.toByteArray()
            }
            return ContentResponse(200,bytes,c.getHeaderField("ETag").orEmpty().takeIf{it.length<=200 && it.none{ch->Character.isISOControl(ch)}}.orEmpty())
        }finally{c.disconnect();connection=null}
    }
    override fun cancel(){connection?.disconnect()}
}
/** No worker, wake lock, alarm or background timer. Called by the visible Studio/Dream tick only. */
class ContentUpdateSession(context:Context,private val changed:()->Unit,
    private val transport:ContentTransport=GithubContentTransport(),
    private val online:(()->Boolean)?=null,
    private val clock:()->Long={System.currentTimeMillis()}) {
    companion object {private val inFlight=AtomicReference<Any?>(null)}
    private val app=context.applicationContext
    private val state=app.getSharedPreferences("reading_update_state",0)
    private val main=Handler(Looper.getMainLooper())
    private val lock=Any()
    private var executor:java.util.concurrent.ExecutorService?=null
    private var task:Future<*>?=null
    @Volatile private var active=false
    private var generation=0
    fun start(){synchronized(lock){active=true}}
    fun refresh(allowed:Boolean) {
        synchronized(lock){
            val now=clock()
            if(!active || !allowed || task?.isDone==false || !ContentUpdatePolicy.due(now,state.getLong("checked",0),state.getLong("after",0)) || !(online?.invoke()?:ContentUpdatePolicy.online(app)))return
            val owner=Any()
            if(!inFlight.compareAndSet(null,owner))return
            val ticket=generation
            val worker=executor?.takeUnless{it.isShutdown}?:Executors.newSingleThreadExecutor().also{executor=it}
            val began=AtomicBoolean(false)
            val work=object:FutureTask<Unit>(Runnable {
                began.set(true)
                try {
                    if(Thread.currentThread().isInterrupted)return@Runnable
                    val previous=ContentRepository.snapshot(app)
                    val cacheMatches=state.getBoolean("verifiedBefore",false) && previous.version==state.getLong("version",0) && previous.digest==state.getString("digest","")
                    val response=transport.fetch(if(cacheMatches)state.getString("etag","").orEmpty() else "")
                    synchronized(lock){
                        if(!active || ticket!=generation || Thread.currentThread().isInterrupted)return@synchronized
                        val edit=state.edit()
                        when(response.status){
                            200->{ContentRepository.accept(app,response.bytes);edit.putString("etag",response.etag)}
                            304->require(cacheMatches && ContentRepository.snapshot(app).let{it.version==previous.version && it.digest==previous.digest})
                            else->throw IllegalArgumentException("Unexpected content response")
                        }
                        require(edit.putLong("checked",clock()).putLong("after",0).putInt("failures",0).putBoolean("verifiedBefore",true).putLong("version",ContentRepository.snapshot(app).version).putString("digest",ContentRepository.snapshot(app).digest).commit())
                        main.post{synchronized(lock){if(active && ticket==generation)changed()}}
                    }
                } catch(error:Exception){synchronized(lock){
                    if(active && ticket==generation && !Thread.currentThread().isInterrupted){
                        val failures=(state.getInt("failures",0)+1).coerceAtMost(4)
                        state.edit().putInt("failures",failures).putLong("after",clock()+maxOf(ContentUpdatePolicy.retryDelay(failures),(error as? ContentHttpException)?.retryAfterMs?:0)).apply()
                    }
                }} finally{inFlight.compareAndSet(owner,null)}
            },Unit){override fun done(){if(!began.get())inFlight.compareAndSet(owner,null)}}
            task=work
            try{worker.execute(work)}catch(_:java.util.concurrent.RejectedExecutionException){inFlight.compareAndSet(owner,null)}
        }
    }
    fun stop(){synchronized(lock){active=false;generation++;task?.cancel(true);task=null;transport.cancel();executor?.shutdownNow();executor=null;main.removeCallbacksAndMessages(null)}}
    internal val running get()=active
}
