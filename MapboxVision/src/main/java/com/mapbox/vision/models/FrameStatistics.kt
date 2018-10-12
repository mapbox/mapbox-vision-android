package com.mapbox.vision.models

data class FrameStatistics(
        val segmentationFPS: Float,
        val detectionFPS: Float,
        val roadConfidenceFPS: Float,
        val segmentationDetectionFPS: Float,
        val coreUpdateFPS: Float
) {
    constructor(floatArrayData: FloatArray) : this(
            segmentationFPS = floatArrayData[0],
            detectionFPS = floatArrayData[1],
            roadConfidenceFPS = floatArrayData[2],
            segmentationDetectionFPS = floatArrayData[3],
            coreUpdateFPS = floatArrayData[4]
    )
}
