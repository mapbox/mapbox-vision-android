package com.mapbox.vision.corewrapper.update

import com.mapbox.vision.models.LaneDepartureState
import com.mapbox.vision.visionevents.CalibrationProgress
import com.mapbox.vision.visionevents.events.classification.SignClassification
import com.mapbox.vision.visionevents.events.detection.Detections
import com.mapbox.vision.visionevents.events.position.Position
import com.mapbox.vision.visionevents.events.roaddescription.RoadDescription
import com.mapbox.vision.visionevents.events.segmentation.SegmentationMask
import com.mapbox.vision.visionevents.events.worlddescription.WorldDescription

/**
 * Listener to receive events from SDK.
 */
interface VisionEventsListener {

    /**
     * New detections are available.
     */
    fun detectionsUpdated(detections: Detections)

    /**
     * New segmentation is available.
     */
    fun segmentationUpdated(segmentationMask: SegmentationMask)

    /**
     * New sign classification is available.
     */
    fun signClassificationUpdated(signClassification: SignClassification)

    /**
     * New road description is available.
     */
    fun roadDescriptionUpdated(roadDescription: RoadDescription)

    /**
     * Distance to closest car ahead is updated.
     */
    fun worldDescriptionUpdated(worldDescription: WorldDescription)

    /**
     * Estimated position is updated.
     */
    fun estimatedPositionUpdated(position: Position)

    /**
     * Camera calibration progress is updated.
     */
    fun calibrationProgressUpdated(calibrationProgress: CalibrationProgress)

    /**
     * Lane Departure State is updated.
     */
    fun laneDepartureStateUpdated(laneDepartureState: LaneDepartureState)
}
