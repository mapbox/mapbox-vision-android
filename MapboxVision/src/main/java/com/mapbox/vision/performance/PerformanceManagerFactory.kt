package com.mapbox.vision.performance

import com.mapbox.vision.core.CoreWrapper

internal class PerformanceManagerFactory(
        private val isSnpeSupported: Boolean = false
) {
    fun getPerformanceManager(coreWrapper: CoreWrapper): PerformanceManager {
        if (isSnpeSupported) {
            return PerformanceManager.SnapdragonImpl(coreWrapper)
        } else {
            TODO("Device does not support SNPE! Vision SDK does not work with this device yet.")
        }
    }
}
