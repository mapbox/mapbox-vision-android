package com.mapbox.vision.visionevents

import com.mapbox.vision.core.buffers.CalibrationDataBuffer

/**
 * Calibration [progress] in percents [0, 100] is [completed].
 */
data class CalibrationProgress(val progress: Int, val completed: Boolean) {
    companion object {
        internal fun fromBuffer(calibrationDataBuffer: CalibrationDataBuffer) = CalibrationProgress(
                progress = (calibrationDataBuffer.calibrationProgress[0] * 100).toInt(),
                completed = calibrationDataBuffer.calibrationProgress[1] == 1f
        )
    }
}
