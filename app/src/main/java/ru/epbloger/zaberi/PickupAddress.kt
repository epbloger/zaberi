package ru.epbloger.zaberi

import android.content.Context
import android.location.Geocoder
import android.os.Build
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

internal fun cleanPickupAddress(value: String?): String? =
    value?.trim()?.takeIf { it.isNotEmpty() && it.length <= 200 }

internal fun matchingPickupLabel(label: String, labelX: Double?, labelY: Double?, x: Double, y: Double): String? =
    if (labelX == x && labelY == y) cleanPickupAddress(label) else null

internal suspend fun reversePickupAddress(context: Context, lat: Double, lon: Double): String? {
    if (!validPoint(lat, lon)) return null
    return try {
        if (!Geocoder.isPresent()) return null
        withTimeoutOrNull(8000L) {
            val geocoder = Geocoder(context.applicationContext, Locale.getDefault())
            val addresses = if (Build.VERSION.SDK_INT >= 33) {
                suspendCancellableCoroutine<List<android.location.Address>> { continuation ->
                    geocoder.getFromLocation(lat, lon, 1, object : Geocoder.GeocodeListener {
                        override fun onGeocode(addresses: MutableList<android.location.Address>) {
                            if (continuation.isActive) continuation.resume(addresses)
                        }
                        override fun onError(errorMessage: String?) {
                            if (continuation.isActive) continuation.resume(emptyList())
                        }
                    })
                }
            } else {
                @Suppress("DEPRECATION")
                runInterruptible(Dispatchers.IO) { geocoder.getFromLocation(lat, lon, 1).orEmpty() }
            }
            addresses.firstOrNull()?.let { address ->
                cleanPickupAddress(address.getAddressLine(0))
                    ?: cleanPickupAddress(listOfNotNull(address.locality, address.thoroughfare, address.subThoroughfare)
                        .filter { it.isNotBlank() }.distinct().joinToString(", "))
            }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }
}
