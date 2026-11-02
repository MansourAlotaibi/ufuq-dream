package com.ufuq.dream

import org.junit.rules.ExternalResource

/** ICU and java.util maintain separate defaults on Linux/Robolectric. */
class SaudiTimeZoneRule : ExternalResource() {
    private lateinit var javaZone: java.util.TimeZone
    private lateinit var icuZone: android.icu.util.TimeZone
    override fun before() {
        javaZone = java.util.TimeZone.getDefault()
        icuZone = android.icu.util.TimeZone.getDefault()
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Asia/Riyadh"))
        setIcuDefault(android.icu.util.TimeZone.getTimeZone("Asia/Riyadh"))
    }
    private fun setIcuDefault(zone: android.icu.util.TimeZone) {
        // Hidden framework setter is used only by this host-side test fixture.
        android.icu.util.TimeZone::class.java.getDeclaredMethod("setDefault", android.icu.util.TimeZone::class.java).apply { isAccessible = true }.invoke(null, zone)
    }
    override fun after() {
        java.util.TimeZone.setDefault(javaZone)
        setIcuDefault(icuZone)
    }
}
