package com.mapbox.vision.performance

import com.mapbox.vision.core.CoreWrapper

class StubPerformanceManagerImp(private val coreWrapper: CoreWrapper) : PerformanceManager {

    override fun setModelConfig(modelConfig: ModelPerformanceConfig) {
        coreWrapper.setDetectionFixedFps(40f)
        coreWrapper.setSegmentationFixedFps(40f)
    }
}