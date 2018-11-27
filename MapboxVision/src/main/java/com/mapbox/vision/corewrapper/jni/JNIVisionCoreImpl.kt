package com.mapbox.vision.corewrapper.jni

import android.app.Application
import android.support.annotation.WorkerThread
import android.util.Log
import com.mapbox.vision.VideoStreamListener
import com.mapbox.vision.core.CoreWrapper
import com.mapbox.vision.core.events.EventManager
import com.mapbox.vision.core.events.ImageSaver
import com.mapbox.vision.core.map.MapDataSource
import com.mapbox.vision.corewrapper.VisionCore
import com.mapbox.vision.corewrapper.update.RoadRestrictionsListener
import com.mapbox.vision.corewrapper.update.VisionEventsListener
import com.mapbox.vision.corewrapper.update.jni.JNICoreUpdateManager
import com.mapbox.vision.models.CameraParamsData
import com.mapbox.vision.models.DeviceMotionData
import com.mapbox.vision.models.GPSData
import com.mapbox.vision.models.HeadingData
import com.mapbox.vision.models.route.NavigationRoute
import com.mapbox.vision.performance.*
import com.mapbox.vision.video.videoprocessor.VideoProcessor
import com.mapbox.vision.view.VisualizationUpdateListener
import com.mapbox.vision.visionevents.CalibrationProgress
import com.mapbox.vision.visionevents.LaneDepartureState
import com.mapbox.vision.visionevents.ScreenCoordinate
import com.mapbox.vision.visionevents.WorldCoordinate
import com.mapbox.vision.visionevents.events.Image
import com.mapbox.vision.visionevents.events.position.Position
import com.mapbox.vision.visionevents.events.roaddescription.RoadDescription
import com.mapbox.vision.visionevents.events.worlddescription.WorldDescription
import java.lang.ref.WeakReference


