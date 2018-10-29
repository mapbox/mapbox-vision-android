package com.mapbox.vision

import android.app.Application
import android.graphics.Bitmap
import android.util.Log
import com.mapbox.android.telemetry.MapboxTelemetry
import com.mapbox.vision.ar.ARDataProvider
import com.mapbox.vision.corewrapper.JNIVisionCoreFactory
import com.mapbox.vision.corewrapper.VisionCore
import com.mapbox.vision.corewrapper.update.VisionEventsListener
import com.mapbox.vision.location.LocationEngine
import com.mapbox.vision.location.LocationEngineListener
import com.mapbox.vision.location.android.AndroidLocationEngineImpl
import com.mapbox.vision.models.*
import com.mapbox.vision.models.route.NavigationRoute
import com.mapbox.vision.performance.ModelPerformanceConfig
import com.mapbox.vision.sensors.SensorDataListener
import com.mapbox.vision.sensors.SensorsRequestsManager
import com.mapbox.vision.telemetry.MapboxTelemetryEventManager
import com.mapbox.vision.telemetry.TelemetrySyncManager
import com.mapbox.vision.utils.FileUtils
import com.mapbox.vision.utils.threads.MainThreadHandler
import com.mapbox.vision.utils.threads.WorkThreadHandler
import com.mapbox.vision.video.videoprocessor.VideoProcessor
import com.mapbox.vision.video.videoprocessor.VideoProcessorListener
import com.mapbox.vision.video.videosource.VideoSource
import com.mapbox.vision.video.videosource.VideoSourceListener
import com.mapbox.vision.video.videosource.camera.CameraVideoSourceImpl
import com.mapbox.vision.view.VisualizationUpdateListener
import com.mapbox.vision.visionevents.CalibrationProgress
import com.mapbox.vision.visionevents.FrameSize
import com.mapbox.vision.visionevents.ScreenCoordinate
import com.mapbox.vision.visionevents.WorldCoordinate
import com.mapbox.vision.visionevents.events.position.Position
import com.mapbox.vision.visionevents.events.roaddescription.RoadDescription
import com.mapbox.vision.visionevents.events.worlddescription.WorldDescription
import java.io.File
import java.lang.ref.WeakReference
import kotlin.properties.Delegates

/**
 * The main object for registering for events from the library,
 * starting and stopping their delivery.
 *
 * It also provides some useful functions for performance configuration and data conversion.
 */
object VisionManager : ARDataProvider {

    private const val MAPBOX_TELEMETRY_CLIENT_NAME = "MapboxEventsAndroid/Vision"
    private const val TAG = "VisionManager"

    // Work resolution
    private const val FRAME_WIDTH = 1280
    private const val FRAME_HEIGHT = 720

    // Video buffer length
    private const val RESTART_SESSION_RECORDING_DELAY_MILLIS = 5 * 60 * 1000L // 5 min

    // Desired update rate up to 30 FPS
    private const val CORE_UPDATE_DELAY_MILLIS = 33L

    private val coreUpdateThreadHandler = WorkThreadHandler()
    private val extractCoreDataThreadHandler = WorkThreadHandler()
    private val mainThreadHandler = MainThreadHandler()

    private lateinit var application: Application
    private lateinit var mapboxToken: String
    private lateinit var mapboxTelemetry: MapboxTelemetry
    private lateinit var visionCore: VisionCore

    private lateinit var videoSource: VideoSource
    private lateinit var sensorsRequestsManager: SensorsRequestsManager
    private lateinit var locationEngine: LocationEngine
    private lateinit var videoProcessor: VideoProcessor

    private lateinit var telemetryManager: TelemetrySyncManager

    // Telemetry recording
    private lateinit var telemetryDirPath: String
    private var currentDataDirPath: String = ""
    private var previousDataDirPath: String = ""
    private var startRecordCoreMillis = 0L
    private var clipTimes: List<VideoProcessor.VideoPart> = emptyList()

    // Listeners
    private val visionManagerVideoProcessorListener = object : VideoProcessorListener {
        override fun onVideoPartsReady(
            videoPartMap: HashMap<String, VideoProcessor.VideoPart>,
            dirPath: String,
            jsonFilePath: String
        ) {
            telemetryManager.syncDataDir(dirPath)
        }
    }

