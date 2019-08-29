package com.mapbox.vision.manager

import com.mapbox.vision.mobile.core.interfaces.VisionEventsListener
import com.mapbox.vision.mobile.core.models.FrameSegmentation
import com.mapbox.vision.mobile.core.models.detection.FrameDetections
import com.mapbox.vision.utils.observable.CompositeListener
import com.mapbox.vision.video.videosource.VideoSource

interface BaseVisionManager : CompositeListener<VisionEventsListener> {

    val videoSource: VideoSource

    fun registerModule(moduleInterface: ModuleInterface)
    fun unregisterModule(moduleInterface: ModuleInterface)
    fun getDetectionsImage(frameDetections: FrameDetections): ByteArray
    fun getSegmentationImage(frameSegmentation: FrameSegmentation): ByteArray
}
