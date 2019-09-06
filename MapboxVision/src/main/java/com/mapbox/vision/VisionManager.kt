package com.mapbox.vision

import android.app.Application
import android.os.Handler
import android.os.Looper
import com.mapbox.android.telemetry.AppUserTurnstile
import com.mapbox.android.telemetry.MapboxTelemetry
import com.mapbox.vision.VisionManager.create
import com.mapbox.vision.VisionManager.destroy
import com.mapbox.vision.VisionManager.start
import com.mapbox.vision.VisionManager.startRecording
import com.mapbox.vision.VisionManager.stop
import com.mapbox.vision.VisionManager.stopRecording
import com.mapbox.vision.location.LocationEngine
import com.mapbox.vision.manager.BaseVisionManager
import com.mapbox.vision.manager.DelegateVisionManager
import com.mapbox.vision.manager.ModuleInterface
import com.mapbox.vision.mobile.core.NativeVisionManager
import com.mapbox.vision.mobile.core.account.AccountManager
import com.mapbox.vision.mobile.core.base.Attachable
import com.mapbox.vision.mobile.core.interfaces.PerformanceProvider
import com.mapbox.vision.mobile.core.interfaces.VisionEventsListener
import com.mapbox.vision.mobile.core.models.CameraParameters
import com.mapbox.vision.mobile.core.models.DeviceMotionData
import com.mapbox.vision.mobile.core.models.FrameSegmentation
import com.mapbox.vision.mobile.core.models.FrameStatistics
import com.mapbox.vision.mobile.core.models.HeadingData
import com.mapbox.vision.mobile.core.models.detection.FrameDetections
import com.mapbox.vision.mobile.core.models.frame.ImageFormat
import com.mapbox.vision.mobile.core.models.frame.ImageSize
import com.mapbox.vision.mobile.core.models.frame.PixelCoordinate
import com.mapbox.vision.mobile.core.models.position.GeoCoordinate
import com.mapbox.vision.mobile.core.models.world.WorldCoordinate
import com.mapbox.vision.mobile.core.utils.extentions.TAG_CLASS
import com.mapbox.vision.mobile.core.utils.extentions.addTo
import com.mapbox.vision.mobile.core.utils.preferences.PreferencesManager
import com.mapbox.vision.performance.ModelPerformanceConfig
import com.mapbox.vision.performance.PerformanceManager
import com.mapbox.vision.sensors.SensorsListener
import com.mapbox.vision.sensors.SensorsManager
import com.mapbox.vision.sync.SessionManager
import com.mapbox.vision.sync.telemetry.MapboxTelemetryEventManager
import com.mapbox.vision.sync.telemetry.TelemetryImageSaverImpl
import com.mapbox.vision.utils.VisionLogger
import com.mapbox.vision.video.videosource.VideoSource
import com.mapbox.vision.video.videosource.VideoSourceListener
import com.mapbox.vision.video.videosource.camera.Camera2VideoSourceImpl
import com.mapbox.vision.video.videosource.camera.SurfaceVideoRecorder
import com.mapbox.vision.video.videosource.camera.VideoRecorder

/**
 * The main object for registering for events from the SDK, starting and stopping their delivery.
 * It also provides some useful functions for performance configuration and data conversion.
 *
 * Lifecycle of VisionManager :
 * 1. [create]
 * 2. [start]
 * 3. [startRecording] (optional)
 * 4. [stopRecording] (optional)
 * 5. [stop], then lifecycle may proceed with [destroy] or [start]
 * 6. [destroy]
 */
object VisionManager : BaseVisionManager {

    private const val MAPBOX_VISION_IDENTIFIER = "MapboxVision"
    private const val MAPBOX_TELEMETRY_USER_AGENT =
        "$MAPBOX_VISION_IDENTIFIER/${BuildConfig.VERSION_NAME}"

    lateinit var application: Application
        private set
    lateinit var mapboxToken: String
        private set

    private lateinit var delegate: DelegateVisionManager
    private lateinit var nativeVisionManager: NativeVisionManager
    private lateinit var telemetryImageSaver: TelemetryImageSaverImpl

