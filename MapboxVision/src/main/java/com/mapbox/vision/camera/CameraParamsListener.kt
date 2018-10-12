package com.mapbox.vision.camera

import com.mapbox.vision.models.CameraParamsData

interface CameraParamsListener {

    fun onCameraParamsReady(cameraParamsData: CameraParamsData)
}