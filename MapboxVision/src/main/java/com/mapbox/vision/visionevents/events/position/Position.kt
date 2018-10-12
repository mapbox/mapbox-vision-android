package com.mapbox.vision.visionevents.events.position

import android.location.Location
import com.mapbox.vision.core.buffers.PositionDataBuffer

/**
 * Current position [coordinate] estimated by SDK, with [speed] and [bearing].
 */
data class Position(val identifier: Long, val coordinate: PositionCoordinate, val speed: Double, val bearing: Double) {

    fun getLocation(): Location = Location("VisionSDK").also { location ->
        location.latitude = coordinate.latitude
        location.longitude = coordinate.longitude
        location.speed = speed.toFloat()
        location.bearing = bearing.toFloat()
    }

    companion object {

        internal fun fromPositionBuffer(positionDataBuffer: PositionDataBuffer) = Position(
                identifier = positionDataBuffer.positionIdentifier,
                coordinate = PositionCoordinate(
                        positionDataBuffer.positionDataArray[0],
                        positionDataBuffer.positionDataArray[1]
                ),
                speed = positionDataBuffer.positionDataArray[2],
                bearing = positionDataBuffer.positionDataArray[3]
        )
    }
}
