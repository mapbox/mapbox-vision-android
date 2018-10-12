package com.mapbox.vision.models

import java.util.*

data class DeviceMotionData(val rotations: FloatArray = FloatArray(3), val orientations: FloatArray = FloatArray(3),
                            val screenOrientation: Int = 0, val gravity: FloatArray = FloatArray(3),
                            val userAcceleration: FloatArray = FloatArray(3), val heading: Float = 0f) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as DeviceMotionData

        if (!Arrays.equals(rotations, other.rotations)) return false
        if (!Arrays.equals(orientations, other.orientations)) return false
        if (screenOrientation != other.screenOrientation) return false
        if (!Arrays.equals(gravity, other.gravity)) return false
        if (!Arrays.equals(userAcceleration, other.userAcceleration)) return false
        if (heading != other.heading) return false

        return true
    }

    override fun hashCode(): Int {
        var result = Arrays.hashCode(rotations)
        result = 31 * result + Arrays.hashCode(orientations)
        result = 31 * result + screenOrientation
        result = 31 * result + Arrays.hashCode(gravity)
        result = 31 * result + Arrays.hashCode(userAcceleration)
        result = 31 * result + heading.hashCode()
        return result
    }
}