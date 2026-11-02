package com.ufuq.dream

import org.junit.Assert.*
import org.junit.Test

class CinematicRotationTest {
    @Test fun longClipFinishesBeforeChangingWithoutSkippingItsNeighbour(){
        val r=CinematicRotation();r.resume(0)
        assertEquals(0L,r.step(60_000,1,true))
        assertEquals(0L,r.step(80_000,1,true))
        assertEquals(1L,r.step(85_000,1,true,boundary=true))
        assertEquals(60_000L,r.remaining(85_000,1))
        assertEquals(1L,r.step(85_000,1,true,boundary=true))
    }
    @Test fun shortClipsLoopUntilDwellTimeThenAdvanceAtAnEnd(){
        val r=CinematicRotation();r.resume(0)
        for(t in listOf(22_000L,44_000L))assertEquals(0L,r.step(t,1,true,true))
        assertEquals(0L,r.step(60_000,1,true))
        assertEquals(1L,r.step(66_000,1,true,true))
        assertEquals(1L,r.step(120_000,1,true))
        assertEquals(2L,r.step(132_000,1,true,true))
    }
    @Test fun photographsAndFailedVideoDoNotWaitForAnUnavailableBoundary(){
        val r=CinematicRotation();r.resume(100)
        assertEquals(1L,r.step(60_100,1,false))
        assertEquals(2L,r.step(120_100,1,false))
    }
    @Test fun fixedModeAndInactiveTimeNeverRotate(){
        val r=CinematicRotation();r.resume(0);r.pause(30_000)
        assertEquals(0L,r.step(999_999,1,false,true))
        r.resume(1_000_000)
        assertEquals(30_000L,r.remaining(1_000_000,1))
        assertEquals(0L,r.step(1_030_000,0,false,true))
        assertEquals(1L,r.step(1_030_000,1,false,true))
    }
}
