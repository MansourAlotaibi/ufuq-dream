package com.ufuq.dream.reflection

import java.time.Instant
import java.time.ZoneId

/** Ancient Arabic devotional texts only. No publisher translation, commentary or database copied. */
object Adhkar {
    val all = listOf(
        ReflectionEntry("dhikr", "سُبْحَانَ اللَّهِ وَبِحَمْدِهِ", "صحيح مسلم 2692 · مائة مرة",
            "https://dorar.net/h/Cfuml05Y?osoul=1", "", "نص تراثي؛ المرجع: صحيح مسلم 2692",
            "ذِكر ثابت للصباح والمساء؛ العرض لا يحسب عدد قراءات المستخدم ولا يقوم مقام الذكر باللسان."),
        ReflectionEntry("dhikr", "اللَّهُمَّ أَنْتَ رَبِّي، لَا إِلَهَ إِلَّا أَنْتَ، خَلَقْتَنِي وَأَنَا عَبْدُكَ، وَأَنَا عَلَى عَهْدِكَ وَوَعْدِكَ مَا اسْتَطَعْتُ، أَعُوذُ بِكَ مِنْ شَرِّ مَا صَنَعْتُ، أَبُوءُ لَكَ بِنِعْمَتِكَ عَلَيَّ، وَأَبُوءُ لَكَ بِذَنْبِي، فَاغْفِرْ لِي، فَإِنَّهُ لَا يَغْفِرُ الذُّنُوبَ إِلَّا أَنْتَ",
            "صحيح البخاري 6306 · سيد الاستغفار", "https://sunnah.com/bukhari:6306", "",
            "نص تراثي؛ المرجع: صحيح البخاري 6306", "نص الدعاء دون شرح أو ترجمة من الناشر؛ ثابت للنهار والليل.")
    )
    fun entry(now: Instant, language: String, zone: ZoneId = ZoneId.systemDefault()): ReflectionEntry {
        // Display grouping, not a fatwa defining the exact beginning/end of devotional periods.
        val morning = now.atZone(zone).hour < 12
        val original = all[Math.floorMod(now.epochSecond / 600 + if(morning)0 else 1, all.size.toLong()).toInt()]
        return original.copy(source=original.source) // Period is grouping metadata; keep the on-screen citation concise.
    }
}
