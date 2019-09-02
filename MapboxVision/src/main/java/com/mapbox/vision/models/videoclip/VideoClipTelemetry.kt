package com.mapbox.vision.models.videoclip

import com.mapbox.vision.mobile.core.models.VideoClip

internal data class VideoClipTelemetry(val startSeconds: Float, val endSeconds: Float)

internal fun VideoClip.mapToTelemetry(): VideoClipTelemetry =
    VideoClipTelemetry(
        startSeconds = startSeconds,
        endSeconds = endSeconds
    )