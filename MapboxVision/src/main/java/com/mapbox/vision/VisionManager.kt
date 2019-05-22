package com.mapbox.vision

import android.app.Application
import android.content.ContentValues.TAG
import com.mapbox.vision.location.LocationEngine
import com.mapbox.vision.manager.BaseVisionManager
import com.mapbox.vision.manager.DelegateVisionManager
import com.mapbox.vision.manager.ModuleInterface
import com.mapbox.vision.mobile.core.NativeVisionManager
import com.mapbox.vision.mobile.core.interfaces.VisionEventsListener
import com.mapbox.vision.mobile.core.models.*
import com.mapbox.vision.mobile.core.models.detection.FrameDetections
import com.mapbox.vision.mobile.core.models.frame.ImageFormat
import com.mapbox.vision.mobile.core.models.frame.ImageSize
import com.mapbox.vision.mobile.core.models.frame.PixelCoordinate
import com.mapbox.vision.mobile.core.models.position.GeoCoordinate
import com.mapbox.vision.mobile.core.models.world.WorldCoordinate
import com.mapbox.vision.performance.ModelPerformanceConfig
import com.mapbox.vision.performance.PerformanceManager
import com.mapbox.vision.sensors.SensorsListener
import com.mapbox.vision.sensors.SensorsManager
import com.mapbox.vision.telemetry.MapboxTelemetryEventManager
import com.mapbox.vision.telemetry.SessionManager
import com.mapbox.vision.telemetry.TelemetryImageSaverImpl
import com.mapbox.vision.utils.VisionLogger
import com.mapbox.vision.video.videoprocessor.VideoProcessor
import com.mapbox.vision.video.videoprocessor.VideoProcessorListener
import com.mapbox.vision.video.videosource.VideoSource
import com.mapbox.vision.video.videosource.VideoSourceListener
import com.mapbox.vision.video.videosource.camera.Camera2VideoSourceImpl
import com.mapbox.vision.video.videosource.camera.SurfaceVideoRecorder
import com.mapbox.vision.video.videosource.camera.VideoRecorder

object VisionManager : BaseVisionManager {

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

    private var isRecording = false

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
            delegate.externalVideoSourceListener?.onNewFrame(rgbaBytes, imageFormat, imageSize)
        }

        override fun onNewCameraParameters(cameraParameters: CameraParameters) {
            nativeVisionManager.setCameraParameters(
                width = cameraParameters.width,
                height = cameraParameters.height,
                focalXPixels = cameraParameters.focalInPixelsX,
                focalYPixels = cameraParameters.focalInPixelsY
            )
            delegate.externalVideoSourceListener?.onNewCameraParameters(cameraParameters)
        }
    }

    private val videoProcessorListener = object : VideoProcessorListener {
        override fun onVideoClipsReady(
            videoClips: HashMap<String, VideoClip>,
            videoDir: String,
            jsonFile: String
        ) {
            delegate.telemetrySyncManager.syncSessionDir(videoDir)
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
    }

    /**
     * Initialize SDK. Creates core services and allocates necessary resources.
     * No-op if called while SDK is created already.
     */
    @JvmStatic
    @JvmOverloads
    fun create(videoSource: VideoSource = Camera2VideoSourceImpl(application)) {
        delegate = DelegateVisionManager.Impl()

        nativeVisionManager = NativeVisionManager(
            mapboxToken,
            application
        )

        telemetryImageSaver = TelemetryImageSaverImpl()

        delegate.create(
            nativeVisionManagerBase = nativeVisionManager,
            performanceManager = PerformanceManager.getPerformanceManager(nativeVisionManager)
        )

        nativeVisionManager.create(
            telemetryEventManager = MapboxTelemetryEventManager(delegate.mapboxTelemetry),
            telemetryImageSaver = telemetryImageSaver
        )

        sensorsManager = SensorsManager.Impl(application)
        locationEngine = LocationEngine.Impl(application)

        val videoRecorder = SurfaceVideoRecorder.MediaCodecPersistentSurfaceImpl(application)
        (videoSource as? Camera2VideoSourceImpl)?.setVideoRecorder(videoRecorder)

        this.videoSource = videoSource
        this.videoRecorder = videoRecorder
    }

    /**
     * Start delivering events from SDK.
     * Should be called with all permission granted, and after [create] is called.
     * No-op if called while SDK is started already.
     */
    @JvmStatic
    fun start(visionEventsListener: VisionEventsListener) {
        delegate.checkManagerCreated()
        if (delegate.isStarted) {
            VisionLogger.e(TAG, "VisionManager was already started.")
            return
        }

        sessionManager = SessionManager.RotatedBuffersImpl(
            application,
            nativeVisionManager,
            delegate.rootTelemetryDir,
            videoRecorder,
            telemetryImageSaver,
            VideoProcessor.Impl(),
            videoProcessorListener
        )

        delegate.start(
            visionEventsListener
        ) { country ->
            if (!isRecording) {
                when (country) {
                    Country.China -> {
                        sessionManager.stop()
                    }
                    Country.Unknown, Country.USA, Country.Other -> {
                        sessionManager.start()
                    }
                }
            }
        }

        sessionManager.start()

        sensorsManager.attach(sensorsListener)
        locationEngine.attach(nativeVisionManager)
        videoSource.attach(videoSourceListener)
    }

    @JvmStatic
    fun startRecording(path: String) {
        if (isRecording) {
            VisionLogger.e(TAG, "Recording was already started.")
            return
        }
        isRecording = true

        sessionManager.stop()

        sessionManager = SessionManager.RecordingImpl(
            nativeVisionManager,
            sessionDir = "$path/",
            videoRecorder = videoRecorder
        )

        sessionManager.start()
    }

    @JvmStatic
    fun stopRecording() {
        if (!isRecording) {
            VisionLogger.e(TAG, "Recording was not started.")
            return
        }
        sessionManager.stop()

        sessionManager = SessionManager.RotatedBuffersImpl(
            application,
            nativeVisionManager,
            delegate.rootTelemetryDir,
            videoRecorder,
            telemetryImageSaver,
            VideoProcessor.Impl(),
            videoProcessorListener
        )

        sessionManager.start()

        isRecording = false
    }

    /**
     * Stop delivering events from SDK.
     * Stops ML processing and video source.
     * To resume call [start] again.
     * No-op if called while SDK is not created or started.
     */
    @JvmStatic
    fun stop() {
        if (!delegate.isCreated || !delegate.isStarted) {
            VisionLogger.e(TAG, "VisionManager was not created yet.")
            return
        }
        sessionManager.stop()

        delegate.stop()

        videoSource.detach()

        locationEngine.detach()
        sensorsManager.detach()
    }

    /**
     * Releases all resources.
     * No-op if called while SDK is not created.
     */
    @JvmStatic
    fun destroy() {
        if (!delegate.isCreated) {
            VisionLogger.e(TAG, "VisionManager wasn't created, nothing to destroy.")
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

    @JvmStatic
    fun worldToPixel(worldCoordinate: WorldCoordinate): PixelCoordinate {
        return delegate.worldToPixel(worldCoordinate)
    }

    @JvmStatic
    fun pixelToWorld(pixelCoordinate: PixelCoordinate): WorldCoordinate {
        return delegate.pixelToWorld(pixelCoordinate)
    }

    @JvmStatic
    fun worldToGeo(worldCoordinate: WorldCoordinate): GeoCoordinate {
        return delegate.worldToGeo(worldCoordinate)
    }

    @JvmStatic
    fun geoToWorld(geoCoordinate: GeoCoordinate): WorldCoordinate {
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

