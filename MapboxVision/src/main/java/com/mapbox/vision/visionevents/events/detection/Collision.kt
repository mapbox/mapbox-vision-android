package com.mapbox.vision.visionevents.events.detection

import com.mapbox.vision.visionevents.events.worlddescription.ObjectDescription

/**
 * Possible collision with other car.
 */
data class Collision(
        val car: ObjectDescription,
        val deceleration: Float,
        val timeToImpact: Float,
        val state: CollisionState
) {
    enum class CollisionState {
        NOT_TRIGGERED,
        WARNING,
        CRITICAL
    }
}
