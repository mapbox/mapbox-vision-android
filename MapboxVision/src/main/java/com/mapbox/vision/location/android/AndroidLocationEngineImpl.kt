package com.mapbox.vision.location.android

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import com.mapbox.vision.location.LocationEngine
import com.mapbox.vision.location.LocationEngineListener

internal class AndroidLocationEngineImpl(context: Context) : LocationEngine, LocationListener {

    private val locationManager: LocationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val currentProvider: String = LocationManager.GPS_PROVIDER

    private lateinit var locationEngineListener: LocationEngineListener

    // Bearing returned can be invalid - it'l contain 0.0f then. We cache last valid bearing to return it to subscribers.
    private var lastValidBearing : Float = 0f

    private var attached = false

    @SuppressLint("MissingPermission")
    override fun attach(locationEngineListener: LocationEngineListener) {
        this.locationEngineListener = locationEngineListener
        if (!TextUtils.isEmpty(currentProvider)) {
            locationManager.requestLocationUpdates(currentProvider, DEFAULT_MIN_TIME, DEFAULT_MIN_DISTANCE, this)
        }
        attached = true
    }

    override fun detach() {
        attached = false
        locationManager.removeUpdates(this)
    }

    override fun isAttached(): Boolean = attached

    // Location Engine listener
    override fun onLocationChanged(location: Location) {
        if (!::locationEngineListener.isInitialized) {
            return
        }

        if (location.bearing != 0.0f) {
            lastValidBearing = location.bearing
        }

        locationEngineListener.onNewLocation(
                latitude = location.latitude,
                longitude = location.longitude,
                speed = location.speed,
                altitude = location.altitude,
                horizontalAccuracy = location.accuracy,
                verticalAccuracy = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) location.verticalAccuracyMeters else 0.0F,
                bearing = lastValidBearing,
                timestamp = location.time
        )
    }

    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {
        Log.v(TAG, String.format("Provider %s status changed to %d (current provider is %s).",
                provider, status, currentProvider))
    }

    override fun onProviderEnabled(provider: String?) {
        Log.v(TAG, String.format("Provider %s was enabled (current provider is %s).", provider, currentProvider))
    }

    override fun onProviderDisabled(provider: String?) {
        Log.v(TAG, String.format("Provider %s was disabled (current provider is %s).", provider, currentProvider))
    }

    companion object {
        private const val DEFAULT_MIN_TIME = 500L
        private const val DEFAULT_MIN_DISTANCE = 0.0f

        private const val TAG = "AndroidLocationEngine"
    }
}
