package com.mapbox.vision.visionevents.events.worlddescription

import android.graphics.Rect
import com.mapbox.vision.core.buffers.WorldDescriptionDataBuffer
import com.mapbox.vision.visionevents.WorldCoordinate
import com.mapbox.vision.visionevents.events.ObjectType
import com.mapbox.vision.visionevents.events.detection.Collision
import com.mapbox.vision.visionevents.events.detection.Detection

/**
 * List of detected [objects], possible [collisions].
 *
 * NOTE : Currently holds only info about car in front of ours.
 */
data class WorldDescription(
    val identifier: Long,
    val objects: List<ObjectDescription>,
    val collisions: List<Collision>,
    val carInFrontIndex: Int
) {

    companion object {

        internal fun fromWorldDescriptionDataBuffer(
            worldDescriptionDataBuffer: WorldDescriptionDataBuffer
        ): WorldDescription {

            val cars = worldDescriptionDataBuffer.cars.let { cars ->
                val size = cars.size / 11;
                val result = mutableListOf<ObjectDescription>()

                (0 until size).map { it * 11 }.forEach { index ->
                    result.add(
                        ObjectDescription(
                            distance = cars[index],
                            worldCoordinate = WorldCoordinate(
                                x = cars[index + 1],
                                y = cars[index + 2],
                                z = cars[index + 3]
                            ),
                            detection = Detection(
                                boundingBox = Rect(
                                    cars[index + 7].toInt(),
                                    cars[index + 8].toInt(),
                                    cars[index + 9].toInt(),
                                    cars[index + 10].toInt()

                                ),
                                objectType = ObjectType.values()[cars[index + 4].toInt()],
                                confidence = cars[index + 5]
                            )
                        )
                    )
                }

                return@let result
            }

            val collisions = worldDescriptionDataBuffer.collisions.let { collisions ->
                val size = collisions.size / 4
                val result = mutableListOf<Collision>()

                (0 until size).map { it * 4 }.forEach { index ->
                    result.add(
                        Collision(
                            objectDescription = cars[collisions[index].toInt()],
                            deceleration = collisions[index + 1],
                            state = Collision.CollisionState.values()[collisions[index + 2].toInt()],
                            timeToImpact = collisions[index + 3]
                        )
                    )
                }

                return@let result
            }


            return WorldDescription(
                identifier = worldDescriptionDataBuffer.worldDescriptionIdentifier,
                objects = cars,
                collisions = collisions,
                carInFrontIndex = worldDescriptionDataBuffer.carInFrontIndex
            )
        }
    }
}
