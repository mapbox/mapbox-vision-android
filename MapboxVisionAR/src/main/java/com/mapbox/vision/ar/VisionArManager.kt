package com.mapbox.vision.ar

import com.mapbox.vision.ar.core.NativeArManager
import com.mapbox.vision.ar.core.VisionArEventsListener
import com.mapbox.vision.ar.core.models.Route
import com.mapbox.vision.manager.BaseVisionManager
import com.mapbox.vision.manager.ModuleInterface

object VisionArManager : ModuleInterface {

    private lateinit var nativeArManager: NativeArManager
    private lateinit var visionManager: BaseVisionManager
    private var modulePtr: Long = 0L

    override fun registerModule(ptr: Long) {
        modulePtr = ptr
    }

    override fun unregisterModule() {
        modulePtr = 0
    }

    @JvmStatic
    fun create(baseVisionManager: BaseVisionManager, visionArEventsListener: VisionArEventsListener) {
        this.visionManager = baseVisionManager
        baseVisionManager.registerModule(this)

        nativeArManager = NativeArManager()
        nativeArManager.create(modulePtr, visionArEventsListener)
    }

    @JvmStatic
    fun destroy() {
        nativeArManager.destroy()
        visionManager.unregisterModule(this)
    }

    @JvmStatic
    fun setRoute(route: Route) {
        nativeArManager.setRoute(route)
    }

    @JvmStatic
    fun setLaneLength(laneLength: Double) {
        nativeArManager.setLaneLength(laneLength)
    }
}
