package com.mapbox.vision.performance

/**
 * Determines whether SDK should adapt its performance to environmental changes
 * (acceleration/deceleration, standing time) or stay fixed.
 * Dynamic option can save some percentage of power.
 */
enum class ModelPerformanceMode {
    FIXED, DYNAMIC
}
