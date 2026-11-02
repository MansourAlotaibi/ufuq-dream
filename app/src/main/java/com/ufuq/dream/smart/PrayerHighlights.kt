package com.ufuq.dream.smart

import java.time.Instant

/** Presentation only: most recently started prayer in today's validated table, not a fatwa
 * about the end of its religious time. Before Fajr no earlier-day time is invented.
 */
internal data class PrayerHighlights(val current:Int?,val next:Int?) {
    companion object {
        fun at(now:Instant,entries:List<PrayerEntry>,upcoming:NextPrayer?):PrayerHighlights {
            val current=entries.indexOfLast{it.instant<=now}.takeIf{it>=0}
            val next=upcoming?.takeIf{it.entry.instant>now}?.let{candidate->
                entries.indexOfFirst{it.name==candidate.entry.name}.takeIf{it>=0}
            }
            return PrayerHighlights(current,next)
        }
    }
}
