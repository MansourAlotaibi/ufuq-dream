package com.ufuq.dream.reflection

import android.os.Looper
import android.view.View
import com.ufuq.dream.*
import com.ufuq.dream.smart.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28,34])
class ContentUpdatesTest {
    private val context get()=RuntimeEnvironment.getApplication()
    private val state get()=context.getSharedPreferences("reading_update_state",0)
    private fun bundled()=context.assets.open("reviewed-library.json").use{it.readBytes()}
    private fun certificate()=context.assets.open("content-verifier-cert.der").use{it.readBytes()}
    @Before fun reset(){
        state.edit().clear().commit();context.getSharedPreferences("dream_settings",0).edit().clear().commit()
        ContentRepository.file(context).delete();ContentRepository.invalidate();DeviceTime.reset()
    }
    private fun await(predicate:()->Boolean){
        repeat(300){Shadows.shadowOf(Looper.getMainLooper()).idle();if(predicate())return;Thread.sleep(5)}
        assertTrue("Asynchronous operation did not finish",predicate())
    }
    @Test fun realProductionSignatureAuthenticatesOnlyExistingReviewedTexts(){
        val library=LibraryVerification.verify(bundled(),certificate(),33)
        assertEquals(1L,library.version);assertEquals(9,library.entries.size)
        assertEquals(DailyReflection.entries(context)+Adhkar.all,library.entries)
        assertEquals(mapOf("quran" to 4,"hadith" to 3,"dhikr" to 2),library.entries.groupingBy{it.kind}.eachCount())
        assertThrows(IllegalArgumentException::class.java){LibraryVerification.verify(bundled(),byteArrayOf(1),33)}
        assertThrows(IllegalArgumentException::class.java){LibraryVerification.verify(bundled(),certificate(),32)}
    }
    @Test fun tamperedPayloadAndSignatureNeverReplaceTheWorkingLibrary(){
        val before=ContentRepository.snapshot(context)
        val envelope=JSONObject(String(bundled(),Charsets.UTF_8))
        val bytes=Base64.getDecoder().decode(envelope.getString("payload"));bytes[30]=(bytes[30].toInt() xor 1).toByte()
        envelope.put("payload",Base64.getEncoder().encodeToString(bytes))
        assertThrows(Exception::class.java){ContentRepository.accept(context,envelope.toString().toByteArray())}
        assertEquals(before,ContentRepository.snapshot(context))
        assertThrows(Exception::class.java){ContentRepository.accept(context,"<html>Error</html>".toByteArray())}
        assertEquals(before,ContentRepository.snapshot(context))
    }
    @Test fun validlySignedButUnsafeContentIsRejectedBeforeStorage(){
        val pair=KeyPairGenerator.getInstance("RSA").apply{initialize(2048)}.generateKeyPair()
        val original=JSONObject(String(Base64.getDecoder().decode(JSONObject(String(bundled())).getString("payload"))))
        fun verify(body:JSONObject):ReviewedLibrary {
            val payload=body.toString().toByteArray();val signature=Signature.getInstance("SHA256withRSA").run{initSign(pair.private);update(payload);sign()}
            val envelope=JSONObject().put("formatVersion",1).put("payload",Base64.getEncoder().encodeToString(payload)).put("signature",Base64.getEncoder().encodeToString(signature))
            return LibraryVerification.verifyPayload(envelope.toString().toByteArray(),pair.public,33)
        }
        assertEquals(9,verify(original).entries.size)
        for(field in listOf("sourceUrl","licenseUrl","kind")){
            val copy=JSONObject(original.toString());copy.getJSONArray("entries").getJSONObject(0).put(field,"https://unreviewed.example/")
            assertThrows(Exception::class.java){verify(copy)}
        }
        val large=JSONObject(original.toString());large.getJSONArray("entries").getJSONObject(0).put("text","a".repeat(4097))
        assertThrows(Exception::class.java){verify(large)}
        assertThrows(Exception::class.java){LibraryVerification.verifyPayload(ByteArray(LibraryVerification.MAX_BYTES+1),pair.public,33)}
    }
    @Test fun rollbackHighWaterAndCorruptCacheKeepAnAuthenticatedFallback(){
        ContentRepository.accept(context,bundled());val previous=ContentRepository.snapshot(context)
        state.edit().putLong("version",2).putString("digest","newer").commit()
        assertThrows(Exception::class.java){ContentRepository.accept(context,bundled())}
        ContentRepository.file(context).baseFile.writeText("broken partial content");ContentRepository.invalidate()
        assertEquals(previous,ContentRepository.snapshot(context))
    }
    @Test fun interruptedAtomicWriteRecoversBeforeConsideringFallback(){
        ContentRepository.accept(context,bundled())
        val file=ContentRepository.file(context)
        file.startWrite().close() // Simulate process death before finishWrite/failWrite.
        ContentRepository.invalidate()
        assertEquals(9,ContentRepository.entries(context).size)
        assertArrayEquals(bundled(),file.baseFile.readBytes())
    }
    @Test fun rapidStartStopDoesNotLeakTheProcessWideAdmissionSlot(){
        repeat(15){
            val session=ContentUpdateSession(context,{},object:ContentTransport {override fun fetch(etag:String)=ContentResponse(200,bundled())},{true})
            session.start();session.refresh(true);session.stop()
        }
        // Let any worker which had already started unwind its finite cancellation path.
        Thread.sleep(50);state.edit().clear().commit()
        val done=AtomicInteger()
        val session=ContentUpdateSession(context,{done.incrementAndGet()},object:ContentTransport {override fun fetch(etag:String)=ContentResponse(200,bundled())},{true})
        try{session.start();await{session.refresh(true);done.get()==1}}finally{session.stop()}
        assertEquals(1,done.get())
    }
    @Test fun offlineAndUnconfirmedSettingsPerformZeroRequests(){
        val calls=AtomicInteger();val transport=object:ContentTransport {override fun fetch(etag:String):ContentResponse{calls.incrementAndGet();return ContentResponse(200,bundled())}}
        val enabled=SmartSettings(reflectionMode=7,contentUpdates=true)
        assertFalse(ContentUpdatePolicy.consent(enabled,enabled.copy(contentUpdates=false)))
        assertFalse(ContentUpdatePolicy.consent(enabled.copy(reflectionMode=0),enabled))
        assertTrue(ContentUpdatePolicy.consent(enabled,enabled))
        val offline=ContentUpdateSession(context,{},transport,{false})
        offline.start();repeat(20){offline.refresh(true)};offline.stop()
        val unsaved=ContentUpdateSession(context,{},transport,{true})
        unsaved.start();repeat(20){unsaved.refresh(false)};unsaved.stop()
        assertEquals(0,calls.get());assertEquals(0L,state.getLong("checked",0));assertEquals(9,ContentRepository.entries(context).size)
    }
    @Test fun successfulFetchThen304UsesFifteenDayCadenceAndEtag(){
        val clock=AtomicLong(System.currentTimeMillis());val calls=AtomicInteger();val changes=AtomicInteger();val etags=mutableListOf<String>()
        val transport=object:ContentTransport {override fun fetch(etag:String):ContentResponse{synchronized(etags){etags.add(etag)};return if(calls.incrementAndGet()==1)ContentResponse(200,bundled(),"\"v1\"")else ContentResponse(304)}}
        val session=ContentUpdateSession(context,{changes.incrementAndGet()},transport,{true},{clock.get()})
        try {
            session.start();session.refresh(true);await{changes.get()==1}
            repeat(20){session.refresh(true)};assertEquals(1,calls.get())
            clock.addAndGet(ContentUpdatePolicy.INTERVAL-1);session.refresh(true);assertEquals(1,calls.get())
            clock.incrementAndGet();session.refresh(true);await{changes.get()==2}
            assertEquals(listOf("","\"v1\""),etags)
            assertEquals(clock.get(),state.getLong("checked",0))
            assertEquals(9,ContentRepository.entries(context).size)
        }finally{session.stop()}
    }
    @Test fun failuresHaveBoundedBackoffAndDoNotInventOrDiscardTexts(){
        val calls=AtomicInteger();val clock=AtomicLong(System.currentTimeMillis())
        val session=ContentUpdateSession(context,{},object:ContentTransport {override fun fetch(etag:String):ContentResponse{calls.incrementAndGet();throw java.net.SocketTimeoutException()}},{true},{clock.get()})
        try {
            session.start();session.refresh(true);await{state.getInt("failures",0)==1}
            repeat(20){session.refresh(true)};assertEquals(1,calls.get())
            assertEquals(clock.get()+ContentUpdatePolicy.DAY,state.getLong("after",0))
            assertEquals(0L,state.getLong("checked",0));assertEquals(9,ContentRepository.entries(context).size)
            clock.addAndGet(ContentUpdatePolicy.DAY);session.refresh(true);await{state.getInt("failures",0)==2}
            assertEquals(clock.get()+3*ContentUpdatePolicy.DAY,state.getLong("after",0))
        }finally{session.stop()}
    }
    @Test fun rateLimitAndInvalid304AreNotSuccessfulUpdates(){
        val now=System.currentTimeMillis()
        val limited=ContentUpdateSession(context,{},object:ContentTransport {override fun fetch(etag:String):ContentResponse{throw ContentHttpException(429,7*ContentUpdatePolicy.DAY)}},{true},{now})
        limited.start();limited.refresh(true);await{state.getInt("failures",0)==1};limited.stop()
        assertEquals(now+7*ContentUpdatePolicy.DAY,state.getLong("after",0))
        state.edit().clear().commit()
        val invalid=ContentUpdateSession(context,{},object:ContentTransport {override fun fetch(etag:String)=ContentResponse(304)},{true},{now})
        invalid.start();invalid.refresh(true);await{state.getInt("failures",0)==1};invalid.stop()
        assertEquals(0L,state.getLong("checked",0))
    }
    @Test fun staleEtagIsNotSentAfterHigherVersionCacheIsLost(){
        state.edit().putBoolean("verifiedBefore",true).putLong("version",2).putString("digest","lost-cache").putString("etag","old").commit()
        var sent:String?=null
        val session=ContentUpdateSession(context,{},object:ContentTransport {override fun fetch(etag:String):ContentResponse{sent=etag;return ContentResponse(200,bundled())}},{true})
        session.start();session.refresh(true);await{state.getInt("failures",0)==1};session.stop()
        assertEquals("",sent);assertEquals(2L,state.getLong("version",0));assertEquals(9,ContentRepository.entries(context).size)
    }
    @Test fun stopCancelsAnInflightRequestAndDropsItsLateResult(){
        val started=CountDownLatch(1);val release=CountDownLatch(1);val changes=AtomicInteger()
        val transport=object:ContentTransport {
            override fun fetch(etag:String):ContentResponse{started.countDown();try{release.await(1,TimeUnit.SECONDS)}catch(_:InterruptedException){};return ContentResponse(200,bundled())}
            override fun cancel(){release.countDown()}
        }
        val session=ContentUpdateSession(context,{changes.incrementAndGet()},transport,{true})
        session.start();session.refresh(true);assertTrue(started.await(1,TimeUnit.SECONDS));session.stop();release.countDown()
        Thread.sleep(30);Shadows.shadowOf(Looper.getMainLooper()).idle()
        assertFalse(session.running);assertEquals(0,changes.get());assertEquals(0L,state.getLong("checked",0))
    }
    @Test fun studioToggleIsContextualAndStagedUntilSave(){
        val repo=SettingsRepository(context);assertFalse(repo.load().smart.contentUpdates)
        val controller=Robolectric.buildActivity(SettingsActivity::class.java).setup();val a=controller.get()
        a.window.decorView.findViewWithTag<View>("section_3").requestFocus();Shadows.shadowOf(Looper.getMainLooper()).idle()
        assertNull(a.window.decorView.findViewWithTag<View>("reading_updates"))
        a.window.decorView.findViewWithTag<View>("reflection").performClick()
        ShadowDialog.getLatestDialog().findViewById<View>(android.R.id.content).findViewWithTag<View>("choice_7").performClick()
        a.window.decorView.findViewWithTag<View>("reading_updates").performClick()
        assertTrue(a.draft.current.smart.contentUpdates);assertFalse(repo.load().smart.contentUpdates)
        a.window.decorView.findViewWithTag<View>("cancel").performClick();assertFalse(a.draft.current.smart.contentUpdates)
        controller.pause().stop().destroy()
    }
    @Test fun invalidClockNeverStartsPolling(){
        assertFalse(ContentUpdatePolicy.due(0,0,0))
        val now=System.currentTimeMillis();assertFalse(ContentUpdatePolicy.due(now,now+1,0))
        assertTrue(ContentUpdatePolicy.due(now,now-ContentUpdatePolicy.INTERVAL,0))
        assertEquals(ContentUpdatePolicy.INTERVAL,ContentUpdatePolicy.retryDelay(99))
    }
}
