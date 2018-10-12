package com.mapbox.vision.visionevents.events.detection

import android.graphics.Rect
import com.mapbox.vision.visionevents.events.ObjectType

/**
 * Object is detected within [boundingBox].
 * Represents detected object of type [objectType].
 * Has [confidence] that specifies probability of detection.
 *
 * @property boundingBox specifies boundaries of detected object in screen coordinates
 * @property objectType type of detected object
 * @property confidence confidence of detection
 */
data class Detection(
        val boundingBox: Rect,
        val objectType: ObjectType,
        val confidence: Double
)
