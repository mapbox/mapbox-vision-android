package com.mapbox.vision.models.videoclip

import com.mapbox.vision.mobile.core.models.VideoClip

internal data class VideoClipVisionPro(val startSecond: Float, val endSecond: Float, val metadata: VideoClipMetadata)

internal fun VideoClip.mapToVisionPro(): VideoClipVisionPro? {
    val metadata = metadata
    return if (metadata == null) {
        null
    } else {
        VideoClipVisionPro(
            startSecond = startSeconds,
            endSecond = endSeconds,
            metadata = metadata.toLocal()
        )
    }
}