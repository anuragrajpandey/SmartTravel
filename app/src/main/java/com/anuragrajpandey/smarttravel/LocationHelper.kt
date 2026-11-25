package com.anuragrajpandey.smarttravel

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat

/** Thin wrapper over the platform LocationManager (no Google Play services needed). */
object LocationHelper {
    val PERMISSIONS = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)

    fun hasPermission(ctx: Context) = PERMISSIONS.any {
        ContextCompat.checkSelfPermission(ctx, it) == PackageManager.PERMISSION_GRANTED
    }

    /** Delivers the freshest available location (or null) on the main thread. */
    @SuppressLint("MissingPermission")
    fun getLocation(ctx: Context, callback: (Location?) -> Unit) {
        if (!hasPermission(ctx)) { callback(null); return }
        try {
            val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val enabled = lm.getProviders(true)
            var best: Location? = null
            for (p in enabled) {
                val l = runCatching { lm.getLastKnownLocation(p) }.getOrNull() ?: continue
                if (best == null || l.time > best.time) best = l
            }
            val fresh = best != null && System.currentTimeMillis() - best.time < 10 * 60 * 1000
            if (fresh) { callback(best); return }
            val provider = when {
                enabled.contains(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
                enabled.contains(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
                else -> null
            }
            if (provider == null) { callback(best); return }
            val fallback = best
            LocationManagerCompat.getCurrentLocation(lm, provider, null, ContextCompat.getMainExecutor(ctx)) { loc ->
                callback(loc ?: fallback)
            }
        } catch (e: SecurityException) {
            callback(null)
        }
    }

    // Last resolved user position is cached so other screens (weather, hotels, map) can reuse it.
    fun save(ctx: Context, lat: Double, lon: Double, name: String, country: String, countryCode: String) {
        ctx.getSharedPreferences("flipdoor", Context.MODE_PRIVATE).edit()
            .putString("last_lat", lat.toString()).putString("last_lon", lon.toString())
            .putString("last_name", name).putString("last_country", country)
            .putString("last_cc", countryCode).apply()
    }

    data class Saved(val lat: Double, val lon: Double, val name: String, val country: String, val countryCode: String)

    fun saved(ctx: Context): Saved? {
        val p = ctx.getSharedPreferences("flipdoor", Context.MODE_PRIVATE)
        val lat = p.getString("last_lat", null)?.toDoubleOrNull() ?: return null
        val lon = p.getString("last_lon", null)?.toDoubleOrNull() ?: return null
        return Saved(lat, lon, p.getString("last_name", "") ?: "", p.getString("last_country", "") ?: "", p.getString("last_cc", "") ?: "")
    }
}
