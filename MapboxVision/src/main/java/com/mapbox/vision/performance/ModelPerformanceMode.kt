package com.mapbox.vision.performance

/**
 * Determines whether SDK should adapt its performance to environmental changes (acceleration/deceleration, standing time) or stay fixed.
 */
enum class ModelPerformanceMode {
    FIXED, DYNAMIC
}
