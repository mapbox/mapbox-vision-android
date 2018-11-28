package com.mapbox.vision.video.videoprocessor

internal interface VideoProcessorListener {

    fun onVideoPartsReady(videoPartMap: HashMap<String, VideoProcessor.VideoPart>, dirPath: String, jsonFilePath: String)

}
