package com.mapbox.vision.manager

import com.mapbox.vision.mobile.core.interfaces.VisionEventsListener
import com.mapbox.vision.mobile.core.models.FrameSegmentation
import com.mapbox.vision.mobile.core.models.detection.FrameDetections
import com.mapbox.vision.mobile.core.models.frame.PixelCoordinate
import com.mapbox.vision.mobile.core.models.position.GeoCoordinate
import com.mapbox.vision.mobile.core.models.world.WorldCoordinate
import com.mapbox.vision.utils.observable.Observable
import com.mapbox.vision.video.videosource.VideoSource

interface BaseVisionManager : Observable<VisionEventsListener> {

    fun registerModule(moduleInterface: ModuleInterface)
    fun unregisterModule(moduleInterface: ModuleInterface)
    fun getDetectionsImage(frameDetections: FrameDetections): ByteArray
    fun getSegmentationImage(frameSegmentation: FrameSegmentation): ByteArray

    fun getVideoSource(): VideoSource

    fun worldToPixel(worldCoordinate: WorldCoordinate): PixelCoordinate?
    fun pixelToWorld(pixelCoordinate: PixelCoordinate): WorldCoordinate?
    fun worldToGeo(worldCoordinate: WorldCoordinate): GeoCoordinate?
    fun geoToWorld(geoCoordinate: GeoCoordinate): WorldCoordinate?
}
