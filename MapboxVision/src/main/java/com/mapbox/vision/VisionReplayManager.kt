package com.mapbox.vision

import android.content.ContentValues.TAG
import com.mapbox.vision.VisionReplayManager.create
import com.mapbox.vision.VisionReplayManager.destroy
import com.mapbox.vision.VisionReplayManager.start
import com.mapbox.vision.VisionReplayManager.stop
import com.mapbox.vision.manager.BaseVisionManager
import com.mapbox.vision.manager.DelegateVisionManager
import com.mapbox.vision.manager.ModuleInterface
import com.mapbox.vision.mobile.core.NativeVisionReplayManager
import com.mapbox.vision.mobile.core.account.AccountManager
import com.mapbox.vision.mobile.core.base.Attachable
import com.mapbox.vision.mobile.core.interfaces.PerformanceProvider
import com.mapbox.vision.mobile.core.interfaces.VisionEventsListener
import com.mapbox.vision.mobile.core.models.CameraParameters
import com.mapbox.vision.mobile.core.models.FrameSegmentation
import com.mapbox.vision.mobile.core.models.FrameStatistics
import com.mapbox.vision.mobile.core.models.detection.FrameDetections
import com.mapbox.vision.mobile.core.models.frame.ImageFormat
import com.mapbox.vision.mobile.core.models.frame.ImageSize
import com.mapbox.vision.mobile.core.models.frame.PixelCoordinate
import com.mapbox.vision.mobile.core.models.position.GeoCoordinate
import com.mapbox.vision.mobile.core.models.world.WorldCoordinate
import com.mapbox.vision.mobile.core.telemetry.TelemetryEventManager
import com.mapbox.vision.mobile.core.telemetry.TelemetryImageSaver
import com.mapbox.vision.mobile.core.utils.delegate.DelegateAlias
import com.mapbox.vision.mobile.core.utils.extentions.addTo
import com.mapbox.vision.performance.ModelPerformanceConfig
import com.mapbox.vision.performance.PerformanceManager
import com.mapbox.vision.utils.VisionLogger
import com.mapbox.vision.video.videosource.VideoSource
import com.mapbox.vision.video.videosource.VideoSourceListener
import com.mapbox.vision.video.videosource.file.FileVideoSource
import java.io.File

/**
 * [VisionReplayManager] is a counterpart of [VisionManager] that uses recorded video and telemetry instead of realtime data.
 * Use it to debug and test functions that use Vision in a development environment before testing on a road.
 * Use it in the same workflow you use [VisionManager] after creating it with specific recorded session.
 *
 * Important : use this class only for debugging purposes.
 * Do NOT use session replay in production application.
 *
 * Lifecycle of VisionReplayManager :
 * 1. [create]
 * 2. [start]
 * 5. [stop], then lifecycle may proceed with [destroy] or [start]
 * 6. [destroy]
 */
object VisionReplayManager : BaseVisionManager {

    object DummyImageSaver : TelemetryImageSaver {
        override fun saveImage(rgbaBytes: ByteArray, width: Int, height: Int, fileName: String) {}
    }

    object DummyEventManager : TelemetryEventManager {
        override fun setEventName(name: String) {}

        override fun setInt(key: String, value: Int) {}

        override fun setDouble(key: String, value: Double) {}

        override fun setString(key: String, value: String) {}

        override fun pushEvent() {}
    }

    private val videoSource: VideoSource.WithProgress
        get() = delegate.videoSource

    private lateinit var delegate: DelegateVisionManager<VideoSource.WithProgress>
    private lateinit var nativeVisionManager: NativeVisionReplayManager
    private lateinit var path: String
    private lateinit var performanceProvider: PerformanceProvider

    private val attachableModules = HashSet<Attachable>()

    private val videoSourceListener = object : VideoSourceListener {
        override fun onNewFrame(
            rgbaBytes: ByteArray,
            imageFormat: ImageFormat,
            imageSize: ImageSize
        ) {
            nativeVisionManager.setFrame(
                rgbaByteArray = rgbaBytes,
                imageFormat = imageFormat,
                width = imageSize.imageWidth,
                height = imageSize.imageHeight,
                timestamp = videoSource.getProgress()
            )
            delegate.externalVideoSourceListener?.onNewFrame(rgbaBytes, imageFormat, imageSize)
        }

        override fun onNewCameraParameters(cameraParameters: CameraParameters) {
            delegate.externalVideoSourceListener?.onNewCameraParameters(cameraParameters)
        }
    }

    /**
     * Listener for [VisionReplayManager]. It's held as a week reference.
     */
    @JvmStatic
    var visionEventsListener: VisionEventsListener? by DelegateAlias { delegate::visionEventsListener }

    /**
     * Fabric method for creating a [VisionReplayManager] instance.
     * Do NOT call this method more than once.
     * It's only allowed to have one living instance of [VisionReplayManager] or [VisionManager].
     * To create [VisionReplayManager] with a different configuration call [destroy] on existing instance or release all references to it.
     * @param path: Path where session was recorded with [startRecording].
     */
    @JvmStatic
    fun create(path: String) {
        this.path = path
        delegate = DelegateVisionManager.Impl()

        performanceProvider =
            PerformanceProvider.Impl(VisionManager.application).addTo(attachableModules)
        // TODO lifecycle

        nativeVisionManager = NativeVisionReplayManager(
            VisionManager.mapboxToken,
            AccountManager.Impl,
            performanceProvider,
            VisionManager.application
        )
        nativeVisionManager.create(
            telemetryImageSaver = DummyImageSaver,
            telemetryEventManager = DummyEventManager,
            path = path
        )

        delegate.videoSource = FileVideoSource(
            VisionManager.application,
            videoFiles = File(path)
                .listFiles
                { _: File?, name: String? ->
                    name?.contains("mp4") ?: false
                }
                .sorted(),
            onVideoStarted = {},
            onVideosEnded = {}
        )

        delegate = DelegateVisionManager.Impl()
        delegate.create(
            nativeVisionManager,
            performanceManager = PerformanceManager.getPerformanceManager(nativeVisionManager)
        )
    }

