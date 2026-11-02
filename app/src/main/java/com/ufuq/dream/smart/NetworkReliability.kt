package com.ufuq.dream.smart

import android.content.Context
import android.net.ConnectivityManager
import android.os.SystemClock
import java.time.Instant
import javax.net.ssl.HttpsURLConnection

enum class SmartFailure { OFFLINE, DNS, TLS, TIMEOUT, HTTP, RATE_LIMIT, INVALID_DATA, CLOCK, STORAGE, IO }
class SmartNetworkException(val failure: SmartFailure,val status: Int=0,val retryMs: Long=0): java.io.IOException(failure.name)
object SmartFailures {
    fun classify(e: Exception): SmartFailure = when(e){
        is SmartNetworkException -> e.failure
        is java.net.UnknownHostException -> SmartFailure.DNS
        is javax.net.ssl.SSLException -> SmartFailure.TLS
        is java.net.SocketTimeoutException -> SmartFailure.TIMEOUT
        is org.json.JSONException,is IllegalArgumentException,is java.time.DateTimeException,is ArithmeticException -> SmartFailure.INVALID_DATA
        else -> SmartFailure.IO
    }
    fun retryDelay(failures: Int,error: SmartFailure,retryAfter: Long=0): Long =
        maxOf(when(failures.coerceIn(1,4)){1->60000L;2->300000L;3->900000L;else->3600000L},
            if(error==SmartFailure.RATE_LIMIT)retryAfter.coerceIn(60000L,6*3600000L) else 0L)
    fun connected(context: Context): Boolean = try {
        @Suppress("DEPRECATION")
        (context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager).activeNetworkInfo?.isConnected==true
    } catch (_: SecurityException){true} // Let finite HTTPS prove availability if OEM restricts the normal state API.
    fun checkResponse(connection: HttpsURLConnection,provider: String,diagnostics: SmartDiagnostics) {
        val code=connection.responseCode
        diagnostics.event(provider,"http",code)
        if(code!=200) {
            val seconds=connection.getHeaderField("Retry-After")?.toLongOrNull()
            val date=connection.getHeaderFieldDate("Retry-After",0L)
            val retry=seconds?.coerceIn(0,21600)?.times(1000) ?: (date-System.currentTimeMillis()).coerceIn(0,6*3600000L)
            throw SmartNetworkException(if(code==429)SmartFailure.RATE_LIMIT else SmartFailure.HTTP,code,retry)
        }
        DeviceTime.verifyServerDate(connection.getHeaderFieldDate("Date",0L))
    }
}
/** No account, address, greetings, URL queries or exception messages are retained. */
class SmartDiagnostics(context: Context) {
    private val prefs=context.applicationContext.getSharedPreferences("smart_diagnostics",Context.MODE_PRIVATE)
    fun event(provider: String,state: String,status: Int=0){
        val edit=prefs.edit().putString(provider,state).putLong("${provider}_at",System.currentTimeMillis())
        if(status>0)edit.putInt("${provider}_http",status)
        edit.apply()
        android.util.Log.i("UfuqSmart","provider=$provider state=$state http=$status")
    }
    fun detail(provider: String)=prefs.getString(provider,"لا نتيجة بعد").orEmpty()+
        " · HTTP ${prefs.getInt("${provider}_http",0)} · "+
        java.text.DateFormat.getDateTimeInstance().format(java.util.Date(prefs.getLong("${provider}_at",0)))
}
/** Detect gross invalid clocks and wall-clock jumps after an HTTPS Date anchor, never change device time. */
object DeviceTime {
    private var prefs:android.content.SharedPreferences?=null
    @Volatile private var anchorWall=0L
    @Volatile private var anchorElapsed=0L
    @Synchronized fun initialize(context:Context){
        if(prefs!=null)return
        prefs=context.applicationContext.getSharedPreferences("smart_clock_anchor",Context.MODE_PRIVATE)
        anchorWall=prefs!!.getLong("wall",0);anchorElapsed=prefs!!.getLong("elapsed",0)
    }
    fun plausible(now: Long): Boolean = try { Instant.ofEpochMilli(now).atZone(java.time.ZoneOffset.UTC).year in 2020..2099 }catch (_: Exception){false}
    fun valid(now: Long): Boolean {
        if(!plausible(now))return false
        val elapsed=SystemClock.elapsedRealtime()
        return anchorWall==0L || elapsed<anchorElapsed || kotlin.math.abs((now-anchorWall)-(elapsed-anchorElapsed))<=5*60000L
    }
    fun verifyServerDate(server: Long){
        val now=System.currentTimeMillis()
        if(server<=0 || !plausible(now) || kotlin.math.abs(now-server)>5*60000L)throw SmartNetworkException(SmartFailure.CLOCK)
        anchorWall=now;anchorElapsed=SystemClock.elapsedRealtime()
        prefs?.edit()?.putLong("wall",anchorWall)?.putLong("elapsed",anchorElapsed)?.apply()
    }
    internal fun reset(){anchorWall=0;anchorElapsed=0;prefs=null}
}
