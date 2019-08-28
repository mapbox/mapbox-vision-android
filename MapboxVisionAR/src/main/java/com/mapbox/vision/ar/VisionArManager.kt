package com.mapbox.vision.ar

import com.mapbox.vision.ar.core.NativeArManager
import com.mapbox.vision.ar.core.VisionArEventsListener
import com.mapbox.vision.ar.core.models.Route
import com.mapbox.vision.manager.BaseVisionManager
import com.mapbox.vision.manager.ModuleInterface
import com.mapbox.vision.utils.observable.Observable
import com.mapbox.vision.utils.observable.delegateWeakPropertyObservable

object VisionArManager : ModuleInterface, Observable<VisionArEventsListener> {

    private lateinit var nativeArManager: NativeArManager
    lateinit var visionManager: BaseVisionManager
        private set
    private var modulePtr: Long = 0L

    private val observerComposerVisionArEvents = ObserverComposerVisionArEvents()

    var visionArEventsListener by delegateWeakPropertyObservable(this)

    override fun registerModule(ptr: Long) {
        modulePtr = ptr
    }

    override fun unregisterModule() {
        modulePtr = 0
    }

    @JvmStatic
    @Deprecated(
        "Will be removed in 0.9.0. Use create() and var visionArEventsListener:VisionEventsListener instead",
        ReplaceWith("VisionArManager.create(baseVisionManager: BaseVisionManager)", "com.mapbox.vision.manager.BaseVisionManager")
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
        nativeArManager.create(modulePtr, observerComposerVisionArEvents)
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

    override fun addObservable(observer: VisionArEventsListener) =
        observerComposerVisionArEvents.addObservable(observer)

    override fun removeObserver(observer: VisionArEventsListener) =
        observerComposerVisionArEvents.removeObserver(observer)
}
