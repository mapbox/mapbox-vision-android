package com.mapbox.vision.visionevents.events.worlddescription

import com.mapbox.vision.visionevents.WorldCoordinate
import com.mapbox.vision.visionevents.events.detection.Detection

/**
 * Represents detected object.
 *
 * @property distance from the car in meters
 * @property worldCoordinate coordinate of object, relative to us
 * @property detection of object
 */
data class ObjectDescription(
        val distance: Double,
        val worldCoordinate: WorldCoordinate,
        val detection: Detection
)
