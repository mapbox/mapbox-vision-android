package com.mapbox.vision.models.video

import com.mapbox.vision.mobile.core.models.video.VideoClipPro

internal data class VideoVisionPro(
    val startSecond: Float,
    val endSecond: Float,
    val metadata: VideoMetadata
)

internal fun VideoClipPro.mapToVisionPro(): VideoVisionPro =
    VideoVisionPro(
        startSecond = startSeconds,
        endSecond = endSeconds,
        metadata = metadata.mapToVideoMetadata(url = url)
    )
