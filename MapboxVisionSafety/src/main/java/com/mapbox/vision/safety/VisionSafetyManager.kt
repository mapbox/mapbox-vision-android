package com.mapbox.vision.safety

import com.mapbox.vision.manager.BaseVisionManager
import com.mapbox.vision.manager.ModuleInterface
import com.mapbox.vision.safety.core.NativeSafetyManager
import com.mapbox.vision.safety.core.VisionSafetyListener

object VisionSafetyManager : ModuleInterface {

    private lateinit var nativeSafetyManager: NativeSafetyManager
    private lateinit var visionManager: BaseVisionManager
    private var modulePtr: Long = 0L

    override fun registerModule(ptr: Long) {
        modulePtr = ptr
    }

    override fun unregisterModule() {
        modulePtr = 0
    }

    @JvmStatic
    fun create(baseVisionManager: BaseVisionManager, visionSafetyListener: VisionSafetyListener) {
        this.visionManager = baseVisionManager
        baseVisionManager.registerModule(this)

        nativeSafetyManager = NativeSafetyManager()
        nativeSafetyManager.create(modulePtr, visionSafetyListener)
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
}

