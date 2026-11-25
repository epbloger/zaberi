package ru.epbloger.zaberi

import org.junit.Assert.*
import org.junit.Test

class AdPrivacyPolicyTest {
    @Test fun categoryAndSeparateConsentAreRequired(){
        for(age in listOf(0,6,12,16,18)){
            for(adult in listOf(false,true)){
                for(consent in listOf(false,true)){
                    val eligible=age>=12&&adult
                    assertEquals(eligible,AdPrivacyPolicy.adultEligible(age,adult))
                    assertEquals(eligible&&consent,AdPrivacyPolicy.personalized(age,adult,consent))
                    assertEquals(!eligible,AdPrivacyPolicy.ageRestricted(age,adult))
                }
            }
        }
        assertFalse(AdPrivacyPolicy.personalized(99,true,true))
    }
    @Test fun bannerRecreationNeverRequestsFasterThanThirtySeconds(){
        assertEquals(0L,AdPrivacyPolicy.bannerWaitMs(100L,null))
        assertEquals(30_000L,AdPrivacyPolicy.bannerWaitMs(100L,100L))
        assertEquals(1L,AdPrivacyPolicy.bannerWaitMs(30_099L,100L))
        assertEquals(0L,AdPrivacyPolicy.bannerWaitMs(30_100L,100L))
        assertEquals(30_000L,AdPrivacyPolicy.bannerWaitMs(99L,100L))
    }
}
