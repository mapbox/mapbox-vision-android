package com.mapbox.vision.performancemanager

import com.mapbox.vision.performance.ModelPerformance

interface PerformanceManager {

    fun setDetectionPerformance(modelPerformance: ModelPerformance)

    fun setSegmentationPerformance(modelPerformance: ModelPerformance)
}