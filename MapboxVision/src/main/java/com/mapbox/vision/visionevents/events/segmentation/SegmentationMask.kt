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

    internal constructor(segmentationDataBuffer: SegmentationDataBuffer) : this(
            identifier = segmentationDataBuffer.maskImageIdentifier,
            sourceImage = Image(
                    format = Image.Format.values()[segmentationDataBuffer.sourceImageDescriptionArray[2]],
                    width = segmentationDataBuffer.sourceImageDescriptionArray[0],
                    height = segmentationDataBuffer.sourceImageDescriptionArray[1],
                    identifier = segmentationDataBuffer.sourceImageIdentifier
            ),
            segmentationMaskImage = Image(
                    format = Image.Format.values()[segmentationDataBuffer.maskImageDescriptionArray[2]],
                    width = segmentationDataBuffer.maskImageDescriptionArray[0],
                    height = segmentationDataBuffer.maskImageDescriptionArray[1],
                    identifier = segmentationDataBuffer.maskImageIdentifier
            )
    )
}
