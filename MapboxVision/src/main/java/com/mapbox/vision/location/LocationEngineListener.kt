package com.mapbox.vision.location

internal interface LocationEngineListener {

    fun onNewLocation(
            latitude: Double,
            longitude: Double,
            speed: Float,
            altitude: Double,
            horizontalAccuracy: Float,
            verticalAccuracy: Float = 0.0f,
            bearing: Float,
            timestamp: Long
    )
}
