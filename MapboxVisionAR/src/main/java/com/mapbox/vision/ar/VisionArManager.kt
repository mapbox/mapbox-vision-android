package com.mapbox.vision.ar

import com.mapbox.vision.ar.core.NativeArManager
import com.mapbox.vision.ar.core.VisionArEventsListener
import com.mapbox.vision.ar.core.models.Route
import com.mapbox.vision.manager.BaseVisionManager
import com.mapbox.vision.manager.ModuleInterface
import com.mapbox.vision.mobile.core.utils.delegate.DelegateWeakRef

object VisionArManager : ModuleInterface {

    lateinit var visionManager: BaseVisionManager
        private set

    private lateinit var nativeArManager: NativeArManager
    private var modulePtr: Long = 0L

    private val compositeListenerVisionArEvents = CompositeListenerVisionArEvents()

    @JvmStatic
    var visionArEventsListener by DelegateWeakRef.valueChange<VisionArEventsListener> { oldValue, newValue ->
        oldValue?.let { removeListener(it) }
        newValue?.let { addListener(it) }
    }

    override fun registerModule(ptr: Long) {
        modulePtr = ptr
    }

    override fun unregisterModule() {
        modulePtr = 0
    }

    @JvmStatic
    @Deprecated(
        "Will be removed in 0.9.0. Use create() and setVisionArEventsListener(VisionEventsListener) instead",
        ReplaceWith(
            "VisionArManager.create(baseVisionManager: BaseVisionManager)",
            "com.mapbox.vision.manager.BaseVisionManager"
        )
    )
    fun create(
        baseVisionManager: BaseVisionManager,
        visionArEventsListener: VisionArEventsListener
    ) {
        this.visionArEventsListener = visionArEventsListener
        create(baseVisionManager)
    }

    fun create(
        baseVisionManager: BaseVisionManager
    ) {
        this.visionManager = baseVisionManager
        baseVisionManager.registerModule(this)

        nativeArManager = NativeArManager()
        nativeArManager.create(modulePtr, compositeListenerVisionArEvents)
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

    internal fun addListener(observer: VisionArEventsListener) =
        compositeListenerVisionArEvents.addListener(observer)

    internal fun removeListener(observer: VisionArEventsListener) =
        compositeListenerVisionArEvents.removeListener(observer)
}
