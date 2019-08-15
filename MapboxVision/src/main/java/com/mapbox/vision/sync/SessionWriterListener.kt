package com.mapbox.vision.sync

import com.mapbox.vision.mobile.core.models.VideoClip

internal interface SessionWriterListener {

    fun onSessionStop(
        clips: Array<VideoClip>,
        videoPath: String,
        cachedTelemetryPath: String,
        coreSessionStartMillis: Long
    )
}
