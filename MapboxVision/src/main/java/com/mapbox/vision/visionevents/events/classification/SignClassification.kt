package com.mapbox.vision.visionevents.events.classification

import android.graphics.Rect
import android.util.Log
import com.mapbox.vision.BuildConfig
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

    internal constructor(signClassificationDataBuffer: SignClassificationDataBuffer) : this(
            identifier = signClassificationDataBuffer.signClassificationIdentifier,
            sourceImage = Image(
                    format = Image.Format.values()[signClassificationDataBuffer.sourceImageDescriptionArray[2]],
                    width = signClassificationDataBuffer.sourceImageDescriptionArray[0],
                    height = signClassificationDataBuffer.sourceImageDescriptionArray[1],
                    identifier = signClassificationDataBuffer.sourceImageIdentifier
            ),
            items = ArrayList<SignValue>().also { signValueItems ->
                var index = 0
                val signValuesItemsSize = signClassificationDataBuffer.signValueItems.size / 10

                for (i in 0 until signValuesItemsSize) {

                    val signTypeIndex = signClassificationDataBuffer.signValueItems[index++].toInt()

                    val signType = if (signTypeIndex < 0 || signTypeIndex >= SignType.values().size) {
                        if (BuildConfig.DEBUG) {
                            throw IllegalArgumentException("Wrong sign type index $signTypeIndex")
                        }
                        Log.e(TAG, "Wrong sign type index $signTypeIndex")
                        SignType.Unknown
                    } else {
                        SignType.values()[signTypeIndex]
                    }

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

                    signValueItems.add(SignValue(signType, typeConfidence, number, numberConfidence, detection))
                }
            }
    )

    companion object {
        const val TAG = "SignClassification"
    }
}