    private val visionManagerLocationEngineListener = object : LocationEngineListener {
        override fun onNewLocation(
            latitude: Double,
            longitude: Double,
            speed: Float,
            altitude: Double,
            horizontalAccuracy: Float,
            verticalAccuracy: Float,
            bearing: Float,
            timestamp: Long
        ) {
            visionCore.setGPSData(
                GPSData(
                    latitude = latitude,
                    longitude = longitude,
                    speed = speed,
                    altitude = altitude,
                    horizontalAccuracy = horizontalAccuracy,
                    verticalAccuracy = verticalAccuracy,
                    bearing = bearing,
                    timestamp = timestamp
                )
            )
        }
    }

    private val visionManagerSensorDataListener = object : SensorDataListener {

        override fun onDeviceMotionDataReady(deviceMotionData: DeviceMotionData) {
            visionCore.setDeviceMotionData(deviceMotionData)
        }

        override fun onHeadingDataReady(headingData: HeadingData) {
            visionCore.setHeadingData(headingData)
        }
    }

    private val visionManagerVideoSourceListener = object : VideoSourceListener {

        override fun onNewFrame(rgbBytes: ByteArray) {
            visionCore.setRGBABytes(rgbBytes, videoSource.getSourceWidth(), videoSource.getSourceHeight())
        }

        override fun onNewBitmap(bitmap: Bitmap) {
            // Do nothing
        }

        override fun onNewCameraParams(cameraParamsData: CameraParamsData) {
            visionCore.setCameraParamsData(cameraParamsData)
        }

        override fun onFileRecorded(recordedFilePath: String) {
            videoProcessor.splitVideoToParts(
                parts = clipTimes,
                fullVideoPath = recordedFilePath,
                saveDirPath = previousDataDirPath,
                startRecordCoreMillis = startRecordCoreMillis
            )
        }
    }

    // Event Listeners
    private var visionEventsListener: VisionEventsListener? by Delegates.observable<VisionEventsListener?>(null) { _, _, new ->
        if (::visionCore.isInitialized) {
            visionCore.setVisionEventListener(new)
        }
    }
    private var visualizationUpdateListener: WeakReference<VisualizationUpdateListener>? = null
    private var videoStreamListener: WeakReference<VideoStreamListener>? = null

    private var isCreated = false
    private var isStarted = false

    /**
     * Initialize SDK with mapbox access token and application instance.
     * Do it once per application session, eg in [android.app.Application.onCreate].
     */
    fun init(application: Application, mapboxToken: String) {
        this.mapboxToken = mapboxToken
        this.application = application
    }

    /**
     * Initialize SDK. Creates core services and allocates necessary resources.
     * Typically is called when application need to launch Vision SDK, eg. [android.app.Activity.onCreate].
     * You should [destroy] when Vision SDK is no longer needed to release all resources.
     * No-op if called while SDK is created already.
     */
    fun create() {
        checkManagerInit()
        if (isCreated) {
            Log.w(TAG, "VisionManager was already created!")
            return
        }

        mapboxTelemetry = MapboxTelemetry(application, mapboxToken, MAPBOX_TELEMETRY_CLIENT_NAME)
        mapboxTelemetry.updateDebugLoggingEnabled(BuildConfig.DEBUG)
        visionCore = JNIVisionCoreFactory(application, MapboxTelemetryEventManager(mapboxTelemetry))
            .createVisionCore(FRAME_WIDTH, FRAME_HEIGHT)

        videoSource = CameraVideoSourceImpl(application, FRAME_WIDTH, FRAME_HEIGHT)
        sensorsRequestsManager = SensorsRequestsManager(application)
        sensorsRequestsManager.setSensorDataListener(visionManagerSensorDataListener)
        locationEngine = AndroidLocationEngineImpl(application)
        videoProcessor = VideoProcessor.Impl()

        telemetryDirPath = FileUtils.getTelemetryDirPath(application)
        telemetryManager = TelemetrySyncManager.Impl(mapboxTelemetry, application)

        isCreated = true
    }

    /**
     * Start delivering events from SDK.
     * Should be called with all permission granted, and after [create] is called.
     * Typically this is called from [android.app.Activity.onStart] or [android.app.Activity.onResume].
     * No-op if called while SDK is started already.
     */
    fun start() {
        checkManagerInit()
        if (isStarted) {
            Log.w(TAG, "VisionManager was already started!")
            return
        } else if (!isCreated) {
            Log.w(TAG, "VisionManager wasn't created, forcing it!")
            create()
        }

        visionCore.setVisionEventListener(visionEventsListener)
        visionCore.setVisualizationUpdateListener(visualizationUpdateListener)
        visionCore.setVideoStreamListener(videoStreamListener)
        visionCore.onResume()

        videoProcessor.setVideoProcessorListener(visionManagerVideoProcessorListener)

        startTelemetry()
        startAllHandlers()

        videoSource.useBitmap(false)
        videoSource.attach(visionManagerVideoSourceListener)
        startSessionRecording()

        sensorsRequestsManager.startDataRequesting()
        locationEngine.attach(visionManagerLocationEngineListener)

        coreUpdateThreadHandler.post { requestCoreUpdate() }

        isStarted = true
    }

