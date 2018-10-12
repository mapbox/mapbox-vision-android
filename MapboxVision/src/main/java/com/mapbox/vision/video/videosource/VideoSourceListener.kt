package com.mapbox.vision.video.videosource

import android.graphics.Bitmap
import com.mapbox.vision.models.CameraParamsData

internal interface VideoSourceListener {

    fun onNewFrame(rgbBytes: ByteArray)

    fun onNewBitmap(bitmap: Bitmap)

    fun onNewCameraParams(cameraParamsData: CameraParamsData)

    fun onFileRecorded(recordedFilePath:String)

}