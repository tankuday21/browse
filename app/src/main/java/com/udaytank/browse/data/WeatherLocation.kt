package com.udaytank.browse.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.core.content.ContextCompat

/**
 * Best-effort coarse location for weather, using the framework [LocationManager] (no Google Play
 * Services dependency). Returns null when the COARSE permission isn't granted or no last-known
 * fix exists — the caller then falls back to the user's set city. Never requests updates or
 * tracks; only reads an already-cached last-known position.
 */
object WeatherLocation {
    fun lastKnownCoarse(context: Context): WeatherPlace? {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) return null

        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        val providers = listOf(
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER,
            LocationManager.GPS_PROVIDER,
        )
        for (provider in providers) {
            val loc = runCatching {
                if (lm.isProviderEnabled(provider)) lm.getLastKnownLocation(provider) else null
            }.getOrNull()
            if (loc != null) return WeatherPlace(loc.latitude, loc.longitude, "Current location")
        }
        return null
    }
}
