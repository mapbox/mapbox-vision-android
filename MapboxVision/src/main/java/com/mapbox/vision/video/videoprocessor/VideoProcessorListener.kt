package com.mapbox.vision.video.videoprocessor

import com.mapbox.vision.mobile.models.VideoClip

internal interface VideoProcessorListener {

    fun onVideoClipsReady(videoClips: HashMap<String, VideoClip>, videoDir: String, jsonFile: String)

}
