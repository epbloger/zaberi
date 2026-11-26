package ru.epbloger.zaberi

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

// Only invoked from the map's explicit "My location" button. No background tracking.
@SuppressLint("MissingPermission")
suspend fun locateForMap(context:Context):Location?=withContext(Dispatchers.Main.immediate){
    val fine=context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED
    val coarse=context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED
    if(!fine&&!coarse)return@withContext null
    val manager=context.getSystemService(LocationManager::class.java)?:return@withContext null
    val providers=(if(fine)listOf(LocationManager.GPS_PROVIDER,LocationManager.NETWORK_PROVIDER) else listOf(LocationManager.NETWORK_PROVIDER))
        .filter{runCatching{manager.isProviderEnabled(it)}.getOrDefault(false)}
    if(providers.isEmpty())return@withContext null
    val recent=providers.mapNotNull{runCatching{manager.getLastKnownLocation(it)}.getOrNull()}
        .filter{validPoint(it.latitude,it.longitude)&&android.os.SystemClock.elapsedRealtimeNanos()-it.elapsedRealtimeNanos in 0..120_000_000_000L}
        .minByOrNull{it.accuracy}
    if(recent!=null&&(recent.accuracy<=100f||!fine))return@withContext recent
    val current=withTimeoutOrNull(18000L){
        suspendCancellableCoroutine<Location?>{cont->
            val listener=object:LocationListener{
                override fun onLocationChanged(location:Location){
                    if(validPoint(location.latitude,location.longitude)&&cont.isActive){runCatching{manager.removeUpdates(this)};cont.resume(location)}
                }
                override fun onProviderEnabled(provider:String){}
                override fun onProviderDisabled(provider:String){}
                @Deprecated("Legacy callback") override fun onStatusChanged(provider:String?,status:Int,extras:Bundle?){}
            }
            cont.invokeOnCancellation{runCatching{manager.removeUpdates(listener)}}
            var started=false
            for(provider in providers){
                if(runCatching{manager.requestLocationUpdates(provider,0L,0f,listener,Looper.getMainLooper())}.isSuccess)started=true
            }
            if(!started&&cont.isActive){runCatching{manager.removeUpdates(listener)};cont.resume(null)}
        }
    }
    current?:recent
}
