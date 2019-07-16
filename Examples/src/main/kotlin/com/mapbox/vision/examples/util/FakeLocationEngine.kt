package com.mapbox.vision.examples.util

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.location.Location
import android.os.Looper
import com.mapbox.android.core.location.LocationEngine
import com.mapbox.android.core.location.LocationEngineCallback
import com.mapbox.android.core.location.LocationEngineRequest
import com.mapbox.android.core.location.LocationEngineResult
import com.mapbox.geojson.Point

class FakeLocationEngine : LocationEngine {

    private val location: Location = Location("fake_location")

    override fun removeLocationUpdates(callback: LocationEngineCallback<LocationEngineResult>) {

    }

    override fun removeLocationUpdates(pendingIntent: PendingIntent?) {
    }

    override fun getLastLocation(callback: LocationEngineCallback<LocationEngineResult>) {
    }

    override fun requestLocationUpdates(
        request: LocationEngineRequest,
        callback: LocationEngineCallback<LocationEngineResult>,
        looper: Looper?
    ) {
    }

    override fun requestLocationUpdates(request: LocationEngineRequest, pendingIntent: PendingIntent?) {
    }
    //
    // override fun activate() {
    //     locationListeners.forEach { it.onConnected() }
    // }
    //
    // override fun removeLocationUpdates() {
    // }
    //
    // override fun isConnected(): Boolean = true
    //
    // @SuppressLint("MissingPermission")
    // override fun getLastLocation(): Location = location
    //
    fun setLocation(point: Point) {
        this.location.longitude = point.longitude()
        this.location.latitude = point.latitude()

        // locationListeners.forEach {
        //     it.onLocationChanged(location)
        // }
    }
    //
    // override fun deactivate() {
    // }
    //
    // override fun obtainType(): Type = Type.MOCK
    //
    // override fun requestLocationUpdates() {
    // }
}