    private lateinit var videoSource: VideoSource
    private lateinit var sensorsManager: SensorsManager
    private lateinit var locationEngine: LocationEngine
    private lateinit var sessionManager: SessionManager
    private lateinit var videoRecorder: VideoRecorder
    private lateinit var performanceProvider: PerformanceProvider

    private val attachableModules = HashSet<Attachable>()

    private lateinit var mapboxTelemetry: MapboxTelemetry

    private val handlerMain = Handler(Looper.getMainLooper())

    private var isTurnstileEventSent = false

    private val sensorsListener = object : SensorsListener {
        override fun onDeviceMotionData(deviceMotionData: DeviceMotionData) {
            nativeVisionManager.setMotion(
                rotations = deviceMotionData.rotations,
                orientations = deviceMotionData.orientations,
                screenOrientation = deviceMotionData.screenOrientation,
                userAccelerationRelativeToGravity = deviceMotionData.userAccelerationRelativeToGravity,
                gravity = deviceMotionData.gravity,
                heading = deviceMotionData.heading
            )
        }

        override fun onHeadingData(headingData: HeadingData) {
            nativeVisionManager.setHeading(
                trueHeading = headingData.trueHeading,
                geomagneticHeading = headingData.geomagneticHeading,
                geomagneticRawValues = headingData.geomagneticRawValues,
                timestamp = headingData.timestamp
            )
        }
    }

    private val videoSourceListener = object : VideoSourceListener {

        private var cachedCameraParameters: CameraParameters? = null

        override fun onNewFrame(
            rgbaBytes: ByteArray,
            imageFormat: ImageFormat,
            imageSize: ImageSize
        ) {
            nativeVisionManager.setFrame(
                rgbaByteArray = rgbaBytes,
                imageFormat = imageFormat,
                width = imageSize.imageWidth,
                height = imageSize.imageHeight
            )
            cachedCameraParameters?.let {
                nativeVisionManager.setCameraParameters(
                    width = it.width,
                    height = it.height,
                    focalXPixels = it.focalInPixelsX,
                    focalYPixels = it.focalInPixelsY
                )
            }
            delegate.externalVideoSourceListener?.onNewFrame(rgbaBytes, imageFormat, imageSize)
        }

        override fun onNewCameraParameters(cameraParameters: CameraParameters) {
            cachedCameraParameters = cameraParameters
            nativeVisionManager.setCameraParameters(
                width = cameraParameters.width,
                height = cameraParameters.height,
                focalXPixels = cameraParameters.focalInPixelsX,
                focalYPixels = cameraParameters.focalInPixelsY
            )
            delegate.externalVideoSourceListener?.onNewCameraParameters(cameraParameters)
        }
    }

    /**
     * Initialize SDK with mapbox access token and application instance.
     * Do it once per application session, eg in [android.app.Application.onCreate].
     */
    @JvmStatic
    fun init(application: Application, mapboxToken: String) {
        this.application = application
        this.mapboxToken = mapboxToken
        PreferencesManager.appContext = application
    }

