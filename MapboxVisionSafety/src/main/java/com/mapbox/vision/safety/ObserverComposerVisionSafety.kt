package com.mapbox.vision.safety

import com.mapbox.vision.safety.core.VisionSafetyListener
import com.mapbox.vision.safety.core.models.CollisionObject
import com.mapbox.vision.safety.core.models.RoadRestrictions
import com.mapbox.vision.utils.observable.ObserverComposer

class ObserverComposerVisionSafety : ObserverComposer<VisionSafetyListener>(),
    VisionSafetyListener {

    override fun onCollisionsUpdated(collisions: Array<CollisionObject>) {
        forEach { onCollisionsUpdated(collisions) }
    }

    override fun onRoadRestrictionsUpdated(roadRestrictions: RoadRestrictions) {
        forEach { onRoadRestrictionsUpdated(roadRestrictions) }
    }
}