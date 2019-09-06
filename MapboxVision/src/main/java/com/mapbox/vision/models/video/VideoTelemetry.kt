package com.mapbox.vision.models.video

import com.mapbox.vision.mobile.core.models.VideoClip

internal data class VideoTelemetry(val startSeconds: Float, val endSeconds: Float)

internal fun VideoClip.mapToTelemetry(): VideoTelemetry =
    VideoTelemetry(
        startSeconds = startSeconds,
        endSeconds = endSeconds
    )
