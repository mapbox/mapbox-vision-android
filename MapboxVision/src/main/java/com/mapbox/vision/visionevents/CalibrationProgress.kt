package com.mapbox.vision.visionevents

import com.mapbox.vision.core.buffers.CalibrationDataBuffer

/**
 * Calibration [progress] in percents [0, 100] is [completed].
 */
data class CalibrationProgress(val progress: Int, val completed: Boolean) {
    internal constructor(buffer: CalibrationDataBuffer) : this(
            progress = (buffer.calibrationProgress[0] * 100).toInt(),
            completed = buffer.calibrationProgress[1] == 1f
    )
}
