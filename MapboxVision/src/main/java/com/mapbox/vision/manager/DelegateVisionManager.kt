package com.mapbox.vision.manager

import com.mapbox.android.telemetry.AppUserTurnstile
import com.mapbox.android.telemetry.MapboxTelemetry
import com.mapbox.vision.BuildConfig
import com.mapbox.vision.VisionManager
import com.mapbox.vision.mobile.core.NativeVisionManagerBase
import com.mapbox.vision.mobile.core.interfaces.VisionEventsListener
import com.mapbox.vision.mobile.core.models.Country
import com.mapbox.vision.mobile.core.models.FrameSegmentation
import com.mapbox.vision.mobile.core.models.FrameStatistics
import com.mapbox.vision.mobile.core.models.detection.FrameDetections
import com.mapbox.vision.mobile.core.models.frame.PixelCoordinate
import com.mapbox.vision.mobile.core.models.position.GeoCoordinate
import com.mapbox.vision.mobile.core.models.world.WorldCoordinate
import com.mapbox.vision.performance.ModelPerformanceConfig
import com.mapbox.vision.performance.PerformanceManager
import com.mapbox.vision.telemetry.TelemetrySyncManager
import com.mapbox.vision.utils.FileUtils
import com.mapbox.vision.video.videosource.VideoSourceListener
import com.mapbox.vision.view.VisionView
import java.io.File

internal interface DelegateVisionManager : BaseVisionManager {

    val externalVideoSourceListener: VideoSourceListener?
    val nativeVisionManagerBase: NativeVisionManagerBase
    val performanceManager: PerformanceManager
    val rootTelemetryDir: String
    val mapboxTelemetry: MapboxTelemetry
    val telemetrySyncManager: TelemetrySyncManager

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
        override lateinit var rootTelemetryDir: String
        override lateinit var mapboxTelemetry: MapboxTelemetry
        override lateinit var telemetrySyncManager: TelemetrySyncManager

        @Volatile
        override var isStarted: Boolean = false

        @Volatile
        override var isCreated: Boolean = false

        companion object {
            private const val MAPBOX_VISION_IDENTIFIER = "MapboxVision"
            private const val MAPBOX_TELEMETRY_USER_AGENT = "$MAPBOX_VISION_IDENTIFIER/${BuildConfig.VERSION_NAME}"

            private const val DIR_TELEMETRY = "Telemetry"
        }

        // TODO factor out
        private var isTurnstileEventSent = false

        private var country: Country = Country.Unknown

        // TODO factor out
        internal fun setCountry(country: Country) {
            if (this.country == country) {
                return
            }

            when (country) {
                Country.China -> {
                    telemetrySyncManager.stop()
                    File(rootTelemetryDir).apply {
                        deleteRecursively()
                        mkdirs()
                    }
                }
                Country.USA, Country.Other -> {
                    telemetrySyncManager.start()
                }
                Country.Unknown -> {
                    telemetrySyncManager.stop()
                }
            }

            this.country = country
        }

        override fun create(
            nativeVisionManagerBase: NativeVisionManagerBase,
            performanceManager: PerformanceManager
        ) {
            isCreated = true

            this.nativeVisionManagerBase = nativeVisionManagerBase
            this.performanceManager = performanceManager

            mapboxTelemetry = MapboxTelemetry(
                VisionManager.application,
                VisionManager.mapboxToken,
                MAPBOX_TELEMETRY_USER_AGENT
            )
            mapboxTelemetry.updateDebugLoggingEnabled(BuildConfig.DEBUG)

            if (!isTurnstileEventSent) {
                mapboxTelemetry.push(
                    AppUserTurnstile(
                        MAPBOX_VISION_IDENTIFIER,
                        BuildConfig.VERSION_NAME
                    )
                )
                isTurnstileEventSent = true
            }

            rootTelemetryDir = FileUtils.getAppRelativeDir(
                VisionManager.application,
                DIR_TELEMETRY
            )
            telemetrySyncManager = TelemetrySyncManager.Impl(
                mapboxTelemetry = mapboxTelemetry,
                context = VisionManager.application
            )
        }

        override fun start(
            visionEventsListener: VisionEventsListener,
            onCountrySet: (Country) -> Unit
        ) {
            isStarted = true

            telemetrySyncManager.start()
            File(rootTelemetryDir).listFiles()?.forEach {
                if (it.list().isNullOrEmpty()) {
                    it?.delete()
                } else {
                    telemetrySyncManager.syncSessionDir(it.absolutePath)
                }
            }

            nativeVisionManagerBase.start(object : VisionEventsListener by visionEventsListener {
                override fun onCountryUpdated(country: Country) {
                    visionEventsListener.onCountryUpdated(country)
                    setCountry(country)
                    onCountrySet(country)
                }
            })
        }

        override fun stop() {
            telemetrySyncManager.stop()
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
                throw IllegalStateException("${javaClass.simpleName} was not started. Call start() first.")
            }
        }
    }
}