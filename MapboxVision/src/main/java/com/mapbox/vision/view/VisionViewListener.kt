package com.mapbox.vision.view

import com.mapbox.vision.mobile.core.models.FrameSegmentation
import com.mapbox.vision.mobile.core.models.detection.FrameDetections
import com.mapbox.vision.video.videosource.VideoSourceListener

interface VisionViewListener : VideoSourceListener {

    fun setSegmentation(frameSegmentation: FrameSegmentation)

    fun setDetections(frameDetections: FrameDetections)

}