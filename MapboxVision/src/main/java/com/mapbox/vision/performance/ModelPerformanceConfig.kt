package com.mapbox.vision.performance

/**
 * Represents different ML models performance configurations.
 */
sealed class ModelPerformanceConfig {

    /**
     * Single model performing both detections and segmentation.
     * Works faster, has better quality than separate models running with equal performance.
     * Used if both segmentation and detection need to work with same FPS.
     */
    class Merged(val performance: ModelPerformance) : ModelPerformanceConfig()

    /**
     * Separate models for segmentation and detection.
     * Allows to run models with different performance hence is more power efficient in such configurations.
     * Used if segmentation or detection can work with lower performance and power consumption is a concern.
     */
    class Separate(
            val segmentationPerformance: ModelPerformance,
            val detectionPerformance: ModelPerformance
    ) : ModelPerformanceConfig()
}
