package com.mapbox.vision.video.videosource

import com.mapbox.vision.models.CameraParams

interface VideoSourceListener {
    fun onNewFrame(rgbBytes: ByteArray)

    fun onNewCameraParams(cameraParams: CameraParams)
}
