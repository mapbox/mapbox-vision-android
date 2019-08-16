package com.mapbox.vision.video.videoprocessor

import com.mapbox.vision.mobile.core.models.VideoClip
import com.mapbox.vision.telemetry.HandlerSyncMangers

internal interface VideoProcessorListener {

    fun onVideoClipsReady(videoClips: HashMap<String, VideoClip>, videoDir: String, jsonFile: String, syncMangerType: HandlerSyncMangers.SyncMangerType)
}
