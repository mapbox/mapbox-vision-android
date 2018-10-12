package com.mapbox.vision.visionevents.events.worlddescription

import com.mapbox.vision.visionevents.WorldCoordinate
import com.mapbox.vision.visionevents.events.ObjectType

/**
 * Represents detected object.
 *
 * @property distance meters from us
 * @property worldCoordinate coordinate, relative to us
 * @property objectType type of object
 */
data class ObjectDescription(
        val distance: Double,
        val worldCoordinate: WorldCoordinate,
        val objectType: ObjectType
)
