package com.mapbox.vision.visionevents.events.segmentation

import com.mapbox.vision.core.buffers.SegmentationDataBuffer
import com.mapbox.vision.visionevents.events.Image

/**
 * Holds [segmentationMaskImage] based on [sourceImage] with unique [identifier].
 *
 * NOTE : [segmentationMaskImage] can NOT be retrieved with [Image.getImageBitmap].
 * Use [Image.getImageBytes] instead.
 *
 * IMPORTANT : do not cache [sourceImage]. Data that it holds can be invalidated.
 * Instead, if you need the data, call [Image.getImageBitmap] or [Image.getImageBytes] and handle it.
 */
data class SegmentationMask(val identifier: Long, val sourceImage: Image, val segmentationMaskImage: Image) {

    companion object {

        @JvmStatic
        internal fun fromSegmentationDataBuffer(segmentationDataBuffer: SegmentationDataBuffer): SegmentationMask {

            val sourceWidth = segmentationDataBuffer.sourceImageDescriptionArray[0]
            val sourceHeight = segmentationDataBuffer.sourceImageDescriptionArray[1]
            val sourceImageFormat = Image.Format.values()[segmentationDataBuffer.sourceImageDescriptionArray[2]]

            val segmentationSourceImage = Image(sourceImageFormat,
                    sourceWidth, sourceHeight, segmentationDataBuffer.sourceImageIdentifier)

            val maskWidth = segmentationDataBuffer.maskImageDescriptionArray[0]
            val maskHeight = segmentationDataBuffer.maskImageDescriptionArray[1]
            val maskImageFormat = Image.Format.values()[segmentationDataBuffer.maskImageDescriptionArray[2]]
            val segmentationMaskImage = Image(maskImageFormat, maskWidth, maskHeight, segmentationDataBuffer.maskImageIdentifier)

            return SegmentationMask(segmentationDataBuffer.maskImageIdentifier, segmentationSourceImage, segmentationMaskImage)
        }
    }
}
