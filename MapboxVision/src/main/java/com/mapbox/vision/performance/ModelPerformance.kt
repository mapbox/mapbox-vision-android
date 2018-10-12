package com.mapbox.vision.performance

/**
 * Represents performance setting for tasks related to specific ML model.
 */
data class ModelPerformance(val mode: ModelPerformanceMode, val rate: ModelPerformanceRate)
