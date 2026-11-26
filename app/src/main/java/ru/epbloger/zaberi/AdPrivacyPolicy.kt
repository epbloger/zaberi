package ru.epbloger.zaberi

internal object AdPrivacyPolicy {
    fun adultEligible(storeAge:Int, confirmedAdult:Boolean):Boolean =
        storeAge in listOf(12,16,18) && confirmedAdult

    fun personalized(storeAge:Int, confirmedAdult:Boolean, consent:Boolean):Boolean =
        adultEligible(storeAge,confirmedAdult) && consent

    fun ageRestricted(storeAge:Int, confirmedAdult:Boolean):Boolean =
        !adultEligible(storeAge,confirmedAdult)

    fun bannerWaitMs(now:Long,lastRequest:Long?):Long =
        if(lastRequest==null) 0L else (30_000L-(now-lastRequest).coerceAtLeast(0L)).coerceAtLeast(0L)
}