internal class JNIVisionCoreImpl constructor(
        override val imageWidth: Int,
        override val imageHeight: Int,
        override val imageFormat: Image.Format,
        mapDataSource: MapDataSource,
        externalFileDir: String,
        application: Application,
        mapboxTelemetryEventManager: EventManager,
        imageSaver: ImageSaver
) : VisionCore {

    private val coreWrapper = CoreWrapper(
            application,
            mapDataSource,
            externalFileDir,
            mapboxTelemetryEventManager,
            imageSaver
    )

    private val jniCoreUpdateManager = JNICoreUpdateManager(coreWrapper, application, imageWidth, imageHeight)
    private val jniCorePerformanceManager = PerformanceManagerFactory.getPerformanceManager(coreWrapper)

    private var isSessionRecording = false

    init {
        mapDataSource.onSuccessCallback = { response: String, url: String ->
            coreWrapper.onMatchResponse(response, url)
        }
        coreWrapper.setCoreConfig(
                debugOverlayUpdate = false,
                drawSegMaskInDebug = false,
                drawCurLaneInDebug = false,
                drawMarkingLanesInDebug = false,
                drawRouteInDebug = false,
                saveTelemetry = true,
                solveCameraWorldTransform = true,
                useCarDistanceMeasure = true,
                useClassification = true,
                useDetectionMapping = true,
                useRoadConfidence = false,
                useTracking = true,
                useTrajectoryEstimator = true,
                useDetection = true,
                useSegmentation = true,
                useMergeModel = true
        )

        jniCorePerformanceManager.setModelConfig(
                ModelPerformanceConfig.Merged(
                        performance = ModelPerformance.On(ModelPerformanceMode.FIXED, ModelPerformanceRate.HIGH)
                )
        )
    }

    override fun setVisionEventListener(visionEventsListener: WeakReference<VisionEventsListener>?) {
        jniCoreUpdateManager.setVisionEventListener(visionEventsListener)
    }

    override fun setRoadRestrictionsListener(roadRestrictionsListener: WeakReference<RoadRestrictionsListener>?) {
        jniCoreUpdateManager.setRoadRestrictionsListener(roadRestrictionsListener)
    }

    override fun setVisualizationUpdateListener(visualizationUpdateListener: WeakReference<VisualizationUpdateListener>?) {
        jniCoreUpdateManager.setVisualizationUpdateListener(visualizationUpdateListener)
    }

    override fun setVideoStreamListener(videoStreamListener: WeakReference<VideoStreamListener>?) {
        jniCoreUpdateManager.setVideoSourceListener(videoStreamListener)
    }

    override fun setModelPerformanceConfig(modelPerformanceConfig: ModelPerformanceConfig) {
        coreWrapper.setUseMergedModel(
                useMergeModel = when (modelPerformanceConfig) {
                    is ModelPerformanceConfig.Merged -> true
                    is ModelPerformanceConfig.Separate -> false
                }
        )
        jniCorePerformanceManager.setModelConfig(modelPerformanceConfig)
    }

    override fun setRGBABytes(rgbaByteArray: ByteArray, width: Int, height: Int) {
        jniCoreUpdateManager.setRGBABytes(rgbaByteArray, width, height)
    }

    override fun setGPSData(gpsData: GPSData) {
        coreWrapper.setGPSData(
                latitude = gpsData.latitude,
                longitude = gpsData.longitude,
                speed = gpsData.speed,
                altitude = gpsData.altitude,
                horizontalAccuracy = gpsData.horizontalAccuracy,
                verticalAccuracy = gpsData.verticalAccuracy,
                bearing = gpsData.bearing,
                timestamp = gpsData.timestamp
        )
    }

    override fun setDeviceMotionData(deviceMotionData: DeviceMotionData) {
        coreWrapper.setDeviceMotionData(
                rotations = deviceMotionData.rotations,
                orientations = deviceMotionData.orientations,
                screenOrientation = deviceMotionData.screenOrientation,
                gravity = deviceMotionData.gravity,
                userAccelerationRelativeToGravity = deviceMotionData.userAccelerationRelativeToGravity,
                heading = deviceMotionData.heading
        )
    }

    override fun setHeadingData(headingData: HeadingData) {
        coreWrapper.setHeadingData(
                heading = headingData.heading,
                geomagnetism = headingData.geomagnetism,
                timestamp = headingData.timestamp
        )
    }

    override fun setCameraParamsData(cameraParamsData: CameraParamsData) {
        coreWrapper.setCameraParams(
                weight = cameraParamsData.width,
                height = cameraParamsData.height,
                focalLength = cameraParamsData.focalLength,
                focalXPixels = cameraParamsData.focalInPixelsX,
                focalYPixels = cameraParamsData.focalInPixelsY
        )
    }

    override fun setRouteData(navigationRoute: NavigationRoute?) {
        if (navigationRoute == null) {
            coreWrapper.setRoute(emptyArray())
            return
        }
        val routePointsArray = Array(navigationRoute.routePoints.size) { DoubleArray(3) }
        var i = 0
        navigationRoute.routePoints.forEach {
            routePointsArray[i][0] = it.latitude
            routePointsArray[i][1] = it.longitude
            routePointsArray[i][2] = if (it.isManeuver) {
                1.0
            } else {
                0.0
            }
            i++
        }
        coreWrapper.setRoute(routePointsArray)
    }

    override fun startDataSavingSession(dataDirPath: String) {
        if (isSessionRecording) {
            Log.e(TAG, "SavingSession is already started")
            return
        }
        coreWrapper.startSavingSession(dataDirPath)
        isSessionRecording = true
    }

    override fun stopDataSavingSession() {
        if (!isSessionRecording) {
            Log.e(TAG, "SavingSession is not started yet")
            return
        }
        coreWrapper.stopSavingSession()
        isSessionRecording = false
    }

    override fun getWorldDescription(): WorldDescription = jniCoreUpdateManager.getCurrentWorldDescription()

    override fun getRoadDescription(): RoadDescription? = jniCoreUpdateManager.getCurrentRoadDescription()

    override fun getPosition(): Position = jniCoreUpdateManager.getCurrentPosition()

    override fun getCalibrationProgress(): CalibrationProgress = jniCoreUpdateManager.getCalibrationProgress()

    override fun getAndResetClipsTimeList(): List<VideoProcessor.VideoPart> {
        val floatParts = coreWrapper.getAndResetClipsTimeList()

        val videoPartList = ArrayList<VideoProcessor.VideoPart>()
        var index = 0
        for (i in 0 until floatParts.size / 2) {
            videoPartList.add(VideoProcessor.VideoPart(
                    (floatParts[index++] * 1000).toLong(),
                    (floatParts[index++] * 1000).toLong()
            ))
        }
        return videoPartList
    }

    override fun getCoreMilliseconds(): Long {
        val seconds = coreWrapper.getCoreSeconds()
        return (seconds * 1000).toLong()
    }

    override fun getARCameraParams(): FloatArray = coreWrapper.getARCameraParams()

    override fun getARRouteData(): DoubleArray? {
        val data = coreWrapper.getARRouteData()
        if (data.isEmpty()) {
            return null
        }
        return data
    }

    override fun getLaneDepartureState(): LaneDepartureState = jniCoreUpdateManager.getLaneDepartureState()

    override fun getFrameStatistics(): FloatArray = coreWrapper.getFrameStat()

    @WorkerThread
    override fun requestUpdate() {
        // Currently the core update process is controlled by Manager
        jniCoreUpdateManager.requestUpdate()
    }

    override fun worldToPixel(worldCoordinate: WorldCoordinate): ScreenCoordinate {
        val worldCoordinateArray = doubleArrayOf(worldCoordinate.x, worldCoordinate.y, worldCoordinate.z)
        val result = coreWrapper.worldToPixel(worldCoordinateArray)
        return ScreenCoordinate(result[0], result[1])
    }

    override fun pixelToWorld(screenCoordinate: ScreenCoordinate): WorldCoordinate {
        val screenCoordinateArray = intArrayOf(screenCoordinate.x, screenCoordinate.y)
        val result = coreWrapper.pixelToWorld(screenCoordinateArray)
        return WorldCoordinate(result[0], result[1], result[2])
    }

    override fun onPause() {
        jniCoreUpdateManager.onPause()
        coreWrapper.onPause()
    }

    override fun onResume() {
        coreWrapper.onResume()
        jniCoreUpdateManager.onResume()
    }

    override fun release() {
        coreWrapper.release()
        jniCoreUpdateManager.release()
    }

    companion object {
        private const val TAG = "JNICoreImpl"
    }
}
