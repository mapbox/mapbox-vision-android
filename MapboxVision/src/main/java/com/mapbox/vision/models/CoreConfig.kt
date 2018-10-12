package com.mapbox.vision.models

internal data class CoreConfig(
        val debugOverlayUpdate: Boolean = false,
        val drawSegMaskInDebug: Boolean = false,
        val drawCurLaneInDebug: Boolean = false,
        val drawMarkingLanesInDebug: Boolean = false,
        val drawRouteInDebug: Boolean = false,
        val saveTelemetry: Boolean = false,
        val solveCameraWorldTransform: Boolean = false,
        val useCarDistanceMeasure: Boolean = false,
        val useClassification: Boolean = false,
        val useDetectionMapping: Boolean = false,
        val useDetection: Boolean = false,
        val useMapMatching: Boolean = false,
        val useRoadConfidence: Boolean = false,
        val useSegmentation: Boolean = false,
        val useTracking: Boolean = false,
        val useTrajectoryEstimator: Boolean = false,
        val segmentationFPSPolicy: FPSPolicy = FPSPolicy.FPSPolicyFixed,
        val segmentationFPS: Float = 10f,
        val detectionFPSPolicy: FPSPolicy = FPSPolicy.FPSPolicyFixed,
        val detectionFPS: Float = 10f
) {

    enum class FPSPolicy {
        FPSPolicyFixed, FPSPolicyDynamic
    }

}
