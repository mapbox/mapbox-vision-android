package com.mapbox.vision.models.videoclip

internal data class VideoClipsCombined(
    val telemetryClips: Array<VideoClipTelemetry>?,
    val visionProClips: Array<VideoClipVisionPro>?
)