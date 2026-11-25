package ru.epbloger.zaberi

/** Rolling 24h cap, including reserved attempts; persisted across process restarts. */
internal object InterstitialPolicy {
    const val GAP_MS=5*60*1000L
    const val DAY_MS=24*60*60*1000L
    const val MAX_PER_DAY=3
    fun recent(now:Long,attempts:List<Long>):List<Long> =
        attempts.filter{it>0&&(it>now||now-it<DAY_MS)}.sorted()
    fun canShow(now:Long,sessionAge:Long,attempts:List<Long>):Boolean {
        if(now<=0||sessionAge<GAP_MS)return false
        val relevant=recent(now,attempts)
        return relevant.size<MAX_PER_DAY&&relevant.none{it>now||now-it<GAP_MS}
    }
}
