package com.mapbox.vision.ar

import com.mapbox.vision.ModuleInterface
import com.mapbox.vision.VisionManager
import com.mapbox.vision.ar.core.NativeArManager
import com.mapbox.vision.ar.core.VisionArEventsListener
import com.mapbox.vision.ar.core.models.Route

object VisionArManager : ModuleInterface {

    private lateinit var nativeArManager: NativeArManager
    private var modulePtr: Long = 0L

    override fun registerModule(ptr: Long) {
        modulePtr = ptr
    }

    override fun unregisterModule() {
        modulePtr = 0
    }

    @JvmStatic
    fun create(visionManager: VisionManager, visionArEventsListener: VisionArEventsListener) {
        nativeArManager = NativeArManager()
        visionManager.registerModule(this)
        nativeArManager.create(modulePtr, visionArEventsListener)
    }

    @JvmStatic
    fun destroy() {
        nativeArManager.destroy()
    }

    @JvmStatic
    fun setRoute(route: Route) {
        nativeArManager.setRoute(route)
    }
}