    /**
     * Method for creating a [VisionManager] instance.
     * It's only allowed to have one living instance of [VisionManager] or [VisionReplayManager].
     * To create [VisionManager] with different configuration call [destroy] on existing instance or release all references to it.
     * @param videoSource: Video source which will be utilized by [VisionManager].
     */
    @JvmStatic
    @JvmOverloads
    fun create(videoSource: VideoSource = Camera2VideoSourceImpl(application)) {
        delegate = DelegateVisionManager.Impl()

        performanceProvider = PerformanceProvider.Impl(application).addTo(attachableModules)

        nativeVisionManager = NativeVisionManager(
            mapboxToken,
            AccountManager.Impl,
            performanceProvider,
            application
        )

        telemetryImageSaver = TelemetryImageSaverImpl()

        sensorsManager = SensorsManager.Impl(application)
        locationEngine = LocationEngine.Impl(application)

        val videoRecorder = if (videoSource is Camera2VideoSourceImpl) {
            SurfaceVideoRecorder.MediaCodecPersistentSurfaceImpl(application).also {
                videoSource.setVideoRecorder(it)
            }
        } else {
            VideoRecorder.DummyVideoRecorder()
        }

        this.videoSource = videoSource
        this.videoRecorder = videoRecorder

        mapboxTelemetry = MapboxTelemetry(
            application,
            mapboxToken,
            MAPBOX_TELEMETRY_USER_AGENT
        )

        if (!isTurnstileEventSent) {
            mapboxTelemetry.push(
                AppUserTurnstile(
                    MAPBOX_VISION_IDENTIFIER,
                    BuildConfig.VERSION_NAME
                )
            )
            isTurnstileEventSent = true
        }

        nativeVisionManager.create(
            telemetryEventManager = MapboxTelemetryEventManager(mapboxTelemetry),
            telemetryImageSaver = telemetryImageSaver
        )

        delegate.create(
            nativeVisionManagerBase = nativeVisionManager,
            performanceManager = PerformanceManager.getPerformanceManager(nativeVisionManager)
        )

        sessionManager = SessionManager.Impl(
            application,
            nativeVisionManager,
            videoRecorder,
            mapboxTelemetry,
            telemetryImageSaver
        )
    }

    /**
     * Start delivering events from [VisionManager].
     * Should be called with all permission granted, and after [create] is called.
     * Do NOT call this method more than once or after [destroy] is called.
     *
     * @param visionEventsListener: listener for [VisionManager]. Is held as a strong reference until [stop] is called.
     */
    @JvmStatic
    fun start(visionEventsListener: VisionEventsListener) {
        delegate.checkManagerCreated()
        if (delegate.isStarted) {
            VisionLogger.e(TAG_CLASS, "VisionManager was already started.")
            return
        }

        delegate.start(
            visionEventsListener
        ) { country ->
            handlerMain.post {
                sessionManager.setCountry(country)
            }
        }

        sessionManager.start()

        sensorsManager.attach(sensorsListener)
        locationEngine.attach(nativeVisionManager)
        videoSource.attach(videoSourceListener)
        attachableModules.forEach { it.attach() }
    }

    /**
     * Start recording a session.
     * Do NOT call this method more than once or before [start] or after [stop] is called.
     * During the session full telemetry and video are recorded to specified path.
     * You may use resulted directory to replay the recorded session with [VisionReplayManager].
     * Important: Method serves debugging purposes.
     * Do NOT use session recording in production applications.
     * @param path: Path to directory where you'd like session to be recorded.
     */
    @JvmStatic
    fun startRecording(path: String) {
        sessionManager.startRecording(path)
    }

    /**
     * Stop recording a session.
     * Do NOT call this method more than once or before [startRecording] or after [stop] is called.
     * Important: Method serves debugging purposes.
     * Do NOT use session recording in production applications.
     */
    @JvmStatic
    fun stopRecording() {
        sessionManager.stopRecording()
    }

    /**
     * Stop delivering events from [VisionManager].
     * Do NOT call this method more than once or before [start] or after [destroy] is called.
     * To resume call [start] again.
     * Call this method after [start] and before [destroy].
     */
    @JvmStatic
    fun stop() {
        if (!delegate.isCreated || !delegate.isStarted) {
            VisionLogger.e(TAG_CLASS, "VisionManager was not created yet.")
            return
        }
        sessionManager.stop()

        delegate.stop()

        videoSource.detach()

        locationEngine.detach()
        sensorsManager.detach()
        attachableModules.forEach { it.detach() }
    }

    /**
     * Clean up the state and resources of [VisionManager].
     * Do NOT call this method more than once.
     */
    @JvmStatic
    fun destroy() {
        if (!delegate.isCreated) {
            VisionLogger.e(TAG_CLASS, "VisionManager wasn't created, nothing to destroy.")
            return
        }

        nativeVisionManager.destroy()
        videoRecorder.release()
    }

    @JvmStatic
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
