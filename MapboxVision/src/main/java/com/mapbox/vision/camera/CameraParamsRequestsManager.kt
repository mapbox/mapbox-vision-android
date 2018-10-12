package com.mapbox.vision.camera

import android.os.Handler
import android.os.Looper
import android.util.Size
import android.util.SizeF
import com.mapbox.vision.models.CameraParamsData


class CameraParamsRequestsManager(private val previewSize: Size, private val focalLength: Float, sensorSize: SizeF) {

    private val requestHandler = Handler(Looper.getMainLooper())
    var cameraParamsListener: CameraParamsListener? = null

    private val focalInPixelsX: Float
    private val focalInPixelsY: Float

    init {
        val w = 0.5f * sensorSize.width
        val h = 0.5f * sensorSize.height

        focalInPixelsX = (previewSize.width * focalLength) / (2 * w)
        focalInPixelsY = (previewSize.height * focalLength) / (2 * h)
    }


    fun startDataRequesting() {
        // TODO: Change logic to set it once per session
        requestHandler.postDelayed({ requestParamsAndNotifyListener() }, UPDATE_DELAY)

    }

    fun stopDataRequesting() {
        requestHandler.removeCallbacksAndMessages(null)
    }

    private fun requestParamsAndNotifyListener() {
        val listener = cameraParamsListener ?: return
        listener.onCameraParamsReady(CameraParamsData(previewSize.width, previewSize.height, focalLength, focalInPixelsX, focalInPixelsY))
        requestHandler.postDelayed({ requestParamsAndNotifyListener() }, UPDATE_DELAY)
    }

    companion object {
        private const val UPDATE_DELAY = (1000 * 5).toLong() // 5 second
    }


}