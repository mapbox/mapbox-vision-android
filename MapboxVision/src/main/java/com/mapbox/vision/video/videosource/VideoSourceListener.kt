package com.mapbox.vision.video.videosource

import com.mapbox.vision.mobile.models.frame.ImageFormat
import com.mapbox.vision.mobile.models.CameraParameters

interface VideoSourceListener {
    fun onNewFrame(rgbaBytes: ByteArray, imageFormat: ImageFormat)

    fun onNewCameraParameters(cameraParameters: CameraParameters)
}
