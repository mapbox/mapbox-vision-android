package com.mapbox.vision.sync

import com.mapbox.vision.models.videoclip.VideoClipsCombined

internal interface SessionWriterListener {

    fun onSessionStop(
        clips: VideoClipsCombined,
        videoPath: String,
        cachedTelemetryPath: String,
        coreSessionStartMillis: Long
    )
}
