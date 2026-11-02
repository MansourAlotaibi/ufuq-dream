package com.ufuq.dream

import org.junit.Assert.*
import org.junit.Test

class LongMotionTest {
    @Test fun originalMinutePlusClipsFitTheBoundedPlaybackProfile() {
        for (duration in listOf(60_000_000L, 61_161_100L, 84_984_900L, 180_000_000L)) {
            assertTrue(MotionProfile.accepts(1920, 1080, duration))
        }
        assertTrue(MotionProfile.accepts(1920, 1080, 11_200_000L)) // Existing library compatibility.
        assertFalse(MotionProfile.accepts(1920, 1080, 181_000_000L))
        assertFalse(MotionProfile.accepts(1920, 1080, 7_000_000L))
        assertFalse(MotionProfile.accepts(3840, 2160, 85_000_000L))
        assertFalse(MotionProfile.accepts(1080, 1920, 85_000_000L))
    }
}
