package com.mapbox.vision

import android.app.Application
import android.util.Log
import com.mapbox.android.telemetry.AppUserTurnstile
import com.mapbox.android.telemetry.MapboxTelemetry
import com.mapbox.vision.ar.ARDataProvider
import com.mapbox.vision.corewrapper.JNIVisionCoreFactory
import com.mapbox.vision.corewrapper.VisionCore
import com.mapbox.vision.corewrapper.update.RoadRestrictionsListener
import com.mapbox.vision.corewrapper.update.VisionEventsListener
import com.mapbox.vision.location.LocationEngine
import com.mapbox.vision.location.LocationEngineListener
import com.mapbox.vision.location.android.AndroidLocationEngineImpl
import com.mapbox.vision.models.CameraParams
import com.mapbox.vision.models.DeviceMotionData
import com.mapbox.vision.models.FrameStatistics
import com.mapbox.vision.models.GPSData
import com.mapbox.vision.models.HeadingData
import com.mapbox.vision.models.route.NavigationRoute
import com.mapbox.vision.performance.ModelPerformanceConfig
import com.mapbox.vision.sensors.SensorDataListener
import com.mapbox.vision.sensors.SensorsRequestsManager
import com.mapbox.vision.telemetry.MapboxTelemetryEventManager
import com.mapbox.vision.telemetry.TelemetryImageSaver
import com.mapbox.vision.telemetry.TelemetryManager
import com.mapbox.vision.utils.FileUtils
import com.mapbox.vision.utils.threads.MainThreadHandler
import com.mapbox.vision.utils.threads.WorkThreadHandler
import com.mapbox.vision.video.videoprocessor.VideoProcessor
import com.mapbox.vision.video.videoprocessor.VideoProcessorListener
import com.mapbox.vision.video.videosource.VideoRecordingListener
import com.mapbox.vision.video.videosource.VideoSource
import com.mapbox.vision.video.videosource.VideoSourceListener
import com.mapbox.vision.video.videosource.camera.Camera2VideoSourceImpl
import com.mapbox.vision.video.videosource.camera.SurfaceVideoRecorder
import com.mapbox.vision.video.videosource.camera.VideoRecorder
import com.mapbox.vision.video.videosource.media.FileVideoSource
import com.mapbox.vision.view.VisualizationUpdateListener
import com.mapbox.vision.visionevents.CalibrationProgress
import com.mapbox.vision.visionevents.FrameSize
import com.mapbox.vision.visionevents.LaneDepartureState
import com.mapbox.vision.visionevents.ScreenCoordinate
import com.mapbox.vision.visionevents.WorldCoordinate
import com.mapbox.vision.visionevents.events.position.Position
import com.mapbox.vision.visionevents.events.roaddescription.RoadDescription
import com.mapbox.vision.visionevents.events.worlddescription.WorldDescription
import java.io.File
import java.lang.ref.WeakReference

/**
 * The main object for registering for events from the library,
 * starting and stopping their delivery.
 *
 * It also provides some useful functions for performance configuration and data conversion.
 */
object VisionManager : ARDataProvider {

    private const val MAPBOX_VISION_IDENTIFIER = "MapboxVision"
    private const val MAPBOX_TELEMETRY_USER_AGENT = "$MAPBOX_VISION_IDENTIFIER/${BuildConfig.VERSION_NAME}"
    private const val TAG = "VisionManager"

    // Video buffer length
    private const val RESTART_SESSION_RECORDING_DELAY_MILLIS = 5 * 60 * 1000L

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
    private lateinit var videoRecorder: VideoRecorder
    private lateinit var sensorsRequestsManager: SensorsRequestsManager
    private lateinit var locationEngine: LocationEngine
    private lateinit var videoProcessor: VideoProcessor

    private lateinit var telemetryManager: TelemetryManager
    private val telemetryImageSaver = TelemetryImageSaver()

    private var currentTelemetryDir: String = ""
    private var previousTelemetryDir: String = ""
    private var startRecordCoreMillis = 0L
    private var clipTimes: List<VideoProcessor.VideoPart> = emptyList()

    // Listeners
    private val videoProcessorListener = object : VideoProcessorListener {
        override fun onVideoPartsReady(
                videoPartMap: HashMap<String, VideoProcessor.VideoPart>,
                dirPath: String,
                jsonFilePath: String
        ) {
//            telemetryManager.syncSessionDir(dirPath)
        }
    }

