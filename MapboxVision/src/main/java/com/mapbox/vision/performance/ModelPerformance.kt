package com.mapbox.vision.performance

/**
 * Represents performance setting for tasks related to specific ML model.
 */
sealed class ModelPerformance {
    object Off : ModelPerformance()
    data class On(val mode: ModelPerformanceMode, val rate: ModelPerformanceRate) : ModelPerformance()
}
