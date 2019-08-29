package com.mapbox.vision.manager

import com.mapbox.vision.mobile.core.interfaces.VisionEventsListener
import com.mapbox.vision.mobile.core.models.AuthorizationStatus
import com.mapbox.vision.mobile.core.models.Camera
import com.mapbox.vision.mobile.core.models.Country
import com.mapbox.vision.mobile.core.models.FrameSegmentation
import com.mapbox.vision.mobile.core.models.classification.FrameSignClassifications
import com.mapbox.vision.mobile.core.models.detection.FrameDetections
import com.mapbox.vision.mobile.core.models.position.VehicleState
import com.mapbox.vision.mobile.core.models.road.RoadDescription
import com.mapbox.vision.mobile.core.models.world.WorldDescription
import com.mapbox.vision.utils.observable.CompositeListener

open class CompositeListenerVisionEvents : CompositeListener.Impl<VisionEventsListener>(),
    VisionEventsListener {

    override fun onAuthorizationStatusUpdated(authorizationStatus: AuthorizationStatus) {
        forEach { onAuthorizationStatusUpdated(authorizationStatus) }
    }

    override fun onFrameSegmentationUpdated(frameSegmentation: FrameSegmentation) {
        forEach { onFrameSegmentationUpdated(frameSegmentation) }
    }

    override fun onFrameDetectionsUpdated(frameDetections: FrameDetections) {
        forEach { onFrameDetectionsUpdated(frameDetections) }
    }

    override fun onFrameSignClassificationsUpdated(frameSignClassifications: FrameSignClassifications) {
        forEach { onFrameSignClassificationsUpdated(frameSignClassifications) }
    }

    override fun onRoadDescriptionUpdated(roadDescription: RoadDescription) {
        forEach { onRoadDescriptionUpdated(roadDescription) }
    }

    override fun onWorldDescriptionUpdated(worldDescription: WorldDescription) {
        forEach { onWorldDescriptionUpdated(worldDescription) }
    }

    override fun onVehicleStateUpdated(vehicleState: VehicleState) {
        forEach { onVehicleStateUpdated(vehicleState) }
    }

    override fun onCameraUpdated(camera: Camera) {
        forEach { onCameraUpdated(camera) }
    }

    override fun onCountryUpdated(country: Country) {
        forEach { onCountryUpdated(country) }
    }

    override fun onUpdateCompleted() {
        forEach { onUpdateCompleted() }
    }
}
