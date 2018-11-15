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
 * @property identifier unique for the session for each world description object.
 * @property carInFrontIndex index of car in [objects] in front of the vehicle. -1 in case of absence of the car in front.
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

            val objects = worldDescriptionDataBuffer.objects.let { objects ->
                val size = objects.size / 11;
                val result = mutableListOf<ObjectDescription>()

                (0 until size).map { it * 11 }.forEach { index ->
                    result.add(
                        ObjectDescription(
                            distance = objects[index],
                            worldCoordinate = WorldCoordinate(
                                x = objects[index + 1],
                                y = objects[index + 2],
                                z = objects[index + 3]
                            ),
                            detection = Detection(
                                boundingBox = Rect(
                                    objects[index + 7].toInt(),
                                    objects[index + 8].toInt(),
                                    objects[index + 9].toInt(),
                                    objects[index + 10].toInt()

                                ),
                                objectType = ObjectType.values()[objects[index + 4].toInt()],
                                confidence = objects[index + 5]
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
                            objectDescription = objects[collisions[index].toInt()],
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
                objects = objects,
                collisions = collisions,
                carInFrontIndex = worldDescriptionDataBuffer.carInFrontIndex
            )
        }
    }
}
