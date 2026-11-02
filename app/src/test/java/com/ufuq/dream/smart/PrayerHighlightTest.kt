package com.ufuq.dream.smart

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.Duration

class PrayerHighlightTest {
    private val entries=listOf("الفجر","الظهر","العصر","المغرب","العشاء").mapIndexed{i,name->PrayerEntry(name,Instant.parse("2026-11-03T00:00:00Z").plusSeconds(listOf(7200L,30000L,42000L,50000L,56000L)[i]))}
    @Test fun currentChangesAtBoundaryWhileNextRemainsDistinct(){
        val boundary=entries[2].instant
        val before=PrayerHighlights.at(boundary.minusSeconds(1),entries,NextPrayer(entries[2],Duration.ofSeconds(1)))
        assertEquals(1,before.current);assertEquals(2,before.next)
        val at=PrayerHighlights.at(boundary,entries,NextPrayer(entries[3],Duration.between(boundary,entries[3].instant)))
        assertEquals(2,at.current);assertEquals(3,at.next)
    }
    @Test fun beforeFajrAndMissingDataDoNotInventCurrentPrayer(){
        val now=entries.first().instant.minusSeconds(60)
        val value=PrayerHighlights.at(now,entries,NextPrayer(entries.first(),Duration.ofMinutes(1)))
        assertNull(value.current);assertEquals(0,value.next)
        assertEquals(PrayerHighlights(null,null),PrayerHighlights.at(now,emptyList(),null))
    }
    @Test fun nextDaysFajrCanBeHighlightedAfterIshaWithoutChangingTimes(){
        val now=entries.last().instant.plusSeconds(60)
        val tomorrow=entries.first().copy(instant=entries.first().instant.plusSeconds(86400))
        val value=PrayerHighlights.at(now,entries,NextPrayer(tomorrow,Duration.between(now,tomorrow.instant)))
        assertEquals(4,value.current);assertEquals(0,value.next)
        assertNull(PrayerHighlights.at(now,entries,null).next)
    }
}
