package com.mapbox.vision.visionevents.events.roadrestrictions

import com.mapbox.vision.core.buffers.RoadRestrictionsDataBuffer

data class SpeedLimit(
        val identifier: Long,
        val minSpeed: Float,
        val maxSpeed: Float
) {
    internal constructor(buffer: RoadRestrictionsDataBuffer) : this(
            identifier = buffer.identifier,
            minSpeed = buffer.speedLimits[0],
            maxSpeed = buffer.speedLimits[1]
    )
}
