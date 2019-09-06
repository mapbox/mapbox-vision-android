package com.mapbox.vision.session

import com.mapbox.vision.models.video.VideoCombined

internal interface SessionWriterListener {

    fun onSessionStop(
        clips: VideoCombined,
        videoPath: String,
        cachedTelemetryPath: String,
        coreSessionStartMillis: Long
    )
}
