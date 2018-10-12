package com.mapbox.vision.models

data class FrameStatistics(val segmentationFPS: Float, val detectionFPS: Float, val roadConfidenceFPS: Float, val segmentationDetectionFPS: Float, val coreUpdateFPS: Float) {

    companion object {
        @JvmStatic
        fun fromFloatArrayData(floatArrayData: FloatArray): FrameStatistics {
            return FrameStatistics(floatArrayData[0], floatArrayData[1], floatArrayData[2], floatArrayData[3], floatArrayData[4])
        }
    }
}