package com.mapbox.vision.visionevents.events.detection

import android.graphics.Rect
import com.mapbox.vision.core.buffers.DetectionDataBuffer
import com.mapbox.vision.visionevents.events.Image
import com.mapbox.vision.visionevents.events.ObjectType

/**
 * Holds [detections] with [sourceImage] they were made from.
 *
 * IMPORTANT : do not cache [sourceImage]. Data that it holds can be invalidated.
 * Instead, if you need the data, call [Image.getImageBitmap] or [Image.getImageBytes] and handle it.
 */
data class Detections(val identifier: Long, val detections: List<Detection>, val sourceImage: Image) {

    internal constructor(detectionDataBuffer: DetectionDataBuffer) : this(
            identifier = detectionDataBuffer.detectionsIdentifier,
            detections = mutableListOf<Detection>().also { detectionsObjectsList ->
                var index = 0
                val detectionsObjectsSize = detectionDataBuffer.detections.size / 6
                for (i in 0 until detectionsObjectsSize) {
                    val type = ObjectType.values()[detectionDataBuffer.detections[index++].toInt()]
                    val confidence = detectionDataBuffer.detections[index++]

                    val startX = detectionDataBuffer.detections[index++].toInt()
                    val startY = detectionDataBuffer.detections[index++].toInt()
                    val endX = detectionDataBuffer.detections[index++].toInt()
                    val endY = detectionDataBuffer.detections[index++].toInt()

                    val detection = Detection(Rect(startX, startY, endX, endY), type, confidence)
                    detectionsObjectsList.add(detection)
                }
            },
            sourceImage = Image(
                    format = Image.Format.values()[detectionDataBuffer.sourceImageDescriptionArray[2]],
                    width = detectionDataBuffer.sourceImageDescriptionArray[0],
                    height = detectionDataBuffer.sourceImageDescriptionArray[1],
                    identifier = detectionDataBuffer.sourceImageIdentifier
            )
    )
}
