package com.ufuq.dream

import java.text.Normalizer

object GreetingText {
    const val LIMIT = 40
    val presets = listOf("يا هلا", "حياك الله", "صباح الخير", "مساء الخير")
    fun clean(raw: String): String {
        val safe = Normalizer.normalize(raw, Normalizer.Form.NFC)
            .filterNot { it in '\u202A'..'\u202E' || it in '\u2066'..'\u2069' || it == '\u200E' || it == '\u200F' }
            .replace(Regex("\\s+"), " ").trim()
        val count = safe.codePointCount(0, safe.length)
        return if (count <= LIMIT) safe else safe.substring(0, safe.offsetByCodePoints(0, LIMIT)).trimEnd()
    }
}
