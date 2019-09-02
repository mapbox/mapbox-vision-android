package com.mapbox.vision.models.videoclip

internal data class VideoClipStartStop(val startSecond: Float, val endSecond: Float)

internal fun VideoClipTelemetry.mapToVideoClipStartStop() =
    VideoClipStartStop(
        startSecond = startSeconds,
        endSecond = endSeconds
    )

internal fun VideoClipVisionPro.mapToVideoClipStartStop() =
    VideoClipStartStop(
        startSecond = startSecond,
        endSecond = endSecond
    )