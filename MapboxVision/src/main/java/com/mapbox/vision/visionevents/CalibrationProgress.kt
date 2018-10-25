package com.mapbox.vision.visionevents

import com.mapbox.vision.core.buffers.CalibrationDataBuffer

data class CalibrationProgress(val progress: Int, val completed: Boolean) {
    companion object {
        internal fun fromBuffer(calibrationDataBuffer: CalibrationDataBuffer) = CalibrationProgress(
                progress = calibrationDataBuffer.calibrationProgress[0].toInt(),
                completed = calibrationDataBuffer.calibrationProgress[1] == 1f
        )
    }
}
