package com.mapbox.vision.performancemanager

import com.mapbox.vision.core.CoreWrapper

internal class PerformanceManagerFactory {

    var isSNPESupportedDevice: Boolean = false
        private set

    fun supportSNPE(isSNPESupported: Boolean) = apply { this.isSNPESupportedDevice = isSNPESupported }

    fun getPerformanceManager(coreWrapper: CoreWrapper) : PerformanceManager {
        if(isSNPESupportedDevice) {
            return JNICoreSnapdragonPerformanceManager(coreWrapper)
        } else {
            TODO("Is not implemented")
        }
    }
}