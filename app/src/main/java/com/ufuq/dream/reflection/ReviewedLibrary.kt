package com.ufuq.dream.reflection

import android.content.Context
import android.util.AtomicFile
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.security.MessageDigest
import java.security.Signature
import java.security.cert.CertificateFactory
import java.time.LocalDate
import java.util.Base64

/** Signed text data only: never executable code, remote HTML, images or arbitrary URLs. */
data class ReviewedLibrary(val version: Long, val digest: String, val entries: List<ReflectionEntry>)
object LibraryVerification {
    const val MAX_BYTES=524288
    const val CERT_SHA256="de13674be215040bafb029fe637a74e9e981accfa5a763bba62ad99dbcda5169"
    private fun utf8(bytes:ByteArray)=Charsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT).onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT).decode(java.nio.ByteBuffer.wrap(bytes)).toString()
    private fun digest(bytes:ByteArray)=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString(""){"%02x".format(it)}
    fun verify(raw:ByteArray,certificate:ByteArray,appVersion:Long):ReviewedLibrary {
        require(raw.size in 1..MAX_BYTES && digest(certificate)==CERT_SHA256)
        val cert=CertificateFactory.getInstance("X.509").generateCertificate(ByteArrayInputStream(certificate))
        return verifyPayload(raw,cert.publicKey,appVersion)
    }
    internal fun verifyPayload(raw:ByteArray,key:java.security.PublicKey,appVersion:Long):ReviewedLibrary {
        require(raw.size in 1..MAX_BYTES)
        val envelope=JSONObject(utf8(raw))
        require(envelope.getInt("formatVersion")==1)
        val payload=Base64.getDecoder().decode(envelope.getString("payload"))
        require(payload.size in 1..262144)
        val signature=Base64.getDecoder().decode(envelope.getString("signature"))
        require(signature.size in 256..1024)
        require(Signature.getInstance("SHA256withRSA").run{initVerify(key);update(payload);verify(signature)})
        val body=JSONObject(utf8(payload))
        require(body.getInt("schema")==1 && body.getLong("version")>0)
        require(body.getLong("minAppVersionCode") in 1..appVersion)
        LocalDate.parse(body.getString("reviewedAt"))
        require(body.getString("reviewedBy")=="Ufuq Dream maintainers")
        val rows=body.getJSONArray("entries");require(rows.length() in 3..128)
        val entries=(0 until rows.length()).map { i ->
            val row=rows.getJSONObject(i)
            ReflectionEntry(row.getString("kind"),row.getString("text"),row.getString("source"),row.getString("sourceUrl"),row.getString("licenseUrl"),row.getString("attribution"),row.getString("notice")).also{e->
                require(e.text.isNotBlank() && e.text.length<=4096 && e.source.length in 1..200)
                require(e.attribution.length in 1..1000 && e.notice.length in 1..8192)
                require(e.text.none{Character.isISOControl(it) && it!='\n' && it!='\t'})
                val source=java.net.URI(e.sourceUrl)
                require(source.scheme=="https" && source.userInfo==null && source.port==-1 && e.sourceUrl.length<=512)
                require(when(e.kind){
                    "quran"->source.host=="tanzil.net" && e.sourceUrl.startsWith("https://tanzil.net/#") && e.licenseUrl=="https://creativecommons.org/licenses/by/3.0/"
                    "hadith"->e.sourceUrl.startsWith("https://ar.wikisource.org/w/index.php?oldid=") && e.licenseUrl=="https://creativecommons.org/licenses/by-sa/4.0/"
                    "dhikr"->source.host in listOf("dorar.net","sunnah.com") && e.licenseUrl.isEmpty()
                    else->false
                })
            }
        }
        require(entries.map{it.kind}.toSet()==setOf("quran","hadith","dhikr"))
        require(entries.map{it.kind to it.text}.distinct().size==entries.size)
        return ReviewedLibrary(body.getLong("version"),digest(payload),entries)
    }
}
/** Verified snapshots are bounded and cached without retaining an Activity/Context. */
object ContentRepository {
    private var cachedKey=""
    private var cached:ReviewedLibrary?=null
    private fun certificate(context:Context)=context.assets.open("content-verifier-cert.der").use{it.readBytes()}
    private fun verify(context:Context,raw:ByteArray)=LibraryVerification.verify(raw,certificate(context),context.packageManager.getPackageInfo(context.packageName,0).longVersionCode)
    internal fun file(context:Context)=AtomicFile(java.io.File(context.filesDir,"reviewed-library.json"))
    @Synchronized fun invalidate(){cachedKey="";cached=null}
    @Synchronized fun snapshot(context:Context):ReviewedLibrary {
        val file=file(context);val key="${file.baseFile.absolutePath}:${file.baseFile.lastModified()}:${file.baseFile.length()}"
        if(key==cachedKey)cached?.let{return it}
        val baseline=try{context.assets.open("reviewed-library.json").use{verify(context,it.readBytes())}}
            catch(_:Exception){ReviewedLibrary(0,"bundled",DailyReflection.entries(context)+Adhkar.all)}
        val result=try {
            val loaded=file.openRead().use{input->
                require(input.channel.size() in 1..LibraryVerification.MAX_BYTES.toLong())
                verify(context,input.readBytes())
            }
            require(loaded.version>=baseline.version)
            if(loaded.version==baseline.version)require(loaded.digest==baseline.digest)
            loaded
        }catch(_:Exception){baseline}
        cached=result;cachedKey=key;return result
    }
    fun entries(context:Context)=snapshot(context).entries
    @Synchronized internal fun accept(context:Context,raw:ByteArray):ReviewedLibrary {
        val candidate=verify(context,raw);val current=snapshot(context)
        val state=context.getSharedPreferences("reading_update_state",0)
        val highest=state.getLong("version",0)
        require(candidate.version>=highest)
        if(candidate.version==highest && state.contains("digest"))require(candidate.digest==state.getString("digest",""))
        require(candidate.version>=current.version)
        if(candidate.version==current.version)require(candidate.digest==current.digest)
        val output=file(context);val stream=output.startWrite()
        try{stream.write(raw);output.finishWrite(stream)}catch(e:Exception){output.failWrite(stream);throw e}
        invalidate();return snapshot(context)
    }
}
