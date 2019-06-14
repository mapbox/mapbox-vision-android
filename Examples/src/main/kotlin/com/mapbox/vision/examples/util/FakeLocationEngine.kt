package com.mapbox.vision.examples.util

import android.annotation.SuppressLint
import android.location.Location
import com.mapbox.android.core.location.LocationEngine
import com.mapbox.geojson.Point

class FakeLocationEngine: LocationEngine() {

    private val location: Location = Location("fake_location")

    override fun activate() {
        locationListeners.forEach { it.onConnected() }
    }

    override fun removeLocationUpdates() {
    }

    override fun isConnected(): Boolean = true

    @SuppressLint("MissingPermission")
    override fun getLastLocation(): Location = location

    fun setLocation(point: Point) {
        this.location.longitude = point.longitude()
        this.location.latitude = point.latitude()

        locationListeners.forEach {
            it.onLocationChanged(location)
        }
    }

    override fun deactivate() {
    }

    override fun obtainType(): Type = Type.MOCK

    override fun requestLocationUpdates() {
    }
}