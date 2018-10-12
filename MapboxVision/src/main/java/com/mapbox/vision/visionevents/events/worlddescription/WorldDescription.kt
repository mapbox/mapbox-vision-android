package com.mapbox.vision.visionevents.events.worlddescription

import com.mapbox.vision.core.buffers.WorldDescriptionDataBuffer
import com.mapbox.vision.visionevents.WorldCoordinate
import com.mapbox.vision.visionevents.events.ObjectType

/**
 * List of detected [objects] with unique [identifier].
 *
 * NOTE : Currently holds only info about car in front of ours.
 */
data class WorldDescription(val identifier: Long, val objects: List<ObjectDescription>) {

    companion object {

        internal fun fromWorldDescriptionDataBuffer(
                worldDescriptionDataBuffer: WorldDescriptionDataBuffer
        ) = WorldDescription(
                identifier = worldDescriptionDataBuffer.worldDescriptionIdentifier,
                objects = listOf(ObjectDescription(
                        // distance is plain `y` since `y` is directed along the lane
                        distance = worldDescriptionDataBuffer.worldDescriptionDataArray[1],
                        worldCoordinate = WorldCoordinate(
                                x = worldDescriptionDataBuffer.worldDescriptionDataArray[0],
                                y = worldDescriptionDataBuffer.worldDescriptionDataArray[1],
                                z = worldDescriptionDataBuffer.worldDescriptionDataArray[2]
                        ),
                        objectType = ObjectType.CAR
                ))
        )
    }
}
