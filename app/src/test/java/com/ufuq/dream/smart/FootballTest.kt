package com.ufuq.dream.smart

import org.junit.Assert.*
import org.junit.Test
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Instant
import org.json.JSONObject
import org.json.JSONArray
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** Synthetic fixtures exercise parsing/lifecycle; these are not presented as real live schedules. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28,34])
class FootballTest {
    private val context get()=RuntimeEnvironment.getApplication()
    @Before fun reset(){context.getSharedPreferences("smart_football_cache",0).edit().clear().commit();DeviceTime.reset();FootballGate.reset()}
    private fun event(start:String,team:String="136013")=JSONObject().put("idHomeTeam",team).put("idAwayTeam",if(team=="136013")"136022" else "136013").put("strHomeTeam","Club A").put("strAwayTeam","Club B").put("strTimestamp",start)
    private fun body(vararg events:JSONObject)=JSONObject().put("events",JSONArray().apply{events.forEach{put(it)}}).toString()
    private fun await(predicate:()->Boolean){repeat(200){shadowOf(android.os.Looper.getMainLooper()).idle();if(predicate())return;Thread.sleep(5)};assertTrue(predicate())}
    @Test fun utcOffsetsUnsortedEventsAndOwnershipAreValidated(){
        val raw=body(event("2026-11-04T21:00:00+03:00"),event("2026-11-03T18:15:00Z"),event("2026-11-02T18:15:00"))
        val parsed=FootballParsing.parse(raw,"136013")
        assertEquals(Instant.parse("2026-11-02T18:15:00Z"),parsed.first().start)
        assertEquals(Instant.parse("2026-11-04T18:00:00Z"),parsed.last().start)
        assertEquals(Instant.parse("2026-11-03T18:15:00Z"),FootballFeed("136013",1,parsed).next(Instant.parse("2026-11-03T00:00:00Z"))!!.start)
        assertThrows(IllegalArgumentException::class.java){FootballParsing.parse(raw,"133738")}
    }
    @Test fun fallbackUtcDateTimeAndEmptyPastInvalidResponsesStayHonest(){
        val row=event("bad").apply{remove("strTimestamp");put("dateEvent","2026-11-03");put("strTime","18:15:00")}
        assertEquals(Instant.parse("2026-11-03T18:15:00Z"),FootballParsing.parse(body(row),"136013").single().start)
        assertTrue(FootballParsing.parse("{\"events\":null}","136013").isEmpty())
        assertNull(FootballFeed("136013",1,FootballParsing.parse(body(row),"136013")).next(Instant.parse("2026-11-04T00:00:00Z")))
        assertThrows(IllegalArgumentException::class.java){FootballParsing.parse("{\"message\":\"upgrade required\"}","136013")}
        assertThrows(IllegalArgumentException::class.java){FootballParsing.parse(body(event("not-a-date")),"136013")}
    }
    @Test fun unsafeNamesSelfMatchesAndExcessiveBodiesAreRejected(){
        assertThrows(IllegalArgumentException::class.java){FootballParsing.parse(body(event("2026-11-03T18:15:00Z").put("strHomeTeam","A\nB")),"136013")}
        assertThrows(IllegalArgumentException::class.java){FootballParsing.parse(body(event("2026-11-03T18:15:00Z").put("idAwayTeam","136013")),"136013")}
        assertThrows(IllegalArgumentException::class.java){FootballParsing.parse(" ".repeat(262145),"136013")}
    }
    @Test fun cacheIsTeamScopedBoundedExpiresAndRejectsPartialReplacement(){
        val cache=FootballCache(context);val now=System.currentTimeMillis()
        val raw=body(event(Instant.ofEpochMilli(now+86400000).toString()))
        cache.write("136013",raw,now)
        assertNotNull(cache.read("136013",now));assertNull(cache.read("136022",now))
        assertNull(cache.read("136013",now-1));assertNull(cache.read("136013",now+86400001))
        assertThrows(IllegalArgumentException::class.java){cache.write("136013",body(event("broken")),now)}
        assertNotNull(cache.read("136013",now))
        FootballTeams.all.forEachIndexed{index,team->cache.write(team.id,body(event(Instant.ofEpochMilli(now+86400000).toString(),team.id)),now+index)}
        assertTrue(context.getSharedPreferences("smart_football_cache",0).all.size<=8)
    }
    @Test fun unsavedChangesNeverAuthorizeRequestsAndFreshCacheAvoidsRefetch(){
        val calls=AtomicInteger(0);val now=System.currentTimeMillis()
        val session=FootballSession(context,{},FootballTransport{calls.incrementAndGet();body(event(Instant.ofEpochMilli(now+86400000).toString()))},{true})
        val draft=SmartSettings(football=true,footballTeam="136013")
        assertFalse(FootballConsent.allowed(draft,SmartSettings()))
        assertFalse(FootballConsent.allowed(draft,draft.copy(footballTeam="136022")))
        assertTrue(FootballConsent.allowed(draft,draft))
        try {
            session.start();session.refresh("136013",now,false);assertEquals(0,calls.get())
            session.refresh("136013",now,true);await{!session.loading&&session.read("136013",System.currentTimeMillis())!=null}
            repeat(5){session.refresh("136013",System.currentTimeMillis(),true)}
            assertEquals(1,calls.get())
        }finally{session.stop()}
    }
    @Test fun offlineFailureAndCancellationPreserveCacheAndDropLateCallbacks(){
        val now=System.currentTimeMillis();val cache=FootballCache(context)
        cache.write("136013",body(event(Instant.ofEpochMilli(now+86400000).toString())),now-7200000)
        val changes=AtomicInteger(0);val started=CountDownLatch(1);val finish=CountDownLatch(1)
        val session=FootballSession(context,{changes.incrementAndGet()},FootballTransport{started.countDown();finish.await(2,TimeUnit.SECONDS);body(event(Instant.ofEpochMilli(now+86400000).toString()))},{true})
        try {
            session.start();session.refresh("136013",now,true);assertTrue(started.await(2,TimeUnit.SECONDS))
            session.stop();finish.countDown();Thread.sleep(30);shadowOf(android.os.Looper.getMainLooper()).idle()
            assertEquals(0,changes.get());assertFalse(session.running);assertEquals(now-7200000,cache.read("136013",now)!!.fetchedAt)
            val offline=FootballSession(context,{},FootballTransport{error("must not fetch offline")},{false})
            offline.start();offline.refresh("136013",now,true);assertTrue(offline.failed);assertNotNull(offline.read("136013",now));offline.stop()
        }finally{finish.countDown();session.stop()}
    }
    @Test fun failuresBackOffAndNeverPoisonAnExistingFutureFixture(){
        val now=System.currentTimeMillis();FootballCache(context).write("136013",body(event(Instant.ofEpochMilli(now+86400000).toString())),now-7200000)
        val calls=AtomicInteger(0)
        val session=FootballSession(context,{},FootballTransport{calls.incrementAndGet();throw java.net.SocketTimeoutException()},{true})
        try{
            session.start();session.refresh("136013",now,true);await{session.failed&&!session.loading}
            assertNotNull(session.read("136013",now)!!.next(Instant.ofEpochMilli(now)))
            repeat(10){session.refresh("136013",now,true)};assertEquals(1,calls.get())
        }finally{session.stop()}
    }
    @Test fun dnsHttpAndMalformedResponsesRetainValidCache(){
        val now=System.currentTimeMillis();val cache=FootballCache(context)
        cache.write("136013",body(event(Instant.ofEpochMilli(now+86400000).toString())),now-7200000)
        for(kind in 0..2){
            FootballGate.reset()
            val session=FootballSession(context,{},FootballTransport{
                when(kind){0->throw java.net.UnknownHostException();1->throw SmartNetworkException(SmartFailure.HTTP,503);else->"malformed-json"}
            },{true})
            try{session.start();session.refresh("136013",now,true);await{session.failed&&!session.loading};assertEquals(now-7200000,cache.read("136013",now)!!.fetchedAt)}finally{session.stop()}
        }
        assertEquals("الهلال",FootballTeams.localized("Al-Hilal","ar"))
        assertEquals("Al-Hilal",FootballTeams.localized("Al-Hilal","en"))
        assertNull(FootballTeams.find("unknown"))
    }

    @Test fun knownProviderLabelsFollowLanguageAndUnknownLabelsArePreserved(){
        assertEquals("الاتفاق",FootballTeams.localized("Al-Ettifaq","ar"))
        assertEquals("Al-Ettifaq",FootballTeams.localized("Al-Ettifaq","en"))
        assertEquals("شباب الأهلي دبي",FootballTeams.localized("Shabab Al-Ahli Dubai","ar"))
        assertEquals("الدوري السعودي للمحترفين",FootballTeams.competition("Saudi-Arabian Pro League","ar"))
        assertEquals("Saudi-Arabian Pro League",FootballTeams.competition("Saudi-Arabian Pro League","en"))
        assertEquals("Unknown competition",FootballTeams.competition("Unknown competition","ar"))
        assertEquals("Unknown opponent",FootballTeams.localized("Unknown opponent","ar"))
    }

}
