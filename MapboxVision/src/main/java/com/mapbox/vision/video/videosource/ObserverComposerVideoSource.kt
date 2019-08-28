package com.mapbox.vision.video.videosource

import com.mapbox.vision.mobile.core.models.CameraParameters
import com.mapbox.vision.mobile.core.models.frame.ImageFormat
import com.mapbox.vision.mobile.core.models.frame.ImageSize
import com.mapbox.vision.utils.observable.ObserverComposer

class ObserverComposerVideoSource : ObserverComposer<VideoSourceListener>(), VideoSourceListener {

    override fun onNewFrame(rgbaBytes: ByteArray, imageFormat: ImageFormat, imageSize: ImageSize) =
        forEach { onNewFrame(rgbaBytes, imageFormat, imageSize) }

    override fun onNewCameraParameters(cameraParameters: CameraParameters) =
        forEach { onNewCameraParameters(cameraParameters) }
}
