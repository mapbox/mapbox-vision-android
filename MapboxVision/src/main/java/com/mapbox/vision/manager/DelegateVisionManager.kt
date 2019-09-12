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
import com.mapbox.vision.mobile.core.utils.delegate.DelegateWeakRef
import com.mapbox.vision.performance.ModelPerformance
import com.mapbox.vision.performance.ModelPerformanceConfig
import com.mapbox.vision.performance.ModelPerformanceMode
import com.mapbox.vision.performance.ModelPerformanceRate
import com.mapbox.vision.performance.PerformanceManager
import com.mapbox.vision.utils.listeners.CompositeListener
import com.mapbox.vision.video.videosource.CompositeVideoSourceListener
import com.mapbox.vision.video.videosource.VideoSource
import com.mapbox.vision.video.videosource.VideoSourceListener
import com.mapbox.vision.view.VisionGLView
import com.mapbox.vision.view.VisionView

internal interface DelegateVisionManager<T : VideoSource> : BaseVisionManager,
    CompositeListener<VisionEventsListener> {

    val externalVideoSourceListener: VideoSourceListener?
    val nativeVisionManagerBase: NativeVisionManagerBase
    val performanceManager: PerformanceManager

    val isStarted: Boolean
    val isCreated: Boolean

    var visionEventsListener: VisionEventsListener?
    var keepListenerStrongRef: Boolean
    var videoSource: T

    fun create(
        nativeVisionManagerBase: NativeVisionManagerBase,
        performanceManager: PerformanceManager
    )

    fun start(onCountrySet: (Country) -> Unit = {})

    fun stop()
    fun destroy()

    fun setVideoSourceListener(videoSourceListener: VideoSourceListener)

    fun attachVideoSourceListener(videoSourceListener: VideoSourceListener)
    fun detachVideoSourceListener()

    fun setModelPerformanceConfig(modelPerformanceConfig: ModelPerformanceConfig)

    fun getFrameStatistics(): FrameStatistics

    fun checkManagerCreated()
    fun checkManagerStarted()

    fun addVideoSourceListener(listener: VideoSourceListener)
    fun removeVideoSourceListener(listener: VideoSourceListener)

    fun worldToPixel(worldCoordinate: WorldCoordinate): PixelCoordinate?
    fun pixelToWorld(pixelCoordinate: PixelCoordinate): WorldCoordinate?
    fun worldToGeo(worldCoordinate: WorldCoordinate): GeoCoordinate?
    fun geoToWorld(geoCoordinate: GeoCoordinate): WorldCoordinate?

    class Impl<T : VideoSource> : DelegateVisionManager<T> {

        override lateinit var videoSource: T

        override var externalVideoSourceListener: VideoSourceListener? = null
            set(value) {
                value?.let { compositeVideoSourceListener.addListener(it) }
                field?.let { compositeVideoSourceListener.removeListener(it) }
                field = value
            }

        override lateinit var nativeVisionManagerBase: NativeVisionManagerBase
        override lateinit var performanceManager: PerformanceManager

        override var isStarted: Boolean = false
        override var isCreated: Boolean = false

        private lateinit var onCountrySet: (Country) -> Unit

        // TODO should be refactored to visionEventsListener by delegateWeakPropertyListener after 0.9.0. Now we should keep strong link on listener
        override var visionEventsListener: VisionEventsListener? by DelegateWeakRef.valueChange { oldValue, newValue ->
            if (keepListenerStrongRef || newValue == null) {
                visionEventsListenerStrongLink = newValue
            }
            oldValue?.let { removeListener(it) }
            newValue?.let { addListener(it) }
        }
        var visionEventsListenerStrongLink: VisionEventsListener? = null
        // TODO remove after 0.9.0 along with visionEventsListenerStrongLink
        override var keepListenerStrongRef: Boolean = false

        private val compositeListener = object : CompositeVisionEventsListener() {
            override fun onCountryUpdated(country: Country) {
                super.onCountryUpdated(country)
                onCountrySet(country)
            }
        }

        private val compositeVideoSourceListener = CompositeVideoSourceListener()
        private var weakVideoSourceListener by DelegateWeakRef.valueChange<VideoSourceListener> { oldValue, newValue ->
            oldValue?.let { compositeVideoSourceListener.removeListener(it) }
            newValue?.let { compositeVideoSourceListener.addListener(it) }
        }

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

        override fun start(onCountrySet: (Country) -> Unit) {
            isStarted = true
            this.onCountrySet = onCountrySet
            nativeVisionManagerBase.start(compositeListener)
        }

        override fun addListener(listener: VisionEventsListener) =
            compositeListener.addListener(listener)

        override fun removeListener(listener: VisionEventsListener) =
            compositeListener.removeListener(listener)

        override fun addVideoSourceListener(listener: VideoSourceListener) =
            compositeVideoSourceListener.addListener(listener)

        override fun removeVideoSourceListener(listener: VideoSourceListener) =
            compositeVideoSourceListener.removeListener(listener)

        override fun stop() {
            nativeVisionManagerBase.stop()
            isStarted = false
        }

        override fun destroy() {
            isCreated = false
        }

        override fun attachVideoSourceListener(videoSourceListener: VideoSourceListener) {
            weakVideoSourceListener = videoSourceListener
            videoSource.attach(compositeVideoSourceListener)
        }

        override fun detachVideoSourceListener() {
            weakVideoSourceListener = null
            externalVideoSourceListener = null
            videoSource.detach()
        }

        override fun setVideoSourceListener(videoSourceListener: VideoSourceListener) {
            this.externalVideoSourceListener = videoSourceListener
            // FIXME
            if (videoSourceListener is VisionView) {
                videoSourceListener.baseVisionManager = this
            } else if (videoSourceListener is VisionGLView) {
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
