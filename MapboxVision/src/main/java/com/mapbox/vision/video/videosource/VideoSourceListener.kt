package com.mapbox.vision.video.videosource

import com.mapbox.vision.models.CameraParamsData

interface VideoSourceListener {
    fun onNewFrame(rgbBytes: ByteArray)

    fun onNewCameraParams(cameraParamsData: CameraParamsData)

    fun onFileRecorded(recordedFilePath: String)
}
