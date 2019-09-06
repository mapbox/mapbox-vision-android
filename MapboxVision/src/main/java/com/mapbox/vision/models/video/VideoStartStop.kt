package com.mapbox.vision.models.video

internal data class VideoStartStop(val startSecond: Float, val endSecond: Float)

internal fun VideoTelemetry.mapToVideoClipStartStop() =
    VideoStartStop(
        startSecond = startSeconds,
        endSecond = endSeconds
    )

internal fun VideoVisionPro.mapToVideoClipStartStop() =
    VideoStartStop(
        startSecond = startSecond,
        endSecond = endSecond
    )