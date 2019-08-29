package com.mapbox.vision.safety

import com.mapbox.vision.manager.BaseVisionManager
import com.mapbox.vision.manager.ModuleInterface
import com.mapbox.vision.safety.core.NativeSafetyManager
import com.mapbox.vision.safety.core.VisionSafetyListener
import com.mapbox.vision.utils.observable.CompositeListener
import com.mapbox.vision.utils.observable.delegateWeakPropertyObservable

object VisionSafetyManager : ModuleInterface, CompositeListener<VisionSafetyListener> {

    private lateinit var nativeSafetyManager: NativeSafetyManager
    private lateinit var visionManager: BaseVisionManager
    private var modulePtr: Long = 0L

    private val compositeListenerVisionEvents = CompositeListenerVisionSafety()

    @JvmStatic
    var visionSafetyListener by delegateWeakPropertyObservable(this)

    override fun registerModule(ptr: Long) {
        modulePtr = ptr
    }

    override fun unregisterModule() {
        modulePtr = 0
    }

    @JvmStatic
    @Deprecated(
        "Will be removed in 0.9.0. Use create() and setVisionArEventsListener(VisionSafetyListener) instead",
        ReplaceWith("VisionSafetyManager.create(baseVisionManager: BaseVisionManager)", "com.mapbox.vision.manager.BaseVisionManager")
    )
    fun create(baseVisionManager: BaseVisionManager, visionSafetyListener: VisionSafetyListener) {
        this.visionManager = baseVisionManager
        baseVisionManager.registerModule(this)

        nativeSafetyManager = NativeSafetyManager()
        nativeSafetyManager.create(modulePtr, visionSafetyListener)
    }

    @JvmStatic
    fun create(baseVisionManager: BaseVisionManager) {
        this.visionManager = baseVisionManager
        baseVisionManager.registerModule(this)

        nativeSafetyManager = NativeSafetyManager()
        nativeSafetyManager.create(modulePtr, compositeListenerVisionEvents)
    }

    @JvmStatic
    fun destroy() {
        nativeSafetyManager.destroy()
        visionManager.unregisterModule(this)
    }

    /**
     * Set sensitivity thresholds for collision detection.
     * @param warningTime a threshold in seconds for [com.mapbox.vision.safety.core.models.CollisionDangerLevel.Warning]
     * @param criticalTime a threshold in seconds for [com.mapbox.vision.safety.core.models.CollisionDangerLevel.Critical]
     */
    @JvmStatic
    fun setTimeToCollisionWithVehicle(warningTime: Float, criticalTime: Float) {
        nativeSafetyManager.setTimeToCollisionWithVehicle(warningTime, criticalTime)
    }

    /**
     * Set minimal speed (in m/s) for collision system to work.
     */
    @JvmStatic
    fun setCollisionMinSpeed(speed: Float) {
        nativeSafetyManager.setCollisionMinSpeed(speed)
    }

    override fun addListener(observer: VisionSafetyListener) =
        compositeListenerVisionEvents.addListener(observer)

    override fun removeListener(observer: VisionSafetyListener) =
        compositeListenerVisionEvents.removeListener(observer)
}