    /**
     * Start delivering events from [VisionReplayManager].
     * Do NOT call this method more than once or after [destroy] is called.
     * @param visionEventsListener: listener for [VisionReplayManager].
     */
    @JvmStatic
    @Deprecated(
        "Will be removed in 0.9.0. Use start() and setVisionEventsListener(VisionEventsListener) instead",
        ReplaceWith("VisionReplayManager.start()")
    )
    fun start(visionEventsListener: VisionEventsListener) {
        delegate.checkManagerCreated()
        if (delegate.isStarted) {
            VisionLogger.e(TAG, "VisionReplayManager was already started.")
            return
        }
        delegate.keepListenerStrongRef = true

        this.visionEventsListener = visionEventsListener

        delegate.start()

        delegate.attachVideoSourceListener(videoSourceListener)

        attachableModules.forEach { it.attach() }
    }

    /**
     * Start delivering events from [VisionReplayManager].
     * Do NOT call this method more than once or after [destroy] is called.
     */
    @JvmStatic
    fun start() {
        delegate.checkManagerCreated()
        if (delegate.isStarted) {
            VisionLogger.e(TAG, "VisionReplayManager was already started.")
            return
        }
        delegate.keepListenerStrongRef = false

        delegate.start()

        delegate.attachVideoSourceListener(videoSourceListener)

        attachableModules.forEach { it.attach() }
    }

    internal fun addListener(observer: VisionEventsListener) = delegate.addListener(observer)

    internal fun removeListener(observer: VisionEventsListener) = delegate.removeListener(observer)

    internal fun addVideoSourceListener(listener: VideoSourceListener) = delegate.addVideoSourceListener(listener)

    internal fun removeVideoSourceListener(listener: VideoSourceListener) = delegate.removeVideoSourceListener(listener)

    /**
     * Stop delivering events from [VisionReplayManager].
     * Do NOT call this method more than once or before [start] or after [destroy] is called.
     */
    @JvmStatic
    fun stop() {
        if (!delegate.isCreated || !delegate.isStarted) {
            VisionLogger.e(TAG, "VisionReplayManager was not started yet.")
            return
        }

        attachableModules.forEach { it.detach() }

        delegate.detachVideoSourceListener()
        delegate.stop()
    }

    /**
     * Clean up the state and resources of [VisionReplayManager].
     * Do NOT call this method more than once.
     */
    @JvmStatic
    fun destroy() {
        if (!delegate.isCreated) {
            VisionLogger.e(TAG, "VisionReplayManager was not created.")
            return
        }

        delegate.destroy()
        nativeVisionManager.destroy()
    }

    @JvmStatic
    @Deprecated("Will be removed in v0.10")
    fun setVideoSourceListener(videoSourceListener: VideoSourceListener) {
        delegate.setVideoSourceListener(videoSourceListener)
    }

    @JvmStatic
    fun setModelPerformanceConfig(modelPerformanceConfig: ModelPerformanceConfig) {
        delegate.setModelPerformanceConfig(modelPerformanceConfig)
    }

    /**
     * Converts the location of the point from a world coordinate to a frame coordinate.
     * @return [PixelCoordinate] if [worldCoordinate] can be represented in screen coordinates and null otherwise
     */
    @JvmStatic
    fun worldToPixel(worldCoordinate: WorldCoordinate): PixelCoordinate? {
        return delegate.worldToPixel(worldCoordinate)
    }

    /**
     * Converts the location of the point from a frame coordinate to a world coordinate.
     * @return [WorldCoordinate] if [pixelCoordinate] can be projected on the road and null otherwise
     */
    @JvmStatic
    fun pixelToWorld(pixelCoordinate: PixelCoordinate): WorldCoordinate? {
        return delegate.pixelToWorld(pixelCoordinate)
    }

    /**
     * Converts the location of the point in a world coordinate to a geographical coordinate.
     */
    @JvmStatic
    fun worldToGeo(worldCoordinate: WorldCoordinate): GeoCoordinate? {
        return delegate.worldToGeo(worldCoordinate)
    }

    /**
     * Converts the location of the point from a geographical coordinate to a world coordinate.
     */
    @JvmStatic
    fun geoToWorld(geoCoordinate: GeoCoordinate): WorldCoordinate? {
        return delegate.geoToWorld(geoCoordinate)
    }

    @JvmStatic
    fun getFrameStatistics(): FrameStatistics {
        return delegate.getFrameStatistics()
    }

    override fun getDetectionsImage(frameDetections: FrameDetections): ByteArray {
        return delegate.getDetectionsImage(frameDetections)
    }

    override fun getSegmentationImage(frameSegmentation: FrameSegmentation): ByteArray {
        return delegate.getSegmentationImage(frameSegmentation)
    }

    override fun registerModule(moduleInterface: ModuleInterface) {
        delegate.registerModule(moduleInterface)
    }

    override fun unregisterModule(moduleInterface: ModuleInterface) {
        delegate.unregisterModule(moduleInterface)
    }
}
