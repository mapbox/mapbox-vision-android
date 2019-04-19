package com.mapbox.vision.video.videosource

import com.mapbox.vision.mobile.core.models.CameraParameters
import com.mapbox.vision.mobile.core.models.frame.ImageFormat
import com.mapbox.vision.mobile.core.models.frame.ImageSize

interface VideoSourceListener {
    fun onNewFrame(
        rgbaBytes: ByteArray,
        imageFormat: ImageFormat,
        imageSize: ImageSize
    )

    fun onNewCameraParameters(cameraParameters: CameraParameters)
}
