package com.mapbox.vision.models

import java.util.*

data class HeadingData(val heading: Float, val geomagnetism: FloatArray, val timestamp: Long) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as HeadingData

        if (heading != other.heading) return false
        if (!Arrays.equals(geomagnetism, other.geomagnetism)) return false
        if (timestamp != other.timestamp) return false

        return true
    }

    override fun hashCode(): Int {
        var result = heading.hashCode()
        result = 31 * result + Arrays.hashCode(geomagnetism)
        result = 31 * result + timestamp.hashCode()
        return result
    }
}