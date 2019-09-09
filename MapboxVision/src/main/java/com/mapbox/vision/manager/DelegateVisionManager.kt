package com.mapbox.vision.manager

import com.mapbox.vision.mobile.core.NativeVisionManagerBase
import com.mapbox.vision.mobile.core.interfaces.VisionEventsListener
import com.mapbox.vision.mobile.core.models.Country
import com.mapbox.vision.mobile.core.models.FrameSegmentation
import com.mapbox.vision.mobile.core.models.FrameStatistics
import com.mapbox.vision.mobile.core.models.detection.FrameDetections
import com.mapbox.vision.mobile.core.models.frame.PixelCoordinate
import com.mapbox.vision.mobile.core.models.position.GeoCoordinate
import com.mapbox.vision.mobile.core.models.world.WorldCoordinate
import com.mapbox.vision.performance.ModelPerformance
import com.mapbox.vision.performance.ModelPerformanceConfig
import com.mapbox.vision.performance.ModelPerformanceMode
import com.mapbox.vision.performance.ModelPerformanceRate
import com.mapbox.vision.performance.PerformanceManager
import com.mapbox.vision.video.videosource.VideoSourceListener
import com.mapbox.vision.view.VisionView

internal interface DelegateVisionManager : BaseVisionManager {

    val externalVideoSourceListener: VideoSourceListener?
    val nativeVisionManagerBase: NativeVisionManagerBase
    val performanceManager: PerformanceManager

    val isStarted: Boolean
    val isCreated: Boolean

    fun create(
        nativeVisionManagerBase: NativeVisionManagerBase,
        performanceManager: PerformanceManager
    )

    fun start(
        visionEventsListener: VisionEventsListener,
        onCountrySet: (Country) -> Unit = {}
    )

    fun stop()
    fun destroy()

    fun setVideoSourceListener(videoSourceListener: VideoSourceListener)

    fun setModelPerformanceConfig(modelPerformanceConfig: ModelPerformanceConfig)

    fun worldToPixel(worldCoordinate: WorldCoordinate): PixelCoordinate?
    fun pixelToWorld(pixelCoordinate: PixelCoordinate): WorldCoordinate?
    fun worldToGeo(worldCoordinate: WorldCoordinate): GeoCoordinate?
    fun geoToWorld(geoCoordinate: GeoCoordinate): WorldCoordinate?

    fun getFrameStatistics(): FrameStatistics

    fun checkManagerCreated()
    fun checkManagerStarted()

    class Impl : DelegateVisionManager {

        override var externalVideoSourceListener: VideoSourceListener? = null

        override lateinit var nativeVisionManagerBase: NativeVisionManagerBase
        override lateinit var performanceManager: PerformanceManager

        override var isStarted: Boolean = false
        override var isCreated: Boolean = false

        override fun create(
            nativeVisionManagerBase: NativeVisionManagerBase,
            performanceManager: PerformanceManager
        ) {
            isCreated = true

            this.nativeVisionManagerBase = nativeVisionManagerBase
            this.performanceManager = performanceManager

            this.performanceManager.setModelConfig(
                ModelPerformanceConfig.Merged(
                    ModelPerformance.On(
                        mode = ModelPerformanceMode.DYNAMIC,
                        rate = ModelPerformanceRate.HIGH
                    )
                )
            )
        }

        override fun start(
            visionEventsListener: VisionEventsListener,
            onCountrySet: (Country) -> Unit
        ) {
            isStarted = true

            nativeVisionManagerBase.start(object : VisionEventsListener by visionEventsListener {
                override fun onCountryUpdated(country: Country) {
                    visionEventsListener.onCountryUpdated(country)
                    onCountrySet(country)
                }
            })
        }

        override fun stop() {
            nativeVisionManagerBase.stop()

            isStarted = false
        }

        override fun destroy() {
            isCreated = false
        }

        override fun setVideoSourceListener(videoSourceListener: VideoSourceListener) {
            this.externalVideoSourceListener = videoSourceListener
            // FIXME
            if (videoSourceListener is VisionView) {
                videoSourceListener.baseVisionManager = this
            }
        }

        override fun setModelPerformanceConfig(modelPerformanceConfig: ModelPerformanceConfig) {
            performanceManager.setModelConfig(modelPerformanceConfig)
        }

        override fun worldToPixel(worldCoordinate: WorldCoordinate): PixelCoordinate? {
            checkManagerStarted()
            return nativeVisionManagerBase.worldToPixel(worldCoordinate)
        }

        override fun pixelToWorld(pixelCoordinate: PixelCoordinate): WorldCoordinate? {
            checkManagerStarted()
            return nativeVisionManagerBase.pixelToWorld(pixelCoordinate)
        }

        override fun worldToGeo(worldCoordinate: WorldCoordinate): GeoCoordinate? {
            checkManagerStarted()
            return nativeVisionManagerBase.worldToGeo(worldCoordinate)
        }

        override fun geoToWorld(geoCoordinate: GeoCoordinate): WorldCoordinate? {
            checkManagerStarted()
            return nativeVisionManagerBase.geoToWorld(geoCoordinate)
        }

        override fun getFrameStatistics(): FrameStatistics {
            checkManagerStarted()
            return nativeVisionManagerBase.getFrameStatistics()
        }

        override fun getDetectionsImage(frameDetections: FrameDetections): ByteArray {
            checkManagerStarted()
            return nativeVisionManagerBase.getDetectionsFrameBytes(frameDetections.frame.image.identifier)
        }

        override fun getSegmentationImage(frameSegmentation: FrameSegmentation): ByteArray {
            checkManagerStarted()
            return nativeVisionManagerBase.getSegmentationFrameBytes(frameSegmentation.frame.image.identifier)
        }

        override fun registerModule(moduleInterface: ModuleInterface) {
            moduleInterface.registerModule(nativeVisionManagerBase.getModulePtr())
        }

        override fun unregisterModule(moduleInterface: ModuleInterface) {
            moduleInterface.unregisterModule()
        }

        override fun checkManagerCreated() {
            if (!isCreated) {
                throw IllegalStateException("${javaClass.simpleName} was not created. Call create() first.")
            }
        }

        override fun checkManagerStarted() {
            if (!isStarted) {
                throw IllegalStateException("${javaClass.simpleName} was not started. Call attach() first.")
            }
        }
    }
}
