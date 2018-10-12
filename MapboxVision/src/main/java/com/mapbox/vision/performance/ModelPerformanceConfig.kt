package com.mapbox.vision.performance

/**
 * Represents different ML models configurations and performance settings for them.
 */
sealed class ModelPerformanceConfig {

    /**
     * Single merged segmentation and detection model.
     * Works faster and more efficient than separate models.
     */
    class Merged(val performance: ModelPerformance) : ModelPerformanceConfig()

    /**
     * Separate segmentation and detection models.
     * Use it if you want to have different FPS (can save some power comparing with merged model)
     */
    class Separate(
            val segmentationPerformance: ModelPerformance,
            val detectionPerformance: ModelPerformance
    ) : ModelPerformanceConfig()
}