    private val locationEngineListener = object : LocationEngineListener {
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

    private val sensorDataListener = object : SensorDataListener {
        override fun onDeviceMotionDataReady(deviceMotionData: DeviceMotionData) {
            visionCore.setDeviceMotionData(deviceMotionData)
        }

        override fun onHeadingDataReady(headingData: HeadingData) {
            visionCore.setHeadingData(headingData)
        }
    }

    private val videoSourceListener = object : VideoSourceListener {
        override fun onNewFrame(rgbBytes: ByteArray) {
            visionCore.setRGBABytes(rgbBytes, videoSource.getSourceWidth(), videoSource.getSourceHeight())
        }

        override fun onNewCameraParams(cameraParams: CameraParams) {
            visionCore.setCameraParams(cameraParams)
        }
    }

    private val recordingListener = object : VideoRecordingListener {
        override fun onVideoRecorded(path: String) {
            videoProcessor.splitVideoToParts(
                    parts = clipTimes,
                    fullVideoPath = path,
                    saveDirPath = previousTelemetryDir,
                    startRecordCoreMillis = startRecordCoreMillis
            )
        }
    }

    // Event Listeners
    private var visionEventsListener: WeakReference<VisionEventsListener>? = null
    private var roadRestrictionsListener: WeakReference<RoadRestrictionsListener>? = null
    private var visualizationUpdateListener: WeakReference<VisualizationUpdateListener>? = null
    private var videoStreamListener: WeakReference<VideoStreamListener>? = null

    private var isCreated = false
    private var isStarted = false
    private var isTurnstileEventSent = false

    /**
     * Initialize SDK with mapbox access token and application instance.
     * Do it once per application session, eg in [android.app.Application.onCreate].
     */
    fun init(application: Application, mapboxToken: String) {
        this.mapboxToken = mapboxToken
        this.application = application
    }

    private val RECORDINGS_BASE_PATH by lazy {
        "${application.getExternalFilesDir(null)!!.absolutePath}/Replays"
    }
    private val LOCK_FILE by lazy {
        "$RECORDINGS_BASE_PATH/.lock"
    }
    private val RECORDED_TELEMETRY_PATH by lazy {
        "$RECORDINGS_BASE_PATH/Telemetry/"
    }
    private val RECORDED_VIDEO_PATH by lazy {
        "$RECORDINGS_BASE_PATH/Video/"
    }
    private var setTelemetry: Boolean = false

    /**
     * Initialize SDK. Creates core services and allocates necessary resources.
     * Typically is called when application need to launch Vision SDK, eg. [android.app.Activity.onCreate].
     * You should [destroy] when Vision SDK is no longer needed to release all resources.
     * No-op if called while SDK is created already.
     */
    fun create(videoSource: VideoSource =
            FileVideoSource(
                    application,
                    videoFiles = File(RECORDED_VIDEO_PATH)
                            .listFiles { _: File?, name: String? ->
                                name?.contains("mp4") ?: false
                            }
                            .sorted(),
                    onVideoStarted = {
                        val lockFile = File(LOCK_FILE)
                        if (!lockFile.exists()) {
                            lockFile.createNewFile()
                        }
                        stopSessionRecording()
                        startSessionRecording(it)
                    },
                    onVideosEnded = {
                        File(LOCK_FILE).delete()
                        stopSessionRecording()
                    }
            )
    ) {
        checkManagerInit()
        if (isCreated) {
            Log.w(TAG, "VisionManager was already created!")
            return
        }

        mapboxTelemetry = MapboxTelemetry(application, mapboxToken, MAPBOX_TELEMETRY_USER_AGENT)
        mapboxTelemetry.updateDebugLoggingEnabled(BuildConfig.DEBUG)

        if (!isTurnstileEventSent) {
            mapboxTelemetry.push(
                    AppUserTurnstile(MAPBOX_VISION_IDENTIFIER, BuildConfig.VERSION_NAME)
            )
            isTurnstileEventSent = true
        }

        this.videoSource = videoSource
        when (videoSource) {
            is Camera2VideoSourceImpl -> {
                val videoRecorder = SurfaceVideoRecorder.MediaCodecPersistentSurfaceImpl(
                        application = VisionManager.application,
                        buffersDir = FileUtils.getDataDirPath(VisionManager.application),
                        recordingListener = recordingListener,
                        sensorOrientation = videoSource.sensorOrientation,
                        frameWidth = videoSource.getSourceWidth(),
                        frameHeight = videoSource.getSourceHeight()
                )
                this.videoRecorder = videoRecorder
                videoSource.setRecordingSurface(videoRecorder.surface)
            }
            else -> {
                // TODO implement video recording for external VideoSources.
            }
        }
        visionCore = JNIVisionCoreFactory(
                application = application,
                eventManager = MapboxTelemetryEventManager(mapboxTelemetry),
                imageSaver = telemetryImageSaver
        )
                .createVisionCore(
                        width = videoSource.getSourceWidth(),
                        height = videoSource.getSourceHeight()
                )

        sensorsRequestsManager = SensorsRequestsManager(application)
        sensorsRequestsManager.setSensorDataListener(sensorDataListener)
        locationEngine = AndroidLocationEngineImpl(application)
        videoProcessor = VideoProcessor.Impl()

        telemetryManager = TelemetryManager.Impl(mapboxTelemetry, application)

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
        visionCore.setRoadRestrictionsListener(roadRestrictionsListener)
        visionCore.setVisualizationUpdateListener(visualizationUpdateListener)
        visionCore.setVideoStreamListener(videoStreamListener)
        visionCore.onResume()

        videoProcessor.setVideoProcessorListener(videoProcessorListener)

        startAllHandlers()

        videoSource.attach(videoSourceListener)

        sensorsRequestsManager.startDataRequesting()
        locationEngine.attach(locationEngineListener)

        coreUpdateThreadHandler.post {
            setTelemetry = File(RECORDED_TELEMETRY_PATH).exists()
            requestCoreUpdate()
        }
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

        locationEngine.detach()
        sensorsRequestsManager.stopDataRequesting()
        stopAllHandlers()
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

        visionCore.release()
        videoRecorder.release()
        videoProcessor.stop()

        isCreated = false
    }

    /**
     * Set listener to listen SDK events.
     */
    fun setVisionEventListener(visionEventsListener: VisionEventsListener) {
        this.visionEventsListener = WeakReference(visionEventsListener)
        if (isCreated) {
            visionCore.setVisionEventListener(this.visionEventsListener)
        }
    }

    /**
     * Set listener to listen speed limit events.
     */
    fun setRoadRestrictionsListener(roadRestrictionsListener: RoadRestrictionsListener) {
        this.roadRestrictionsListener = WeakReference(roadRestrictionsListener)
        if (isCreated) {
            visionCore.setRoadRestrictionsListener(this.roadRestrictionsListener)
        }
    }

    /**
     * @return current road situation or NULL in case of data is not ready .
     *
     * @throws [IllegalStateException] if called before [create] and [start]
     */
    fun getRoadDescription(): RoadDescription? {
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
     * @return current [LaneDepartureState] estimated by SDK.
     *
     * @throws [IllegalStateException] if called before [create] and [start]
     */
    fun getLaneDepartureState(): LaneDepartureState {
        checkManagerRunningState()
        return visionCore.getLaneDepartureState()
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
        if (isCreated) {
            visionCore.setVisualizationUpdateListener(this.visualizationUpdateListener)
        }
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
        coreUpdateThreadHandler.postDelayed(
                { requestCoreUpdate() },
                CORE_UPDATE_DELAY_MILLIS
        )
        visionCore.requestUpdate()
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

    private fun generateTelemetryDir(videoName: String): String {
        val file = File("${application.getExternalFilesDir(null)!!.absolutePath}/Telemetry/$videoName")
        if (!file.exists() && !file.mkdirs()) {
            return ""
        }

        return "${file.absolutePath}/"
    }

    private fun startSessionRecording(videoName: String) {
        currentTelemetryDir = generateTelemetryDir(videoName)
        telemetryImageSaver.setSessionDir(currentTelemetryDir)
        visionCore.startDataSavingSession(currentTelemetryDir)
        startRecordCoreMillis = visionCore.getCoreMilliseconds()
    }

    private fun stopSessionRecording() {
        visionCore.stopDataSavingSession()
        clipTimes = visionCore.getAndResetClipsTimeList()
        previousTelemetryDir = currentTelemetryDir
        currentTelemetryDir = ""
    }
}
