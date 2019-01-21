package com.mapbox.vision.corewrapper

import com.mapbox.vision.VideoStreamListener
import com.mapbox.vision.corewrapper.update.RoadRestrictionsListener
import com.mapbox.vision.corewrapper.update.VisionEventsListener
import com.mapbox.vision.models.CameraParams
import com.mapbox.vision.models.DeviceMotionData
import com.mapbox.vision.models.GPSData
import com.mapbox.vision.models.HeadingData
import com.mapbox.vision.models.route.NavigationRoute
import com.mapbox.vision.performance.ModelPerformanceConfig
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

/**
 * Main interface for ML Core
 */
internal interface VisionCore {

    val imageWidth: Int
    val imageHeight: Int

    val imageFormat: Image.Format

    fun setVisionEventListener(visionEventsListener: WeakReference<VisionEventsListener>?)

    fun setRoadRestrictionsListener(roadRestrictionsListener: WeakReference<RoadRestrictionsListener>?)

    fun setVisualizationUpdateListener(visualizationUpdateListener: WeakReference<VisualizationUpdateListener>?)

    fun setVideoStreamListener(videoStreamListener: WeakReference<VideoStreamListener>?)

    fun setModelPerformanceConfig(modelPerformanceConfig: ModelPerformanceConfig)

    fun setRGBABytes(rgbaByteArray: ByteArray, width: Int, height: Int)

    fun playTelemetry(telemetryPath: String)

    fun setTelemetryTimestamp(timestamp: Long)

    fun requestUpdate()

    fun setGPSData(gpsData: GPSData)

    fun setDeviceMotionData(deviceMotionData: DeviceMotionData)

    fun setHeadingData(headingData: HeadingData)

    fun setCameraParams(cameraParams: CameraParams)

    fun setRouteData(navigationRoute: NavigationRoute?)

    fun startDataSavingSession(dataDirPath: String)

    fun stopDataSavingSession()

    fun getWorldDescription(): WorldDescription

    fun getRoadDescription(): RoadDescription?

    fun getPosition(): Position

    fun getCalibrationProgress(): CalibrationProgress

    fun getAndResetClipsTimeList(): List<VideoProcessor.VideoPart>

    fun getCoreMilliseconds(): Long

    fun getARCameraParams(): FloatArray?

    fun getARRouteData(): DoubleArray?

    fun getLaneDepartureState() : LaneDepartureState

    fun getFrameStatistics(): FloatArray

    fun worldToPixel(worldCoordinate: WorldCoordinate): ScreenCoordinate

    fun pixelToWorld(screenCoordinate: ScreenCoordinate): WorldCoordinate

    fun onPause()

    fun onResume()

    fun release()
}
