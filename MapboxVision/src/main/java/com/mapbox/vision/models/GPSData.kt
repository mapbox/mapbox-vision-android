package com.mapbox.vision.models

internal data class GPSData(
        val latitude: Double,
        val longitude: Double,
        val speed: Float,
        val altitude: Double,
        val horizontalAccuracy: Float,
        val verticalAccuracy: Float = 0.0f,
        val bearing: Float,
        val timestamp: Long
)
