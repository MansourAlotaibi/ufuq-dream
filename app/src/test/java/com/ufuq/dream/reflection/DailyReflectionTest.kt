package com.ufuq.dream.reflection

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.json.JSONObject
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28,34])
class DailyReflectionTest {
    private val context get()=RuntimeEnvironment.getApplication()
    @Test fun corpusIsTheUnchangedLauncherAssetWithSeparateLicenses(){
        val bytes=context.assets.open("daily_reflection.json").use{it.readBytes()}
        val provenance=JSONObject(context.assets.open("reflection-provenance.json").bufferedReader().use{it.readText()})
        val sha=java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString(""){"%02x".format(it)}
        assertEquals(provenance.getString("sha256"),sha)
        val entries=DailyReflection.entries(context)
        assertEquals(4,entries.count{it.kind=="quran"});assertEquals(3,entries.count{it.kind=="hadith"})
        val original=JSONObject(String(bytes,Charsets.UTF_8)).getJSONArray("entries")
        entries.forEachIndexed{index,entry->
            assertEquals(original.getJSONObject(index).getString("text"),entry.text)
            assertEquals(original.getJSONObject(index).getString("notice"),entry.notice)
            assertTrue(entry.sourceUrl.startsWith("https://"))
            if(entry.kind=="quran")assertTrue(entry.notice.contains("CHANGING IT IS NOT ALLOWED"))
            else assertTrue(entry.licenseUrl.endsWith("by-sa/4.0/"))
        }
    }
    @Test fun deterministicDailySelectionFiltersKindsAndPreviewDoesNotAdvanceIt(){
        val entries=DailyReflection.entries(context);val date=LocalDate.of(2026,11,3)
        assertNull(DailyReflection.select(entries,date,0))
        repeat(20){assertEquals(DailyReflection.select(entries,date,3),DailyReflection.select(entries,date,3))}
        for(index in 0..30){
            val day=date.plusDays(index.toLong())
            assertEquals("quran",DailyReflection.select(entries,day,1)!!.kind)
            assertEquals("hadith",DailyReflection.select(entries,day,2)!!.kind)
        }
        assertEquals(7,(0..6).map{DailyReflection.select(entries,date.plusDays(it.toLong()),3)}.toSet().size)
        assertNull(DailyReflection.select(entries,date,99))
    }
    @Test fun malformedOrUnattributedContentIsRejectedRatherThanRendered(){
        assertThrows(Exception::class.java){DailyReflection.parse("not-json")}
        assertThrows(IllegalArgumentException::class.java){DailyReflection.parse("{\"formatVersion\":1,\"entries\":[]}")}
        val root=JSONObject(context.assets.open("daily_reflection.json").bufferedReader().use{it.readText()})
        root.getJSONArray("entries").getJSONObject(0).put("sourceUrl","https://unknown.invalid/")
        assertThrows(IllegalArgumentException::class.java){DailyReflection.parse(root.toString())}
    }
}