    /**
     * Stop delivering events from SDK.
     * Stops ML processing and video source.
     * To resume call [start] again.
     * Typically this is called from [android.app.Activity.onStop] or [android.app.Activity.onPause].
     * No-op if called while SDK is not created or started.
     */
    fun stop() {
        checkManagerInit()
        if (!isCreated || !isStarted) {
            Log.w(TAG, "VisionManager was not created yet!")
            return
        }

        stopTelemetry()

        locationEngine.detach()
        sensorsRequestsManager.stopDataRequesting()
        stopAllHandlers()
        stopSessionRecording()
        videoSource.detach()
        visionCore.onPause()

        isStarted = false
    }

    /**
     * Releases all resources.
     * Typically this is called from [android.app.Activity.onDestroy].
     * No-op if called while SDK is not created.
     */
    fun destroy() {
        checkManagerInit()
        if (!isCreated) {
            Log.w(TAG, "VisionManager wasn't created, nothing to destroy!")
            return
        }

        videoSource.release()
        visionCore.release()
        videoProcessor.stop()

        isCreated = false
    }

    /**
     * Set listener to listen SDK events.
     */
    fun setVisionEventListener(visionEventsListener: VisionEventsListener) {
        this.visionEventsListener = visionEventsListener
    }

    /**
     * @return current road situation.
     *
     * @throws [IllegalStateException] if called before [create] and [start]
     */
    fun getRoadDescription(): RoadDescription {
        checkManagerRunningState()
        return visionCore.getRoadDescription()
    }

    /**
     * @return [WorldDescription] world description with objects in it.
     *
     * @throws [IllegalStateException] if called before [create] and [start]
     */
    fun getWorldDescription(): WorldDescription {
        checkManagerRunningState()
        return visionCore.getWorldDescription()
    }

    /**
     * @return current [Position] estimated by SDK.
     *
     * @throws [IllegalStateException] if called before [create] and [start]
     */
    fun getPosition(): Position {
        checkManagerRunningState()
        return visionCore.getPosition()
    }

    /**
     * @return current [CalibrationProgress] estimated by SDK.
     *
     * @throws [IllegalStateException] if called before [create] and [start]
     */
    fun getCalibrationProgress(): CalibrationProgress {
        checkManagerRunningState()
        return visionCore.getCalibrationProgress()
    }

    /**
     * Configure performance of ML models used.
     */
    fun setModelPerformanceConfig(modelPerformanceConfig: ModelPerformanceConfig) {
        visionCore.setModelPerformanceConfig(modelPerformanceConfig)
    }

    fun getFrameSize(): FrameSize = FrameSize(visionCore.imageWidth, visionCore.imageHeight)

    /**
     * Converts location of the point from world coordinates to screen coordinates.
     *
     * @param worldCoordinate coordinate of the point from world coordinates
     * @return location of the point in screen coordinates
     */
    fun worldToPixel(worldCoordinate: WorldCoordinate): ScreenCoordinate = visionCore.worldToPixel(worldCoordinate)

    /**
     * Converts location of the point from screen coordinates to world coordinates.
     *
     * @param screenCoordinate coordinate of the point on screen
     * @return location of the point in world coordinates
     */
    fun pixelToWorld(screenCoordinate: ScreenCoordinate): WorldCoordinate = visionCore.pixelToWorld(screenCoordinate)

    /**
     * Provide information about navigation route to get instructions on rendering AR navigation.
     *
     * @param navigationRoute
     */
    fun startNavigation(navigationRoute: NavigationRoute) {
        visionCore.setRouteData(navigationRoute)
    }

    /**
     * Stop navigation.
     */
    fun stopNavigation() {
        visionCore.setRouteData(null)
    }

    /**
     * Get camera params for AR.
     *
     * @return six camera parameters [
     *      m_verticalFOV, - vertical field of view, in radians
     *      m_aspectRatio, - aspect ratio
     *      m_roll,        - roll, in radians
     *      m_pitch,       - pitch, in radians
     *      m_yaw,         - yaw, in radians
     *      m_height       - camera height, in meters
     *      ]
     *
     */
    override fun getCameraParams(): FloatArray? {
        if (!isCreated || !isStarted) {
            return null
        }
        return visionCore.getARCameraParams()
    }

