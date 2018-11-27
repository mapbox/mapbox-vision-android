package com.mapbox.vision.visionevents.events.position

import com.mapbox.vision.core.buffers.PositionDataBuffer

/**
 * Current position [coordinate] estimated by SDK, with [speed] and [bearing].
 */
data class Position(val identifier: Long, val coordinate: PositionCoordinate, val speed: Double, val bearing: Double) {

    internal constructor(positionDataBuffer: PositionDataBuffer) : this(
            identifier = positionDataBuffer.positionIdentifier,
            coordinate = PositionCoordinate(
                    positionDataBuffer.positionDataArray[0],
                    positionDataBuffer.positionDataArray[1]
            ),
            speed = positionDataBuffer.positionDataArray[2],
            bearing = positionDataBuffer.positionDataArray[3]
    )
}
