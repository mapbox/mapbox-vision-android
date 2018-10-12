package com.mapbox.vision.visionevents.events.classification

import android.graphics.Rect
import com.mapbox.vision.core.buffers.SignClassificationDataBuffer
import com.mapbox.vision.visionevents.events.Image
import com.mapbox.vision.visionevents.events.ObjectType
import com.mapbox.vision.visionevents.events.detection.Detection

/**
 * @param identifier unique identifier of object
 * @param sourceImage source image used for classification
 * @param items signs detected
 */
data class SignClassification(val identifier: Long, val sourceImage: Image, val items: List<SignValue>) {

    companion object {

        @JvmStatic
        internal fun fromSignClassificationDataBuffer(signClassificationDataBuffer: SignClassificationDataBuffer): SignClassification {

            // Set of items - 10. For each:
            // SignType
            // TypeConfidence
            // Number
            // NumberConfidence
            // Detection: 4 coordinates + 1 confidence + 1 type
            val signValuesItemsSize = signClassificationDataBuffer.signValueItems.size / 10

            val signValuesItems = ArrayList<SignValue>()

            var index = 0
            for (i in 0 until signValuesItemsSize) {

                val signType = SignType.values()[signClassificationDataBuffer.signValueItems[index++].toInt()]
                val typeConfidence = signClassificationDataBuffer.signValueItems[index++]
                val number = signClassificationDataBuffer.signValueItems[index++]
                val numberConfidence = signClassificationDataBuffer.signValueItems[index++]

                val type = ObjectType.values()[signClassificationDataBuffer.signValueItems[index++].toInt()]
                val confidence = signClassificationDataBuffer.signValueItems[index++]

                val startX = signClassificationDataBuffer.signValueItems[index++].toInt()
                val startY = signClassificationDataBuffer.signValueItems[index++].toInt()
                val endX = signClassificationDataBuffer.signValueItems[index++].toInt()
                val endY = signClassificationDataBuffer.signValueItems[index++].toInt()

                val detection = Detection(Rect(startX, startY, endX, endY), type, confidence)

                signValuesItems.add(SignValue(signType, typeConfidence, number, numberConfidence, detection))
            }

            val width = signClassificationDataBuffer.sourceImageDescriptionArray[0]
            val height = signClassificationDataBuffer.sourceImageDescriptionArray[1]
            val imageFormat = Image.Format.values()[signClassificationDataBuffer.sourceImageDescriptionArray[2]]

            val sourceImage = Image(imageFormat, width, height, signClassificationDataBuffer.sourceImageIdentifier)

            return SignClassification(signClassificationDataBuffer.signClassificationIdentifier, sourceImage, signValuesItems)

        }
    }
}