    /**
     * Get AR cubic spline of route.
     *
     * @return AR cubic spline of route
     *
     */
    override fun getARRouteData(): DoubleArray? {
        if (!isCreated || !isStarted) {
            return null
        }
        return visionCore.getARRouteData()
    }

    /**
     * Set listener
     */
    fun setVideoStreamListener(videoStreamListener: VideoStreamListener) {
        this.videoStreamListener = WeakReference(videoStreamListener)
        if (isCreated) {
            visionCore.setVideoStreamListener(this.videoStreamListener)
        }
    }

    /**
     * Get frame statistics with FPS.
     */
    fun getFrameStatistics() = FrameStatistics(visionCore.getFrameStatistics())

    internal fun setVisualizationUpdateListener(visualizationUpdateListener: VisualizationUpdateListener) {
        this.visualizationUpdateListener = WeakReference(visualizationUpdateListener)
    }

    private fun checkManagerRunningState() {
        checkManagerInit()
        checkManagerCreated()
        checkManagerStarted()
    }

    private fun checkManagerInit() {
        if (!::application.isInitialized || !::mapboxToken.isInitialized) {
            throw IllegalStateException("Visual manager was not initialized. You should call VisionManager.init() first.")
        }
    }

    private fun checkManagerCreated() {
        if (!isCreated) {
            throw IllegalStateException("Visual manager was not created. You should call VisionManager.create() first.")
        }
    }

    private fun checkManagerStarted() {
        if (!isStarted) {
            throw IllegalStateException("Visual manager was not started. You should call VisionManager.start() first.")
        }
    }

    private fun requestCoreUpdate() {
        val lastCoreUpdateStartTime = System.currentTimeMillis()
        visionCore.requestUpdate()
        val coreUpdateRunTime = System.currentTimeMillis() - lastCoreUpdateStartTime
        coreUpdateThreadHandler.postDelayed({ requestCoreUpdate() }, CORE_UPDATE_DELAY_MILLIS - coreUpdateRunTime)
    }

    private fun startAllHandlers() {
        if (!coreUpdateThreadHandler.isStarted()) {
            coreUpdateThreadHandler.start()
        }

        if (!extractCoreDataThreadHandler.isStarted()) {
            extractCoreDataThreadHandler.start()
        }

        if (!mainThreadHandler.isStarted()) {
            mainThreadHandler.start()
        }
    }

    private fun stopAllHandlers() {
        if (coreUpdateThreadHandler.isStarted()) {
            coreUpdateThreadHandler.stop()
        }

        if (extractCoreDataThreadHandler.isStarted()) {
            extractCoreDataThreadHandler.stop()
        }

        if (mainThreadHandler.isStarted()) {
            mainThreadHandler.stop()
        }
    }

    private fun startSessionRecording() {
        if (currentDataDirPath.isEmpty()) {
            currentDataDirPath = getNextDirName()
        }
        visionCore.startDataSavingSession(currentDataDirPath)
        startRecordCoreMillis = visionCore.getCoreMilliseconds()
        mainThreadHandler.postDelayed({
            stopSessionRecording()
            videoSource.stopVideoRecording()
            startSessionRecording()
            mainThreadHandler.post { videoSource.startVideoRecording() }

        }, RESTART_SESSION_RECORDING_DELAY_MILLIS)
    }

    private fun stopSessionRecording() {
        visionCore.stopDataSavingSession()
        clipTimes = visionCore.getAndResetClipsTimeList()
        previousDataDirPath = currentDataDirPath
        currentDataDirPath = ""
    }

    private fun getNextDirName(): String {
        val file = File(telemetryDirPath, System.currentTimeMillis().toString())
        if (!file.exists() && !file.mkdirs()) {
            return ""
        }

        return "${file.absolutePath}/"
    }

    private fun startTelemetry() {
        if (!mapboxTelemetry.enable()) {
            Log.e(TAG, "Can not enable telemetry")
        } else {
            telemetryManager.reset()
            File(telemetryDirPath).listFiles().forEach {
                if (it.list().isEmpty()) {
                    it.delete()
                } else {
                    telemetryManager.syncDataDir(it.absolutePath)
                }
            }
        }
    }

    private fun stopTelemetry() {
        if (!mapboxTelemetry.disable()) {
            Log.e(TAG, "Can not disable telemetry")
        }
    }
}
