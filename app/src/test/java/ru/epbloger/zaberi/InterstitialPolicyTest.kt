package ru.epbloger.zaberi

import org.junit.Assert.*
import org.junit.Test

class InterstitialPolicyTest {
    private val now=1_800_000_000_000L
    private val gap=InterstitialPolicy.GAP_MS
    private val day=InterstitialPolicy.DAY_MS
    @Test fun notAtLaunch(){
        assertFalse(InterstitialPolicy.canShow(now,gap-1,emptyList()))
        assertTrue(InterstitialPolicy.canShow(now,gap,emptyList()))
    }
    @Test fun cooldownSurvivesRestart(){
        assertFalse(InterstitialPolicy.canShow(now,gap,listOf(now-gap+1)))
        assertTrue(InterstitialPolicy.canShow(now,gap,listOf(now-gap)))
    }
    @Test fun rollingCap(){
        assertFalse(InterstitialPolicy.canShow(now,gap,listOf(now-gap,now-2*gap,now-3*gap)))
        assertTrue(InterstitialPolicy.canShow(now,gap,listOf(now-gap,now-2*gap,now-day)))
    }
    @Test fun clockRollbackDoesNotBypassCap(){
        assertFalse(InterstitialPolicy.canShow(now,gap,listOf(now+1000)))
    }
}
