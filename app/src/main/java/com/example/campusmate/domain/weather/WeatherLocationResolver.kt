package com.example.campusmate.domain.weather

import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import com.example.campusmate.util.PermissionUtils
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Resolves the user's coarse location into a city name without storing coordinates. */
class WeatherLocationResolver(context: Context) {
    private val appContext = context.applicationContext

    @SuppressLint("MissingPermission")
    fun resolveCity(): String? {
        if (!PermissionUtils.hasCoarseLocationPermission(appContext)) return null
        val location = readLastKnownLocation() ?: requestCurrentLocation()
        return location?.let(::reverseGeocodeCity)
    }

    @SuppressLint("MissingPermission")
    private fun readLastKnownLocation(): Location? {
        val locationManager = appContext.getSystemService(LocationManager::class.java) ?: return null
        val providers = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
        return providers
            .filter { provider -> runCatching { locationManager.isProviderEnabled(provider) }.getOrDefault(false) }
            .mapNotNull { provider -> runCatching { locationManager.getLastKnownLocation(provider) }.getOrNull() }
            .maxByOrNull { it.time }
    }

    @SuppressLint("MissingPermission")
    private fun requestCurrentLocation(): Location? {
        val locationManager = appContext.getSystemService(LocationManager::class.java) ?: return null
        val provider = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
            .firstOrNull { runCatching { locationManager.isProviderEnabled(it) }.getOrDefault(false) }
            ?: return null
        val latch = CountDownLatch(1)
        val cancellationSignal = CancellationSignal()
        var result: Location? = null

        runCatching {
            locationManager.getCurrentLocation(
                provider,
                cancellationSignal,
                ContextCompat.getMainExecutor(appContext)
            ) { location ->
                result = location
                latch.countDown()
            }
        }.onFailure {
            cancellationSignal.cancel()
            latch.countDown()
        }

        runCatching { latch.await(LOCATION_TIMEOUT_SECONDS, TimeUnit.SECONDS) }
        cancellationSignal.cancel()
        return result
    }

    @Suppress("DEPRECATION")
    private fun reverseGeocodeCity(location: Location): String? {
        return runCatching {
            val addresses = Geocoder(appContext, Locale.getDefault())
                .getFromLocation(location.latitude, location.longitude, 1)
                .orEmpty()
            val address = addresses.firstOrNull() ?: return@runCatching null
            listOf(
                address.locality,
                address.subAdminArea,
                address.adminArea,
                address.countryName
            ).firstOrNull { !it.isNullOrBlank() }?.trim()
        }.getOrNull()
    }

    companion object {
        private const val LOCATION_TIMEOUT_SECONDS = 5L
    }
}
