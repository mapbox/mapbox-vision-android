package com.mapbox.vision.models.video

import com.mapbox.vision.mobile.core.models.VideoClip

internal data class VideoVisionPro(val startSecond: Float, val endSecond: Float, val metadata: VideoMetadata)

internal fun VideoClip.mapToVisionPro(): VideoVisionPro? {
    val metadata = metadata
    return if (metadata == null) {
        null
    } else {
        VideoVisionPro(
            startSecond = startSeconds,
            endSecond = endSeconds,
            metadata = metadata.toLocal()
        )
    }
}