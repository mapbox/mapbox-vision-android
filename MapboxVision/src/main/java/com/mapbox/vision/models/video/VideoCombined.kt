package com.mapbox.vision.models.video

internal data class VideoCombined(
    val telemetries: Array<VideoTelemetry>?,
    val visionPros: Array<VideoVisionPro>?
)
