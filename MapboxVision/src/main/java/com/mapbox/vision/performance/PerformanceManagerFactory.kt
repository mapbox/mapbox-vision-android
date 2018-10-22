package com.mapbox.vision.performance

import com.mapbox.vision.core.CoreWrapper
import com.mapbox.vision.core.utils.SystemInfoUtils

internal object PerformanceManagerFactory {

    private val supportedSnpeBoard = SystemInfoUtils.getSnpeSupportedBoard()

    fun getPerformanceManager(coreWrapper: CoreWrapper): PerformanceManager {
        if (supportedSnpeBoard.isNotBlank()) {
            return PerformanceManager.SnapdragonImpl(coreWrapper, supportedSnpeBoard)
        } else {
            TODO("Device does not support SNPE! Vision SDK does not work with this device yet.")
        }
    }
}
