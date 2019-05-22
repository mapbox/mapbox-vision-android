package com.mapbox.vision.manager

import com.mapbox.vision.mobile.core.models.FrameSegmentation
import com.mapbox.vision.mobile.core.models.detection.FrameDetections

interface BaseVisionManager {

    fun registerModule(moduleInterface: ModuleInterface)
    fun unregisterModule(moduleInterface: ModuleInterface)
    fun getDetectionsImage(frameDetections: FrameDetections): ByteArray
    fun getSegmentationImage(frameSegmentation: FrameSegmentation): ByteArray
}
