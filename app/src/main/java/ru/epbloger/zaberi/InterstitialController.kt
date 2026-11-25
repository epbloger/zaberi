package ru.epbloger.zaberi

import android.content.Context
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.lifecycle.Lifecycle
import com.yandex.mobile.ads.common.AdError
import com.yandex.mobile.ads.common.AdRequest
import com.yandex.mobile.ads.common.AdRequestError
import com.yandex.mobile.ads.common.ImpressionData
import com.yandex.mobile.ads.interstitial.InterstitialAd
import com.yandex.mobile.ads.interstitial.InterstitialAdEventListener
import com.yandex.mobile.ads.interstitial.InterstitialAdLoadListener
import com.yandex.mobile.ads.interstitial.InterstitialAdLoader

/** Main-thread only. Loading callbacks never trigger a display. */
internal class InterstitialController(private val activity:ComponentActivity,private val unitId:String){
    private val prefs=activity.applicationContext.getSharedPreferences("interstitial_limits",Context.MODE_PRIVATE)
    private val created=SystemClock.elapsedRealtime()
    private var loader:InterstitialAdLoader?=null
    private var loaded:InterstitialAd?=null
    private var showing:InterstitialAd?=null
    private var loadedAt=0L
    private var lastLoad:Long?=null
    private var loadAttempts=0
    private var loading=false
    private var closed=false

    fun preload(){
        if(closed||loading||loaded!=null||showing!=null||loadAttempts>=4)return
        val now=SystemClock.elapsedRealtime()
        if(lastLoad?.let{now-it<InterstitialPolicy.GAP_MS}==true)return
        // Preload only while a further attempt is permitted by the persistent cap.
        if(!canReserve(System.currentTimeMillis(),now-created,checkSession=false))return
        lastLoad=now
        loadAttempts++
        loading=true
        runCatching{
            val current=loader?:InterstitialAdLoader(activity.applicationContext).also{loader=it}
            current.loadAd(AdRequest.Builder(unitId).build(),object:InterstitialAdLoadListener{
                override fun onAdLoaded(ad:InterstitialAd){
                    if(closed){runCatching{ad.setAdEventListener(null)};return}
                    loading=false;loaded=ad;loadedAt=SystemClock.elapsedRealtime()
                }
                override fun onAdFailedToLoad(adRequestError:AdRequestError){loading=false}
            })
        }.onFailure{loading=false}
    }

    private fun attempts():List<Long> = prefs.getString("attempts","").orEmpty().split(',').mapNotNull{it.toLongOrNull()}
    private fun canReserve(now:Long,age:Long,checkSession:Boolean)=
        InterstitialPolicy.canShow(now,if(checkSession)age else InterstitialPolicy.GAP_MS,attempts())
    private fun reserve(now:Long):Boolean =
        prefs.edit().putString("attempts",(InterstitialPolicy.recent(now,attempts())+now).joinToString(",")).commit()

    fun atNaturalPause(){
        if(closed||showing!=null||activity.isFinishing||activity.isDestroyed||
            !activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)||!activity.hasWindowFocus())return
        val elapsed=SystemClock.elapsedRealtime()
        if(loaded!=null&&elapsed-loadedAt>=30*60*1000L){runCatching{loaded?.setAdEventListener(null)};loaded=null}
        val ad=loaded
        if(ad==null){preload();return}
        val now=System.currentTimeMillis()
        if(!canReserve(now,elapsed-created,checkSession=true))return
        // Count attempts before invoking the SDK; a failed show conservatively consumes a slot.
        if(!reserve(now))return
        loaded=null;showing=ad
        fun finish(){
            runCatching{ad.setAdEventListener(null)}
            if(showing===ad)showing=null
        }
        runCatching{
            ad.setAdEventListener(object:InterstitialAdEventListener{
                override fun onAdShown(){}
                override fun onAdFailedToShow(adError:AdError){finish()}
                override fun onAdDismissed(){finish()}
                override fun onAdClicked(){}
                override fun onAdImpression(impressionData:ImpressionData?){}
            })
            ad.show(activity)
        }.onFailure{finish()}
    }

    fun close(){
        closed=true
        runCatching{loaded?.setAdEventListener(null)}
        runCatching{showing?.setAdEventListener(null)}
        loaded=null;showing=null;loader=null
    }
}
